package io.casehub.qhorus.api.gateway;

/**
 * Transport-agnostic SPI for receiving notification of every persisted qhorus
 * message. All 9 speech-act types fire; {@link MessageReceivedEvent#content()}
 * is null for EVENT.
 *
 * <p>Multiple implementations may coexist as CDI beans — the runtime iterates
 * all of them. {@link Scope#LOCAL} is the fast path (in-JVM, CDI); declare
 * {@link Scope#CLUSTER} for network-crossing transports (Kafka, WebSocket, etc.).
 *
 * <p><strong>Scope:</strong> any normal CDI scope is valid ({@code @ApplicationScoped},
 * {@code @RequestScoped}, etc.). The dispatcher closes each
 * {@link jakarta.enterprise.inject.Instance.Handle} in a {@code finally} block,
 * correctly destroying {@code @Dependent}-scoped implementations after each dispatch.
 *
 * <p><strong>Do not query qhorus message state</strong> in observer implementations.
 * The dispatcher fires before the enclosing transaction commits; querying the message
 * store may yield stale or absent data. The {@link MessageReceivedEvent} payload is
 * self-contained. JTA after-commit dispatch is tracked in qhorus#166.
 *
 * <p>Implementations must not propagate exceptions — the runtime logs and
 * continues regardless.
 */
@FunctionalInterface
public interface MessageObserver {

    void onMessage(MessageReceivedEvent event);

    default Scope scope() {
        return Scope.LOCAL;
    }

    enum Scope {
        /** In-JVM only. Zero serialisation, zero network overhead. */
        LOCAL,
        /** Crosses process/machine boundaries via a network transport. */
        CLUSTER
    }
}
