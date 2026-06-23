package io.casehub.qhorus.examples;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Multi-model sweep: Claude (Haiku/Sonnet) — all zones, all variants.
 *
 * <p>
 * Milestone D (#299). Currently disabled — requires Anthropic API key.
 *
 * <p>
 * <strong>To enable:</strong>
 * <ol>
 *   <li>Set environment variable: {@code export ANTHROPIC_API_KEY=sk-ant-...}</li>
 *   <li>In {@code pom.xml}, swap {@code quarkus-langchain4j-jlama} for
 *       {@code quarkus-langchain4j-anthropic}</li>
 *   <li>In {@code application.properties}, uncomment the Anthropic section and
 *       set the desired model (Haiku recommended for cost; Sonnet for quality)</li>
 *   <li>Remove the {@code @Disabled} annotation below</li>
 *   <li>Run: {@code mvn test -Pwith-llm-examples -Dtest=Zone1Zone2Zone3ClaudeTest}</li>
 * </ol>
 *
 * <p>
 * <strong>Why Claude matters for the paper:</strong> ImpossibleBench found that
 * stronger models cheat <em>more</em> — they construct convincing lies rather than
 * confused failures. If Claude sends false DONE more confidently than 1B, it
 * demonstrates that normative infrastructure + Zone 3 is universally necessary,
 * not just a small-model fix.
 *
 * <p>
 * The test body mirrors {@link Zone1Zone2Zone3Jlama1BTest} exactly — only the
 * model configuration differs. Once the Anthropic profile is active, copy the
 * test body and change the MODEL constant.
 *
 * <p>
 * Refs #299.
 */
@Disabled("Requires ANTHROPIC_API_KEY and Anthropic dependency — see class Javadoc to enable")
class Zone1Zone2Zone3ClaudeTest {

    @Test
    void placeholder() {
        // Remove @Disabled on the class and implement following Zone1Zone2Zone3Jlama1BTest.
        // Change MODEL constant to the Claude model name (e.g. "claude-haiku-4-5-20251001").
        // Swap pom.xml dependency and uncomment application.properties Anthropic section.
    }
}
