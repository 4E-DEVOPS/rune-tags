package com.runetags.mention;

import org.junit.Assert;
import org.junit.Test;

public class NameNormalizerTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    private final NameNormalizer normalizer =
            new NameNormalizer();

    /*
     * TESTS
     */

    @Test
    public void normalizesSeparators()
    {
        Assert.assertEquals(
                "santaclause",
                normalizer.comparisonKey(
                        "Santa Clause"));

        Assert.assertEquals(
                "santaclause",
                normalizer.comparisonKey(
                        "santa_clause"));

        Assert.assertEquals(
                "santaclause",
                normalizer.comparisonKey(
                        "santa-clause"));

        Assert.assertEquals(
                "santa_clause",
                normalizer.taggedToken(
                        "Santa Clause"));
    }

    @Test
    public void stripsFormatting()
    {
        Assert.assertEquals(
                "santaclause",
                normalizer.comparisonKey(
                        "<col=ff0000>Santa Clause</col>"));
    }

    @Test
    public void nullNormalizesToEmptyValues()
    {
        final NormalizedPlayerName normalized =
                normalizer.normalize(
                        null);

        Assert.assertEquals(
                "",
                normalized.getCanonicalName());

        Assert.assertEquals(
                "",
                normalized.getComparisonKey());

        Assert.assertEquals(
                "",
                normalized.getTaggedToken());
    }

    @Test
    public void canonicalizeTrimsWhitespace()
    {
        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "   Santa Clause   "));
    }

    @Test
    public void canonicalizeCollapsesRepeatedWhitespace()
    {
        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "Santa     Clause"));
    }

    @Test
    public void canonicalizeConvertsUnderscoresToSpaces()
    {
        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "Santa_Clause"));
    }

    @Test
    public void canonicalizeConvertsHyphensToSpaces()
    {
        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "Santa-Clause"));
    }

    @Test
    public void canonicalizeConvertsNonBreakingSpaces()
    {
        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "Santa\u00A0Clause"));

        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "Santa\u202FClause"));
    }

    @Test
    public void canonicalizeRemovesUnsupportedCharacters()
    {
        Assert.assertEquals(
                "Santa Clause",
                normalizer.canonicalize(
                        "Santa! Clause?"));
    }

    @Test
    public void normalizePreservesCanonicalCase()
    {
        final NormalizedPlayerName normalized =
                normalizer.normalize(
                        "FasT 07");

        Assert.assertEquals(
                "FasT 07",
                normalized.getCanonicalName());

        Assert.assertEquals(
                "fast07",
                normalized.getComparisonKey());

        Assert.assertEquals(
                "fast_07",
                normalized.getTaggedToken());
    }

    @Test
    public void normalizeProducesAllRepresentationsTogether()
    {
        final NormalizedPlayerName normalized =
                normalizer.normalize(
                        "<col=ff0000>Santa-Clause</col>");

        Assert.assertEquals(
                "Santa Clause",
                normalized.getCanonicalName());

        Assert.assertEquals(
                "santaclause",
                normalized.getComparisonKey());

        Assert.assertEquals(
                "santa_clause",
                normalized.getTaggedToken());
    }

    @Test
    public void nameNormalizerPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final String value =
                "<col=ff0000>Santa_Clause</col>";

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    normalizer.comparisonKey(
                            value));

            consume(
                    normalizer.taggedToken(
                            value));

            consume(
                    normalizer.canonicalize(
                            value));
        }

        final long comparisonStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    normalizer.comparisonKey(
                            value));
        }

        final long comparisonElapsed =
                System.nanoTime()
                        - comparisonStart;

        final long taggedStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    normalizer.taggedToken(
                            value));
        }

        final long taggedElapsed =
                System.nanoTime()
                        - taggedStart;

        final long canonicalStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    normalizer.canonicalize(
                            value));
        }

        final long canonicalElapsed =
                System.nanoTime()
                        - canonicalStart;

        final double comparisonTotalMs =
                comparisonElapsed
                        / 1_000_000.0;

        final double taggedTotalMs =
                taggedElapsed
                        / 1_000_000.0;

        final double canonicalTotalMs =
                canonicalElapsed
                        / 1_000_000.0;

        System.out.printf(
                "[RuneTags][NameNormalizerTest] Performance= "
                        + "ComparisonKey: %.3fms (%.6fms) | "
                        + "TaggedToken: %.3fms (%.6fms) | "
                        + "Canonicalize: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                comparisonTotalMs,
                comparisonTotalMs / PERFORMANCE_ITERATIONS,
                taggedTotalMs,
                taggedTotalMs / PERFORMANCE_ITERATIONS,
                canonicalTotalMs,
                canonicalTotalMs / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
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