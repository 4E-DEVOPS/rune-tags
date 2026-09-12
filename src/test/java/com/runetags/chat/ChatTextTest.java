package com.runetags.chat;

import org.junit.Assert;
import org.junit.Test;

public class ChatTextTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void nullInputReturnsEmptyString()
    {
        Assert.assertEquals(
                "",
                ChatText.toSemanticPlain(
                        null));
    }

    @Test
    public void emptyInputReturnsEmptyString()
    {
        Assert.assertEquals(
                "",
                ChatText.toSemanticPlain(
                        ""));
    }

    @Test
    public void preservesVisibleAtControlTag()
    {
        Assert.assertEquals(
                "@definitelyunknown123",
                ChatText.toSemanticPlain(
                        "<at>definitelyunknown123"));
    }

    @Test
    public void stripsFormattingButPreservesAt()
    {
        Assert.assertEquals(
                "@Zezima",
                ChatText.toSemanticPlain(
                        "<col=ff0000><at>Zezima</col>"));
    }

    @Test
    public void stripsColorFormattingFromKnownPlayer()
    {
        Assert.assertEquals(
                "Santa Clause",
                ChatText.toSemanticPlain(
                        "<col=ff0000>Santa Clause</col>"));
    }

    @Test
    public void preservesSpacesInsidePlayerName()
    {
        Assert.assertEquals(
                "FasT 07",
                ChatText.toSemanticPlain(
                        "<col=ffffff>FasT 07</col>"));
    }

    @Test
    public void stripsMultipleFormattingTags()
    {
        Assert.assertEquals(
                "Party Hat",
                ChatText.toSemanticPlain(
                        "<col=ff0000><u=ffffff>Party Hat</u></col>"));
    }

    @Test
    public void preservesNormalUnformattedText()
    {
        Assert.assertEquals(
                "Zezima met Santa",
                ChatText.toSemanticPlain(
                        "Zezima met Santa"));
    }

    @Test
    public void preservesAtWhileRemovingSurroundingFormatting()
    {
        Assert.assertEquals(
                "@Santa Clause",
                ChatText.toSemanticPlain(
                        "<col=00ff00><at>Santa Clause</col>"));
    }

    @Test
    public void preservesLiteralAtCharacter()
    {
        Assert.assertEquals(
                "@Santa",
                ChatText.toSemanticPlain(
                        "@Santa"));
    }

    @Test
    public void preservesMultipleVisibleAtControlTags()
    {
        Assert.assertEquals(
                "@Zezima met @Santa",
                ChatText.toSemanticPlain(
                        "<at>Zezima met <at>Santa"));
    }

    @Test
    public void removesFormattingAcrossMultipleNames()
    {
        Assert.assertEquals(
                "Zezima met Santa Clause",
                ChatText.toSemanticPlain(
                        "<col=ff0000>Zezima</col> met <u=ffffff>Santa Clause</u>"));
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
                        "<col=ff0000>Santa Clause</col>");

        final long multipleTagsNanos =
                measurePerformance(
                        "<col=ff0000><u=ffffff>Party Hat</u></col>");

        final long visibleAtNanos =
                measurePerformance(
                        "<col=00ff00><at>Santa Clause</col>");

        System.out.printf(
                "[RuneTags][ChatTextTest] Performance= "
                        + "Plain: %.3fms (%.6fms) | "
                        + "Color: %.3fms (%.6fms) | "
                        + "MultipleTags: %.3fms (%.6fms) | "
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
                        multipleTagsNanos),
                nanosToMilliseconds(
                        multipleTagsNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        visibleAtNanos),
                nanosToMilliseconds(
                        visibleAtNanos) / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * PERFORMANCE
     */

    private static void warmUpPerformanceSamples()
    {
        for (int i = 0; i < PERFORMANCE_WARMUP_ITERATIONS; i++)
        {
            ChatText.toSemanticPlain(
                    "Zezima met Santa");

            ChatText.toSemanticPlain(
                    "<col=ff0000>Santa Clause</col>");

            ChatText.toSemanticPlain(
                    "<col=ff0000><u=ffffff>Party Hat</u></col>");

            ChatText.toSemanticPlain(
                    "<col=00ff00><at>Santa Clause</col>");
        }
    }

    private static long measurePerformance(
            String raw)
    {
        int checksum =
                0;

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            checksum +=
                    ChatText.toSemanticPlain(
                                    raw)
                            .length();
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertTrue(
                checksum > 0);

        return elapsed;
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos / 1_000_000.0;
    }
}