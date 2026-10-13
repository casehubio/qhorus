package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.config.QhorusConfig.ChannelConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashSet;
import java.util.List;

@ApplicationScoped
public class ChannelConfigBootstrap {

    private static final Logger LOG = Logger.getLogger(ChannelConfigBootstrap.class);

    private final QhorusConfig config;
    private final ChannelService channelService;
    private final SpaceService spaceService;

    @Inject
    public ChannelConfigBootstrap(QhorusConfig config,
                                  ChannelService channelService,
                                  SpaceService spaceService) {
        this.config = config;
        this.channelService = channelService;
        this.spaceService = spaceService;
    }

    void onStartup(@Observes StartupEvent event) {
        var channels = config.channels();
        if (channels.isEmpty()) return;

        int created = 0, existing = 0;
        for (var entry : channels.entrySet()) {
            try {
                var result = processChannel(entry.getKey(), entry.getValue());
                if (result.wasCreated()) created++;
                else existing++;
            } catch (Exception e) {
                LOG.warnf("Failed to bootstrap channel '%s': %s",
                        entry.getKey(), e.getMessage());
            }
        }
        LOG.infof("Channel config bootstrap: %d channels processed (%d created, %d existing)",
                channels.size(), created, existing);
    }

    FindOrCreateResult processChannel(String name, ChannelConfig cfg) {
        var request = toRequest(name, cfg);
        var result = channelService.findOrCreate(request);
        if (!result.wasCreated()) {
            var drifts = detectDrift(name, cfg, result.channel());
            for (var drift : drifts) {
                LOG.warnf("Channel '%s': drift detected — %s declared as %s, actual is %s",
                        drift.channelName(), drift.field(), drift.declared(), drift.actual());
            }
        }
        return result;
    }

    ChannelCreateRequest toRequest(String name, ChannelConfig cfg) {
        var builder = ChannelCreateRequest.builder(name)
                .semantic(cfg.semantic());
        cfg.description().ifPresent(builder::description);
        cfg.allowedWriters().ifPresent(builder::allowedWriters);
        cfg.adminInstances().ifPresent(builder::adminInstances);
        cfg.barrierContributors().ifPresent(builder::barrierContributors);
        cfg.reviewerInstances().ifPresent(builder::reviewerInstances);
        cfg.allowedTypes().ifPresent(list -> builder.allowedTypes(new LinkedHashSet<>(list)));
        cfg.deniedTypes().ifPresent(list -> builder.deniedTypes(new LinkedHashSet<>(list)));
        cfg.protocols().ifPresent(builder::protocols);
        cfg.protocolParticipants().ifPresent(builder::protocolParticipants);
        cfg.enforcementMode().ifPresent(builder::enforcementMode);
        cfg.enforcementExclusions().ifPresent(builder::enforcementExclusions);
        if (cfg.rateLimitPerChannel().isPresent())
            builder.rateLimitPerChannel(cfg.rateLimitPerChannel().getAsInt());
        if (cfg.rateLimitPerInstance().isPresent())
            builder.rateLimitPerInstance(cfg.rateLimitPerInstance().getAsInt());
        cfg.routingTrustThreshold().ifPresent(builder::routingTrustThreshold);
        cfg.trackDelivery().ifPresent(builder::trackDelivery);
        if (!cfg.metadata().isEmpty())
            builder.metadata(cfg.metadata());

        cfg.space().ifPresent(spaceName -> resolveSpace(name, spaceName, builder));

        cfg.connector().ifPresent(conn -> {
            builder.inboundConnectorId(conn.inboundConnectorId());
            builder.externalKey(conn.externalKey());
            builder.outboundConnectorId(conn.outboundConnectorId());
            builder.outboundDestination(conn.outboundDestination());
        });

        return builder.build();
    }

    private void resolveSpace(String channelName, String spaceName, ChannelCreateRequest.Builder builder) {
        if (spaceService == null) {
            LOG.warnf("Channel '%s': space '%s' requested but SpaceService not available", channelName, spaceName);
            return;
        }
        try {
            spaceService.findByName(spaceName).ifPresentOrElse(
                    s -> builder.spaceId(s.id()),
                    () -> LOG.warnf("Channel '%s': space '%s' not found — skipping space assignment",
                            channelName, spaceName));
        } catch (IllegalStateException e) {
            LOG.warnf("Channel '%s': space '%s' is ambiguous — skipping space assignment",
                    channelName, spaceName);
        }
    }

    record ChannelDrift(String channelName, String field, String declared, String actual) {}

    List<ChannelDrift> detectDrift(String name, ChannelConfig cfg, io.casehub.qhorus.api.channel.Channel existing) {
        var drifts = new java.util.ArrayList<ChannelDrift>();

        if (cfg.semantic() != existing.semantic()) {
            drifts.add(new ChannelDrift(name, "semantic", cfg.semantic().name(), existing.semantic().name()));
        }

        cfg.description().ifPresent(d -> {
            if (!java.util.Objects.equals(d, existing.description())) {
                drifts.add(new ChannelDrift(name, "description", d, String.valueOf(existing.description())));
            }
        });

        cfg.allowedWriters().ifPresent(declared -> {
            if (!declared.equals(existing.allowedWriters())) {
                drifts.add(new ChannelDrift(name, "allowedWriters", declared.toString(), existing.allowedWriters().toString()));
            }
        });

        cfg.adminInstances().ifPresent(declared -> {
            if (!declared.equals(existing.adminInstances())) {
                drifts.add(new ChannelDrift(name, "adminInstances", declared.toString(), existing.adminInstances().toString()));
            }
        });

        cfg.barrierContributors().ifPresent(declared -> {
            if (!declared.equals(existing.barrierContributors())) {
                drifts.add(new ChannelDrift(name, "barrierContributors", declared.toString(), existing.barrierContributors().toString()));
            }
        });

        cfg.reviewerInstances().ifPresent(declared -> {
            if (!declared.equals(existing.reviewerInstances())) {
                drifts.add(new ChannelDrift(name, "reviewerInstances", declared.toString(), existing.reviewerInstances().toString()));
            }
        });

        cfg.allowedTypes().ifPresent(declared -> {
            var declaredSet = new LinkedHashSet<>(declared);
            if (!java.util.Objects.equals(declaredSet, existing.allowedTypes())) {
                drifts.add(new ChannelDrift(name, "allowedTypes", declaredSet.toString(), String.valueOf(existing.allowedTypes())));
            }
        });

        cfg.deniedTypes().ifPresent(declared -> {
            var declaredSet = new LinkedHashSet<>(declared);
            if (!java.util.Objects.equals(declaredSet, existing.deniedTypes())) {
                drifts.add(new ChannelDrift(name, "deniedTypes", declaredSet.toString(), String.valueOf(existing.deniedTypes())));
            }
        });

        cfg.protocols().ifPresent(declared -> {
            if (!declared.equals(existing.protocols())) {
                drifts.add(new ChannelDrift(name, "protocols", declared.toString(), existing.protocols().toString()));
            }
        });

        cfg.protocolParticipants().ifPresent(declared -> {
            if (!declared.equals(existing.protocolParticipants())) {
                drifts.add(new ChannelDrift(name, "protocolParticipants", declared.toString(), existing.protocolParticipants().toString()));
            }
        });

        cfg.enforcementMode().ifPresent(declared -> {
            if (!java.util.Objects.equals(declared, existing.enforcementMode())) {
                drifts.add(new ChannelDrift(name, "enforcementMode", declared.name(), String.valueOf(existing.enforcementMode())));
            }
        });

        cfg.enforcementExclusions().ifPresent(declared -> {
            if (!declared.equals(existing.enforcementExclusions())) {
                drifts.add(new ChannelDrift(name, "enforcementExclusions", declared.toString(), existing.enforcementExclusions().toString()));
            }
        });

        if (cfg.rateLimitPerChannel().isPresent()) {
            int declared = cfg.rateLimitPerChannel().getAsInt();
            if (!java.util.Objects.equals(declared, existing.rateLimitPerChannel())) {
                drifts.add(new ChannelDrift(name, "rateLimitPerChannel", String.valueOf(declared), String.valueOf(existing.rateLimitPerChannel())));
            }
        }

        if (cfg.rateLimitPerInstance().isPresent()) {
            int declared = cfg.rateLimitPerInstance().getAsInt();
            if (!java.util.Objects.equals(declared, existing.rateLimitPerInstance())) {
                drifts.add(new ChannelDrift(name, "rateLimitPerInstance", String.valueOf(declared), String.valueOf(existing.rateLimitPerInstance())));
            }
        }

        cfg.routingTrustThreshold().ifPresent(declared -> {
            if (!java.util.Objects.equals(declared, existing.routingTrustThreshold())) {
                drifts.add(new ChannelDrift(name, "routingTrustThreshold", declared.toString(), String.valueOf(existing.routingTrustThreshold())));
            }
        });

        cfg.trackDelivery().ifPresent(declared -> {
            if (!java.util.Objects.equals(declared, existing.trackDelivery())) {
                drifts.add(new ChannelDrift(name, "trackDelivery", declared.toString(), String.valueOf(existing.trackDelivery())));
            }
        });

        return drifts;
    }

}
