package io.casehub.qhorus.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.pages.push.PushColumn;
import io.casehub.pages.push.PushMessage;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.UnreadCount;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.MembershipReader;
import io.casehub.qhorus.api.store.ReactionReader;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.TopicReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class QhorusDatasetBuilder {

    public static final String TOPIC_CHANNELS    = "chat:channels";
    public static final String TOPIC_TOPICS      = "chat:topics";
    public static final String TOPIC_MESSAGES    = "chat:messages";
    public static final String TOPIC_MEMBERS     = "chat:members";
    public static final String TOPIC_PRESENCE    = "chat:presence";
    public static final String TOPIC_REACTIONS   = "chat:reactions";
    public static final String TOPIC_COMMITMENTS = "chat:commitments";
    public static final String TOPIC_SPACES      = "chat:spaces";


    public static final List<String> ALL_TOPICS = List.of(
        TOPIC_CHANNELS, TOPIC_TOPICS, TOPIC_MESSAGES,
        TOPIC_MEMBERS, TOPIC_PRESENCE, TOPIC_REACTIONS, TOPIC_COMMITMENTS, TOPIC_SPACES);

    public static final List<PushColumn> CHANNEL_COLUMNS = List.of(
        new PushColumn("id", "ID", "LABEL"),
        new PushColumn("name", "Name", "LABEL"),
        new PushColumn("topic", "Topic", "LABEL"),
        new PushColumn("description", "Description", "LABEL"),
        new PushColumn("isPrivate", "Private", "LABEL"),
        new PushColumn("spaceId", "Space ID", "LABEL"),
        new PushColumn("spaceName", "Space Name", "LABEL"),
        new PushColumn("parentSpaceId", "Parent Space", "LABEL"),
        new PushColumn("displayOrder", "Order", "LABEL"));
    public static final List<PushColumn> CHANNEL_SNAPSHOT_COLUMNS;

    static {
        var cols = new java.util.ArrayList<>(CHANNEL_COLUMNS);
        cols.add(new PushColumn("unreadCount", "Unread", "LABEL"));
        CHANNEL_SNAPSHOT_COLUMNS = List.copyOf(cols);
    }


    public static final List<PushColumn> MESSAGE_COLUMNS = List.of(
        new PushColumn("channelId", "Channel", "LABEL"),
        new PushColumn("messageId", "Message ID", "LABEL"),
        new PushColumn("parentId", "Parent", "LABEL"),
        new PushColumn("senderId", "Sender", "LABEL"),
        new PushColumn("text", "Text", "LABEL"),
        new PushColumn("timestamp", "Timestamp", "DATE"),
        new PushColumn("messageType", "Type", "LABEL"),
        new PushColumn("actorType", "Actor", "LABEL"),
        new PushColumn("topicId", "Topic", "LABEL"),
        new PushColumn("correlationId", "Correlation", "LABEL"),
        new PushColumn("artefactRefs", "Artefacts", "LABEL"),
        new PushColumn("target", "Target", "LABEL"));

    public static final List<PushColumn> MEMBER_COLUMNS = List.of(
        new PushColumn("membershipId", "Membership", "LABEL"),
        new PushColumn("channelId", "Channel", "LABEL"),
        new PushColumn("memberId", "Member", "LABEL"),
        new PushColumn("displayName", "Display Name", "LABEL"),
        new PushColumn("role", "Role", "LABEL"));

    public static final List<PushColumn> PRESENCE_COLUMNS = List.of(
        new PushColumn("memberId", "Member", "LABEL"),
        new PushColumn("status", "Status", "LABEL"),
        new PushColumn("lastActiveAt", "Last Active", "DATE"));

    public static final List<PushColumn> REACTION_COLUMNS = List.of(
        new PushColumn("messageId", "Message ID", "LABEL"),
        new PushColumn("emoji", "Emoji", "LABEL"));

    public static final List<PushColumn> COMMITMENT_COLUMNS = List.of(
        new PushColumn("correlationId", "Correlation", "LABEL"),
        new PushColumn("channelId", "Channel", "LABEL"),
        new PushColumn("state", "State", "LABEL"),
        new PushColumn("deadline", "Deadline", "DATE"),
        new PushColumn("acknowledgedAt", "Acknowledged", "DATE"),
        new PushColumn("resolvedAt", "Resolved", "DATE"),
        new PushColumn("createdAt", "Created", "DATE"));

    public static final List<PushColumn> TOPIC_COLUMNS = List.of(
        new PushColumn("topicId", "Topic ID", "LABEL"),
        new PushColumn("channelId", "Channel", "LABEL"),
        new PushColumn("name", "Name", "LABEL"),
        new PushColumn("state", "State", "LABEL"),
        new PushColumn("messageCount", "Messages", "LABEL"),
        new PushColumn("latestActivityTs", "Latest", "DATE"),
        new PushColumn("createdAt", "Created", "DATE"));
    public static final List<PushColumn> SPACE_COLUMNS = List.of(
            new PushColumn("id", "ID", "LABEL"),
            new PushColumn("name", "Name", "LABEL"),
            new PushColumn("description", "Description", "LABEL"),
            new PushColumn("parentSpaceId", "Parent Space", "LABEL"));


    @Inject ChannelReader channelReader;
    @Inject ConsumerMessaging messaging;
    @Inject MembershipReader memberReader;
    @Inject ReactionReader reactionReader;
    @Inject CommitmentReader commitmentReader;
    @Inject TopicReader topicReader;
    @Inject TopicManager topicManager;
    @Inject PresenceTracker presenceTracker;
    @Inject SpaceStore spaceStore;
    @Inject UnreadCountProvider unreadCountProvider;

    @Inject ObjectMapper objectMapper;

    public String buildSnapshot(String topic) {
        return buildSnapshot(topic, null);
    }

    public String buildSnapshot(String topic, Long seq) {
        return switch (topic) {
            case TOPIC_CHANNELS -> buildChannelSnapshot(seq);
            case TOPIC_TOPICS -> buildTopicSnapshot(seq);
            case TOPIC_MESSAGES -> buildMessageSnapshot(seq);
            case TOPIC_MEMBERS -> buildMemberSnapshot(seq);
            case TOPIC_PRESENCE -> buildPresenceSnapshot(seq);
            case TOPIC_REACTIONS -> buildReactionSnapshot(seq);
            case TOPIC_COMMITMENTS -> buildCommitmentSnapshot(seq);
            case TOPIC_SPACES -> buildSpaceSnapshot(seq);
            default -> throw new IllegalArgumentException("Unknown topic: " + topic);
        };
    }

    public String buildSnapshot(String topic, Long seq, String userId, String tenancyId) {
        if (TOPIC_CHANNELS.equals(topic) && userId != null) {
            return buildChannelSnapshot(seq, userId, tenancyId);
        }
        return buildSnapshot(topic, seq);
    }


    private String buildChannelSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var spaceIds = channels.stream()
                               .map(ch -> ch.spaceId())
                               .filter(id -> id != null)
                               .distinct()
                               .toList();
        Map<UUID, Space> spaces = spaceIds.isEmpty()
                                  ? Map.of()
                                  : spaceStore.findByIds(spaceIds).stream()
                                              .collect(Collectors.toMap(Space::id, s -> s));
        var rows = channels.stream()
                           .map(ch -> {
                               Space space = ch.spaceId() != null ? spaces.get(ch.spaceId()) : null;
                               return channelToRow(ch, space);
                           })
                           .toList();
        return PushMessage.snapshot("channels", CHANNEL_COLUMNS, rows, seq);}

    private String buildChannelSnapshot(Long seq, String userId, String tenancyId) {
        var channels = channelReader.listAll();
        var spaceIds = channels.stream()
                               .map(ch -> ch.spaceId())
                               .filter(id -> id != null)
                               .distinct()
                               .toList();
        Map<UUID, Space> spaces = spaceIds.isEmpty()
                                  ? Map.of()
                                  : spaceStore.findByIds(spaceIds).stream()
                                              .collect(Collectors.toMap(Space::id, s -> s));
        Map<UUID, UnreadCount> unreadCounts = unreadCountProvider.getUnreadCounts(userId, tenancyId);
        var rows = channels.stream()
                           .map(ch -> {
                               Space       space = ch.spaceId() != null ? spaces.get(ch.spaceId()) : null;
                               UnreadCount uc    = unreadCounts.get(ch.id());
                               var         row   = new ArrayList<>(channelToRow(ch, space));
                               row.add(uc != null ? String.valueOf(uc.count()) : "0");
                               return (List<String>) row;
                           })
                           .toList();
        return PushMessage.snapshot("channels", CHANNEL_SNAPSHOT_COLUMNS, rows, seq);}


    private String buildTopicSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var rows = new ArrayList<List<String>>();
        for (var ch : channels) {
            for (var ts : topicManager.listTopics(ch.id())) {
                var topic = topicReader.find(ch.id(), ts.name());
                Long topicId = topic.map(Topic::id).orElse(null);
                rows.add(List.of(
                    topicId != null ? String.valueOf(topicId) : ts.name(),
                    ch.id().toString(), ts.name(),
                    ts.resolved() ? "RESOLVED" : "ACTIVE",
                    String.valueOf(ts.messageCount()),
                    ts.lastActivityAt() != null ? ts.lastActivityAt().toString() : "",
                    topic.map(t -> t.createdAt().toString()).orElse("")));
            }
        }
        return PushMessage.snapshot("topics", TOPIC_COLUMNS, rows, seq);
    }

    private String buildMessageSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var rows = new ArrayList<List<String>>();
        for (var ch : channels) {
            for (var msg : messaging.history(ch.id(), 0, 10000)) {
                rows.add(messageToRow(msg));
            }
        }
        return PushMessage.snapshot("messages", MESSAGE_COLUMNS, rows, seq);
    }

    private String buildMemberSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var rows = new ArrayList<List<String>>();
        for (var ch : channels) {
            for (var m : memberReader.findByChannel(ch.id())) {
                String membershipId = ch.id().toString() + ":" + m.memberId();
                rows.add(List.of(membershipId, ch.id().toString(), m.memberId(), m.memberId(), m.role().name()));
            }
        }
        return PushMessage.snapshot("members", MEMBER_COLUMNS, rows, seq);
    }

    private String buildPresenceSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var rows = new ArrayList<List<String>>();
        for (var ch : channels) {
            for (var p : presenceTracker.getChannelPresence(ch.id())) {
                rows.add(List.of(p.memberId(), p.status().name(),
                    p.lastSeenAt() != null ? p.lastSeenAt().toString() : ""));
            }
        }
        return PushMessage.snapshot("presence", PRESENCE_COLUMNS, rows, seq);
    }

    private String buildReactionSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var rows = new ArrayList<List<String>>();
        for (var ch : channels) {
            var msgs = messaging.history(ch.id(), 0, 10000);
            var msgIds = msgs.stream().map(Message::id).toList();
            if (!msgIds.isEmpty()) {
                var reactionsMap = reactionReader.findByMessages(msgIds);
                for (var entry : reactionsMap.entrySet()) {
                    for (var r : entry.getValue()) {
                        rows.add(List.of(String.valueOf(r.messageId()), r.emoji()));
                    }
                }
            }
        }
        return PushMessage.snapshot("reactions", REACTION_COLUMNS, rows, seq);
    }

    private String buildCommitmentSnapshot(Long seq) {
        var channels = channelReader.listAll();
        var rows = new ArrayList<List<String>>();
        for (var ch : channels) {
            for (var c : commitmentReader.findByChannel(ch.id())) {
                rows.add(commitmentToRow(c));
            }
        }
        return PushMessage.snapshot("commitments", COMMITMENT_COLUMNS, rows, seq);
    }

    private String buildSpaceSnapshot(Long seq) {
        List<Space> allSpaces = collectAllSpaces();
        var         rows      = new ArrayList<List<String>>();
        for (Space s : allSpaces) {
            rows.add(spaceToRow(s));
        }
        return PushMessage.snapshot("spaces", SPACE_COLUMNS, rows, seq);
    }

    private List<Space> collectAllSpaces() {
        List<Space> result = new ArrayList<>();
        List<Space> roots  = spaceStore.listRoots();
        for (Space root : roots) {
            collectSpaceTree(root, result);
        }
        return result;
    }

    private void collectSpaceTree(Space space, List<Space> result) {
        result.add(space);
        for (Space child : spaceStore.listByParent(space.id())) {
            collectSpaceTree(child, result);
        }
    }

    public List<String> spaceToRow(Space space) {
        return List.of(
                space.id().toString(),
                space.name(),
                space.description() != null ? space.description() : "",
                space.parentSpaceId() != null ? space.parentSpaceId().toString() : "");
    }

    public List<String> channelToRow(io.casehub.qhorus.api.channel.Channel ch, Space space) {
        return List.of(
                ch.id().toString(), ch.name(), "",
                ch.description() != null ? ch.description() : "", "false",
                ch.spaceId() != null ? ch.spaceId().toString() : "",
                space != null ? space.name() : "",
                space != null && space.parentSpaceId() != null ? space.parentSpaceId().toString() : "",
                ch.displayOrder() != null ? String.valueOf(ch.displayOrder()) : "");
    }


    public List<String> messageToRow(Message msg) {
        String topicIdStr = "";
        if (msg.topic() != null && !msg.topic().isEmpty()) {
            var topic = topicReader.find(msg.channelId(), msg.topic());
            topicIdStr = topic.map(t -> String.valueOf(t.id())).orElse("");
        }
        String artefactRefsJson = "[]";
        if (msg.artefactRefs() != null && !msg.artefactRefs().isEmpty()) {
            artefactRefsJson = toJson(msg.artefactRefs());
        }
        var row = new ArrayList<String>(12);
        row.add(msg.channelId().toString());
        row.add(String.valueOf(msg.id()));
        row.add(msg.inReplyTo() != null ? String.valueOf(msg.inReplyTo()) : null);
        row.add(msg.sender());
        row.add(msg.content());
        row.add(msg.createdAt().toString());
        row.add(msg.messageType().name());
        row.add(msg.actorType().name());
        row.add(topicIdStr);
        row.add(msg.correlationId());
        row.add(artefactRefsJson);
        row.add(msg.target());
        return row;
    }

    public List<String> outboundMessageToRow(ChannelRef channel, OutboundMessage message) {
        String artefactRefsJson = "[]";
        if (message.artefactRefs() != null && !message.artefactRefs().isEmpty()) {
            artefactRefsJson = toJson(message.artefactRefs());
        }
        var row = new ArrayList<String>(12);
        row.add(channel.id().toString());
        row.add(String.valueOf(message.sequenceId()));
        row.add(message.inReplyTo() != null ? String.valueOf(message.inReplyTo()) : null);
        row.add(message.sender());
        row.add(message.content());
        row.add(Instant.now().toString());
        row.add(message.type().name());
        row.add(message.senderActorType().name());
        row.add(message.topic() != null ? message.topic() : "");
        row.add(message.correlationId());
        row.add(artefactRefsJson);
        row.add(message.target());
        return row;
    }

    public List<String> commitmentToRow(Commitment c) {
        return List.of(
            c.correlationId(), c.channelId().toString(), c.state().name(),
            c.expiresAt() != null ? c.expiresAt().toString() : "",
            c.acknowledgedAt() != null ? c.acknowledgedAt().toString() : "",
            c.resolvedAt() != null ? c.resolvedAt().toString() : "",
            c.createdAt().toString());
    }

    public List<String> topicToRow(UUID channelId, Topic topic) {
        return List.of(
            String.valueOf(topic.id()), channelId.toString(), topic.name(),
            topic.resolved() ? "RESOLVED" : "ACTIVE",
            "0", topic.createdAt() != null ? topic.createdAt().toString() : "",
            topic.createdAt() != null ? topic.createdAt().toString() : "");
    }

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialisation failed", e);
        }
    }
}
