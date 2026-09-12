package com.runetags.history;

import com.google.gson.Gson;
import com.runetags.Configurations;
import com.runetags.Constants;
import com.runetags.chat.TaggedMessage;
import com.runetags.mention.MatchReason;

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
import net.runelite.client.config.ConfigManager;

@Slf4j
public class MentionHistoryService
{
    private static final int STORE_VERSION = 1;

    private static final String LEGACY_CONFIG_KEY =
            "mentionHistoryV1";

    private static final Path DEFAULT_HISTORY_DIRECTORY =
            RuneLite.RUNELITE_DIR
                    .toPath()
                    .resolve("RuneTags")
                    .resolve("history");

    private static final Path DEFAULT_HISTORY_FILE =
            DEFAULT_HISTORY_DIRECTORY.resolve(
                    "history.json");

    private final Configurations config;
    private final Gson gson;
    private final ConfigManager configManager;
    private final Path historyDirectory;
    private final Path historyFile;
    private final Path historyTempFile;

    private final Deque<MentionHistoryEntry> entries =
            new ArrayDeque<>();

    @Inject
    public MentionHistoryService(
            Configurations config,
            Gson gson,
            ConfigManager configManager)
    {
        this(
                config,
                gson,
                configManager,
                DEFAULT_HISTORY_FILE);
    }

    MentionHistoryService(
            Configurations config,
            Gson gson)
    {
        this(
                config,
                gson,
                null,
                DEFAULT_HISTORY_FILE);
    }

    MentionHistoryService(
            Configurations config,
            Gson gson,
            ConfigManager configManager,
            Path historyFile)
    {
        this.config = config;
        this.gson = gson;
        this.configManager = configManager;
        this.historyFile = historyFile;
        this.historyDirectory = historyFile.getParent();
        this.historyTempFile = historyFile.resolveSibling(
                historyFile.getFileName().toString() + ".tmp");

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
                || !taggedMessage.getLocalMentionMatch().isMatchesLocalPlayer())
        {
            return;
        }

        final MentionHistoryEntry entry =
                new MentionHistoryEntry(
                        taggedMessage.getId(),
                        taggedMessage.getCanonicalSender(),
                        taggedMessage.getOriginalMessage(),
                        taggedMessage.getType(),
                        taggedMessage.getLocalMentionMatch().getReason(),
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

    public synchronized void clear()
    {
        entries.clear();
        save();
    }

    public synchronized void enforceLimit()
    {
        final int before = entries.size();
        trim();

        if (entries.size() != before)
        {
            save();
        }
    }

    private void load()
    {
        entries.clear();

        if (Files.isRegularFile(historyFile))
        {
            if (loadFile())
            {
                removeLegacyConfig();
            }
            return;
        }

        migrateLegacyConfig();
    }

    private boolean loadFile()
    {
        try (BufferedReader reader =
                     Files.newBufferedReader(
                             historyFile,
                             StandardCharsets.UTF_8))
        {
            final PersistedStore store =
                    gson.fromJson(
                            reader,
                            PersistedStore.class);

            if (!isValidStore(store))
            {
                log.warn(
                        "[RuneTags][Mention-History] Ignoring Invalid Store '{}'",
                        historyFile);
                return false;
            }

            loadStore(store);
            return true;
        }
        catch (Exception exception)
        {
            log.warn(
                    "[RuneTags][Mention-History] Unable to Load from '{}'",
                    historyFile,
                    exception);
            return false;
        }
    }

    private void migrateLegacyConfig()
    {
        if (configManager == null)
        {
            return;
        }

        final String json =
                configManager.getConfiguration(
                        Constants.CONFIG_GROUP,
                        LEGACY_CONFIG_KEY);

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

            if (!isValidStore(store))
            {
                log.warn(
                        "[RuneTags][Mention-History] Legacy Config Store is Invalid; Leaving it Untouched");
                return;
            }

            entries.clear();
            loadStore(store);
            save();

            entries.clear();
            if (!loadFile())
            {
                log.warn(
                        "[RuneTags][Mention-History] Migration Verification Failed; Legacy Config Preserved");
                entries.clear();
                loadStore(store);
                return;
            }

            removeLegacyConfig();
            log.info(
                    "[RuneTags][Mention-History] Migrated Legacy ConfigManager History to '{}'",
                    historyFile);
        }
        catch (RuntimeException exception)
        {
            log.warn(
                    "[RuneTags][Mention-History] Unable to Migrate Legacy ConfigManager History | ERROR: {}",
                    exception.getMessage());
        }
    }

    private void loadStore(
            PersistedStore store)
    {
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
                entries.addLast(entry);
            }
        }

        trim();
    }

    private static boolean isValidStore(
            PersistedStore store)
    {
        return store != null
                && store.version == STORE_VERSION
                && store.entries != null;
    }

    private void save()
    {
        final PersistedStore store =
                new PersistedStore();

        store.version = STORE_VERSION;
        store.entries = new ArrayList<>();

        for (MentionHistoryEntry entry : entries)
        {
            store.entries.add(toPersisted(entry));
        }

        try
        {
            Files.createDirectories(historyDirectory);

            try (BufferedWriter writer =
                         Files.newBufferedWriter(
                                 historyTempFile,
                                 StandardCharsets.UTF_8))
            {
                gson.newBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(
                                store,
                                PersistedStore.class,
                                writer);
            }

            moveIntoPlace(
                    historyTempFile,
                    historyFile);
        }
        catch (IOException exception)
        {
            log.warn(
                    "[RuneTags][Mention-History] Unable to Save to '{}'",
                    historyFile,
                    exception);
        }
    }

    private void removeLegacyConfig()
    {
        if (configManager == null)
        {
            return;
        }

        final String json =
                configManager.getConfiguration(
                        Constants.CONFIG_GROUP,
                        LEGACY_CONFIG_KEY);

        if (json == null
                || json.trim().isEmpty())
        {
            return;
        }

        try
        {
            configManager.unsetConfiguration(
                    Constants.CONFIG_GROUP,
                    LEGACY_CONFIG_KEY);
        }
        catch (RuntimeException exception)
        {
            log.warn(
                    "[RuneTags][Mention-History] Unable to Remove Migrated Legacy Config",
                    exception);
        }
    }

    private static void moveIntoPlace(
            Path source,
            Path target)
            throws IOException
    {
        try
        {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException atomicMoveFailure)
        {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
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