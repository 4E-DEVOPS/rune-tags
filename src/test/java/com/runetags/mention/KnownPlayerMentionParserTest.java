package com.runetags.mention;

import com.runetags.player.PlayerIdentity;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;
import com.runetags.player.PlayerDirectory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class KnownPlayerMentionParserTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private NameNormalizer normalizer;
    private PlayerDirectory playerDirectory;
    private KnownPlayerMentionParser parser;

    @Before
    public void setUp()
    {
        normalizer =
                new NameNormalizer();

        playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        parser =
                new KnownPlayerMentionParser(
                        playerDirectory,
                        normalizer);
    }

    /*
     * TESTS
     */

    @Test
    public void nullAndEmptyMessagesReturnNoMentions()
    {
        Mockito.when(
                        playerDirectory.allSortedLongestNameFirst())
                .thenReturn(
                        Collections.emptyList());

        Assert.assertTrue(
                parser.parse(
                                null,
                                Collections.emptyList())
                        .isEmpty());

        Assert.assertTrue(
                parser.parse(
                                "",
                                Collections.emptyList())
                        .isEmpty());
    }

    @Test
    public void canonicalSpaceVariantMatchesCaseInsensitively()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        directoryContains(
                santaClause);

        final List<PlayerReference> references =
                parser.parse(
                        "hello SANTA CLAUSE!",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "SANTA CLAUSE",
                "Santa Clause",
                6,
                18);
    }

    @Test
    public void underscoreVariantMatchesKnownPlayer()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        directoryContains(
                santaClause);

        final List<PlayerReference> references =
                parser.parse(
                        "hello santa_clause!",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "santa_clause",
                "Santa Clause",
                6,
                18);
    }

    @Test
    public void hyphenVariantMatchesKnownPlayer()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        directoryContains(
                santaClause);

        final List<PlayerReference> references =
                parser.parse(
                        "hello santa-clause!",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "santa-clause",
                "Santa Clause",
                6,
                18);
    }

    @Test
    public void normalizedFallbackMatchesSeparatedSourceText()
    {
        final PlayerIdentity fast07 =
                identity(
                        "FasT 07");

        directoryContains(
                fast07);

        final List<PlayerReference> references =
                parser.parse(
                        "FasT07 is here",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "FasT07",
                "FasT 07",
                0,
                6);
    }

    @Test
    public void missingNormalizedIdentityFallsBackToCanonicalName()
    {
        final PlayerIdentity fast07 =
                PlayerIdentity.builder()
                        .canonicalName(
                                "FasT 07")
                        .normalizedName(
                                null)
                        .build();

        directoryContains(
                fast07);

        final List<PlayerReference> references =
                parser.parse(
                        "FasT07 is here",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "FasT07",
                "FasT 07",
                0,
                6);
    }

    @Test
    public void explicitTagIsNotAlsoReturnedAsKnownMention()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        directoryContains(
                santaClause);

        final List<PlayerReference> references =
                parser.parse(
                        "@Santa_Clause hello Santa Clause",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "Santa Clause",
                "Santa Clause",
                20,
                32);
    }

    @Test
    public void normalizedFallbackDoesNotStartInsideExplicitTag()
    {
        final PlayerIdentity fast07 =
                identity(
                        "FasT 07");

        directoryContains(
                fast07);

        final List<PlayerReference> references =
                parser.parse(
                        "@FasT07",
                        Collections.emptyList());

        Assert.assertTrue(
                references.isEmpty());
    }

    @Test
    public void partialNameBoundariesDoNotMatch()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        directoryContains(
                santaClause);

        final List<PlayerReference> references =
                parser.parse(
                        "xSanta Clause and Santa Clause2",
                        Collections.emptyList());

        Assert.assertTrue(
                references.isEmpty());
    }

    @Test
    public void repeatedKnownPlayerMentionsAreReturnedIndependently()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        directoryContains(
                santaClause);

        final List<PlayerReference> references =
                parser.parse(
                        "Santa Clause met santa_clause.",
                        Collections.emptyList());

        Assert.assertEquals(
                2,
                references.size());

        assertMention(
                references.get(0),
                "Santa Clause",
                "Santa Clause",
                0,
                12);

        assertMention(
                references.get(1),
                "santa_clause",
                "Santa Clause",
                17,
                29);
    }

    @Test
    public void longerKnownNameWinsOverlappingMatch()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        final PlayerIdentity santa =
                identity(
                        "Santa");

        /*
         * PlayerDirectory guarantees longest-name-first ordering.
         */
        directoryContains(
                santaClause,
                santa);

        final List<PlayerReference> references =
                parser.parse(
                        "Santa Clause",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "Santa Clause",
                "Santa Clause",
                0,
                12);
    }

    @Test
    public void reservedReferencePreventsOverlappingKnownMention()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        final PlayerIdentity zezima =
                identity(
                        "Zezima");

        directoryContains(
                santaClause,
                zezima);

        final PlayerReference reserved =
                PlayerReference.builder()
                        .rawText("Santa Clause")
                        .normalizedToken("santa_clause")
                        .lookupName("Santa Clause")
                        .startOffset(0)
                        .endOffset(12)
                        .type(ReferenceType.TAG)
                        .locallyResolved(true)
                        .identity(santaClause)
                        .build();

        final List<PlayerReference> references =
                parser.parse(
                        "Santa Clause and Zezima",
                        Collections.singletonList(
                                reserved));

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "Zezima",
                "Zezima",
                17,
                23);
    }

    @Test
    public void returnedReferencesAreSortedByMessageOffset()
    {
        final PlayerIdentity santaClause =
                identity(
                        "Santa Clause");

        final PlayerIdentity zezima =
                identity(
                        "Zezima");

        /*
         * Directory order is independent of message position: Santa Clause is processed
         * before Zezima, then the final references are sorted by source offset.
         */
        directoryContains(
                santaClause,
                zezima);

        final List<PlayerReference> references =
                parser.parse(
                        "Zezima met Santa Clause",
                        Collections.emptyList());

        Assert.assertEquals(
                2,
                references.size());

        assertMention(
                references.get(0),
                "Zezima",
                "Zezima",
                0,
                6);

        assertMention(
                references.get(1),
                "Santa Clause",
                "Santa Clause",
                11,
                23);
    }

    @Test
    public void punctuationProvidesValidMentionBoundaries()
    {
        final PlayerIdentity partyHat =
                identity(
                        "Party Hat");

        directoryContains(
                partyHat);

        final List<PlayerReference> references =
                parser.parse(
                        "(Party Hat), hello!",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "Party Hat",
                "Party Hat",
                1,
                10);
    }

    @Test
    public void singleWordKnownPlayerMatchesNormally()
    {
        final PlayerIdentity santa =
                identity(
                        "Santa");

        directoryContains(
                santa);

        final List<PlayerReference> references =
                parser.parse(
                        "Hello Santa!",
                        Collections.emptyList());

        Assert.assertEquals(
                1,
                references.size());

        assertMention(
                references.get(0),
                "Santa",
                "Santa",
                6,
                11);
    }

    @Test
    public void overLengthUnknownNameDoesNotCreateKnownMention()
    {
        directoryContains(
                identity(
                        "Zezima"));

        final List<PlayerReference> references =
                parser.parse(
                        "definitelyunknown123",
                        Collections.emptyList());

        Assert.assertTrue(
                references.isEmpty());
    }

    @Test
    public void knownPlayerMentionParserPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final List<PlayerIdentity> identities =
                performanceIdentities();

        Mockito.when(
                        playerDirectory.allSortedLongestNameFirst())
                .thenReturn(
                        identities);

        final String knownMessage =
                "Zezima met Santa Clause near Party Hat while FasT07 waited.";

        final String unknownMessage =
                "definitelyunknown123 said hello to everyone.";

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    parser.parse(
                            knownMessage,
                            Collections.emptyList()));

            consume(
                    parser.parse(
                            unknownMessage,
                            Collections.emptyList()));
        }

        final long knownStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    parser.parse(
                            knownMessage,
                            Collections.emptyList()));
        }

        final long knownElapsed =
                System.nanoTime()
                        - knownStart;

        final long unknownStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    parser.parse(
                            unknownMessage,
                            Collections.emptyList()));
        }

        final long unknownElapsed =
                System.nanoTime()
                        - unknownStart;

        final double knownTotalMs =
                knownElapsed
                        / 1_000_000.0;

        final double unknownTotalMs =
                unknownElapsed
                        / 1_000_000.0;

        final double knownAverageMs =
                knownTotalMs
                        / PERFORMANCE_ITERATIONS;

        final double unknownAverageMs =
                unknownTotalMs
                        / PERFORMANCE_ITERATIONS;

        System.out.printf(
                "[RuneTags][KnownPlayerMentionParserTest] Performance= "
                        + "KnownCandidates: %.3fms (%.6fms) | "
                        + "UnknownCandidates: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                knownTotalMs,
                knownAverageMs,
                unknownTotalMs,
                unknownAverageMs,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * HELPERS
     */

    private void directoryContains(
            PlayerIdentity... identities)
    {
        Mockito.when(
                        playerDirectory.allSortedLongestNameFirst())
                .thenReturn(
                        Arrays.asList(
                                identities));
    }

    private PlayerIdentity identity(
            String canonicalName)
    {
        return PlayerIdentity.builder()
                .canonicalName(
                        canonicalName)
                .normalizedName(
                        normalizer.comparisonKey(
                                canonicalName))
                .build();
    }

    private static void assertMention(
            PlayerReference reference,
            String expectedRawText,
            String expectedLookupName,
            int expectedStart,
            int expectedEnd)
    {
        Assert.assertEquals(
                expectedRawText,
                reference.getRawText());

        Assert.assertEquals(
                expectedLookupName,
                reference.getLookupName());

        Assert.assertEquals(
                expectedStart,
                reference.getStartOffset());

        Assert.assertEquals(
                expectedEnd,
                reference.getEndOffset());

        Assert.assertEquals(
                ReferenceType.MENTION,
                reference.getType());

        Assert.assertTrue(
                reference.isLocallyResolved());

        Assert.assertNotNull(
                reference.getIdentity());
    }

    /*
     * PERFORMANCE
     */

    private static volatile Object performanceSink;

    private List<PlayerIdentity> performanceIdentities()
    {
        final PlayerIdentity zezima =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .normalizedName(
                                normalizer.comparisonKey(
                                        "Zezima"))
                        .build();

        final PlayerIdentity fast07 =
                PlayerIdentity.builder()
                        .canonicalName(
                                "FasT 07")
                        .normalizedName(
                                normalizer.comparisonKey(
                                        "FasT 07"))
                        .build();

        final PlayerIdentity santa =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .normalizedName(
                                normalizer.comparisonKey(
                                        "Santa"))
                        .build();

        final PlayerIdentity santaClause =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa Clause")
                        .normalizedName(
                                normalizer.comparisonKey(
                                        "Santa Clause"))
                        .build();

        final PlayerIdentity partyHat =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Party Hat")
                        .normalizedName(
                                normalizer.comparisonKey(
                                        "Party Hat"))
                        .build();

        final List<PlayerIdentity> identities =
                new java.util.ArrayList<>();

        for (int i = 0;
             i < 8;
             i++)
        {
            identities.add(
                    santaClause);

            identities.add(
                    partyHat);

            identities.add(
                    fast07);

            identities.add(
                    zezima);

            identities.add(
                    santa);
        }

        return identities;
    }

    private static void consume(
            Object value)
    {
        performanceSink =
                value;
    }
}