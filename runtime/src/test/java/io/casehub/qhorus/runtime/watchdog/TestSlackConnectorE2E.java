package io.casehub.qhorus.runtime.watchdog;

import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class TestSlackConnectorE2E implements Connector {

    public static final CopyOnWriteArrayList<ConnectorMessage> sent = new CopyOnWriteArrayList<>();

    public static void clear() {
        sent.clear();
    }

    @Override
    public String id() {
        return "slack";
    }

    @Override
    public void send(ConnectorMessage message) {
        sent.add(message);
    }
}
