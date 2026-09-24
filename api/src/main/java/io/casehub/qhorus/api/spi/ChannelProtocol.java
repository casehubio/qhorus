package io.casehub.qhorus.api.spi;

import java.util.List;

public interface ChannelProtocol {

    String protocolName();

    List<DispatchAdvisory> evaluate(ProtocolContext context);
}
