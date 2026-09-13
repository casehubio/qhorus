package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.ProtocolContext;

import java.util.ArrayList;
import java.util.List;

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
    public List<String> evaluate(ProtocolContext ctx) {
        List<Commitment> openQueries = ctx.activeCommitments().stream()
                .filter(c -> c.messageType() == MessageType.QUERY)
                .toList();
        if (openQueries.isEmpty()) return List.of();

        List<String> advisories = new ArrayList<>();
        if (ctx.incomingType() == MessageType.QUERY && openQueries.size() >= maxOpenQueries) {
            advisories.add("[REQUEST_RESPONSE] " + openQueries.size()
                    + " unanswered QUERYs in channel '" + ctx.channelName()
                    + "' — consider waiting for responses");
        }
        if (ctx.incomingType() != MessageType.RESPONSE && ctx.incomingType() != MessageType.QUERY) {
            advisories.add("[REQUEST_RESPONSE] channel '" + ctx.channelName()
                    + "' has open QUERYs awaiting RESPONSE");
        }
        return advisories;
    }
}
