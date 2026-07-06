package io.casehub.qhorus.postgres.broadcaster;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.gateway.DeliverySignalQueue;
import io.quarkus.reactive.datasource.ReactiveDataSource;
import io.vertx.mutiny.pgclient.PgConnection;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;

/**
 * PostgreSQL LISTEN/NOTIFY implementation of {@link ChannelActivityBroadcaster}.
 *
 * <p>When a message commits on any node, {@link #broadcast(ChannelActivityEvent)} fires
 * {@code pg_notify('qhorus_channel_activity', 'channelId:messageId')}. All nodes
 * subscribed via {@code LISTEN} receive the notification, check the self-notification
 * filter, and offload to a virtual thread for {@link ChannelGateway#deliverRemote(UUID, Long)}.
 *
 * <h2>Activation</h2>
 * <p>
 * {@code @Alternative @Priority(1)} displaces {@code NoOpChannelActivityBroadcaster @DefaultBean}
 * by classpath presence. Add {@code casehub-qhorus-postgres-broadcaster} as a dependency —
 * no configuration needed beyond the shared PostgreSQL datasource.
 *
 * <h2>Lossy delivery is acceptable</h2>
 * <p>
 * LISTEN/NOTIFY is lossy — notifications are missed during connection drops. This is
 * acceptable because BEST_EFFORT backends are best-effort by definition, and AT_LEAST_ONCE
 * backends have {@code DeliveryService} reconciliation every 30s as a backup.
 *
 * <p>Refs #162.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class PostgresChannelActivityBroadcaster implements ChannelActivityBroadcaster {

    private static final Logger LOG = Logger.getLogger(PostgresChannelActivityBroadcaster.class);

    /** PostgreSQL channel name. Must be a valid unquoted identifier (underscores, lowercase). */
    static final String CHANNEL = "qhorus_channel_activity";
    private static final int FILTER_SIZE = 1000;
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 60_000;

    @Inject
    @ReactiveDataSource("qhorus")
    PgPool pool;

    @Inject
    ChannelGateway channelGateway;

    @Inject
    DeliverySignalQueue deliverySignalQueue;

    private final SelfNotificationFilter filter = new SelfNotificationFilter(FILTER_SIZE);
    private volatile PgConnection subscriberConnection;
    private final AtomicLong currentDelayMs = new AtomicLong(INITIAL_DELAY_MS);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @PostConstruct
    void startListening() {
        acquireAndListen();
    }

    @PreDestroy
    void stopListening() {
        stopped.set(true);
        PgConnection conn = subscriberConnection;
        if (conn != null) {
            conn.close().subscribe().with(ok -> {}, err -> {});
        }
    }

    @Override
    public void broadcast(ChannelActivityEvent event) {
        filter.recordSent(event.messageId());
        String payload = event.channelId() + ":" + event.messageId();
        pool.preparedQuery("SELECT pg_notify($1, $2)")
                .execute(Tuple.of(CHANNEL, payload))
                .subscribe().with(
                        ok -> {},
                        err -> LOG.warnf("pg_notify failed on channel '%s': %s",
                                CHANNEL, err.getMessage()));
    }

    /**
     * Notification handler — runs on the Vert.x event loop. Steps 1-2 (parse, filter)
     * are non-blocking. Step 3 offloads to a virtual thread because
     * {@code deliverRemote()} performs blocking JPA queries.
     */
    void handleNotification(String payload) {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            LOG.warnf("Malformed notification payload: %s", payload);
            return;
        }
        UUID channelId;
        Long messageId;
        try {
            channelId = UUID.fromString(parts[0]);
            messageId = Long.parseLong(parts[1]);
        } catch (Exception e) {
            LOG.warnf("Failed to parse notification payload '%s': %s",
                    payload, e.getMessage());
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
                        LOG.warnf("Remote delivery failed for message %d on channel %s: %s",
                                messageId, channelId, e.getMessage());
                    }
                });
    }

    /**
     * Acquires a persistent connection from the pool, registers the notification handler,
     * and issues LISTEN. The delegate's closeHandler triggers reconnection with re-LISTEN
     * (PostgreSQL LISTEN is session-scoped — a new connection has no active subscriptions).
     */
    private void acquireAndListen() {
        if (stopped.get()) return;
        if (!reconnecting.compareAndSet(false, true)) return;

        // Close previous connection to prevent leaked LISTEN subscriptions
        PgConnection prev = subscriberConnection;
        if (prev != null) {
            prev.close().subscribe().with(ok -> {}, err -> {});
            subscriberConnection = null;
        }

        pool.getConnection().subscribe().with(
                conn -> {
                    io.vertx.pgclient.PgConnection pgDelegate =
                            (io.vertx.pgclient.PgConnection) conn.getDelegate();
                    PgConnection pgConn = PgConnection.newInstance(pgDelegate);
                    subscriberConnection = pgConn;
                    pgConn.notificationHandler(n -> handleNotification(n.getPayload()));
                    pgConn.query("LISTEN " + CHANNEL).execute()
                            .subscribe().with(
                                    ok -> {
                                        LOG.infof("Subscribed to PostgreSQL channel '%s'", CHANNEL);
                                        currentDelayMs.set(INITIAL_DELAY_MS);
                                        reconnecting.set(false);
                                    },
                                    err -> {
                                        LOG.errorf(err, "Failed to LISTEN on '%s'", CHANNEL);
                                        pgConn.close().subscribe().with(ok2 -> {}, err2 -> {});
                                        subscriberConnection = null;
                                        reconnecting.set(false);
                                        scheduleReconnect();
                                    });
                    pgDelegate.closeHandler(v -> {
                        if (subscriberConnection != pgConn) return; // stale — already replaced
                        LOG.warn("PostgreSQL subscriber connection lost — reconnecting");
                        scheduleReconnect();
                    });
                },
                err -> {
                    LOG.errorf(err, "Failed to acquire subscriber connection for '%s'", CHANNEL);
                    reconnecting.set(false);
                    scheduleReconnect();
                });
    }

    private void scheduleReconnect() {
        long delay = currentDelayMs.getAndUpdate(d -> Math.min(d * 2, MAX_DELAY_MS));
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            acquireAndListen();
        });
    }
}
