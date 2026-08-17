package io.casehub.qhorus.push;

import io.casehub.pages.push.PushColumn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QhorusDatasetBuilderTest {

    @Test
    void channelColumnsIncludeSpaceFields() {
        var names = QhorusDatasetBuilder.CHANNEL_COLUMNS.stream()
            .map(PushColumn::id).toList();
        assertThat(names).contains("spaceId", "spaceName", "parentSpaceId");
    }

    @Test
    void channelColumnsHaveEightEntries() {
        assertThat(QhorusDatasetBuilder.CHANNEL_COLUMNS).hasSize(8);
    }

    @Test
    void allTopicsHasSevenEntries() {
        assertThat(QhorusDatasetBuilder.ALL_TOPICS).hasSize(7);
        assertThat(QhorusDatasetBuilder.ALL_TOPICS).contains(
            "chat:channels", "chat:topics", "chat:messages",
            "chat:members", "chat:presence", "chat:reactions", "chat:commitments");
    }

    @Test
    void messageColumnsHaveTwelveEntries() {
        assertThat(QhorusDatasetBuilder.MESSAGE_COLUMNS).hasSize(12);
    }

    @Test
    void topicConstantsMatchColumnDatasetNames() {
        assertThat(QhorusDatasetBuilder.TOPIC_CHANNELS).isEqualTo("chat:channels");
        assertThat(QhorusDatasetBuilder.TOPIC_MESSAGES).isEqualTo("chat:messages");
        assertThat(QhorusDatasetBuilder.TOPIC_MEMBERS).isEqualTo("chat:members");
        assertThat(QhorusDatasetBuilder.TOPIC_PRESENCE).isEqualTo("chat:presence");
        assertThat(QhorusDatasetBuilder.TOPIC_REACTIONS).isEqualTo("chat:reactions");
        assertThat(QhorusDatasetBuilder.TOPIC_COMMITMENTS).isEqualTo("chat:commitments");
        assertThat(QhorusDatasetBuilder.TOPIC_TOPICS).isEqualTo("chat:topics");
    }
}
