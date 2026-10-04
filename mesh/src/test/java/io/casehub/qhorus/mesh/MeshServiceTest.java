package io.casehub.qhorus.mesh;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class MeshServiceTest {

    @Inject MeshService service;

    @Test
    @Transactional
    void register_and_discover() {
        MeshRegistration reg1 = service.meshRegister("casehub/qhorus", "qhorus session",
                Map.of("project", "qhorus", "family", "casehub"));
        assertThat(reg1.instanceId()).isEqualTo("casehub/qhorus");

        MeshRegistration reg2 = service.meshRegister("casehub/claudony", "claudony session",
                Map.of("project", "claudony", "family", "casehub"));
        assertThat(reg2.instanceId()).isEqualTo("casehub/claudony");

        List<PeerInfo> peers = service.meshDiscoverPeers("family", "casehub");
        assertThat(peers).extracting(PeerInfo::instanceId)
                .contains("casehub/qhorus", "casehub/claudony");

        List<PeerInfo> qhorusPeers = service.meshDiscoverPeers("project", "qhorus");
        assertThat(qhorusPeers).extracting(PeerInfo::instanceId)
                .contains("casehub/qhorus")
                .doesNotContain("casehub/claudony");
    }

    @Test
    @Transactional
    void create_channel_and_send_message() {
        service.meshRegister("test-sender", "test", Map.of());

        String created = service.meshCreateChannel("design-review",
                Map.of("project", "qhorus", "purpose", "review"));
        assertThat(created).contains("design-review");

        MessageResult sent = service.meshSendMessage("design-review", "test-sender",
                "status", "Has anyone reviewed the mesh spec?");
        assertThat(sent.type()).isEqualTo(io.casehub.qhorus.api.message.MessageType.STATUS);

        String messages = service.meshCheckMessages("design-review", null);
        assertThat(messages).contains("Has anyone reviewed the mesh spec?");
    }

    @Test
    @Transactional
    void list_channels_filtered_by_metadata() {
        service.meshCreateChannel("qhorus-work", Map.of("project", "qhorus"));
        service.meshCreateChannel("claudony-work", Map.of("project", "claudony"));

        String filtered = service.meshListChannels("project", "qhorus");
        assertThat(filtered).contains("qhorus-work");
        assertThat(filtered).doesNotContain("claudony-work");
    }
}
