package io.casehub.qhorus.channel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ParticipationPolicyMode;

class ParticipationPolicyModeTest {

    @Test
    void openDoesNotRequireLookup() {
        assertFalse(ParticipationPolicyMode.OPEN.requiresLookup());
    }

    @Test
    void autoRegisterRequiresLookup() {
        assertTrue(ParticipationPolicyMode.AUTO_REGISTER.requiresLookup());
    }

    @Test
    void enforceRequiresLookup() {
        assertTrue(ParticipationPolicyMode.ENFORCE.requiresLookup());
    }

    @Test
    void threeValuesExist() {
        assertEquals(3, ParticipationPolicyMode.values().length);
    }

    @Test
    void channelBuilder_roundTrips_participationPolicy() {
        Channel ch = Channel.builder("test")
                .participationPolicy(ParticipationPolicyMode.AUTO_REGISTER)
                .build();
        assertEquals(ParticipationPolicyMode.AUTO_REGISTER, ch.participationPolicy());

        Channel rebuilt = ch.toBuilder().participationPolicy(ParticipationPolicyMode.ENFORCE).build();
        assertEquals(ParticipationPolicyMode.ENFORCE, rebuilt.participationPolicy());
    }

    @Test
    void channelBuilder_defaultsToNull() {
        Channel ch = Channel.builder("test").build();
        assertNull(ch.participationPolicy());
    }
}
