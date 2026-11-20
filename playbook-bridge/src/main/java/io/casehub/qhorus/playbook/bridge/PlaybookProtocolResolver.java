package io.casehub.qhorus.playbook.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.ProtocolResolver;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PlaybookProtocolResolver implements ProtocolResolver {

    private static final Logger LOG = Logger.getLogger(PlaybookProtocolResolver.class);

    @Inject
    ProtocolDefinitionStore definitionStore;

    @Inject
    PlaybookExecutor executor;

    @Inject
    CompilationCache cache;

    @Override
    public List<ChannelProtocol> resolve(List<String> unresolvedNames, UUID channelId) {
        List<ChannelProtocol> resolved = new ArrayList<>();
        for (String name : unresolvedNames) {
            Optional<ProtocolDefinition> def =
                    definitionStore.findByChannelAndProtocol(channelId, name);
            if (def.isPresent()) {
                String yaml = cache.get(channelId, name)
                        .orElseGet(() -> {
                            String content = def.get().yamlContent();
                            cache.put(channelId, name, content);
                            return content;
                        });
                resolved.add(new PlaybookChannelProtocol(name, yaml, executor));
            } else {
                LOG.debugf("No playbook definition for protocol '%s' on channel %s", name, channelId);
            }
        }
        return resolved;
    }
}
