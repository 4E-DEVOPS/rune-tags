package com.runetags.history;

import com.google.gson.Gson;
import com.runetags.Configurations;
import com.runetags.mention.MatchReason;
import com.runetags.chat.TaggedMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.client.RuneLite;

@Slf4j
public class MentionHistoryService
{
    private static final Path HISTORY_DIRECTORY =
            RuneLite.RUNELITE_DIR
                    .toPath()
                    .resolve("RuneTags")
                    .resolve("history");

    private static final Path HISTORY_FILE =
            HISTORY_DIRECTORY.resolve(
                    "history.jsonl");

    private static final Path HISTORY_TEMP_FILE =
            HISTORY_DIRECTORY.resolve(
                    "history.jsonl.tmp");

    private final Configurations config;
    private final Gson gson;

    private final Deque<MentionHistoryEntry> entries =
            new ArrayDeque<>();

    @Inject
    public MentionHistoryService(
            Configurations config,
            Gson gson)
    {
        this.config = config;
        this.gson = gson;

        load();
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

        if (!Files.exists(HISTORY_FILE))
        {
            return;
        }

        try (BufferedReader reader =
                     Files.newBufferedReader(
                             HISTORY_FILE,
                             StandardCharsets.UTF_8))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    continue;
                }

                try
                {
                    final PersistedEntry persisted =
                            gson.fromJson(
                                    line,
                                    PersistedEntry.class);

                    final MentionHistoryEntry entry =
                            fromPersisted(persisted);

                    if (entry != null)
                    {
                        /*
                         * The file is stored newest-to-oldest; preserve that order when loading.
                         */
                        entries.addLast(entry);
                    }
                }
                catch (RuntimeException ex)
                {
                    /*
                     * One corrupt line must not discard the rest of the history.
                     */
                    log.debug("[RuneTags][Mention-History] Unable to Read Entry", ex);
                }
            }

            trim();

            //log.debug("[RuneTags][Mention-History] Loaded {} Entries from '{}'", entries.size(), HISTORY_FILE);
        }
        catch (IOException ex)
        {
            log.warn("[RuneTags][Mention-History] Unable to Load from '{}'", HISTORY_FILE, ex);
        }
    }

    private void save()
    {
        try
        {
            Files.createDirectories(
                    HISTORY_DIRECTORY);

            try (BufferedWriter writer =
                         Files.newBufferedWriter(
                                 HISTORY_TEMP_FILE,
                                 StandardCharsets.UTF_8))
            {
                for (MentionHistoryEntry entry : entries)
                {
                    writer.write(
                            gson.toJson(
                                    toPersisted(entry)));

                    writer.newLine();
                }
            }

            /*
             * Write to a temporary file first so an interrupted write is
             * less likely to leave history.jsonl partially written.
             */
            try
            {
                Files.move(
                        HISTORY_TEMP_FILE,
                        HISTORY_FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException atomicMoveFailure)
            {
                /*
                 * Some file systems do not support ATOMIC_MOVE.
                 */
                Files.move(
                        HISTORY_TEMP_FILE,
                        HISTORY_FILE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ex)
        {
            log.warn("[RuneTags][Mention-History] Unable to Save to '{}'", HISTORY_FILE, ex);
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
     * the on-disk format remains simple and tolerant of internal model changes.
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
}