package io.casehub.qhorus.api.judgment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentEventKindsTest {

    @Test
    void allConstantsStartWithPrefix() {
        for (String kind : JudgmentEventKinds.ALL) {
            assertThat(kind).startsWith(JudgmentEventKinds.TOOL_NAME_PREFIX);
        }
    }

    @Test
    void allListContainsAllFourKinds() {
        assertThat(JudgmentEventKinds.ALL).containsExactly(
                JudgmentEventKinds.YIELDED,
                JudgmentEventKinds.RESPONDED,
                JudgmentEventKinds.VERIFIED,
                JudgmentEventKinds.ESCALATED);
    }

    @Test
    void terminalListContainsVerifiedAndEscalated() {
        assertThat(JudgmentEventKinds.TERMINAL).containsExactly(
                JudgmentEventKinds.VERIFIED,
                JudgmentEventKinds.ESCALATED);
    }

    @Test
    void constantValuesMatchExpectedStrings() {
        assertThat(JudgmentEventKinds.YIELDED).isEqualTo("judgment_yielded");
        assertThat(JudgmentEventKinds.RESPONDED).isEqualTo("judgment_responded");
        assertThat(JudgmentEventKinds.VERIFIED).isEqualTo("judgment_verified");
        assertThat(JudgmentEventKinds.ESCALATED).isEqualTo("judgment_escalated");
    }

    @Test
    void listsAreUnmodifiable() {
        assertThat(JudgmentEventKinds.ALL).isUnmodifiable();
        assertThat(JudgmentEventKinds.TERMINAL).isUnmodifiable();
    }
}
