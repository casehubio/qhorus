package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.ArtefactDetail;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SharedDataToolTest {

    @Inject QhorusTestHelper helper;

    @Inject
    InstanceService instanceService;

    @Test
    @TestTransaction
    void shareDataCreatesCompleteArtefact() {
        ArtefactDetail result = helper.shareArtefact("my-report", "Analysis report",
                "alice", "Full content here", false, true);

        assertNotNull(result.artefactId());
        assertEquals("my-report", result.key());
        assertTrue(result.complete());
        assertEquals("Full content here".length(), result.sizeBytes());
    }

    @Test
    @TestTransaction
    void shareDataChunkedUpload() {
        helper.shareArtefact("chunked-report", "Big report", "alice", "chunk1", false, false);
        helper.shareArtefact("chunked-report", null, "alice", " chunk2", true, false);
        ArtefactDetail final_ = helper.shareArtefact("chunked-report", null, "alice", " chunk3", true, true);

        assertEquals("chunk1 chunk2 chunk3", final_.content());
        assertTrue(final_.complete());
    }

    @Test
    @TestTransaction
    void getSharedDataByKey() {
        helper.shareArtefact("get-by-key", "desc", "alice", "content", false, true);

        ArtefactDetail found = helper.getArtefact("get-by-key", null);

        assertEquals("get-by-key", found.key());
        assertEquals("content", found.content());
    }

    @Test
    @TestTransaction
    void getSharedDataByUuid() {
        ArtefactDetail stored = helper.shareArtefact("get-by-uuid", "desc", "alice", "content", false, true);

        ArtefactDetail found = helper.getArtefact(null, stored.artefactId().toString());

        assertEquals(stored.artefactId(), found.artefactId());
    }

    @Test
    @TestTransaction
    void getSharedDataMissingKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.getArtefact("no-such-key", null));
    }

    @Test
    @TestTransaction
    void getSharedDataBothNullThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> helper.getArtefact(null, null),
                "providing neither key nor id should throw IllegalArgumentException");
    }

    @Test
    @TestTransaction
    void getSharedDataMalformedUuidThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.getArtefact(null, "not-a-uuid"));
    }

    @Test
    @TestTransaction
    void getSharedDataIncompleteReturnsResult() {
        ArtefactDetail partial = helper.shareArtefact("partial-data", "desc", "alice", "chunk1", false, false);

        // Should return the artefact even if incomplete — content is available
        ArtefactDetail found = helper.getArtefact("partial-data", null);
        assertFalse(found.complete());
    }

    @Test
    @TestTransaction
    void listSharedDataIncludesStoredArtefacts() {
        helper.shareArtefact("list-a", "desc", "alice", "content a", false, true);
        helper.shareArtefact("list-b", "desc", "bob", "content b", false, true);

        List<ArtefactDetail> all = helper.listArtefacts();

        assertTrue(all.stream().anyMatch(d -> "list-a".equals(d.key())));
        assertTrue(all.stream().anyMatch(d -> "list-b".equals(d.key())));
    }

    @Test
    @TestTransaction
    void listSharedDataIncludesIncompleteArtefacts() {
        helper.shareArtefact("incomplete-item", "desc", "alice", "chunk1", false, false);

        List<ArtefactDetail> all = helper.listArtefacts();

        assertTrue(all.stream().anyMatch(d -> "incomplete-item".equals(d.key())));
    }

    @Test
    @TestTransaction
    void claimAndReleaseChangesGcEligibility() {
        ArtefactDetail artefact = helper.shareArtefact("claim-test", "desc", "alice", "content", false, true);
        var claimant = instanceService.register("claim-agent", "Agent", List.of());

        helper.claimArtefact(artefact.artefactId().toString(), claimant.id().toString());
        assertFalse(helper.isGcEligible(artefact.artefactId().toString()),
                "claimed artefact should not be GC eligible");

        helper.releaseArtefact(artefact.artefactId().toString(), claimant.id().toString());
        assertTrue(helper.isGcEligible(artefact.artefactId().toString()),
                "released artefact with no remaining claims should be GC eligible");
    }
}
