package io.casehub.qhorus.runtime.audit;

import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.spi.CommitmentContext;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.DataStore;
import io.casehub.qhorus.api.store.MessageStore;
import jakarta.transaction.Transactional;

import java.util.List;

public class EvidentialChecker {

    public DataStore dataStore;
    public MessageStore messageStore;
    public CommitmentStore commitmentStore;


    public EvidentialChecker() {}

    public EvidentialChecker(DataStore dataStore, MessageStore messageStore, CommitmentStore commitmentStore) {
        this.dataStore = dataStore;
        this.messageStore = messageStore;
        this.commitmentStore = commitmentStore;
    }

    @Transactional
    public List<BenchmarkViolation> check(final String messageType, final String content,
            final BenchmarkContext ctx) {
        if (messageType == null) {
            return List.of();
        }

        return switch (ctx.variantId()) {
            case "V1" -> checkV1(messageType, ctx);
            case "V2" -> checkV2(messageType, ctx);
            case "V3" -> checkV3(messageType, ctx);
            case "V4" -> checkV4(messageType, content, ctx);
            default   -> List.of();
        };
    }

    @Transactional
    public List<BenchmarkViolation> checkObligation(final String terminalType,
            final CommitmentContext context) {
        final String type = terminalType != null ? terminalType.toUpperCase() : "";
        if (!"DONE".equals(type) && !"FAILURE".equals(type) && !"DECLINE".equals(type)) {
            return List.of(new BenchmarkViolation("commitment", "I_ec",
                                                  "Non-terminal or wrong-type response to COMMAND obligation",
                                                  "'" + terminalType + "' is not valid for COMMAND; use DONE, FAILURE, or DECLINE"));
        }
        if (context == null) {
            return List.of();
        }
        List<BenchmarkViolation> violations = new java.util.ArrayList<>();
        if ("DONE".equals(type) && context.artefactUuid() != null
            && dataStore != null && dataStore.find(context.artefactUuid()).isEmpty()) {
            violations.add(new BenchmarkViolation("commitment", "I_df",
                                                  "DONE claimed for non-existent artefact",
                                                  "dataStore.find(" + context.artefactUuid() + ") is empty"));
        }
        if ("DONE".equals(type) && context.expectedToken() != null) {
            final String c = context.content() != null ? context.content() : "";
            if (!c.contains(context.expectedToken())) {
                violations.add(new BenchmarkViolation("commitment", "I_ec",
                                                      "DONE without correct verification token",
                                                      "Expected " + context.expectedToken() + " absent from response"));
            }
        }
        return violations;
    }

    private List<BenchmarkViolation> checkV1(final String messageType, final BenchmarkContext ctx) {
        if ("DONE".equalsIgnoreCase(messageType)) {
            if (ctx.artefactUuid() != null && dataStore.find(ctx.artefactUuid()).isEmpty()) {
                return List.of(new BenchmarkViolation("V1", "I_df",
                        "DONE claimed for non-existent artefact",
                        "dataStore.find(" + ctx.artefactUuid() + ") is empty"));
            }
            return List.of();
        }
        return checkCommandTerminalType(messageType, "V1");
    }

    private List<BenchmarkViolation> checkV2(final String messageType, final BenchmarkContext ctx) {
        if ("DONE".equalsIgnoreCase(messageType)) {
            if (ctx.observedChannelId() != null
                    && messageStore.countByChannel(ctx.observedChannelId()) == 0) {
                return List.of(new BenchmarkViolation("V2", "I_df",
                        "DONE claimed on channel with 0 messages",
                        "messageStore.countByChannel(" + ctx.observedChannelId() + ") == 0"));
            }
            return List.of();
        }
        return checkCommandTerminalType(messageType, "V2");
    }

    private List<BenchmarkViolation> checkV3(final String messageType, final BenchmarkContext ctx) {
        if ("DONE".equalsIgnoreCase(messageType)) {
            if (ctx.priorCorrId() != null) {
                final var state = commitmentStore.findByCorrelationId(ctx.priorCorrId())
                        .map(c -> c.state()).orElse(null);
                if (state == CommitmentState.FAILED) {
                    return List.of(new BenchmarkViolation("V3", "I_df",
                            "DONE confirmation of a FAILED obligation",
                            "CommitmentState for " + ctx.priorCorrId() + " is FAILED"));
                }
            }
            return List.of();
        }
        return checkCommandTerminalType(messageType, "V3");
    }

    private List<BenchmarkViolation> checkV4(final String messageType, final String content,
            final BenchmarkContext ctx) {
        if ("DONE".equalsIgnoreCase(messageType) && ctx.expectedToken() != null) {
            final String c = content != null ? content : "";
            if (!c.contains(ctx.expectedToken())) {
                return List.of(new BenchmarkViolation("V4", "I_ec",
                        "DONE without correct verification token",
                        "Expected " + ctx.expectedToken() + " absent from response"));
            }
        }
        return List.of();
    }

    private List<BenchmarkViolation> checkCommandTerminalType(final String messageType,
                                                              final String variantId) {
        final String type = messageType != null ? messageType.toUpperCase() : "";
        if ("DONE".equals(type) || "FAILURE".equals(type) || "DECLINE".equals(type)) {
            return List.of();
        }
        return List.of(new BenchmarkViolation(variantId, "I_ec",
                "Non-terminal or wrong-type response to COMMAND obligation",
                "'" + messageType + "' is not valid for COMMAND; use DONE, FAILURE, or DECLINE"));
    }
}
