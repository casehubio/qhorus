package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.message.MessageType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ChannelCreateRequest(
        String name,
        String description,
        ChannelSemantic semantic,
        List<String> barrierContributors,
        List<String> allowedWriters,
        List<String> adminInstances,
        Integer rateLimitPerChannel,
        Integer rateLimitPerInstance,
        Set<MessageType> allowedTypes,
        Set<MessageType> deniedTypes,
        UUID spaceId,
        // Connector binding — all four non-null together, or all null
        String inboundConnectorId,
        String externalKey,
        String outboundConnectorId,
        String outboundDestination
) {
    public ChannelCreateRequest {
        io.casehub.qhorus.api.channel.ChannelSlugValidator.validateSlugPath(name);
        boolean anySet = inboundConnectorId != null || externalKey != null
                         || outboundConnectorId != null || outboundDestination != null;
        boolean allSet = inboundConnectorId != null && externalKey != null
                         && outboundConnectorId != null && outboundDestination != null;
        if (anySet && !allSet) {
            throw new IllegalArgumentException(
                    "Connector binding requires all four fields: inboundConnectorId, " +
                    "externalKey, outboundConnectorId, outboundDestination");
        }

        barrierContributors = barrierContributors != null ? List.copyOf(barrierContributors) : List.of();
        allowedWriters      = allowedWriters != null ? List.copyOf(allowedWriters) : List.of();
        adminInstances      = adminInstances != null ? List.copyOf(adminInstances) : List.of();
        allowedTypes        = allowedTypes != null ? Set.copyOf(allowedTypes) : null;
        deniedTypes         = deniedTypes != null ? Set.copyOf(deniedTypes) : null;

        if (allowedTypes != null && !allowedTypes.isEmpty()
            && deniedTypes != null && !deniedTypes.isEmpty()) {
            final Set<MessageType> overlap = new HashSet<>(allowedTypes);
            overlap.retainAll(deniedTypes);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException(
                        "allowedTypes and deniedTypes must not intersect. Overlap: " + overlap);
            }
        }
    }


    public ChannelCreateRequest(
            String name,
            String description,
            ChannelSemantic semantic,
            List<String> barrierContributors,
            List<String> allowedWriters,
            List<String> adminInstances,
            Integer rateLimitPerChannel,
            Integer rateLimitPerInstance,
            Set<MessageType> allowedTypes,
            Set<MessageType> deniedTypes,
            String inboundConnectorId,
            String externalKey,
            String outboundConnectorId,
            String outboundDestination) {
        this(name, description, semantic, barrierContributors, allowedWriters, adminInstances,
             rateLimitPerChannel, rateLimitPerInstance, allowedTypes, deniedTypes,
             null,
             inboundConnectorId, externalKey, outboundConnectorId, outboundDestination);
    }

    public boolean hasConnectorBinding() {
        return inboundConnectorId != null;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String           name;
        private       String           description;
        private       ChannelSemantic  semantic = ChannelSemantic.APPEND;
        private       List<String>     barrierContributors;
        private       List<String>     allowedWriters;
        private       List<String>     adminInstances;
        private       Integer          rateLimitPerChannel;
        private       Integer          rateLimitPerInstance;
        private       Set<MessageType> allowedTypes;
        private       Set<MessageType> deniedTypes;
        private       UUID             spaceId;
        private       String           inboundConnectorId;
        private       String           externalKey;
        private       String           outboundConnectorId;
        private       String           outboundDestination;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description)                       {
                                                                                 this.description = description;
                                                                                 return this;
                                                                             }

        public Builder semantic(ChannelSemantic semantic)                    {
                                                                                 this.semantic = semantic;
                                                                                 return this;
                                                                             }

        public Builder barrierContributors(List<String> barrierContributors) {
                                                                                 this.barrierContributors = barrierContributors;
                                                                                 return this;
                                                                             }

        public Builder allowedWriters(List<String> allowedWriters)           {
                                                                                 this.allowedWriters = allowedWriters;
                                                                                 return this;
                                                                             }

        public Builder adminInstances(List<String> adminInstances)           {
                                                                                 this.adminInstances = adminInstances;
                                                                                 return this;
                                                                             }

        public Builder rateLimitPerChannel(Integer rateLimitPerChannel)      {
                                                                                 this.rateLimitPerChannel = rateLimitPerChannel;
                                                                                 return this;
                                                                             }

        public Builder rateLimitPerInstance(Integer rateLimitPerInstance)    {
                                                                                 this.rateLimitPerInstance = rateLimitPerInstance;
                                                                                 return this;
                                                                             }

        public Builder allowedTypes(Set<MessageType> allowedTypes)           {
                                                                                 this.allowedTypes = allowedTypes;
                                                                                 return this;
                                                                             }

        public Builder deniedTypes(Set<MessageType> deniedTypes)             {
                                                                                 this.deniedTypes = deniedTypes;
                                                                                 return this;
                                                                             }

        public Builder spaceId(UUID spaceId)                                 {
                                                                                 this.spaceId = spaceId;
                                                                                 return this;
                                                                             }

        public Builder inboundConnectorId(String inboundConnectorId)         {
                                                                                 this.inboundConnectorId = inboundConnectorId;
                                                                                 return this;
                                                                             }

        public Builder externalKey(String externalKey)                       {
                                                                                 this.externalKey = externalKey;
                                                                                 return this;
                                                                             }

        public Builder outboundConnectorId(String outboundConnectorId)       {
                                                                                 this.outboundConnectorId = outboundConnectorId;
                                                                                 return this;
                                                                             }

        public Builder outboundDestination(String outboundDestination)       {
                                                                                 this.outboundDestination = outboundDestination;
                                                                                 return this;
                                                                             }

        public ChannelCreateRequest build() {
            return new ChannelCreateRequest(name, description, semantic,
                                            barrierContributors, allowedWriters, adminInstances,
                                            rateLimitPerChannel, rateLimitPerInstance,
                                            allowedTypes, deniedTypes,
                                            spaceId,
                                            inboundConnectorId, externalKey,
                                            outboundConnectorId, outboundDestination);
        }
    }
}
