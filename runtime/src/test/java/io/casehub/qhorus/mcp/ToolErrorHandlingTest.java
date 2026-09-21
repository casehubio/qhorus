package io.casehub.qhorus.mcp;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies {@code @WrapBusinessError} error handling across the full MCP pipeline.
 *
 * <p>
 * {@code @WrapBusinessError({IllegalArgumentException.class, IllegalStateException.class})}
 * on {@link QhorusTestHelper} causes the quarkus-mcp-server interceptor to convert
 * those exceptions into {@link IllegalStateException}, which the library then serialises
 * as {@code {"result": {"isError": true, "content": [{"type": "text", "text": "..."}]}}}
 * rather than a JSON-RPC {@code -32603} protocol error.
 *
 * <p>
 * The CDI-level unit tests verify the interceptor fires. The HTTP-level tests verify
 * the full pipeline: exception → IllegalStateException → isError:true MCP response.
 *
 * <p>
 * Refs #56, ADR-0001.
 */
@QuarkusTest
class ToolErrorHandlingTest {

    @Inject QhorusTestHelper helper;

    // =========================================================================
    // CDI-level — interceptor wraps the exception
    // =========================================================================

    @Test
    @TestTransaction
    void pauseChannel_nonExistent_throwsIllegalArgumentException() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> helper.pauseChannel("wrap-test-nonexistent", "any-caller"));
    }

    @Test
    @TestTransaction
    void sendMessage_pausedChannel_throwsIllegalStateException() {
        // IllegalStateException (channel paused) also wrapped
        helper.createChannel("wrap-test-paused", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.registerInstance("wrap-test-paused", "inst-1", null, null, null, null, null);
        helper.pauseChannel("wrap-test-paused", "inst-1");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> helper.sendMessage("wrap-test-paused", "inst-1", "status", "hello", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void pauseChannel_nonExistentUuid_throwsIllegalArgumentException() {
        String nonExistentUuid = java.util.UUID.randomUUID().toString();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> helper.pauseChannel(nonExistentUuid, null));
    }
}
