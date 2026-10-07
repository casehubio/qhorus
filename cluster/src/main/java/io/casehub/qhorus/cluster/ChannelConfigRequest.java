package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChannelConfigRequest(String operation, Map<String, Object> params) {

    @SuppressWarnings("unchecked")
    public Channel applyTo(ChannelService service, UUID channelId) {
        return switch (operation) {
            case "setRateLimits" -> service.setRateLimits(channelId,
                    getInt("rateLimitPerChannel"), getInt("rateLimitPerInstance"));
            case "setAllowedWriters" -> service.setAllowedWriters(channelId,
                    (List<String>) params.get("writers"));
            case "setAdminInstances" -> service.setAdminInstances(channelId,
                    (List<String>) params.get("admins"));
            case "setReviewerInstances" -> service.setReviewerInstances(channelId,
                    (List<String>) params.get("reviewers"));
            case "setProtocols" -> service.setProtocols(channelId,
                    (List<String>) params.get("protocols"));
            case "setProtocolParticipants" -> service.setProtocolParticipants(channelId,
                    (List<String>) params.get("participants"));
            case "setEnforcementMode" -> service.setEnforcementMode(channelId,
                    EnforcementMode.valueOf((String) params.get("mode")));
            case "setEnforcementExclusions" -> service.setEnforcementExclusions(channelId,
                    (List<String>) params.get("exclusions"));
            case "setRoutingTrustThreshold" -> service.setRoutingTrustThreshold(channelId,
                    getDouble("threshold"));
            case "setRedistributionCapacityThreshold" -> service.setRedistributionCapacityThreshold(channelId,
                    getDouble("threshold"));
            case "setRoutingCapacityThreshold" -> service.setRoutingCapacityThreshold(channelId,
                    getDouble("threshold"));
            case "setPolicyOverrides" -> service.setPolicyOverrides(channelId,
                    (Map<String, String>) params.get("overrides"));
            case "setTypeConstraints" -> {
                var allowed = MessageType.parseTypes((String) params.get("allowedTypes"));
                var denied = MessageType.parseTypes((String) params.get("deniedTypes"));
                yield service.setTypeConstraints(channelId, allowed, denied);
            }
            case "setTrackDelivery" -> {
                service.setTrackDelivery(channelId, (Boolean) params.get("trackDelivery"));
                yield null;
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Integer getInt(String key) {
        Object v = params.get(key);
        return v == null ? null : ((Number) v).intValue();
    }

    private Double getDouble(String key) {
        Object v = params.get(key);
        return v == null ? null : ((Number) v).doubleValue();
    }
}
