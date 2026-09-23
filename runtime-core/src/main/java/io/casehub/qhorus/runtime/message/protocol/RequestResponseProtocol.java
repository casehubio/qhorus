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

public class RequestResponseProtocol implements ChannelProtocol {

    int maxOpenQueries;


    RequestResponseProtocol() {}

    public RequestResponseProtocol(int maxOpenQueries) {
        this.maxOpenQueries = maxOpenQueries;
    }

    @Override
    public String protocolName() {
        return "REQUEST_RESPONSE";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        List<Commitment> openQueries = ctx.activeCommitments().stream()
                                          .filter(c -> c.messageType() == MessageType.QUERY)
                                          .toList();
        if (openQueries.isEmpty()) {return List.of();}

        List<DispatchAdvisory> advisories = new ArrayList<>();
        if (ctx.incomingType() == MessageType.QUERY && openQueries.size() >= maxOpenQueries) {
            advisories.add(new DispatchAdvisory("REQUEST_RESPONSE", Severity.WARNING,
                                                openQueries.size() + " unanswered QUERYs in channel '" + ctx.channelName()
                                                + "' — consider waiting for responses",
                                                Map.of("openQueryCount", openQueries.size(), "threshold", maxOpenQueries,
                                                       "channelName", ctx.channelName()),
                                                SuggestedAction.LOG));
        }
        if (ctx.incomingType() != MessageType.RESPONSE && ctx.incomingType() != MessageType.QUERY) {
            advisories.add(new DispatchAdvisory("REQUEST_RESPONSE", Severity.ADVISORY,
                                                "channel '" + ctx.channelName() + "' has open QUERYs awaiting RESPONSE",
                                                Map.of("openQueryCount", openQueries.size(), "channelName", ctx.channelName()),
                                                SuggestedAction.LOG));
        }
        return advisories;
    }
}
