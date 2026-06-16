package io.casehub.qhorus.message;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageTypeViolationException;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.message.StoredMessageTypePolicy;

class StoredMessageTypePolicyTest {

    private final StoredMessageTypePolicy policy = new StoredMessageTypePolicy();

    @Test
    void nullAllowedTypes_permitsAllTypes() {
        Channel ch = channel(null);
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.QUERY));
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.COMMAND));
    }

    @Test
    void blankAllowedTypes_permitsAllTypes() {
        Channel ch = channel("   ");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.STATUS));
    }

    @Test
    void singleType_permitsThatType() {
        Channel ch = channel("EVENT");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
    }

    @Test
    void singleType_rejectsOtherType() {
        Channel ch = channel("EVENT");
        assertThrows(MessageTypeViolationException.class,
                () -> policy.validate(ch, MessageType.QUERY));
    }

    @Test
    void multipleTypes_permitsAllListed() {
        Channel ch = channel("QUERY,COMMAND,RESPONSE");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.QUERY));
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.COMMAND));
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.RESPONSE));
    }

    @Test
    void multipleTypes_rejectsUnlisted() {
        Channel ch = channel("QUERY,COMMAND,RESPONSE");
        // validate() is a no-op for EVENT (not obligation-creating)
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        // advisory() fires instead
        assertNotNull(policy.advisory(ch, MessageType.EVENT));
    }

    @Test
    void whitespaceAroundCommas_isTrimmed() {
        Channel ch = channel("EVENT , STATUS");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.STATUS));
    }

    @Test
    void unknownTypeName_throwsIllegalArgument() {
        // validate() is a no-op for EVENT; advisory() calls parseTypes("RUBBISH") which throws IAE
        Channel ch = channel("RUBBISH");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        assertThrows(IllegalArgumentException.class,
                () -> policy.advisory(ch, MessageType.EVENT));
    }

    @Test
    void violationMessage_containsChannelNameAndTypes() {
        Channel ch = channel("EVENT");
        ch.name = "case-abc/observe";
        MessageTypeViolationException ex = assertThrows(MessageTypeViolationException.class,
                () -> policy.validate(ch, MessageType.QUERY));
        assertTrue(ex.getMessage().contains("case-abc/observe"));
        assertTrue(ex.getMessage().contains("QUERY"));
        assertTrue(ex.getMessage().contains("EVENT"));
    }

    @Test
    void allNineTypes_permitted_whenOpen() {
        Channel ch = channel(null);
        for (MessageType t : MessageType.values()) {
            assertDoesNotThrow(() -> policy.validate(ch, t),
                    "Expected " + t + " to be permitted on open channel");
        }
    }

    // ------------------------------------------------------------------
    // Denied types — denial-first semantics
    // ------------------------------------------------------------------

    @Test
    void deniedType_onOpenChannel_isRejected() {
        Channel ch = channelWithDenied(null, "EVENT");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        String adv = policy.advisory(ch, MessageType.EVENT);
        assertNotNull(adv);
        assertTrue(adv.contains("denies"), "Advisory should mention denial: " + adv);
    }

    @Test
    void deniedType_onOpenChannel_otherTypesStillPass() {
        Channel ch = channelWithDenied(null, "EVENT");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.QUERY));
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.COMMAND));
    }

    @Test
    void deniedType_advisoryIndicatesDenial() {
        Channel ch = channelWithDenied(null, "EVENT");
        ch.name = "case-abc/oversight";
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        String adv = policy.advisory(ch, MessageType.EVENT);
        assertNotNull(adv);
        assertTrue(adv.contains("denies"), "Expected 'denies' in advisory: " + adv);
        assertTrue(adv.contains("case-abc/oversight"), "Expected channel name in advisory: " + adv);
        assertTrue(adv.contains("EVENT"), "Expected type in advisory: " + adv);
    }

    @Test
    void nullDeniedTypes_hasNoEffect() {
        Channel ch = channelWithDenied("QUERY", null);
        // QUERY is obligation-creating — hard-enforced; allowedTypes="QUERY" so no violation
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.QUERY));
        // EVENT is not obligation-creating — validate() no-op; advisory() fires
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
        assertNotNull(policy.advisory(ch, MessageType.EVENT));
    }

    @Test
    void blankDeniedTypes_hasNoEffect() {
        Channel ch = channelWithDenied(null, "  ");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
    }

    @Test
    void allNineTypes_permitted_whenOpenAndNoDenied() {
        Channel ch = channelWithDenied(null, null);
        for (MessageType t : MessageType.values()) {
            assertDoesNotThrow(() -> policy.validate(ch, t),
                    "Expected " + t + " to be permitted on fully open channel");
        }
    }

    // ── Hybrid enforcement — COMMAND/QUERY hard, others advisory ────────────

    @Test
    void validate_command_onEventOnlyChannel_throws() {
        Channel ch = channel("EVENT");
        assertThrows(MessageTypeViolationException.class,
                () -> policy.validate(ch, MessageType.COMMAND));
    }

    @Test
    void validate_query_onEventOnlyChannel_throws() {
        Channel ch = channel("EVENT");
        assertThrows(MessageTypeViolationException.class,
                () -> policy.validate(ch, MessageType.QUERY));
    }

    @Test
    void validate_status_onEventOnlyChannel_doesNotThrow() {
        Channel ch = channel("EVENT");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.STATUS));
    }

    @Test
    void validate_event_onDeniedChannel_doesNotThrow() {
        Channel ch = channelWithDenied(null, "EVENT");
        assertDoesNotThrow(() -> policy.validate(ch, MessageType.EVENT));
    }

    @Test
    void advisory_commandOnEventOnlyChannel_returnsNull() {
        // COMMAND is hard-enforced; advisory() returns null for obligation-creating types
        Channel ch = channel("EVENT");
        assertNull(policy.advisory(ch, MessageType.COMMAND));
    }

    @Test
    void advisory_statusOnEventOnlyChannel_returnsText() {
        Channel ch = channel("EVENT");
        ch.name = "case/observe";
        String adv = policy.advisory(ch, MessageType.STATUS);
        assertNotNull(adv);
        assertTrue(adv.contains("case/observe"), "Advisory should contain channel name: " + adv);
        assertTrue(adv.contains("STATUS"), "Advisory should contain type: " + adv);
        assertTrue(adv.contains("Message dispatched."), "Advisory should end with dispatch confirmation: " + adv);
    }

    @Test
    void advisory_eventOnDeniedChannel_returnsText() {
        Channel ch = channelWithDenied(null, "EVENT");
        ch.name = "case/oversight";
        String adv = policy.advisory(ch, MessageType.EVENT);
        assertNotNull(adv);
        assertTrue(adv.contains("denies"), "Advisory should mention denial: " + adv);
        assertTrue(adv.contains("Message dispatched."), "Advisory should end with dispatch confirmation: " + adv);
    }

    @Test
    void advisory_commandOnOpenChannel_returnsNull() {
        Channel ch = channel(null);
        assertNull(policy.advisory(ch, MessageType.COMMAND));
    }

    private Channel channel(String allowedTypes) {
        return channelWithDenied(allowedTypes, null);
    }

    private Channel channelWithDenied(String allowedTypes, String deniedTypes) {
        Channel ch = new Channel();
        ch.name = "test-channel";
        ch.allowedTypes = allowedTypes;
        ch.deniedTypes = deniedTypes;
        ch.semantic = ChannelSemantic.APPEND;
        return ch;
    }
}
