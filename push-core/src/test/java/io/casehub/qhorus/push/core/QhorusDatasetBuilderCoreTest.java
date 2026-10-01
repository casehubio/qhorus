package io.casehub.qhorus.push.core;

import io.casehub.pages.push.PushColumn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QhorusDatasetBuilderCoreTest {

    @Test
    void channelColumnsIncludeSpaceFields() {
        var names = QhorusDatasetBuilderCore.CHANNEL_COLUMNS.stream()
            .map(PushColumn::id).toList();
        assertThat(names).contains("spaceId", "spaceName", "parentSpaceId");
    }

    @Test
    void channelColumnsHaveEightEntries() {
        assertThat(QhorusDatasetBuilderCore.CHANNEL_COLUMNS).hasSize(9);
    }

    @Test
    void channelSnapshotColumnsIncludeUnreadCount() {
        var names = QhorusDatasetBuilderCore.CHANNEL_SNAPSHOT_COLUMNS.stream()
                                                                     .map(PushColumn::id).toList();
        assertThat(names).hasSize(10);
        assertThat(names.get(9)).isEqualTo("unreadCount");
        assertThat(names).containsAll(
                QhorusDatasetBuilderCore.CHANNEL_COLUMNS.stream().map(PushColumn::id).toList());
    }

    @Test
    void allTopicsHasEightEntries() {
        assertThat(QhorusDatasetBuilderCore.ALL_TOPICS).hasSize(8);
        assertThat(QhorusDatasetBuilderCore.ALL_TOPICS).contains(
            "chat:channels", "chat:topics", "chat:messages",
            "chat:members", "chat:presence", "chat:reactions", "chat:commitments", "chat:spaces");
    }

    @Test
    void messageColumnsHaveTwelveEntries() {
        assertThat(QhorusDatasetBuilderCore.MESSAGE_COLUMNS).hasSize(12);
    }

    @Test
    void topicConstantsMatchColumnDatasetNames() {
        assertThat(QhorusDatasetBuilderCore.TOPIC_CHANNELS).isEqualTo("chat:channels");
        assertThat(QhorusDatasetBuilderCore.TOPIC_MESSAGES).isEqualTo("chat:messages");
        assertThat(QhorusDatasetBuilderCore.TOPIC_MEMBERS).isEqualTo("chat:members");
        assertThat(QhorusDatasetBuilderCore.TOPIC_PRESENCE).isEqualTo("chat:presence");
        assertThat(QhorusDatasetBuilderCore.TOPIC_REACTIONS).isEqualTo("chat:reactions");
        assertThat(QhorusDatasetBuilderCore.TOPIC_COMMITMENTS).isEqualTo("chat:commitments");
        assertThat(QhorusDatasetBuilderCore.TOPIC_TOPICS).isEqualTo("chat:topics");
    }
}
