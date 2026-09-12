package com.runetags.chat;

import org.junit.Assert;
import org.junit.Test;

public class MessageMarkupMapTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void nullInputProducesEmptySemanticMessage()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        null);

        Assert.assertTrue(
                map.matchesPlain(
                        ""));

        Assert.assertTrue(
                map.matchesPlain(
                        null));

        Assert.assertEquals(
                0,
                map.rawBoundary(
                        0));
    }

    @Test
    public void emptyInputProducesEmptySemanticMessage()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        "");

        Assert.assertTrue(
                map.matchesPlain(
                        ""));

        Assert.assertEquals(
                0,
                map.rawBoundary(
                        0));
    }

    @Test
    public void mapsPlainMessage()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        "hello Zezima");

        Assert.assertTrue(
                map.matchesPlain(
                        "hello Zezima"));

        Assert.assertEquals(
                0,
                map.rawBoundary(
                        0));

        Assert.assertEquals(
                5,
                map.rawBoundary(
                        5));

        Assert.assertEquals(
                12,
                map.rawBoundary(
                        12));
    }

    @Test
    public void plainMessageRejectsDifferentSemanticText()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        "hello Zezima");

        Assert.assertFalse(
                map.matchesPlain(
                        "hello Santa"));
    }

    @Test
    public void nonEmptyMessageRejectsNullSemanticText()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        "Zezima");

        Assert.assertFalse(
                map.matchesPlain(
                        null));
    }

    @Test
    public void skipsColorMarkup()
    {
        final String raw =
                "<col=ff0000>Zezima</col> says hello";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "Zezima says hello"));

        final int nameStart =
                map.rawBoundary(
                        0);

        final int nameEnd =
                map.rawBoundary(
                        6);

        Assert.assertEquals(
                raw.indexOf(
                        "Zezima"),
                nameStart);

        Assert.assertEquals(
                raw.indexOf(
                        "</col>")
                        + "</col>".length(),
                nameEnd);
    }

    @Test
    public void mapsTextAfterClosingColorTag()
    {
        final String raw =
                "<col=ff0000>Zezima</col> met Santa";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "Zezima met Santa"));

        final int santaPlainStart =
                "Zezima met ".length();

        final int santaRawStart =
                map.rawBoundary(
                        santaPlainStart);

        Assert.assertEquals(
                raw.indexOf(
                        "Santa"),
                santaRawStart);
    }

    @Test
    public void mapsMultipleFormattedSegments()
    {
        final String raw =
                "<col=ff0000>Zezima</col> met <col=00ff00>Santa Clause</col>";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "Zezima met Santa Clause"));

        Assert.assertEquals(
                raw.indexOf(
                        "Zezima"),
                map.rawBoundary(
                        0));

        Assert.assertEquals(
                raw.indexOf(
                        "Santa Clause"),
                map.rawBoundary(
                        "Zezima met ".length()));
    }

    @Test
    public void nestedFormattingDoesNotChangeSemanticMessage()
    {
        final String raw =
                "<col=ff0000><u=ffffff>Party Hat</u></col>";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "Party Hat"));

        Assert.assertEquals(
                raw.indexOf(
                        "Party Hat"),
                map.rawBoundary(
                        0));

        Assert.assertEquals(
                raw.indexOf(
                        "</u>"),
                map.rawBoundary(
                        "Party Hat".length()));
    }

    @Test
    public void visibleAtControlTagMapsToRawTagBoundary()
    {
        final String raw =
                "<at>Zezima";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "@Zezima"));

        Assert.assertEquals(
                0,
                map.rawBoundary(
                        0));

        Assert.assertEquals(
                "<at>".length(),
                map.rawBoundary(
                        1));

        Assert.assertEquals(
                raw.indexOf(
                        "Zezima"),
                map.rawBoundary(
                        1));
    }

    @Test
    public void visibleAtControlTagMapsCorrectlyInsideFormatting()
    {
        final String raw =
                "<col=ff0000><at>Santa Clause</col>";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "@Santa Clause"));

        final int atPlainStart =
                0;

        final int namePlainStart =
                1;

        Assert.assertEquals(
                raw.indexOf(
                        "<at>"),
                map.rawBoundary(
                        atPlainStart));

        Assert.assertEquals(
                raw.indexOf(
                        "Santa Clause"),
                map.rawBoundary(
                        namePlainStart));

        Assert.assertEquals(
                raw.indexOf(
                        "</col>"),
                map.rawBoundary(
                        "@Santa Clause".length()));
    }

    @Test
    public void multipleVisibleAtControlTagsMapIndependently()
    {
        final String raw =
                "<at>Zezima met <at>Santa";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "@Zezima met @Santa"));

        final int firstAtPlainStart =
                0;

        final int secondAtPlainStart =
                "@Zezima met ".length();

        Assert.assertEquals(
                raw.indexOf(
                        "<at>"),
                map.rawBoundary(
                        firstAtPlainStart));

        Assert.assertEquals(
                raw.lastIndexOf(
                        "<at>"),
                map.rawBoundary(
                        secondAtPlainStart));

        Assert.assertEquals(
                raw.indexOf(
                        "Santa"),
                map.rawBoundary(
                        secondAtPlainStart + 1));
    }

    @Test
    public void formattedBoundaryAtEndStopsBeforeTrailingMarkup()
    {
        final String raw =
                "<col=ff0000>Santa</col>";

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        raw);

        Assert.assertTrue(
                map.matchesPlain(
                        "Santa"));

        final int start =
                map.rawBoundary(
                        0);

        final int end =
                map.rawBoundary(
                        "Santa".length());

        Assert.assertEquals(
                raw.indexOf(
                        "Santa"),
                start);

        Assert.assertEquals(
                raw.indexOf(
                        "</col>"),
                end);
    }

    @Test
    public void markupOnlyMessageProducesEmptySemanticMessage()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        "<col=ff0000></col>");

        Assert.assertTrue(
                map.matchesPlain(
                        ""));

        Assert.assertEquals(
                0,
                map.rawBoundary(
                        0));
    }

    @Test
    public void invalidPlainBoundariesReturnNegativeOne()
    {
        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        "Zezima");

        Assert.assertEquals(
                -1,
                map.rawBoundary(
                        -1));

        Assert.assertEquals(
                -1,
                map.rawBoundary(
                        "Zezima".length() + 1));
    }

    @Test
    public void performanceSample()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        warmUpPerformanceSamples();

        final long plainNanos =
                measurePerformance(
                        "Zezima met Santa");

        final long colorNanos =
                measurePerformance(
                        "<col=ff0000>Zezima</col> met Santa");

        final long nestedNanos =
                measurePerformance(
                        "<col=ff0000><u=ffffff>Party Hat</u></col>");

        final long visibleAtNanos =
                measurePerformance(
                        "<col=00ff00><at>Santa Clause</col>");

        System.out.printf(
                "[RuneTags][MessageMarkupMapTest] Performance= "
                        + "Plain: %.3fms (%.6fms) | "
                        + "Color: %.3fms (%.6fms) | "
                        + "Nested: %.3fms (%.6fms) | "
                        + "VisibleAt: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                nanosToMilliseconds(
                        plainNanos),
                nanosToMilliseconds(
                        plainNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        colorNanos),
                nanosToMilliseconds(
                        colorNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        nestedNanos),
                nanosToMilliseconds(
                        nestedNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        visibleAtNanos),
                nanosToMilliseconds(
                        visibleAtNanos) / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * HELPERS
     */

    /*
     * PERFORMANCE
     */

    private static void warmUpPerformanceSamples()
    {
        for (int i = 0; i < PERFORMANCE_WARMUP_ITERATIONS; i++)
        {
            consumeMap(
                    MessageMarkupMap.create(
                            "Zezima met Santa"));

            consumeMap(
                    MessageMarkupMap.create(
                            "<col=ff0000>Zezima</col> met Santa"));

            consumeMap(
                    MessageMarkupMap.create(
                            "<col=ff0000><u=ffffff>Party Hat</u></col>"));

            consumeMap(
                    MessageMarkupMap.create(
                            "<col=00ff00><at>Santa Clause</col>"));
        }
    }

    private static long measurePerformance(
            String raw)
    {
        final String expectedPlain =
                ChatText.toSemanticPlain(
                        raw);

        int checksum =
                0;

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            final MessageMarkupMap map =
                    MessageMarkupMap.create(
                            raw);

            checksum +=
                    map.rawBoundary(
                            expectedPlain.length());

            if (map.matchesPlain(
                    expectedPlain))
            {
                checksum++;
            }
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertTrue(
                checksum > 0);

        return elapsed;
    }

    private static int consumeMap(
            MessageMarkupMap map)
    {
        final int endBoundary =
                map.rawBoundary(
                        0);

        return endBoundary
                + (map.matchesPlain(
                "") ? 1 : 0);
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos / 1_000_000.0;
    }
}