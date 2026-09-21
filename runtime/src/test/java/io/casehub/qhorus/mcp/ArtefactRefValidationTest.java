package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.api.data.SharedData;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.ArtefactDetail;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ArtefactRefValidationTest {

    @Inject QhorusTestHelper helper;

    @Inject
    InstanceService instanceService;

    @Test
    @TestTransaction
    void sendMessageWithValidArtefactRefSucceeds() {
        helper.createChannel("arv-ch-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ArtefactDetail artefact = helper.shareArtefact(
                "arv-data-1", "desc", "alice", "content", false, true);

        assertDoesNotThrow(() -> helper.sendMessage("arv-ch-1", "alice", "status", "with valid ref", null, null, null, List.of(artefact.artefactId().toString()), null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void sendMessageWithUnknownArtefactRefThrowsIllegalArgument() {
        helper.createChannel("arv-ch-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String fakeUuid = UUID.randomUUID().toString();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> helper.sendMessage("arv-ch-2", "alice", "status", "with bad ref", null, null, null, List.of(fakeUuid), null, null, null, null, null));

        assertTrue(ex.getMessage().contains(fakeUuid),
                "Error message should identify the unknown artefact UUID");
    }

    @Test
    @TestTransaction
    void sendMessageWithMixedValidAndInvalidRefsThrows() {
        helper.createChannel("arv-ch-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ArtefactDetail good = helper.shareArtefact(
                "arv-data-3", "desc", "alice", "content", false, true);
        String badUuid = UUID.randomUUID().toString();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> helper.sendMessage("arv-ch-3", "alice", "status", "mixed refs", null, null, null, List.of(good.artefactId().toString(), badUuid), null, null, null, null, null));

        assertTrue(ex.getMessage().contains(badUuid));
    }

    @Test
    @TestTransaction
    void sendMessageWithNullArtefactRefsSkipsValidation() {
        helper.createChannel("arv-ch-4", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // null refs should always succeed — no validation needed
        assertDoesNotThrow(() -> helper.sendMessage("arv-ch-4", "alice", "status", "no refs", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void sendMessageWithEmptyArtefactRefsListSkipsValidation() {
        helper.createChannel("arv-ch-5", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> helper.sendMessage("arv-ch-5", "alice", "status", "empty refs", null, null, null, List.of(), null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void sendMessageWithIncompleteArtefactRefIsAllowed() {
        // An artefact mid-chunked-upload (complete=false) can still be referenced —
        // the receiver checks completeness via get_artefact before consuming
        helper.createChannel("arv-ch-6", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ArtefactDetail incomplete = helper.shareArtefact(
                "arv-data-6", "desc", "alice", "chunk1", false, false);

        assertDoesNotThrow(() -> helper.sendMessage("arv-ch-6", "alice", "status", "ref to incomplete", null, null, null, List.of(incomplete.artefactId().toString()), null, null, null, null, null),
                "References to incomplete artefacts should be allowed");
    }
}
