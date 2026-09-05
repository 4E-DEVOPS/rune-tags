package com.runetags.chat;

import com.runetags.model.TaggedMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.runelite.api.ChatMessageType;

/**
 * Runtime semantic storage for RuneTags chat messages.
 *
 * Retention is enforced independently per ChatMessageType.
 *
 * This is important because RuneScape retains separate chat histories for
 * different message types. A busy PUBLICCHAT stream must therefore never evict
 * a still-retained CLAN_CHAT / PRIVATECHAT / FRIENDSCHAT semantic record.
 *
 * The master deque remains globally chronological so existing consumers can
 * continue to treat snapshot() as one ordered stream.
 */
public class TaggedMessageRepository
{
    private final int capacityPerType;

    private final Deque<TaggedMessage> messages =
            new ArrayDeque<>();

    private final Map<ChatMessageType, Integer> countsByType =
            new EnumMap<>(
                    ChatMessageType.class);

    public TaggedMessageRepository(
            int capacityPerType)
    {
        if (capacityPerType < 1)
        {
            throw new IllegalArgumentException(
                    "capacityPerType must be >= 1");
        }

        this.capacityPerType =
                capacityPerType;
    }

    public synchronized void add(
            TaggedMessage message)
    {
        if (message == null)
        {
            return;
        }

        final ChatMessageType retentionType =
                retentionType(
                        message);

        messages.addLast(
                message);

        countsByType.put(
                retentionType,
                countsByType.getOrDefault(
                        retentionType,
                        0) + 1);

        /*
         * Evict only the oldest semantic record belonging to this same chat
         * type.
         *
         * Traffic in another channel must never consume this type's retention
         * allowance.
         */
        while (countsByType.getOrDefault(
                retentionType,
                0) > capacityPerType)
        {
            if (!removeOldestOfType(
                    retentionType))
            {
                /*
                 * Defensive consistency fallback.
                 *
                 * This should never occur because the count was incremented
                 * together with insertion.
                 */
                countsByType.remove(
                        retentionType);

                break;
            }
        }
    }

    public synchronized Optional<TaggedMessage> get(
            long id)
    {
        return messages.stream()
                .filter(message ->
                        message.getId() == id)
                .findFirst();
    }

    public synchronized List<TaggedMessage> snapshot()
    {
        return Collections.unmodifiableList(
                new ArrayList<>(
                        messages));
    }

    public synchronized int size()
    {
        return messages.size();
    }

    public synchronized void clear()
    {
        messages.clear();
        countsByType.clear();
    }

    private boolean removeOldestOfType(
            ChatMessageType type)
    {
        final Iterator<TaggedMessage> iterator =
                messages.iterator();

        while (iterator.hasNext())
        {
            final TaggedMessage candidate =
                    iterator.next();

            if (retentionType(candidate)
                    != type)
            {
                continue;
            }

            iterator.remove();

            final int remaining =
                    countsByType.getOrDefault(
                            type,
                            0) - 1;

            if (remaining > 0)
            {
                countsByType.put(
                        type,
                        remaining);
            }
            else
            {
                countsByType.remove(
                        type);
            }

            return true;
        }

        return false;
    }

    private static ChatMessageType retentionType(
            TaggedMessage message)
    {
        if (message == null
                || message.getType() == null)
        {
            return ChatMessageType.UNKNOWN;
        }

        return message.getType();
    }
}