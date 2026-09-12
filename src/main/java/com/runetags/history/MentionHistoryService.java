package com.runetags.history;

import com.google.gson.Gson;
import com.runetags.Configurations;
import com.runetags.Constants;
import com.runetags.mention.MatchReason;
import com.runetags.chat.TaggedMessage;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class MentionHistoryService
{
    private static final int STORE_VERSION = 1;

    /*
     * v1 launch storage. Keep this key stable so a later file-backed release can
     * migrate existing user history without losing data.
     */
    private static final String CONFIG_KEY =
            "mentionHistoryV1";

    private final Configurations config;
    private final Gson gson;
    private final ConfigManager configManager;

    private final Deque<MentionHistoryEntry> entries =
            new ArrayDeque<>();

    @Inject
    public MentionHistoryService(
            Configurations config,
            Gson gson,
            ConfigManager configManager)
    {
        this.config = config;
        this.gson = gson;
        this.configManager = configManager;

        load();
    }

    /*
     * Test-only convenience constructor. Production construction is owned by
     * RuneLite/Guice through the @Inject constructor above.
     */
    MentionHistoryService(
            Configurations config,
            Gson gson)
    {
        this(config, gson, null);
    }

    public synchronized void add(
            TaggedMessage taggedMessage,
            Integer world,
            String locationName,
            String channelName)
    {
        if (!config.mentionHistory()
                || taggedMessage == null
                || taggedMessage.getLocalMentionMatch() == null
                || !taggedMessage
                .getLocalMentionMatch()
                .isMatchesLocalPlayer())
        {
            return;
        }

        final MentionHistoryEntry entry =
                new MentionHistoryEntry(
                        taggedMessage.getId(),
                        taggedMessage.getCanonicalSender(),
                        taggedMessage.getOriginalMessage(),
                        taggedMessage.getType(),
                        taggedMessage
                                .getLocalMentionMatch()
                                .getReason(),
                        world,
                        locationName,
                        channelName,
                        taggedMessage.getTimestamp());

        entries.addFirst(entry);

        trim();

        save();
    }

    public synchronized List<MentionHistoryEntry> snapshot()
    {
        return Collections.unmodifiableList(
                new ArrayList<>(entries));
    }

    public synchronized int size()
    {
        return entries.size();
    }

    /**
     * Clear persistent history intentionally.
     *
     * Do not call this during normal plugin shutdown.
     */
    public synchronized void clear()
    {
        entries.clear();
        save();
    }

    /**
     * Re-apply the configured maximum and persist if entries were removed.
     */
    public synchronized void enforceLimit()
    {
        final int before =
                entries.size();

        trim();

        if (entries.size() != before)
        {
            save();
        }
    }

    private void load()
    {
        entries.clear();

        if (configManager == null)
        {
            return;
        }

        final String json =
                configManager.getConfiguration(
                        Constants.CONFIG_GROUP,
                        CONFIG_KEY);

        if (json == null
                || json.trim().isEmpty())
        {
            return;
        }

        try
        {
            final PersistedStore store =
                    gson.fromJson(
                            json,
                            PersistedStore.class);

            if (store == null
                    || store.entries == null)
            {
                return;
            }

            for (PersistedEntry persisted : store.entries)
            {
                final MentionHistoryEntry entry =
                        fromPersisted(persisted);

                if (entry != null)
                {
                    /*
                     * Stored entries are newest-to-oldest; preserve that order.
                     */
                    entries.addLast(entry);
                }
            }

            trim();
        }
        catch (RuntimeException ex)
        {
            log.warn(
                    "[RuneTags][Mention-History] Unable to Load Persisted History",
                    ex);
        }
    }

    private void save()
    {
        if (configManager == null)
        {
            return;
        }

        final PersistedStore store =
                new PersistedStore();

        store.version =
                STORE_VERSION;

        store.entries =
                new ArrayList<>();

        for (MentionHistoryEntry entry : entries)
        {
            store.entries.add(
                    toPersisted(entry));
        }

        try
        {
            configManager.setConfiguration(
                    Constants.CONFIG_GROUP,
                    CONFIG_KEY,
                    gson.toJson(store));
        }
        catch (RuntimeException ex)
        {
            log.warn(
                    "[RuneTags][Mention-History] Unable to Save Persisted History",
                    ex);
        }
    }

    private void trim()
    {
        final int maximum =
                Math.max(
                        1,
                        config.maximumHistory());

        while (entries.size() > maximum)
        {
            entries.removeLast();
        }
    }

    private static PersistedEntry toPersisted(
            MentionHistoryEntry entry)
    {
        final PersistedEntry persisted =
                new PersistedEntry();

        persisted.messageId =
                entry.getMessageId();

        persisted.sender =
                entry.getSender();
        persisted.message =
                entry.getMessage();

        persisted.chatType =
                entry.getChatType() != null
                        ? entry.getChatType().name()
                        : null;
        persisted.matchReason =
                entry.getMatchReason() != null
                        ? entry.getMatchReason().name()
                        : null;

        persisted.world =
                entry.getWorld();
        persisted.locationName =
                entry.getLocationName();
        persisted.channelName =
                entry.getChannelName();

        persisted.timestampMillis =
                entry.getTimestamp() != null
                        ? entry.getTimestamp().toEpochMilli()
                        : System.currentTimeMillis();

        return persisted;
    }

    private static MentionHistoryEntry fromPersisted(
            PersistedEntry persisted)
    {
        if (persisted == null)
        {
            return null;
        }

        final ChatMessageType chatType;

        try
        {
            chatType =
                    persisted.chatType != null
                            ? ChatMessageType.valueOf(
                            persisted.chatType)
                            : null;
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }

        final MatchReason matchReason;

        try
        {
            matchReason =
                    persisted.matchReason != null
                            ? MatchReason.valueOf(
                            persisted.matchReason)
                            : null;
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }

        return new MentionHistoryEntry(
                persisted.messageId,
                persisted.sender,
                persisted.message,
                chatType,
                matchReason,
                persisted.world,
                persisted.locationName,
                persisted.channelName,
                Instant.ofEpochMilli(
                        persisted.timestampMillis));
    }

    /*
     * Store primitives and enum names instead of RuneLite objects or Instant so
     * the persisted format remains simple and tolerant of internal model changes.
     */
    private static class PersistedEntry
    {
        long messageId;

        String sender;
        String message;

        String chatType;
        String matchReason;

        Integer world;
        String locationName;
        String channelName;

        long timestampMillis;
    }
    private static final class PersistedStore
    {
        private int version =
                STORE_VERSION;

        private List<PersistedEntry> entries =
                new ArrayList<>();
    }

}