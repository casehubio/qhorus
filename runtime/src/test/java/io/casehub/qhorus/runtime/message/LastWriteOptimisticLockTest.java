package io.casehub.qhorus.runtime.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestTransaction
class LastWriteOptimisticLockTest {

    @Inject QhorusTestHelper helper;
    @Inject @PersistenceUnit("qhorus") EntityManager em;

    @Test
    void lastWrite_secondDispatch_overwritesWithLocking() {
        var ch = helper.createChannel("lw-lock-" + UUID.randomUUID().toString().substring(0, 8),
                ChannelSemantic.LAST_WRITE);
        helper.registerInstance("agent-lw", null);

        helper.sendMessage(ch.name(), "agent-lw", "status", "first");
        helper.sendMessage(ch.name(), "agent-lw", "status", "second");

        var entity = em.createQuery(
                "SELECT e FROM Message e WHERE e.channelId = ?1 AND e.sender = ?2",
                MessageEntity.class)
            .setParameter(1, ch.id())
            .setParameter(2, "agent-lw")
            .getSingleResult();

        assertThat(entity.content).isEqualTo("second");
        assertThat(entity.version).isGreaterThanOrEqualTo(1);
    }
}
