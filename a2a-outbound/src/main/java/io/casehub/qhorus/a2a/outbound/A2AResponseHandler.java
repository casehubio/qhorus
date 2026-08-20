package io.casehub.qhorus.a2a.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.a2a.model.A2AArtifact;
import io.casehub.a2a.model.A2APart;
import io.casehub.a2a.model.A2ATask;
import io.casehub.a2a.model.A2ATaskState;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class A2AResponseHandler {

    private final ObjectMapper mapper;

    @Inject
    public A2AResponseHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public MessageDispatch mapResponse(A2ATask task, ResponseContext ctx) {
        A2ATaskState state = task.status().state();
        MessageType  type  = mapState(state);

        String content = extractContent(task);
        String payload = extractPayload(task, state);

        MessageDispatch.Builder builder = MessageDispatch.builder()
                                                         .channelId(ctx.channelId())
                                                         .sender(ctx.externalAgentInstanceId())
                                                         .type(type)
                                                         .content(content)
                                                         .payload(payload)
                                                         .actorType(ActorType.AGENT);

        if (type == MessageType.DONE || type == MessageType.FAILURE || type == MessageType.DECLINE) {
            builder.correlationId(ctx.correlationId())
                   .inReplyTo(ctx.inReplyTo());
        }

        return builder.build();
    }

    private MessageType mapState(A2ATaskState state) {
        return switch (state) {
            case COMPLETED -> MessageType.DONE;
            case FAILED -> MessageType.FAILURE;
            case CANCELED -> MessageType.DECLINE;
            case WORKING, SUBMITTED, INPUT_REQUIRED -> MessageType.STATUS;
        };
    }

    private String extractContent(A2ATask task) {
        List<String> textParts = new ArrayList<>();

        if (task.status().message() != null) {
            for (A2APart part : task.status().message().parts()) {
                if (part instanceof A2APart.TextPart tp) {
                    textParts.add(tp.text());
                }
            }
        }

        if (task.artifacts() != null) {
            for (A2AArtifact artifact : task.artifacts()) {
                for (A2APart part : artifact.parts()) {
                    if (part instanceof A2APart.TextPart tp) {
                        textParts.add(tp.text());
                    }
                }
            }
        }

        return textParts.isEmpty() ? null : String.join("\n\n", textParts);
    }

    private String extractPayload(A2ATask task, A2ATaskState state) {
        if (state == A2ATaskState.INPUT_REQUIRED) {
            return "{\"input_required\":true}";
        }

        List<JsonNode> dataParts = new ArrayList<>();

        if (task.status().message() != null) {
            for (A2APart part : task.status().message().parts()) {
                if (part instanceof A2APart.DataPart dp) {
                    dataParts.add(dp.data());
                }
            }
        }

        if (task.artifacts() != null) {
            for (A2AArtifact artifact : task.artifacts()) {
                for (A2APart part : artifact.parts()) {
                    if (part instanceof A2APart.DataPart dp) {
                        ObjectNode artifactNode = mapper.createObjectNode();
                        artifactNode.put("name", artifact.name());
                        artifactNode.put("index", artifact.index());
                        artifactNode.set("data", dp.data());
                        dataParts.add(artifactNode);
                    }
                }
            }
        }

        if (dataParts.isEmpty()) {
            return null;
        }
        if (dataParts.size() == 1) {
            return dataParts.get(0).toString();
        }
        ArrayNode array = mapper.createArrayNode();
        dataParts.forEach(array::add);
        return array.toString();
    }
}
