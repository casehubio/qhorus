package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.NlpTemplateRegistry;
import io.casehub.template.registry.nlp.OpenNlpTokenizer;
import io.casehub.yaml.core.resolver.VariableConstraint;

/**
 * End-to-end proof that template matching — the perception layer of communication
 * governance — works from qhorus's classpath.
 *
 * <p>The scenario throughout: a governed architecture review where three agents
 * (architect, security-lead, tech-lead) must vote on a proposed change. The
 * governance vocabulary is defined as templates with typed constraints. Agents
 * write natural language; the infrastructure extracts structured governance acts.
 *
 * <p>This test validates three things:
 * <ol>
 *   <li>NLP extraction handles grammatical variation — past tense, plural,
 *       determiners — producing the same typed fields regardless of phrasing</li>
 *   <li>Unstructured prose is rejected — governance acts must conform to
 *       published templates to be recognised</li>
 *   <li>The advisory flow connects template matching to qhorus's enforcement
 *       pipeline via PlaybookChannelProtocol</li>
 * </ol>
 */
class TemplateMatchIntegrationTest {

    private NlpTemplateRegistry registry;

    /**
     * The governance vocabulary: two templates define the protocol's
     * recognised actions.
     *
     * <p>Template: "vote" — an agent casts a governance decision.
     * Pattern: "I ${decision} this ${subject}[ because ${rationale}]"
     * where decision is constrained to {approve, reject, abstain}.
     *
     * <p>Template: "escalate" — an agent routes an issue upward.
     * Pattern: "Escalating to ${target}[ due to ${reason}]"
     */
    @BeforeEach
    void defineGovernanceVocabulary() {
        registry = new NlpTemplateRegistry(new OpenNlpTokenizer());

        var vote = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}[ because ${rationale}]")
                .variable("decision", VariableConstraint.enumOf(
                        "approve", "reject", "abstain"))
                .variable("subject", VariableConstraint.string())
                .variable("rationale", VariableConstraint.string().optional())
                .build();

        var escalate = TemplateDefinition.builder("escalate")
                .pattern("Escalating to ${target}[ due to ${reason}]")
                .variable("target", VariableConstraint.string())
                .variable("reason", VariableConstraint.string().optional())
                .build();

        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        templates.put("escalate", escalate);
        registry.register(new TemplateNamespace("protocol:review", templates));
    }

    /**
     * NLP extraction handles how LLMs actually write.
     *
     * <p>An LLM might write "I approve", "I approved", or "I approves" —
     * all meaning the same governance action. The NLP pipeline lemmatises
     * inflected words to their base form, so all variants produce
     * {@code decision=approve}. Without this, regex-only matching fails
     * on any tense or agreement variation.
     */
    @Nested
    class AgentProseBecomesStructuredGovernance {

        @ParameterizedTest(name = "agent writes \"{0}\" → decision={1}")
        @CsvSource({
                "I approve this design,          approve",
                "I approved this architecture,   approve",
                "I reject this proposal,         reject",
                "I rejected this API change,     reject",
                "I abstain this vote,            abstain"
        })
        void verbInflectionNormalisesToEnumValue(String agentProse,
                                                 String expectedDecision) {
            var result = registry.match("protocol:review", agentProse);
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);

            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.templateName()).isEqualTo("vote");
            assertThat(matched.fields().get("decision")).isEqualTo(expectedDecision);
        }

        @Test
        void rationaleExtractedVerbatim_whenPresent() {
            var matched = (TemplateMatch.Matched) registry.match("protocol:review",
                    "I reject this proposal because it lacks test coverage");

            assertThat(matched.fields().get("decision")).isEqualTo("reject");
            assertThat(matched.fields().get("rationale"))
                    .contains("test coverage");
        }

        @Test
        void rationaleAbsent_whenOmitted() {
            var matched = (TemplateMatch.Matched) registry.match("protocol:review",
                    "I approve this design");

            assertThat(matched.fields()).doesNotContainKey("rationale");
        }

        @Test
        void escalationExtractsTargetAndReason() {
            var matched = (TemplateMatch.Matched) registry.match("protocol:review",
                    "Escalating to the security team due to unresolved compliance gaps");

            assertThat(matched.templateName()).isEqualTo("escalate");
            assertThat(matched.fields().get("target")).contains("security");
            assertThat(matched.fields().get("reason")).contains("compliance");
        }
    }

    /**
     * Not everything matches — governance acts must be recognisable.
     *
     * <p>Casual conversation, discussion, and reasoning are not governance
     * acts. The template registry rejects prose that does not conform to
     * a registered template. This precision is the point: agents converse
     * freely, but governed outcomes must be structurally unambiguous.
     */
    @Nested
    class UngoverernedProseIsRejected {

        @ParameterizedTest(name = "rejects: \"{0}\"")
        @ValueSource(strings = {
                "Looks good to me, let's proceed",
                "I think we should go with option B",
                "Great work on the implementation",
                "Can we schedule a meeting to discuss?",
                "The deployment pipeline is running smoothly"
        })
        void conversationalInputDoesNotMatch(String casualProse) {
            var result = registry.match("protocol:review", casualProse);
            assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
        }

        @Test
        void wrongEnumValueRejected_evenWhenStructureMatches() {
            var result = registry.match("protocol:review",
                    "I celebrate this achievement");
            assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    /**
     * Templates are bidirectional — extract fields from prose and generate
     * conformant prose from fields.
     *
     * <p>This is how schema publication closes the loop: the infrastructure
     * tells an agent what sentence shapes the protocol expects, the agent
     * conforms, and extraction is mechanical. Generation uses the same
     * template as extraction, so a round trip preserves every field.
     */
    @Nested
    class BidirectionalExtractAndGenerate {

        @Test
        void extractThenGenerate_preservesAllFields() {
            String input = "I approve this design";
            var matched = (TemplateMatch.Matched) registry.match(
                    "protocol:review", input);

            String generated = registry.generate(
                    "protocol:review", matched.templateName(), matched.fields());
            assertThat(generated).isEqualTo(input);
        }

        @Test
        void generateFromFields_thenReExtract() {
            String generated = registry.generate("protocol:review", "vote",
                    Map.of("decision", "reject",
                            "subject", "the proposed API",
                            "rationale", "it breaks backward compatibility"));

            var rematched = (TemplateMatch.Matched) registry.match(
                    "protocol:review", generated);
            assertThat(rematched.fields().get("decision")).isEqualTo("reject");
            assertThat(rematched.fields().get("rationale"))
                    .isEqualTo("it breaks backward compatibility");
        }

        @Test
        void optionalSegmentOmitted_whenNotProvided() {
            String generated = registry.generate("protocol:review", "vote",
                    Map.of("decision", "approve", "subject", "architecture"));
            assertThat(generated).isEqualTo("I approve this architecture");
        }
    }

    /**
     * Schema publication — agents discover what the protocol expects.
     *
     * <p>When templates are registered, the NLP pipeline compiles each
     * pattern into structural elements (LITERAL with POS tag and lemma,
     * CLAUSE for variable positions). This is published as a schema that
     * tells an LLM: "this protocol expects sentences that look like this,
     * with these variable positions and these allowed values."
     */
    @Nested
    class SchemaPublicationForAgentConformance {

        @Test
        void schemaExposesVariableConstraints() {
            var schema = registry.schema("protocol:review", "vote");
            assertThat(schema).isPresent();
            assertThat(schema.get().variables())
                    .containsKeys("decision", "subject", "rationale");
        }

        @Test
        void nlpCompilesStructuralElements() {
            var schema = registry.schema("protocol:review", "vote").orElseThrow();
            assertThat(schema.structuralSummaries()).isNotEmpty();

            var elements = schema.structuralSummaries().get(0).elements();
            assertThat(elements.stream().anyMatch(e -> "LITERAL".equals(e.kind())))
                    .as("LITERAL elements carry POS tags and lemmas")
                    .isTrue();
            assertThat(elements.stream().anyMatch(e -> "CLAUSE".equals(e.kind())))
                    .as("CLAUSE elements mark variable positions")
                    .isTrue();
        }

        @Test
        void allTemplatesInNamespaceDiscoverable() {
            var schemas = registry.schemas("protocol:review");
            assertThat(schemas).hasSize(2);
        }
    }

    /**
     * The enforcement bridge — template match results flow through qhorus's
     * protocol enforcement pipeline as DispatchAdvisory instances.
     *
     * <p>PlaybookChannelProtocol wraps a PlaybookExecutor. The executor runs
     * the playbook (which includes the match step). The playbook returns
     * advisories based on template match results. The protocol converts
     * those to DispatchAdvisory — the currency of qhorus enforcement.
     *
     * <p>These tests simulate the executor's output — the real match step
     * in PlaybookRunnerCore produces this same map structure. The qhorus
     * test proves the advisory plumbing works; the platform test proves
     * the match step itself works.
     */
    @Nested
    class EnforcementPipelineIntegration {

        @Test
        void matchedGovernanceAct_noAdvisory() {
            PlaybookExecutor executor = (yaml, vars) -> {
                var match = registry.match("protocol:review",
                        "I approve this design");
                assertThat(match).isInstanceOf(TemplateMatch.Matched.class);
                return Map.of("advisories", List.of());
            };

            var protocol = new PlaybookChannelProtocol(
                    "protocol:review", "version: 1.0", executor);

            List<DispatchAdvisory> advisories = protocol.evaluate(protocolContext());
            assertThat(advisories).isEmpty();
        }

        @Test
        void unmatchedProse_producesWarningAdvisory() {
            PlaybookExecutor executor = (yaml, vars) -> {
                var match = registry.match("protocol:review",
                        "Looks good to me");
                assertThat(match).isInstanceOf(TemplateMatch.Unmatched.class);
                return Map.of("advisories", List.of(Map.of(
                        "severity", "WARNING",
                        "message", "Message does not match any governance template")));
            };

            var protocol = new PlaybookChannelProtocol(
                    "protocol:review", "version: 1.0", executor);

            List<DispatchAdvisory> advisories = protocol.evaluate(protocolContext());
            assertThat(advisories).hasSize(1);
            assertThat(advisories.get(0).severity()).isEqualTo(Severity.WARNING);
            assertThat(advisories.get(0).message())
                    .contains("governance template");
        }

        @Test
        void advisorySource_carriesProtocolName() {
            PlaybookExecutor executor = (yaml, vars) -> Map.of(
                    "advisories", List.of(Map.of(
                            "severity", "CRITICAL",
                            "message", "Negation detected")));

            var protocol = new PlaybookChannelProtocol(
                    "protocol:review", "version: 1.0", executor);

            List<DispatchAdvisory> advisories = protocol.evaluate(protocolContext());
            assertThat(advisories.get(0).source())
                    .as("advisory source = protocol name, for enforcement filtering")
                    .isEqualTo("protocol:review");
        }

        private ProtocolContext protocolContext() {
            return new ProtocolContext(
                    UUID.randomUUID(), "architecture-review",
                    MessageType.STATUS, "architect", null,
                    List.of("architect", "security-lead", "tech-lead"),
                    List.of(), List.of(), Map.of());
        }
    }
}
