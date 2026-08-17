package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.message.CommitmentState;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("CommitmentFilterInput")
public record CommitmentFilterInput(
        UUID channelId,
        CommitmentState state,
        String obligor,
        String requester) {}
