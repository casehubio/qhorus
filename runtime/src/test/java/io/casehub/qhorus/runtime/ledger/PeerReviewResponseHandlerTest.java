package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeerReviewResponseHandlerTest {

    private PeerReviewResponseHandler handler;
    private PeerAttestationWriter peerAttestationWriter;
    private static final String TENANT = "test-tenant";
    private static final UUID ENTRY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new PeerReviewResponseHandler();
        peerAttestationWriter = mock(PeerAttestationWriter.class);
        handler.peerAttestationWriter = peerAttestationWriter;
        handler.objectMapper = new ObjectMapper();
        when(peerAttestationWriter.write(any(), any(), any(), any(), any()))
                .thenReturn(new LedgerAttestation());
    }

    @Test
    void parses_structured_response_and_writes_attestation() {
        String content = "{\"peer_review_response\": {"
                + "\"ledger_entry_id\": \"" + ENTRY_ID + "\","
                + "\"verdict\": \"ENDORSED\","
                + "\"evidence\": \"output verified\"}}";

        handler.onMessage(responseEvent(content));

        verify(peerAttestationWriter).write(
                eq(ENTRY_ID), eq(AttestationVerdict.ENDORSED),
                eq("output verified"), eq("reviewer-a"), eq(TENANT));
    }

    @Test
    void ignores_non_response_messages() {
        handler.onMessage(event(MessageType.STATUS, "some content"));
        verify(peerAttestationWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void ignores_response_without_peer_review_response_key() {
        handler.onMessage(responseEvent("{\"regular\": \"response\"}"));
        verify(peerAttestationWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void ignores_response_with_null_content() {
        handler.onMessage(responseEvent(null));
        verify(peerAttestationWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void falls_back_silently_on_malformed_json() {
        handler.onMessage(responseEvent("not json at all"));
        verify(peerAttestationWriter, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void extracts_challenged_verdict() {
        String content = "{\"peer_review_response\": {"
                + "\"ledger_entry_id\": \"" + ENTRY_ID + "\","
                + "\"verdict\": \"CHALLENGED\","
                + "\"evidence\": \"missing fields\"}}";

        handler.onMessage(responseEvent(content));

        verify(peerAttestationWriter).write(
                eq(ENTRY_ID), eq(AttestationVerdict.CHALLENGED),
                eq("missing fields"), eq("reviewer-a"), eq(TENANT));
    }

    @Test
    void handles_missing_evidence_field() {
        String content = "{\"peer_review_response\": {"
                + "\"ledger_entry_id\": \"" + ENTRY_ID + "\","
                + "\"verdict\": \"ENDORSED\"}}";

        handler.onMessage(responseEvent(content));

        verify(peerAttestationWriter).write(
                eq(ENTRY_ID), eq(AttestationVerdict.ENDORSED),
                eq((String) null), eq("reviewer-a"), eq(TENANT));
    }

    private MessageReceivedEvent responseEvent(String content) {
        return event(MessageType.RESPONSE, content);
    }

    private MessageReceivedEvent event(MessageType type, String content) {
        return new MessageReceivedEvent(1L, "test-ch", UUID.randomUUID(), TENANT,
                type, "reviewer-a", UUID.randomUUID().toString(), Instant.now(), content, null);
    }
}
