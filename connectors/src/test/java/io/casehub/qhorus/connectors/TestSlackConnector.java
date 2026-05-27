package io.casehub.qhorus.connectors;

import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class TestSlackConnector implements Connector {

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
