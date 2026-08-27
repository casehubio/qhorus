package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ChannelObligationSummary;
import org.eclipse.microprofile.graphql.Type;

import java.util.UUID;

@Type("ChannelObligationSummary")
public record ChannelObligationSummaryType(
        UUID channelId,
        String channelName,
        int total,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double fulfillmentRate) {

    public static ChannelObligationSummaryType from(ChannelObligationSummary s) {
        return new ChannelObligationSummaryType(
                s.channelId(), s.channelName(), s.total(), s.fulfilled(),
                s.failed(), s.declined(), s.delegated(), s.stillOpen(),
                s.stalled(), s.fulfillmentRate());
    }
}
