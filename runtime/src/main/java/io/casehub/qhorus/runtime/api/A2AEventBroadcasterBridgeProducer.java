package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.runtime.config.QhorusConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

@ApplicationScoped
public class A2AEventBroadcasterBridgeProducer {

    private static final Logger LOG = Logger.getLogger(A2AEventBroadcasterBridgeProducer.class);

    @Inject
    QhorusConfig config;

    @Inject
    jakarta.enterprise.inject.spi.BeanManager beanManager;

    @Produces
    @Singleton
    A2AEventBroadcasterBridge produce() {
        boolean enabled = config.a2a().pushToEventBroadcaster();
        if (!enabled) {
            LOG.debug("A2A EventBroadcaster bridge disabled by config");
            return new A2AEventBroadcasterBridge(false, (t, d) -> {});
        }

        try {
            Class<?> ebClass = Class.forName("io.casehub.pages.push.EventBroadcaster");
            var beans = beanManager.getBeans(ebClass);
            if (beans.isEmpty()) {
                LOG.warn("A2A EventBroadcaster bridge enabled but EventBroadcaster bean not found — is casehub-pages-push on the classpath?");
                return new A2AEventBroadcasterBridge(false, (t, d) -> {});
            }
            var bean = beanManager.resolve(beans);
            var ctx = beanManager.createCreationalContext(bean);
            Object broadcaster = beanManager.getReference(bean, ebClass, ctx);

            var broadcastMethod = ebClass.getMethod("broadcast", String.class, String.class);
            LOG.info("A2A EventBroadcaster bridge enabled — task status updates will be published");
            return new A2AEventBroadcasterBridge(true, (topic, data) -> {
                try {
                    broadcastMethod.invoke(broadcaster, topic, data);
                } catch (Exception e) {
                    LOG.debugf(e, "EventBroadcaster.broadcast() failed");
                }
            });
        } catch (ClassNotFoundException e) {
            LOG.warn("A2A EventBroadcaster bridge enabled but casehub-pages-push not on classpath — disabling");
            return new A2AEventBroadcasterBridge(false, (t, d) -> {});
        } catch (Exception e) {
            LOG.warnf(e, "A2A EventBroadcaster bridge initialization failed — disabling");
            return new A2AEventBroadcasterBridge(false, (t, d) -> {});
        }
    }
}
