package com.runetags.mention;

import com.runetags.Configurations;
import com.runetags.reference.PlayerReference;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class LocalMentionMatcherTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void nullReferenceReturnsNoMatch()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.match(
                        null,
                        "Santa Clause");

        assertNoMatch(
                match);
    }

    @Test
    public void nullLocalPlayerReturnsNoReferenceMatch()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "@Santa_Clause"),
                        null);

        assertNoMatch(
                match);
    }

    @Test
    public void emptyLocalPlayerReturnsNoReferenceMatch()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "@Santa_Clause"),
                        "");

        assertNoMatch(
                match);
    }

    @Test
    public void explicitTagMatchesLocalAccountByNormalizedName()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "@santa_clause"),
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.ACCOUNT_NAME,
                match.getReason());

        Assert.assertEquals(
                "@santa_clause",
                match.getMatchedToken());
    }

    @Test
    public void hyphenatedReferenceMatchesLocalAccount()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "Santa-Clause"),
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.ACCOUNT_NAME,
                match.getReason());
    }

    @Test
    public void uniqueHighlightMatchesStructuredReference()
    {
        final TestHarness harness =
                createHarness(
                        "Party Hat");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "@Party_Hat"),
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.UNIQUE_HIGHLIGHT,
                match.getReason());

        Assert.assertEquals(
                "@Party_Hat",
                match.getMatchedToken());
    }

    @Test
    public void localAccountTakesPriorityOverUniqueHighlight()
    {
        final TestHarness harness =
                createHarness(
                        "Santa Clause");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "@Santa_Clause"),
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.ACCOUNT_NAME,
                match.getReason());
    }

    @Test
    public void unrelatedReferenceReturnsNoMatch()
    {
        final TestHarness harness =
                createHarness(
                        "Party Hat");

        final LocalMentionMatch match =
                harness.matcher.match(
                        reference(
                                "@Zezima"),
                        "Santa Clause");

        assertNoMatch(
                match);
    }

    @Test
    public void nullMessageReturnsNoMatch()
    {
        final TestHarness harness =
                createHarness(
                        "");

        assertNoMatch(
                harness.matcher.matchMessage(
                        null,
                        "Santa Clause"));
    }

    @Test
    public void emptyMessageReturnsNoMatch()
    {
        final TestHarness harness =
                createHarness(
                        "");

        assertNoMatch(
                harness.matcher.matchMessage(
                        "",
                        "Santa Clause"));
    }

    @Test
    public void canonicalAccountNameMatchesMessage()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Hello Santa Clause!",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NORMALIZED_ACCOUNT_NAME,
                match.getReason());

        Assert.assertEquals(
                "santa clause",
                match.getMatchedToken());
    }

    @Test
    public void underscoredAccountNameMatchesMessage()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Hello santa_clause!",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NORMALIZED_ACCOUNT_NAME,
                match.getReason());

        Assert.assertEquals(
                "santa_clause",
                match.getMatchedToken());
    }

    @Test
    public void hyphenatedAccountNameMatchesMessage()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Hello santa-clause!",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NORMALIZED_ACCOUNT_NAME,
                match.getReason());

        Assert.assertEquals(
                "santa-clause",
                match.getMatchedToken());
    }

    @Test
    public void accountMatchIsCaseInsensitive()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "HELLO SANTA CLAUSE!",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NORMALIZED_ACCOUNT_NAME,
                match.getReason());
    }

    @Test
    public void accountNameRequiresWholePhraseBoundary()
    {
        final TestHarness harness =
                createHarness(
                        "");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "xSanta Clause2",
                        "Santa Clause");

        assertNoMatch(
                match);
    }

    @Test
    public void uniqueHighlightMatchesOrdinaryMessage()
    {
        final TestHarness harness =
                createHarness(
                        "Party Hat");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Did someone say Party Hat?",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.UNIQUE_HIGHLIGHT,
                match.getReason());

        Assert.assertEquals(
                "Party Hat",
                match.getMatchedToken());
    }

    @Test
    public void uniqueHighlightsAreTrimmed()
    {
        final TestHarness harness =
                createHarness(
                        "Zezima,   Party Hat  , Santa");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Party Hat dropped.",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.UNIQUE_HIGHLIGHT,
                match.getReason());

        Assert.assertEquals(
                "Party Hat",
                match.getMatchedToken());
    }

    @Test
    public void blankUniqueHighlightEntriesAreIgnored()
    {
        final TestHarness harness =
                createHarness(
                        " , , ");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Hello Zezima",
                        "Santa Clause");

        assertNoMatch(
                match);
    }

    @Test
    public void localAccountMessageMatchTakesPriorityOverUniqueHighlight()
    {
        final TestHarness harness =
                createHarness(
                        "Party Hat");

        final LocalMentionMatch match =
                harness.matcher.matchMessage(
                        "Santa Clause found a Party Hat.",
                        "Santa Clause");

        Assert.assertTrue(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NORMALIZED_ACCOUNT_NAME,
                match.getReason());

        Assert.assertEquals(
                "santa clause",
                match.getMatchedToken());
    }

    @Test
    public void nullUniqueMentionsConfigurationIsSafe()
    {
        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        Mockito.when(
                        config.uniqueMentions())
                .thenReturn(
                        null);

        final LocalMentionMatcher matcher =
                new LocalMentionMatcher(
                        config,
                        new NameNormalizer());

        assertNoMatch(
                matcher.matchMessage(
                        "Hello Party Hat",
                        "Santa Clause"));
    }

    @Test
    public void localMentionMatcherPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final TestHarness harness =
                createHarness(
                        "Party Hat, Zezima");

        final String accountMessage =
                "Hello Santa Clause!";

        final String highlightMessage =
                "A Party Hat appeared.";

        final String missMessage =
                "definitelyunknown123 said hello.";

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    harness.matcher.matchMessage(
                            accountMessage,
                            "Santa Clause"));

            consume(
                    harness.matcher.matchMessage(
                            highlightMessage,
                            "Santa Clause"));

            consume(
                    harness.matcher.matchMessage(
                            missMessage,
                            "Santa Clause"));
        }

        final long accountStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    harness.matcher.matchMessage(
                            accountMessage,
                            "Santa Clause"));
        }

        final long accountElapsed =
                System.nanoTime()
                        - accountStart;

        final long highlightStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    harness.matcher.matchMessage(
                            highlightMessage,
                            "Santa Clause"));
        }

        final long highlightElapsed =
                System.nanoTime()
                        - highlightStart;

        final long missStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    harness.matcher.matchMessage(
                            missMessage,
                            "Santa Clause"));
        }

        final long missElapsed =
                System.nanoTime()
                        - missStart;

        final double accountTotalMs =
                accountElapsed
                        / 1_000_000.0;

        final double highlightTotalMs =
                highlightElapsed
                        / 1_000_000.0;

        final double missTotalMs =
                missElapsed
                        / 1_000_000.0;

        System.out.printf(
                "[RuneTags][LocalMentionMatcherTest] Performance= "
                        + "AccountMatch: %.3fms (%.6fms) | "
                        + "HighlightMatch: %.3fms (%.6fms) | "
                        + "Miss: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                accountTotalMs,
                accountTotalMs / PERFORMANCE_ITERATIONS,
                highlightTotalMs,
                highlightTotalMs / PERFORMANCE_ITERATIONS,
                missTotalMs,
                missTotalMs / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * HELPERS
     */

    private static TestHarness createHarness(
            String uniqueMentions)
    {
        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        Mockito.when(
                        config.uniqueMentions())
                .thenReturn(
                        uniqueMentions);

        return new TestHarness(
                new LocalMentionMatcher(
                        config,
                        new NameNormalizer()));
    }

    private static PlayerReference reference(
            String rawText)
    {
        return PlayerReference.builder()
                .rawText(
                        rawText)
                .build();
    }

    private static void assertNoMatch(
            LocalMentionMatch match)
    {
        Assert.assertNotNull(
                match);

        Assert.assertFalse(
                match.isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NONE,
                match.getReason());

        Assert.assertNull(
                match.getMatchedToken());
    }

    private static final class TestHarness
    {
        private final LocalMentionMatcher matcher;

        private TestHarness(
                LocalMentionMatcher matcher)
        {
            this.matcher =
                    matcher;
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