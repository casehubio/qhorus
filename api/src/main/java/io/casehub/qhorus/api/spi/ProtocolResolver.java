package io.casehub.qhorus.api.spi;

import java.util.List;
import java.util.UUID;

public interface ProtocolResolver {

    List<ChannelProtocol> resolve(List<String> unresolvedNames, UUID channelId);
}
