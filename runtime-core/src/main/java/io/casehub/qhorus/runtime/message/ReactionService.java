package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.channel.ReactionManager;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.message.ReactionChangedEvent;
import io.casehub.qhorus.api.message.ReactionGroup;
import io.casehub.qhorus.api.store.ReactionStore;
import io.casehub.platform.api.identity.CurrentPrincipal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ReactionService implements ReactionManager {

    private final ReactionStore reactionStore;
    private final Consumer<ReactionChangedEvent> reactionConsumer;
    private final CurrentPrincipal currentPrincipal;

    public ReactionService(ReactionStore reactionStore,
                           Consumer<ReactionChangedEvent> reactionConsumer,
                           CurrentPrincipal currentPrincipal) {
        this.reactionStore = reactionStore;
        this.reactionConsumer = reactionConsumer;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public Reaction react(Long messageId, String emoji) {
        return react(messageId, emoji, currentPrincipal.actorId(), currentPrincipal.tenancyId());
    }

    @Override
    public boolean unreact(Long messageId, String emoji) {
        return unreact(messageId, emoji, currentPrincipal.actorId());
    }

    public Reaction react(Long messageId, String emoji, String actorId, String tenancyId) {
        String trimmed = validateEmoji(emoji);
        Reaction r = reactionStore.react(messageId, trimmed, actorId, tenancyId);
        if (reactionConsumer != null) {
            reactionConsumer.accept(new ReactionChangedEvent(messageId, trimmed, actorId, true));
        }
        return r;
    }

    public boolean unreact(Long messageId, String emoji, String actorId) {
        String trimmed = validateEmoji(emoji);
        boolean removed = reactionStore.unreact(messageId, trimmed, actorId);
        if (removed && reactionConsumer != null) {
            reactionConsumer.accept(new ReactionChangedEvent(messageId, trimmed, actorId, false));
        }
        return removed;
    }

    public List<ReactionGroup> getReactions(Long messageId) {
        return groupReactions(reactionStore.findByMessage(messageId));
    }

    public Map<Long, List<ReactionGroup>> getReactionsBatch(Collection<Long> messageIds) {
        Map<Long, List<Reaction>> raw = reactionStore.findByMessages(messageIds);
        return raw.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> groupReactions(e.getValue())));
    }

    private static List<ReactionGroup> groupReactions(List<Reaction> reactions) {
        return reactions.stream()
                .collect(Collectors.groupingBy(Reaction::emoji))
                .entrySet().stream()
                .map(e -> new ReactionGroup(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(Reaction::actorId).toList()))
                .toList();
    }

    private static String validateEmoji(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("emoji is required");
        }
        return emoji.strip();
    }
}
