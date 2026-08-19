package io.casehub.a2a.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2ATaskStateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void wireValue_matchesA2ASpec() {
        assertThat(A2ATaskState.SUBMITTED.wireValue()).isEqualTo("submitted");
        assertThat(A2ATaskState.WORKING.wireValue()).isEqualTo("working");
        assertThat(A2ATaskState.INPUT_REQUIRED.wireValue()).isEqualTo("input-required");
        assertThat(A2ATaskState.COMPLETED.wireValue()).isEqualTo("completed");
        assertThat(A2ATaskState.FAILED.wireValue()).isEqualTo("failed");
        assertThat(A2ATaskState.CANCELED.wireValue()).isEqualTo("canceled");
    }

    @Test
    void isTerminal_completedFailedCanceled() {
        assertThat(A2ATaskState.COMPLETED.isTerminal()).isTrue();
        assertThat(A2ATaskState.FAILED.isTerminal()).isTrue();
        assertThat(A2ATaskState.CANCELED.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_nonTerminal() {
        assertThat(A2ATaskState.SUBMITTED.isTerminal()).isFalse();
        assertThat(A2ATaskState.WORKING.isTerminal()).isFalse();
        assertThat(A2ATaskState.INPUT_REQUIRED.isTerminal()).isFalse();
    }

    @Test
    void fromWireValue_validValues() {
        assertThat(A2ATaskState.fromWireValue("submitted")).isEqualTo(A2ATaskState.SUBMITTED);
        assertThat(A2ATaskState.fromWireValue("canceled")).isEqualTo(A2ATaskState.CANCELED);
        assertThat(A2ATaskState.fromWireValue("input-required")).isEqualTo(A2ATaskState.INPUT_REQUIRED);
    }

    @Test
    void fromWireValue_unknownValue_throws() {
        assertThatThrownBy(() -> A2ATaskState.fromWireValue("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void jsonSerialization_usesWireValue() throws Exception {
        String json = MAPPER.writeValueAsString(A2ATaskState.COMPLETED);
        assertThat(json).isEqualTo("\"completed\"");
    }
}
