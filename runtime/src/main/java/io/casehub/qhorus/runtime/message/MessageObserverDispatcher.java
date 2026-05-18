package io.casehub.qhorus.runtime.message;

import java.util.UUID;

import org.jboss.logging.Logger;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;

/**
 * Shared dispatch logic for blocking and reactive message services.
 * Each observer is called independently — a failure in one does not affect others.
 *
 * <p><strong>Transaction timing:</strong> this method is called inside the
 * {@code MessageService.send()} transaction, before it commits. Observers that
 * call {@code fireAsync()} (like {@link io.casehub.qhorus.runtime.gateway.InProcessMessageBus})
 * run in a separate thread that may start before the enclosing transaction commits.
 * Therefore: <em>observer implementations must not query qhorus message state</em>
 * in response to this event. The {@link MessageReceivedEvent} payload is intentionally
 * self-contained (channelName, channelId, messageType, senderId, correlationId, content)
 * to make a DB read unnecessary. JTA after-commit dispatch is tracked in qhorus#166.
 *
 * <p><strong>Observer scope:</strong> implementations must be {@code @ApplicationScoped}.
 * {@code @Dependent} beans obtained via {@code Instance} are not destroyed by this
 * dispatcher and will leak. Lifecycle management via {@code Instance.handles()} is
 * tracked in qhorus#167.
 */
final class MessageObserverDispatcher {

    private static final Logger LOG = Logger.getLogger(MessageObserverDispatcher.class);

    private MessageObserverDispatcher() {}

    static void dispatch(final String channelName, final UUID channelId,
            final Message message, final Iterable<MessageObserver> observers) {
        final String content = message.messageType == MessageType.EVENT
                ? null : message.content;
        final MessageReceivedEvent event = new MessageReceivedEvent(
                channelName, channelId,
                message.messageType, message.sender,
                message.correlationId, content);
        for (final MessageObserver observer : observers) {
            try {
                observer.onMessage(event);
            } catch (Exception e) {
                // getSuperclass() unwraps Quarkus CDI subclass proxy to the real class name
                LOG.warnf("MessageObserver %s failed for channel '%s' type %s: %s",
                        observer.getClass().getSuperclass().getSimpleName(),
                        channelName, message.messageType, e.getMessage());
            }
        }
    }
}
