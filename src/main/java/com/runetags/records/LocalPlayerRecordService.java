package com.runetags.records;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.runetags.Constants;
import com.runetags.mention.NameNormalizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

/**
 * Persistent RuneTags metadata keyed by the player's current normalized RSN.
 *
 * Previous RSNs are stored as history only and are never used as lookup aliases.
 * Authoritative rename events migrate records to the new current RSN.
 */
@Slf4j
public class LocalPlayerRecordService
{
    public static final int MAX_NOTE_LENGTH = 256;

    private static final int STORE_VERSION = 1;

    private static final String LEGACY_CONFIG_KEY =
            "localPlayerRecordsV1";

    private static final Path DEFAULT_RECORD_DIRECTORY =
            RuneLite.RUNELITE_DIR
                    .toPath()
                    .resolve("RuneTags")
                    .resolve("records");

    private static final Path DEFAULT_RECORD_FILE =
            DEFAULT_RECORD_DIRECTORY.resolve(
                    "players.json");

    private static final Path DEFAULT_RECORD_TEMP_FILE =
            DEFAULT_RECORD_DIRECTORY.resolve(
                    "players.json.tmp");

    private final Gson gson;
    private final NameNormalizer normalizer;
    private final ConfigManager configManager;
    private final Path recordDirectory;
    private final Path recordFile;
    private final Path recordTempFile;

    private final Map<String, LocalPlayerRecord> records =
            new LinkedHashMap<>();

    private final ExecutorService ioExecutor =
            Executors.newSingleThreadExecutor(
                    runnable ->
                    {
                        final Thread thread =
                                new Thread(
                                        runnable,
                                        "RuneTags-LocalRecords");

                        thread.setDaemon(true);
                        return thread;
                    });

    private boolean closed;

    /*
     * Incremented when Favorite presentation changes so consumers can refresh
     * without polling record state every tick.
     */
    private long favoriteRevision;

    public LocalPlayerRecordService(
            Gson gson,
            NameNormalizer normalizer,
            ConfigManager configManager)
    {
        this(
                gson,
                normalizer,
                configManager,
                DEFAULT_RECORD_FILE);
    }

    LocalPlayerRecordService(
            Gson gson,
            NameNormalizer normalizer)
    {
        this(
                gson,
                normalizer,
                null,
                DEFAULT_RECORD_FILE);
    }

    LocalPlayerRecordService(
            Gson gson,
            NameNormalizer normalizer,
            ConfigManager configManager,
            Path recordFile)
    {
        this.gson = gson;
        this.normalizer = normalizer;
        this.configManager = configManager;
        this.recordFile = recordFile;
        this.recordDirectory = recordFile.getParent();
        this.recordTempFile = recordFile.resolveSibling(
                recordFile.getFileName().toString() + ".tmp");

        load();
    }

    public synchronized LocalPlayerRecord get(
            String playerName)
    {
        final String key =
                key(playerName);

        if (key.isEmpty())
        {
            return null;
        }

        return records.get(key);
    }

    public synchronized boolean isFavorite(
            String playerName)
    {
        final LocalPlayerRecord record =
                get(playerName);

        return record != null
                && record.isFavorite();
    }

    public synchronized long getFavoriteRevision()
    {
        return favoriteRevision;
    }

    public synchronized List<String> getTags(
            String playerName)
    {
        final LocalPlayerRecord record =
                get(playerName);

        if (record == null
                || record.getTags() == null
                || record.getTags().isEmpty())
        {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
                new ArrayList<>(record.getTags()));
    }

    /** Replace tags for the supplied current RSN. */
    public synchronized void setTags(
            String playerName,
            List<String> tags)
    {
        final String currentRsn = canonical(playerName);
        final String currentKey = key(currentRsn);

        if (currentKey.isEmpty())
        {
            return;
        }

        final List<String> cleanTags = sanitizeTags(tags);
        final LocalPlayerRecord existing = records.get(currentKey);
        final List<String> existingTags =
                existing != null
                        ? sanitizeTags(existing.getTags())
                        : Collections.emptyList();

        if (existingTags.equals(cleanTags))
        {
            return;
        }

        final LocalPlayerRecord updated =
                existing != null
                        ? existing.toBuilder()
                        .currentRsn(currentRsn)
                        .tags(cleanTags)
                        .build()
                        : LocalPlayerRecord.builder()
                        .currentRsn(currentRsn)
                        .tags(cleanTags)
                        .build();

        if (shouldRetain(updated))
        {
            records.put(currentKey, sanitize(updated));
        }
        else
        {
            records.remove(currentKey);
        }

        saveAsync();

    }

    public synchronized String getNote(
            String playerName)
    {
        final LocalPlayerRecord record =
                get(playerName);

        return record != null
                ? sanitizeNote(
                record.getNote())
                : null;
    }

    /**
     * Replace the local Note for the supplied CURRENT RSN.
     *
     * A blank note removes the note. The record itself is retained only when
     * another durable field (Favorite, Tags, or Previous RSNs) still needs it.
     */
    public synchronized void setNote(
            String playerName,
            String note)
    {
        final String currentRsn =
                canonical(playerName);

        final String currentKey =
                key(currentRsn);

        if (currentKey.isEmpty())
        {
            return;
        }

        final String cleanNote =
                sanitizeNote(note);

        final LocalPlayerRecord existing =
                records.get(currentKey);

        final String existingNote =
                existing != null
                        ? sanitizeNote(
                        existing.getNote())
                        : null;

        if (Objects.equals(
                existingNote,
                cleanNote))
        {
            return;
        }

        final LocalPlayerRecord updated =
                existing != null
                        ? existing.toBuilder()
                        .currentRsn(currentRsn)
                        .note(cleanNote)
                        .build()
                        : LocalPlayerRecord.builder()
                        .currentRsn(currentRsn)
                        .note(cleanNote)
                        .build();

        if (shouldRetain(updated))
        {
            records.put(
                    currentKey,
                    sanitize(updated));
        }
        else
        {
            records.remove(
                    currentKey);
        }

        saveAsync();

    }

    /**
     * Toggle Favorite for the supplied CURRENT RSN and return the new state.
     */
    public synchronized boolean toggleFavorite(
            String playerName)
    {
        final String currentRsn =
                canonical(playerName);

        final String currentKey =
                key(currentRsn);

        if (currentKey.isEmpty())
        {
            return false;
        }

        final LocalPlayerRecord existing =
                records.get(currentKey);

        final boolean favorite =
                existing == null
                        || !existing.isFavorite();

        final LocalPlayerRecord updated =
                existing != null
                        ? existing.toBuilder()
                        .currentRsn(currentRsn)
                        .favorite(favorite)
                        .build()
                        : LocalPlayerRecord.builder()
                        .currentRsn(currentRsn)
                        .favorite(true)
                        .build();

        if (shouldRetain(updated))
        {
            records.put(
                    currentKey,
                    sanitize(updated));
        }
        else
        {
            records.remove(
                    currentKey);
        }

        ++favoriteRevision;

        saveAsync();


        return favorite;
    }

    public synchronized void setFavorite(
            String playerName,
            boolean favorite)
    {
        final String currentRsn =
                canonical(playerName);

        final String currentKey =
                key(currentRsn);

        if (currentKey.isEmpty())
        {
            return;
        }

        final LocalPlayerRecord existing =
                records.get(currentKey);

        if (existing != null
                && existing.isFavorite() == favorite)
        {
            return;
        }

        final LocalPlayerRecord updated =
                existing != null
                        ? existing.toBuilder()
                        .currentRsn(currentRsn)
                        .favorite(favorite)
                        .build()
                        : LocalPlayerRecord.builder()
                        .currentRsn(currentRsn)
                        .favorite(favorite)
                        .build();

        if (shouldRetain(updated))
        {
            records.put(
                    currentKey,
                    sanitize(updated));
        }
        else
        {
            records.remove(
                    currentKey);
        }

        ++favoriteRevision;

        saveAsync();
    }

    public synchronized List<String> getPreviousRsns(
            String playerName)
    {
        final LocalPlayerRecord record =
                get(playerName);

        if (record == null
                || record.getPreviousRsns() == null
                || record.getPreviousRsns().isEmpty())
        {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
                new ArrayList<>(
                        record.getPreviousRsns()));
    }

    /**
     * Records an authoritative observed rename.
     *
     * The previous RSN is preserved as history while ownership moves to the new
     * current RSN. Historical names are not used for lookup.
     */
    public synchronized void observeNameChange(
            String currentName,
            String previousName)
    {
        if (applyObservedNameChange(
                currentName,
                previousName))
        {
            saveAsync();
        }
    }

    /**
     * Applies known current-to-previous RSN relationships and saves once when
     * changes are detected.
     */
    public synchronized void observeNameChanges(
            Map<String, String> currentToPrevious)
    {
        if (currentToPrevious == null
                || currentToPrevious.isEmpty())
        {
            return;
        }

        boolean changed = false;

        for (Map.Entry<String, String> entry
                : currentToPrevious.entrySet())
        {
            changed |=
                    applyObservedNameChange(
                            entry.getKey(),
                            entry.getValue());
        }

        if (changed)
        {
            saveAsync();
        }
    }

    private boolean applyObservedNameChange(
            String currentName,
            String previousName)
    {
        final String currentRsn =
                canonical(currentName);

        final String previousRsn =
                canonical(previousName);

        final String currentKey =
                key(currentRsn);

        final String previousKey =
                key(previousRsn);

        if (currentKey.isEmpty()
                || previousKey.isEmpty()
                || currentKey.equals(previousKey))
        {
            return false;
        }

        final LocalPlayerRecord currentRecord =
                records.get(currentKey);

        /*
         * Avoid repeating a migration. An old RSN may belong to a different
         * account after release, so existing records under that name are not
         * overwritten blindly.
         */
        if (hasPreviousRsn(
                currentRecord,
                previousKey))
        {
            return false;
        }

        final LocalPlayerRecord previousRecord =
                records.get(previousKey);

        final LocalPlayerRecord merged =
                mergeRename(
                        currentRsn,
                        previousRsn,
                        currentRecord,
                        previousRecord);

        final boolean changed =
                previousRecord != null
                        || currentRecord == null
                        || !merged.equals(currentRecord);

        if (!changed)
        {
            return false;
        }

        /*
         * Move previous-name ownership to the new RSN while preserving metadata
         * already stored under either identity.
         */
        records.remove(previousKey);
        records.put(
                currentKey,
                merged);

        ++favoriteRevision;


        return true;
    }

    /**
     * Stop background persistence without blocking plugin shutdown.
     *
     * No persistent records are cleared; players.json remains the durable store.
     */
    public synchronized void shutdown()
    {
        closed = true;
        ioExecutor.shutdown();
    }

    private LocalPlayerRecord mergeRename(
            String currentRsn,
            String previousRsn,
            LocalPlayerRecord currentRecord,
            LocalPlayerRecord previousRecord)
    {
        final boolean favorite =
                (currentRecord != null
                        && currentRecord.isFavorite())
                        || (previousRecord != null
                        && previousRecord.isFavorite());

        final String note =
                firstNonBlank(
                        currentRecord != null
                                ? currentRecord.getNote()
                                : null,
                        previousRecord != null
                                ? previousRecord.getNote()
                                : null);

        final List<String> tags =
                mergeTags(
                        currentRecord,
                        previousRecord);

        final List<String> previousRsns =
                mergePreviousRsns(
                        currentRsn,
                        previousRsn,
                        currentRecord,
                        previousRecord);

        return sanitize(
                LocalPlayerRecord.builder()
                        .currentRsn(currentRsn)
                        .previousRsns(previousRsns)
                        .favorite(favorite)
                        .note(note)
                        .tags(tags)
                        .build());
    }

    private List<String> mergePreviousRsns(
            String currentRsn,
            String previousRsn,
            LocalPlayerRecord currentRecord,
            LocalPlayerRecord previousRecord)
    {
        final LinkedHashMap<String, String> merged =
                new LinkedHashMap<>();

        /*
         * Keep previous names ordered for display in the history UI.
         */
        addPreviousRsn(
                merged,
                previousRsn,
                currentRsn);

        if (previousRecord != null)
        {
            addPreviousRsn(
                    merged,
                    previousRecord.getCurrentRsn(),
                    currentRsn);
        }

        addPreviousRsns(
                merged,
                previousRecord != null
                        ? previousRecord.getPreviousRsns()
                        : null,
                currentRsn);

        if (currentRecord != null)
        {
            addPreviousRsn(
                    merged,
                    currentRecord.getCurrentRsn(),
                    currentRsn);
        }

        addPreviousRsns(
                merged,
                currentRecord != null
                        ? currentRecord.getPreviousRsns()
                        : null,
                currentRsn);

        return Collections.unmodifiableList(
                new ArrayList<>(
                        merged.values()));
    }

    private void addPreviousRsns(
            Map<String, String> output,
            List<String> names,
            String currentRsn)
    {
        if (names == null)
        {
            return;
        }

        for (String name : names)
        {
            addPreviousRsn(
                    output,
                    name,
                    currentRsn);
        }
    }

    private void addPreviousRsn(
            Map<String, String> output,
            String previousRsn,
            String currentRsn)
    {
        final String canonicalPrevious =
                canonical(previousRsn);

        final String previousKey =
                key(canonicalPrevious);

        if (previousKey.isEmpty()
                || previousKey.equals(
                key(currentRsn)))
        {
            return;
        }

        output.putIfAbsent(
                previousKey,
                canonicalPrevious);
    }

    private List<String> mergeTags(
            LocalPlayerRecord first,
            LocalPlayerRecord second)
    {
        final List<String> merged = new ArrayList<>();

        addTags(merged, first != null ? first.getTags() : null);
        addTags(merged, second != null ? second.getTags() : null);

        return sanitizeTags(merged);
    }

    private static void addTags(
            List<String> output,
            List<String> tags)
    {
        if (tags == null)
        {
            return;
        }

        for (String value : tags)
        {
            final String tag = PlayerTagCatalog.canonical(value);

            if (tag != null
                    && !output.contains(tag)
                    && output.size() < PlayerTagCatalog.MAX_TAGS_PER_PLAYER)
            {
                output.add(tag);
            }
        }
    }

    private static List<String> sanitizeTags(
            List<String> tags)
    {
        final List<String> clean = new ArrayList<>();
        addTags(clean, tags);
        return Collections.unmodifiableList(clean);
    }

    private boolean hasPreviousRsn(
            LocalPlayerRecord record,
            String previousKey)
    {
        if (record == null
                || previousKey == null
                || previousKey.isEmpty()
                || record.getPreviousRsns() == null)
        {
            return false;
        }

        for (String previousRsn
                : record.getPreviousRsns())
        {
            if (previousKey.equals(
                    key(previousRsn)))
            {
                return true;
            }
        }

        return false;
    }

    private boolean shouldRetain(
            LocalPlayerRecord record)
    {
        return record != null
                && (record.isFavorite()
                || !isBlank(record.getNote())
                || (record.getTags() != null
                && !record.getTags().isEmpty())
                || (record.getPreviousRsns() != null
                && !record.getPreviousRsns().isEmpty()));
    }

    private LocalPlayerRecord sanitize(
            LocalPlayerRecord record)
    {
        if (record == null)
        {
            return null;
        }

        final String currentRsn =
                canonical(record.getCurrentRsn());

        final LinkedHashMap<String, String> previous =
                new LinkedHashMap<>();

        addPreviousRsns(
                previous,
                record.getPreviousRsns(),
                currentRsn);

        final List<String> tags =
                mergeTags(
                        record,
                        null);

        return LocalPlayerRecord.builder()
                .currentRsn(currentRsn)
                .previousRsns(
                        Collections.unmodifiableList(
                                new ArrayList<>(
                                        previous.values())))
                .favorite(record.isFavorite())
                .note(
                        sanitizeNote(
                                record.getNote()))
                .tags(tags)
                .build();
    }

    private void load()
    {
        synchronized (this)
        {
            records.clear();

            if (Files.isRegularFile(recordFile))
            {
                if (loadFile())
                {
                    removeLegacyConfig();
                }
                return;
            }

            migrateLegacyConfig();
        }
    }

    private boolean loadFile()
    {
        try (BufferedReader reader =
                     Files.newBufferedReader(
                             recordFile,
                             StandardCharsets.UTF_8))
        {
            final PersistedStore store =
                    gson.fromJson(
                            reader,
                            PersistedStore.class);

            if (!isValidStore(store))
            {
                log.warn(
                        "[RuneTags][Records] Ignoring Invalid Store '{}'",
                        recordFile);
                return false;
            }

            loadStore(store);
            return true;
        }
        catch (Exception exception)
        {
            log.warn(
                    "[RuneTags][Records] Unable to Load Local Player Records from '{}'",
                    recordFile,
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
                        "[RuneTags][Records] Legacy Config Store is Invalid; Leaving it Untouched");
                return;
            }

            records.clear();
            loadStore(store);

            final PersistedStore snapshot =
                    snapshot();

            writeSnapshot(snapshot);

            records.clear();
            if (!loadFile())
            {
                log.warn(
                        "[RuneTags][Records] Migration Verification Failed; Legacy Config Preserved");
                records.clear();
                loadStore(store);
                return;
            }

            removeLegacyConfig();
            log.info(
                    "[RuneTags][Records] Migrated Legacy ConfigManager Records to '{}'",
                    recordFile);
        }
        catch (RuntimeException exception)
        {
            log.warn(
                    "[RuneTags][Records] Unable to Migrate Legacy ConfigManager Records | ERROR: {}",
                    exception.getMessage());
        }
    }

    private void loadStore(
            PersistedStore store)
    {
        if (store == null
                || store.players == null)
        {
            return;
        }

        for (Map.Entry<String, LocalPlayerRecord> entry
                : store.players.entrySet())
        {
            final LocalPlayerRecord sanitized =
                    sanitize(entry.getValue());

            if (sanitized == null
                    || isBlank(sanitized.getCurrentRsn()))
            {
                continue;
            }

            final String currentKey =
                    key(sanitized.getCurrentRsn());

            if (currentKey.isEmpty()
                    || !shouldRetain(sanitized))
            {
                continue;
            }

            records.put(
                    currentKey,
                    sanitized);
        }
    }

    private static boolean isValidStore(
            PersistedStore store)
    {
        return store != null
                && store.version == STORE_VERSION
                && store.players != null;
    }

    private PersistedStore snapshot()
    {
        final PersistedStore snapshot =
                new PersistedStore();

        snapshot.version = STORE_VERSION;
        snapshot.players =
                new LinkedHashMap<>(records);

        return snapshot;
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
                    "[RuneTags][Records] Unable to Remove Migrated Legacy Config",
                    exception);
        }
    }

    private synchronized void saveAsync()
    {
        if (closed)
        {
            return;
        }

        final PersistedStore snapshot =
                snapshot();

        ioExecutor.execute(() ->
                writeSnapshot(
                        snapshot));
    }

    private void writeSnapshot(
            PersistedStore snapshot)
    {
        if (snapshot == null)
        {
            return;
        }

        try
        {
            Files.createDirectories(
                    recordDirectory);

            try (BufferedWriter writer =
                         Files.newBufferedWriter(
                                 recordTempFile,
                                 StandardCharsets.UTF_8);
                 JsonWriter jsonWriter =
                         new JsonWriter(
                                 writer))
            {
                jsonWriter.setIndent(
                        "    ");

                gson.toJson(
                        snapshot,
                        PersistedStore.class,
                        jsonWriter);
            }

            try
            {
                Files.move(
                        recordTempFile,
                        recordFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException atomicMoveFailure)
            {
                Files.move(
                        recordTempFile,
                        recordFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException exception)
        {
            log.warn("[RuneTags][Records] Unable to Save Local Player Records to '{}'", recordFile, exception);
        }
    }

    private String canonical(
            String value)
    {
        return normalizer != null
                ? normalizer.canonicalize(value)
                : value == null
                ? ""
                : value.trim();
    }

    private String key(
            String value)
    {
        return normalizer != null
                ? normalizer.comparisonKey(value)
                : canonical(value)
                .toLowerCase(
                        java.util.Locale.ROOT);
    }

    private static String sanitizeNote(
            String value)
    {
        if (value == null)
        {
            return null;
        }

        final String normalized =
                value.replace("\r\n", "\n")
                        .replace('\r', '\n');

        final StringBuilder clean =
                new StringBuilder();

        for (String line
                : normalized.split("\\n", -1))
        {
            final String trimmed =
                    line.trim();

            /*
             * Empty trailing bullet rows are editor scaffolding, not durable
             * Note content. Ordinary pre-bullet Notes remain valid.
             */
            if (trimmed.isEmpty()
                    || "\u2022".equals(trimmed))
            {
                continue;
            }

            if (clean.length() > 0)
            {
                clean.append('\n');
            }

            clean.append(trimmed);
        }

        if (clean.length() == 0)
        {
            return null;
        }

        if (clean.length() > MAX_NOTE_LENGTH)
        {
            clean.setLength(
                    MAX_NOTE_LENGTH);

            while (clean.length() > 0
                    && Character.isWhitespace(
                    clean.charAt(
                            clean.length() - 1)))
            {
                clean.setLength(
                        clean.length() - 1);
            }
        }

        return clean.length() == 0
                ? null
                : clean.toString();
    }

    private static String firstNonBlank(
            String first,
            String second)
    {
        if (!isBlank(first))
        {
            return first;
        }

        return !isBlank(second)
                ? second
                : null;
    }

    private static boolean isBlank(
            String value)
    {
        return value == null
                || value.trim().isEmpty();
    }

    private static final class PersistedStore
    {
        private int version =
                STORE_VERSION;

        private Map<String, LocalPlayerRecord> players =
                new LinkedHashMap<>();
    }
}