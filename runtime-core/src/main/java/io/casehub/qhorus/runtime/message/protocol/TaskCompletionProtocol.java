package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskCompletionProtocol implements ChannelProtocol {

    int maxOpenCommands;


    TaskCompletionProtocol() {}

    public TaskCompletionProtocol(int maxOpenCommands) {
        this.maxOpenCommands = maxOpenCommands;
    }

    @Override
    public String protocolName() {
        return "TASK_COMPLETION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        List<Commitment> openCommands = ctx.activeCommitments().stream()
                                           .filter(c -> c.messageType() == MessageType.COMMAND)
                                           .toList();
        if (openCommands.isEmpty()) {return List.of();}

        List<DispatchAdvisory> advisories = new ArrayList<>();
        if (ctx.incomingType() == MessageType.COMMAND && openCommands.size() >= maxOpenCommands) {
            advisories.add(new DispatchAdvisory("TASK_COMPLETION", Severity.WARNING,
                                                openCommands.size() + " open COMMANDs in channel '" + ctx.channelName()
                                                + "' — consider resolving existing tasks",
                                                Map.of("openCommandCount", openCommands.size(), "threshold", maxOpenCommands,
                                                       "channelName", ctx.channelName()),
                                                SuggestedAction.LOG));
        }

        boolean senderIsObligor = openCommands.stream()
                                              .anyMatch(c -> ctx.sender().equals(c.obligor()));
        if (senderIsObligor && ctx.incomingType() != MessageType.DONE
            && ctx.incomingType() != MessageType.FAILURE
            && ctx.incomingType() != MessageType.DECLINE) {
            advisories.add(new DispatchAdvisory("TASK_COMPLETION", Severity.WARNING,
                                                "you have an open obligation in channel '" + ctx.channelName()
                                                + "' — consider sending DONE/FAILURE/DECLINE",
                                                Map.of("channelName", ctx.channelName(), "senderIsObligor", true),
                                                SuggestedAction.LOG));
        }
        return advisories;
    }
}
