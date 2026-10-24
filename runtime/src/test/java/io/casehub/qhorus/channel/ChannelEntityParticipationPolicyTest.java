package io.casehub.qhorus.channel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ParticipationPolicyMode;
import io.casehub.qhorus.runtime.channel.ChannelEntity;

class ChannelEntityParticipationPolicyTest {

    @Test
    void fromDomain_toDomain_roundTrips_autoRegister() {
        Channel ch = Channel.builder("test-ch")
                .participationPolicy(ParticipationPolicyMode.AUTO_REGISTER)
                .build();
        ChannelEntity entity = ChannelEntity.fromDomain(ch);
        assertEquals("AUTO_REGISTER", entity.participationPolicy);
        Channel roundTripped = entity.toDomain();
        assertEquals(ParticipationPolicyMode.AUTO_REGISTER, roundTripped.participationPolicy());
    }

    @Test
    void fromDomain_toDomain_roundTrips_null() {
        Channel ch = Channel.builder("test-ch").build();
        ChannelEntity entity = ChannelEntity.fromDomain(ch);
        assertNull(entity.participationPolicy);
        Channel roundTripped = entity.toDomain();
        assertNull(roundTripped.participationPolicy());
    }

    @Test
    void fromDomain_toDomain_roundTrips_enforce() {
        Channel ch = Channel.builder("test-ch")
                .participationPolicy(ParticipationPolicyMode.ENFORCE)
                .build();
        ChannelEntity entity = ChannelEntity.fromDomain(ch);
        assertEquals("ENFORCE", entity.participationPolicy);
        Channel roundTripped = entity.toDomain();
        assertEquals(ParticipationPolicyMode.ENFORCE, roundTripped.participationPolicy());
    }
}
