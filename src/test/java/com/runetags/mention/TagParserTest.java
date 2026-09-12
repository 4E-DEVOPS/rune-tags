package com.runetags.mention;

import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerIdentity;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TagParserTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void nullMessageReturnsNoTags()
    {
        final TestHarness harness =
                createHarness();

        Assert.assertTrue(
                harness.parser
                        .parse(
                                null)
                        .isEmpty());
    }

    @Test
    public void emptyMessageReturnsNoTags()
    {
        final TestHarness harness =
                createHarness();

        Assert.assertTrue(
                harness.parser
                        .parse(
                                "")
                        .isEmpty());
    }

    @Test
    public void messageWithoutTagReturnsNoReferences()
    {
        final TestHarness harness =
                createHarness();

        Assert.assertTrue(
                harness.parser
                        .parse(
                                "Hello Santa")
                        .isEmpty());
    }

    @Test
    public void whitespaceTerminatesExplicitTag()
    {
        final TestHarness harness =
                createHarness();

        final List<PlayerReference> refs =
                harness.parser.parse(
                        "hey @party hat");

        Assert.assertEquals(
                1,
                refs.size());

        Assert.assertEquals(
                "@party",
                refs.get(0)
                        .getRawText());

        Assert.assertEquals(
                "party",
                refs.get(0)
                        .getLookupName()
                        .toLowerCase());
    }

    @Test
    public void underscoreRemainsInsideTag()
    {
        final TestHarness harness =
                createHarness();

        final List<PlayerReference> refs =
                harness.parser.parse(
                        "hey @santa_clause");

        Assert.assertEquals(
                1,
                refs.size());

        Assert.assertEquals(
                "santa_clause",
                refs.get(0)
                        .getNormalizedToken());
    }

    @Test
    public void hyphenRemainsInsideTag()
    {
        final TestHarness harness =
                createHarness();

        final List<PlayerReference> refs =
                harness.parser.parse(
                        "hey @santa-clause");

        Assert.assertEquals(
                1,
                refs.size());

        Assert.assertEquals(
                "@santa-clause",
                refs.get(0)
                        .getRawText());

        Assert.assertEquals(
                "santa_clause",
                refs.get(0)
                        .getNormalizedToken());
    }

    @Test
    public void unresolvedTagUsesCanonicalizedLookupName()
    {
        final TestHarness harness =
                createHarness();

        final List<PlayerReference> refs =
                harness.parser.parse(
                        "@Santa_Clause");

        Assert.assertEquals(
                1,
                refs.size());

        final PlayerReference reference =
                refs.get(0);

        Assert.assertEquals(
                "Santa Clause",
                reference.getLookupName());

        Assert.assertFalse(
                reference.isLocallyResolved());

        Assert.assertNull(
                reference.getIdentity());
    }

    @Test
    public void resolvedTagUsesDirectoryCanonicalName()
    {
        final TestHarness harness =
                createHarness();

        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa Clause")
                        .build();

        Mockito.when(
                        harness.playerDirectory.find(
                                "santa clause"))
                .thenReturn(
                        Optional.of(
                                identity));

        /*
         * TagParser preserves the token's canonicalized case when asking the
         * directory, so stub that exact public call as well.
         */
        Mockito.when(
                        harness.playerDirectory.find(
                                "Santa Clause"))
                .thenReturn(
                        Optional.of(
                                identity));

        final List<PlayerReference> refs =
                harness.parser.parse(
                        "@santa_clause");

        Assert.assertEquals(
                1,
                refs.size());

        final PlayerReference reference =
                refs.get(0);

        Assert.assertEquals(
                "Santa Clause",
                reference.getLookupName());

        Assert.assertTrue(
                reference.isLocallyResolved());

        Assert.assertSame(
                identity,
                reference.getIdentity());
    }

    @Test
    public void tagReferenceUsesTagType()
    {
        final TestHarness harness =
                createHarness();

        final PlayerReference reference =
                harness.parser
                        .parse(
                                "@Santa")
                        .get(0);

        Assert.assertEquals(
                ReferenceType.TAG,
                reference.getType());
    }

    @Test
    public void offsetsCoverCompleteTagIncludingAtSymbol()
    {
        final TestHarness harness =
                createHarness();

        final PlayerReference reference =
                harness.parser
                        .parse(
                                "hello @Santa!")
                        .get(0);

        Assert.assertEquals(
                6,
                reference.getStartOffset());

        Assert.assertEquals(
                12,
                reference.getEndOffset());

        Assert.assertEquals(
                "@Santa",
                reference.getRawText());
    }

    @Test
    public void punctuationTerminatesTag()
    {
        final TestHarness harness =
                createHarness();

        final PlayerReference reference =
                harness.parser
                        .parse(
                                "@Santa, hello")
                        .get(0);

        Assert.assertEquals(
                "@Santa",
                reference.getRawText());
    }

    @Test
    public void multipleTagsAreReturnedInMessageOrder()
    {
        final TestHarness harness =
                createHarness();

        final List<PlayerReference> refs =
                harness.parser.parse(
                        "@Zezima met @Santa_Clause");

        Assert.assertEquals(
                2,
                refs.size());

        Assert.assertEquals(
                "@Zezima",
                refs.get(0)
                        .getRawText());

        Assert.assertEquals(
                "@Santa_Clause",
                refs.get(1)
                        .getRawText());

        Assert.assertTrue(
                refs.get(0)
                        .getStartOffset()
                        < refs.get(1)
                        .getStartOffset());
    }

    @Test
    public void loneAtSymbolDoesNotCreateTag()
    {
        final TestHarness harness =
                createHarness();

        Assert.assertTrue(
                harness.parser
                        .parse(
                                "hello @")
                        .isEmpty());
    }

    @Test
    public void tagParserPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final NameNormalizer normalizer =
                new NameNormalizer();

        final PlayerDirectory directory =
                new PlayerDirectory(
                        null,
                        null,
                        null,
                        normalizer);

        final TagParser parser =
                new TagParser(
                        normalizer,
                        directory);

        final String oneTag =
                "Hello @Santa_Clause!";

        final String multipleTags =
                "@Zezima met @Santa_Clause near @Party_Hat.";

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    parser.parse(
                            oneTag));

            consume(
                    parser.parse(
                            multipleTags));
        }

        final long oneStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    parser.parse(
                            oneTag));
        }

        final long oneElapsed =
                System.nanoTime()
                        - oneStart;

        final long multipleStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    parser.parse(
                            multipleTags));
        }

        final long multipleElapsed =
                System.nanoTime()
                        - multipleStart;

        final double oneTotalMs =
                oneElapsed
                        / 1_000_000.0;

        final double multipleTotalMs =
                multipleElapsed
                        / 1_000_000.0;

        System.out.printf(
                "[RuneTags][TagParserTest] Performance= "
                        + "OneTag: %.3fms (%.6fms) | "
                        + "ThreeTags: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                oneTotalMs,
                oneTotalMs / PERFORMANCE_ITERATIONS,
                multipleTotalMs,
                multipleTotalMs / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * HELPERS
     */

    private static TestHarness createHarness()
    {
        final NameNormalizer normalizer =
                new NameNormalizer();

        final PlayerDirectory playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        Mockito.when(
                        playerDirectory.find(
                                Mockito.anyString()))
                .thenReturn(
                        Optional.empty());

        return new TestHarness(
                playerDirectory,
                new TagParser(
                        normalizer,
                        playerDirectory));
    }

    private static final class TestHarness
    {
        private final PlayerDirectory playerDirectory;
        private final TagParser parser;

        private TestHarness(
                PlayerDirectory playerDirectory,
                TagParser parser)
        {
            this.playerDirectory =
                    playerDirectory;

            this.parser =
                    parser;
        }
    }

    /*
     * PERFORMANCE
     */

    private static volatile Object performanceSink;

    private static void consume(
            Object value)
    {
        performanceSink =
                value;
    }
}