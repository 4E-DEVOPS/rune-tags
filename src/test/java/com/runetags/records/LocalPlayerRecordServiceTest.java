package com.runetags.records;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.runetags.Constants;
import com.runetags.mention.NameNormalizer;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import net.runelite.client.config.ConfigManager;

public class LocalPlayerRecordServiceTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    private Gson gson;
    private NameNormalizer normalizer;
    private ConfigManager configManager;
    private LocalPlayerRecordService recordService;
    private Path recordFile;

    @Before
    public void setUp()
            throws Exception
    {
        gson =
                new Gson();

        normalizer =
                new NameNormalizer();

        configManager =
                Mockito.mock(
                        ConfigManager.class);

        recordFile =
                Files.createTempDirectory(
                                "runetags-records-test")
                        .resolve(
                                "players.json");

        recordService =
                new LocalPlayerRecordService(
                        gson,
                        normalizer,
                        configManager,
                        recordFile);

        records(
                recordService)
                .clear();
    }

    /*
     * TESTS
     */

    @Test
    public void nullBlankAndUnknownNamesReturnEmptyState()
    {
        Assert.assertNull(
                recordService.get(
                        null));

        Assert.assertNull(
                recordService.get(
                        ""));

        Assert.assertNull(
                recordService.get(
                        "   "));

        Assert.assertNull(
                recordService.get(
                        "definitelyunknown123"));

        Assert.assertFalse(
                recordService.isFavorite(
                        "definitelyunknown123"));

        Assert.assertEquals(
                Collections.emptyList(),
                recordService.getTags(
                        "definitelyunknown123"));

        Assert.assertNull(
                recordService.getNote(
                        "definitelyunknown123"));

        Assert.assertEquals(
                Collections.emptyList(),
                recordService.getPreviousRsns(
                        "definitelyunknown123"));
    }

    @Test
    public void setFavoriteCreatesNormalizedRecord()
    {
        recordService.setFavorite(
                "  Santa Clause  ",
                true);

        Assert.assertTrue(
                recordService.isFavorite(
                        "Santa Clause"));

        Assert.assertTrue(
                recordService.isFavorite(
                        "santa_clause"));

        final LocalPlayerRecord record =
                recordService.get(
                        "SANTA-CLAUSE");

        Assert.assertNotNull(
                record);

        Assert.assertEquals(
                "Santa Clause",
                record.getCurrentRsn());

        Assert.assertTrue(
                record.isFavorite());
    }

    @Test
    public void settingSameFavoriteStateDoesNotAdvanceRevision()
    {
        Assert.assertEquals(
                0L,
                recordService.getFavoriteRevision());

        recordService.setFavorite(
                "Santa",
                true);

        final long firstRevision =
                recordService.getFavoriteRevision();

        Assert.assertEquals(
                1L,
                firstRevision);

        recordService.setFavorite(
                "santa",
                true);

        Assert.assertEquals(
                firstRevision,
                recordService.getFavoriteRevision());
    }

    @Test
    public void toggleFavoriteCreatesAndRemovesOtherwiseEmptyRecord()
    {
        Assert.assertTrue(
                recordService.toggleFavorite(
                        "Party Hat"));

        Assert.assertTrue(
                recordService.isFavorite(
                        "party_hat"));

        Assert.assertNotNull(
                recordService.get(
                        "Party Hat"));

        Assert.assertFalse(
                recordService.toggleFavorite(
                        "PARTY-HAT"));

        Assert.assertFalse(
                recordService.isFavorite(
                        "Party Hat"));

        Assert.assertNull(
                recordService.get(
                        "Party Hat"));

        Assert.assertEquals(
                2L,
                recordService.getFavoriteRevision());
    }

    @Test
    public void noteSanitizationNormalizesLinesAndDropsEditorScaffolding()
    {
        recordService.setNote(
                "Santa",
                "  First line  \r\n"
                        + "\r\n"
                        + "\u2022\r\n"
                        + "   Second line   \r"
                        + " Third line ");

        Assert.assertEquals(
                "First line\nSecond line\nThird line",
                recordService.getNote(
                        "Santa"));
    }

    @Test
    public void noteIsTruncatedToMaximumLength()
    {
        final StringBuilder note =
                new StringBuilder();

        for (int i = 0;
             i < LocalPlayerRecordService.MAX_NOTE_LENGTH + 50;
             i++)
        {
            note.append(
                    'x');
        }

        recordService.setNote(
                "Zezima",
                note.toString());

        final String stored =
                recordService.getNote(
                        "Zezima");

        Assert.assertNotNull(
                stored);

        Assert.assertEquals(
                LocalPlayerRecordService.MAX_NOTE_LENGTH,
                stored.length());
    }

    @Test
    public void blankNoteRemovesOtherwiseEmptyRecord()
    {
        recordService.setNote(
                "FasT 07",
                "Temporary note");

        Assert.assertNotNull(
                recordService.get(
                        "FasT 07"));

        recordService.setNote(
                "fast_07",
                "   \n\u2022\n   ");

        Assert.assertNull(
                recordService.getNote(
                        "FasT 07"));

        Assert.assertNull(
                recordService.get(
                        "FasT 07"));
    }

    @Test
    public void tagsAreCanonicalizedDeduplicatedCappedAndOrdered()
    {
        recordService.setTags(
                "Santa Clause",
                Arrays.asList(
                        "alt",
                        " Avoid ",
                        "BIS",
                        "carrier",
                        "Chill",
                        "Drama",
                        "definitelyunknown123",
                        "ALT"));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Avoid",
                        "BiS",
                        "Carrier",
                        "Chill"),
                recordService.getTags(
                        "santa_clause"));
    }

    @Test
    public void tagsResultIsDefensiveAndUnmodifiable()
    {
        recordService.setTags(
                "Party Hat",
                Arrays.asList(
                        "Trusted",
                        "Raider"));

        final List<String> first =
                recordService.getTags(
                        "Party Hat");

        try
        {
            first.add(
                    "MVP");

            Assert.fail(
                    "Expected returned Tags to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }

        Assert.assertEquals(
                Arrays.asList(
                        "Trusted",
                        "Raider"),
                recordService.getTags(
                        "Party Hat"));
    }

    @Test
    public void clearingTagsRemovesOtherwiseEmptyRecord()
    {
        recordService.setTags(
                "Santa",
                Arrays.asList(
                        "MVP",
                        "Elite"));

        Assert.assertNotNull(
                recordService.get(
                        "Santa"));

        recordService.setTags(
                "Santa",
                Collections.emptyList());

        Assert.assertNull(
                recordService.get(
                        "Santa"));
    }

    @Test
    public void durableFieldsPreserveEachOther()
    {
        recordService.setFavorite(
                "Zezima",
                true);

        recordService.setNote(
                "Zezima",
                "Trusted player");

        recordService.setTags(
                "Zezima",
                Arrays.asList(
                        "Trusted",
                        "Elite"));

        recordService.setNote(
                "Zezima",
                "Updated note");

        final LocalPlayerRecord record =
                recordService.get(
                        "Zezima");

        Assert.assertNotNull(
                record);

        Assert.assertTrue(
                record.isFavorite());

        Assert.assertEquals(
                "Updated note",
                record.getNote());

        Assert.assertEquals(
                Arrays.asList(
                        "Trusted",
                        "Elite"),
                record.getTags());
    }

    @Test
    public void noteAndTagChangesDoNotAdvanceFavoriteRevision()
    {
        Assert.assertEquals(
                0L,
                recordService.getFavoriteRevision());

        recordService.setNote(
                "Santa",
                "Note");

        recordService.setTags(
                "Santa",
                Collections.singletonList(
                        "Trusted"));

        Assert.assertEquals(
                0L,
                recordService.getFavoriteRevision());

        recordService.setFavorite(
                "Santa",
                true);

        Assert.assertEquals(
                1L,
                recordService.getFavoriteRevision());
    }

    @Test
    public void observeNameChangeMigratesRecordAndHistory()
    {
        recordService.setFavorite(
                "Santa",
                true);

        recordService.setNote(
                "Santa",
                "Persistent note");

        recordService.setTags(
                "Santa",
                Collections.singletonList(
                        "Trusted"));

        final long revisionBefore =
                recordService.getFavoriteRevision();

        recordService.observeNameChange(
                "Santa Clause",
                "Santa");

        Assert.assertNull(
                recordService.get(
                        "Santa"));

        final LocalPlayerRecord migrated =
                recordService.get(
                        "Santa Clause");

        Assert.assertNotNull(
                migrated);

        Assert.assertEquals(
                "Santa Clause",
                migrated.getCurrentRsn());

        Assert.assertTrue(
                migrated.isFavorite());

        Assert.assertEquals(
                "Persistent note",
                migrated.getNote());

        Assert.assertEquals(
                Collections.singletonList(
                        "Trusted"),
                migrated.getTags());

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                migrated.getPreviousRsns());

        Assert.assertEquals(
                revisionBefore + 1L,
                recordService.getFavoriteRevision());
    }

    @Test
    public void observedRenamePreservesFavoriteNoteTagsAndExistingNameHistory()
            throws Exception
    {
        final String note =
                "• Reliable raider who always splits.\n"
                        + "• Experienced raider who communicates well, follows calls, "
                        + "and is happy to fill whatever role the team needs during runs.\n"
                        + "• Split Twisted Bow, and Ancestral Robe Top. 10/10 Player.";

        /*
         * Seed the same durable state we would have loaded from persistent storage:
         *
         * "whipaholic": {
         *     "currentRsn": "Whipaholic",
         *     "previousRsns": [
         *         "Whippr",
         *         "get the whip"
         *     ],
         *     "favorite": true,
         *     "note": "...",
         *     "tags": [
         *         "Splits",
         *         "Trusted",
         *         "Verified"
         *     ]
         * }
         */
        records(
                recordService)
                .put(
                        "whipaholic",
                        LocalPlayerRecord.builder()
                                .currentRsn(
                                        "Whipaholic")
                                .previousRsns(
                                        Arrays.asList(
                                                "Whippr",
                                                "get the whip"))
                                .favorite(
                                        true)
                                .note(
                                        note)
                                .tags(
                                        Arrays.asList(
                                                "Splits",
                                                "Trusted",
                                                "Verified"))
                                .build());

        Assert.assertTrue(
                recordService.isFavorite(
                        "Whipaholic"));

        Assert.assertEquals(
                note,
                recordService.getNote(
                        "Whipaholic"));

        Assert.assertEquals(
                Arrays.asList(
                        "Splits",
                        "Trusted",
                        "Verified"),
                recordService.getTags(
                        "Whipaholic"));

        Assert.assertEquals(
                Arrays.asList(
                        "Whippr",
                        "get the whip"),
                recordService.getPreviousRsns(
                        "Whipaholic"));

        /*
         * RuneLite now authoritatively observes:
         *
         * Current RSN:  whip
         * Previous RSN: Whipaholic
         */
        recordService.observeNameChange(
                "whip",
                "Whipaholic");

        /*
         * Metadata ownership must leave the previous current-RSN key.
         */
        Assert.assertNull(
                recordService.get(
                        "Whipaholic"));

        final LocalPlayerRecord renamed =
                recordService.get(
                        "whip");

        Assert.assertNotNull(
                renamed);

        Assert.assertEquals(
                "whip",
                renamed.getCurrentRsn());

        /*
         * Favorite ownership survives the rename.
         */
        Assert.assertTrue(
                renamed.isFavorite());

        Assert.assertTrue(
                recordService.isFavorite(
                        "whip"));

        /*
         * The complete user-authored Note survives unchanged.
         */
        Assert.assertEquals(
                note,
                renamed.getNote());

        Assert.assertEquals(
                note,
                recordService.getNote(
                        "whip"));

        /*
         * All Tags survive in their existing order.
         */
        Assert.assertEquals(
                Arrays.asList(
                        "Splits",
                        "Trusted",
                        "Verified"),
                renamed.getTags());

        Assert.assertEquals(
                Arrays.asList(
                        "Splits",
                        "Trusted",
                        "Verified"),
                recordService.getTags(
                        "whip"));

        /*
         * The immediately previous current RSN is inserted first, while the
         * older existing history remains intact behind it.
         */
        Assert.assertEquals(
                Arrays.asList(
                        "Whipaholic",
                        "Whippr",
                        "get the whip"),
                renamed.getPreviousRsns());

        Assert.assertEquals(
                Arrays.asList(
                        "Whipaholic",
                        "Whippr",
                        "get the whip"),
                recordService.getPreviousRsns(
                        "whip"));

        /*
         * Normalized CURRENT-name lookup must now resolve the migrated record.
         * This is the key behavior used when subsequent RuneTags references use
         * the new RSN.
         */
        Assert.assertSame(
                renamed,
                recordService.get(
                        "WHIP"));
    }

    @Test
    public void observedRenameMergesExistingCurrentAndPreviousMetadata()
    {
        recordService.setNote(
                "Santa Clause",
                "Current note");

        recordService.setTags(
                "Santa Clause",
                Arrays.asList(
                        "BiS",
                        "Carrier"));

        recordService.setFavorite(
                "Santa",
                true);

        recordService.setNote(
                "Santa",
                "Previous note");

        recordService.setTags(
                "Santa",
                Arrays.asList(
                        "Alt",
                        "BiS"));

        recordService.observeNameChange(
                "Santa Clause",
                "Santa");

        Assert.assertNull(
                recordService.get(
                        "Santa"));

        final LocalPlayerRecord merged =
                recordService.get(
                        "Santa Clause");

        Assert.assertNotNull(
                merged);

        Assert.assertTrue(
                merged.isFavorite());

        /*
         * Current-name metadata has note priority.
         */
        Assert.assertEquals(
                "Current note",
                merged.getNote());

        /*
         * Current record Tags are retained first, then non-duplicate Tags
         * from the previous-name record.
         */
        Assert.assertEquals(
                Arrays.asList(
                        "BiS",
                        "Carrier",
                        "Alt"),
                merged.getTags());

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                merged.getPreviousRsns());
    }

    @Test
    public void repeatedObservedRenameDoesNotConsumeReusedOldNameRecord()
    {
        recordService.setFavorite(
                "Santa",
                true);

        recordService.observeNameChange(
                "Santa Clause",
                "Santa");

        /*
         * Simulate a different account later claiming the released old RSN.
         */
        recordService.setNote(
                "Santa",
                "Different account");

        recordService.observeNameChange(
                "Santa Clause",
                "Santa");

        final LocalPlayerRecord current =
                recordService.get(
                        "Santa Clause");

        final LocalPlayerRecord reusedOldName =
                recordService.get(
                        "Santa");

        Assert.assertNotNull(
                current);

        Assert.assertTrue(
                current.isFavorite());

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                current.getPreviousRsns());

        Assert.assertNotNull(
                reusedOldName);

        Assert.assertEquals(
                "Different account",
                reusedOldName.getNote());

        Assert.assertFalse(
                reusedOldName.isFavorite());
    }

    @Test
    public void observeNameChangesMigratesMultipleRecords()
    {
        recordService.setFavorite(
                "Santa",
                true);

        recordService.setNote(
                "Zezima",
                "Persistent note");

        final Map<String, String> changes =
                new LinkedHashMap<>();

        changes.put(
                "Santa Clause",
                "Santa");

        changes.put(
                "Party Hat",
                "Zezima");

        recordService.observeNameChanges(
                changes);

        Assert.assertNull(
                recordService.get(
                        "Santa"));

        Assert.assertNull(
                recordService.get(
                        "Zezima"));

        Assert.assertTrue(
                recordService.isFavorite(
                        "Santa Clause"));

        Assert.assertEquals(
                "Persistent note",
                recordService.getNote(
                        "Party Hat"));

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                recordService.getPreviousRsns(
                        "Santa Clause"));

        Assert.assertEquals(
                Collections.singletonList(
                        "Zezima"),
                recordService.getPreviousRsns(
                        "Party Hat"));
    }

    @Test
    public void invalidObservedNameChangesDoNothing()
    {
        recordService.setFavorite(
                "Santa",
                true);

        final LocalPlayerRecord before =
                recordService.get(
                        "Santa");

        final long revisionBefore =
                recordService.getFavoriteRevision();

        recordService.observeNameChange(
                null,
                "Santa");

        recordService.observeNameChange(
                "Santa Clause",
                null);

        recordService.observeNameChange(
                "Santa",
                "santa");

        recordService.observeNameChanges(
                null);

        recordService.observeNameChanges(
                Collections.emptyMap());

        Assert.assertSame(
                before,
                recordService.get(
                        "Santa"));

        Assert.assertEquals(
                revisionBefore,
                recordService.getFavoriteRevision());
    }

    @Test
    public void previousRsnsAreUnmodifiableAndAreNotLookupAliases()
    {
        recordService.setFavorite(
                "Santa",
                true);

        recordService.observeNameChange(
                "Santa Clause",
                "Santa");

        final List<String> previous =
                recordService.getPreviousRsns(
                        "Santa Clause");

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                previous);

        try
        {
            previous.add(
                    "FasT 07");

            Assert.fail(
                    "Expected Previous RSNs to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }

        /*
         * Historical names intentionally do not resolve as aliases.
         */
        Assert.assertNull(
                recordService.get(
                        "Santa"));

        Assert.assertNotNull(
                recordService.get(
                        "Santa Clause"));
    }

    @Test
    public void mutationPersistsVersionedRecordsToFile()
            throws Exception
    {
        recordService.setFavorite(
                "Santa",
                true);

        waitForFile(recordFile);

        final JsonObject root =
                gson.fromJson(
                        new String(
                                Files.readAllBytes(recordFile),
                                StandardCharsets.UTF_8),
                        JsonObject.class);

        Assert.assertEquals(
                1,
                root.get("version").getAsInt());

        Assert.assertEquals(
                1,
                root.getAsJsonObject("players").size());
    }

    @Test
    public void legacyConfigMigratesToFileAndIsRemoved()
            throws Exception
    {
        final LocalPlayerRecord record =
                LocalPlayerRecord.builder()
                        .currentRsn("Santa")
                        .favorite(true)
                        .note("Persistent note")
                        .tags(Arrays.asList("Alt", "BiS"))
                        .build();

        final JsonObject players = new JsonObject();
        players.add(
                normalizer.comparisonKey("Santa"),
                gson.toJsonTree(record));

        final JsonObject store = new JsonObject();
        store.addProperty("version", 1);
        store.add("players", players);

        Mockito.when(
                        configManager.getConfiguration(
                                Constants.CONFIG_GROUP,
                                "localPlayerRecordsV1"))
                .thenReturn(gson.toJson(store));

        final LocalPlayerRecordService migrated =
                new LocalPlayerRecordService(
                        gson,
                        normalizer,
                        configManager,
                        recordFile);

        Assert.assertTrue(
                migrated.isFavorite("Santa"));
        Assert.assertEquals(
                "Persistent note",
                migrated.getNote("Santa"));
        Assert.assertEquals(
                Arrays.asList("Alt", "BiS"),
                migrated.getTags("Santa"));
        Assert.assertTrue(
                Files.isRegularFile(recordFile));

        Mockito.verify(configManager)
                .unsetConfiguration(
                        Constants.CONFIG_GROUP,
                        "localPlayerRecordsV1");

        migrated.shutdown();
    }

    @Test
    public void malformedLegacyConfigIsIgnoredAndPreserved()
    {
        Mockito.when(
                        configManager.getConfiguration(
                                Constants.CONFIG_GROUP,
                                "localPlayerRecordsV1"))
                .thenReturn("{not-valid-json");

        final LocalPlayerRecordService malformedService =
                new LocalPlayerRecordService(
                        gson,
                        normalizer,
                        configManager,
                        recordFile);

        Assert.assertNull(
                malformedService.get("Santa"));
        Assert.assertFalse(
                Files.exists(recordFile));

        Mockito.verify(configManager, Mockito.never())
                .unsetConfiguration(
                        Constants.CONFIG_GROUP,
                        "localPlayerRecordsV1");

        malformedService.shutdown();
    }

    @Test
    public void shutdownPreventsFurtherPersistence()
            throws Exception
    {
        recordService.shutdown();

        recordService.setFavorite(
                "Santa",
                true);

        Assert.assertTrue(
                recordService.isFavorite("Santa"));
        Assert.assertFalse(
                Files.exists(recordFile));
    }

    /*
     * HELPERS
     */

    @SuppressWarnings("unchecked")
    private static Map<String, LocalPlayerRecord> records(
            LocalPlayerRecordService service)
            throws Exception
    {
        final Field field =
                LocalPlayerRecordService.class.getDeclaredField(
                        "records");

        field.setAccessible(
                true);

        return (Map<String, LocalPlayerRecord>)
                field.get(
                        service);
    }

    private static void waitForFile(
            Path file)
            throws Exception
    {
        for (int attempt = 0; attempt < 100; ++attempt)
        {
            if (Files.isRegularFile(file)
                    && Files.size(file) > 0)
            {
                return;
            }

            Thread.sleep(10L);
        }

        Assert.fail("Timed out waiting for persisted file: " + file);
    }

    /*
     * PERFORMANCE
     */

    @Test
    public void performanceNormalizedRecordReads()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        recordService.setFavorite(
                "Santa Clause",
                true);

        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            if (recordService.get(
                    "santa_clause") != null)
            {
                ++checksum;
            }

            if (recordService.isFavorite(
                    "SANTA-CLAUSE"))
            {
                ++checksum;
            }
        }

        long started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            if (recordService.get(
                    "santa_clause") != null)
            {
                ++checksum;
            }
        }

        final long getElapsed =
                System.nanoTime()
                        - started;

        started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            if (recordService.isFavorite(
                    "SANTA-CLAUSE"))
            {
                ++checksum;
            }
        }

        final long favoriteElapsed =
                System.nanoTime()
                        - started;

        Assert.assertTrue(
                checksum > 0L);

        final double getTotalMs =
                getElapsed
                        / 1_000_000.0;

        final double favoriteTotalMs =
                favoriteElapsed
                        / 1_000_000.0;

        System.out.printf(
                "[RuneTags][LocalPlayerRecordServiceTest] Performance= "
                        + "NormalizedGet: %.3fms (%.6fms) | "
                        + "FavoriteRead: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                getTotalMs,
                getTotalMs
                        / PERFORMANCE_ITERATIONS,
                favoriteTotalMs,
                favoriteTotalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }
}