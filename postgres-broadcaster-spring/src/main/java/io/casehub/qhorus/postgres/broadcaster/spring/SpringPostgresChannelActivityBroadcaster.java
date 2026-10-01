package io.casehub.qhorus.postgres.broadcaster.spring;

import io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.gateway.DeliverySignalQueue;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpringPostgresChannelActivityBroadcaster implements ChannelActivityBroadcaster {

    private static final Logger LOG = Logger.getLogger(SpringPostgresChannelActivityBroadcaster.class.getName());

    static final String CHANNEL = "qhorus_channel_activity";
    private static final int FILTER_SIZE = 1000;
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 60_000;
    private static final long POLL_INTERVAL_MS = 500;

    private final DataSource dataSource;
    private final ChannelGateway channelGateway;
    private final DeliverySignalQueue deliverySignalQueue;
    private final SelfNotificationFilter filter = new SelfNotificationFilter(FILTER_SIZE);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicLong currentDelayMs = new AtomicLong(INITIAL_DELAY_MS);
    private volatile Connection listenerConnection;

    public SpringPostgresChannelActivityBroadcaster(DataSource dataSource,
                                                     ChannelGateway channelGateway,
                                                     DeliverySignalQueue deliverySignalQueue) {
        this.dataSource = dataSource;
        this.channelGateway = channelGateway;
        this.deliverySignalQueue = deliverySignalQueue;
    }

    public void start() {
        Thread.ofVirtual().name("pg-listen-" + CHANNEL).start(this::listenLoop);
    }

    public void stop() {
        stopped.set(true);
        Connection conn = listenerConnection;
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void broadcast(ChannelActivityEvent event) {
        filter.recordSent(event.messageId());
        String payload = event.channelId() + ":" + event.messageId();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT pg_notify('" + CHANNEL + "', '" + payload + "')");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "pg_notify failed on channel ''{0}'': {1}",
                    new Object[]{CHANNEL, e.getMessage()});
        }
    }

    private void listenLoop() {
        while (!stopped.get()) {
            try {
                listenerConnection = dataSource.getConnection();
                listenerConnection.setAutoCommit(true);
                PGConnection pgConn = listenerConnection.unwrap(PGConnection.class);

                try (Statement stmt = listenerConnection.createStatement()) {
                    stmt.execute("LISTEN " + CHANNEL);
                }
                LOG.log(Level.INFO, "Subscribed to PostgreSQL channel ''{0}''", CHANNEL);
                currentDelayMs.set(INITIAL_DELAY_MS);

                while (!stopped.get() && !listenerConnection.isClosed()) {
                    PGNotification[] notifications = pgConn.getNotifications(
                            (int) POLL_INTERVAL_MS);
                    if (notifications != null) {
                        for (PGNotification n : notifications) {
                            handleNotification(n.getParameter());
                        }
                    }
                }
            } catch (Exception e) {
                if (!stopped.get()) {
                    LOG.log(Level.WARNING, "PostgreSQL listener connection lost — reconnecting: {0}",
                            e.getMessage());
                }
            } finally {
                Connection conn = listenerConnection;
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignored) {}
                    listenerConnection = null;
                }
            }

            if (!stopped.get()) {
                long delay = currentDelayMs.getAndUpdate(d -> Math.min(d * 2, MAX_DELAY_MS));
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    void handleNotification(String payload) {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            LOG.log(Level.WARNING, "Malformed notification payload: {0}", payload);
            return;
        }
        UUID channelId;
        Long messageId;
        try {
            channelId = UUID.fromString(parts[0]);
            messageId = Long.parseLong(parts[1]);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse notification payload ''{0}'': {1}",
                    new Object[]{payload, e.getMessage()});
            return;
        }

        if (filter.wasSentLocally(messageId)) {
            return;
        }

        Thread.ofVirtual().name("qhorus-remote-deliver-" + messageId)
                .start(() -> {
                    try {
                        channelGateway.deliverRemote(channelId, messageId);
                        deliverySignalQueue.signal(channelId);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Remote delivery failed for message {0} on channel {1}: {2}",
                                new Object[]{messageId, channelId, e.getMessage()});
                    }
                });
    }
}
