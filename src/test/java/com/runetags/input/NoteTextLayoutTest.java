package com.runetags.input;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class NoteTextLayoutTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    private static final FontMetrics METRICS =
            new FixedWidthFontMetrics(
                    8);

    /*
     * TESTS
     */

    @Test
    public void nullEditorValueBecomesEmptyBullet()
    {
        Assert.assertEquals(
                NoteTextLayout.BULLET_PREFIX,
                NoteTextLayout.normalizeForEditor(
                        null));
    }

    @Test
    public void blankEditorValueBecomesEmptyBullet()
    {
        Assert.assertEquals(
                NoteTextLayout.BULLET_PREFIX,
                NoteTextLayout.normalizeForEditor(
                        "   "));
    }

    @Test
    public void plainEditorLineReceivesBulletPrefix()
    {
        Assert.assertEquals(
                "\u2022 Known player",
                NoteTextLayout.normalizeForEditor(
                        "Known player"));
    }

    @Test
    public void existingBulletIsNotDuplicated()
    {
        Assert.assertEquals(
                "\u2022 Known player",
                NoteTextLayout.normalizeForEditor(
                        "\u2022 Known player"));
    }

    @Test
    public void editorNormalizationTrimsBulletSpacing()
    {
        Assert.assertEquals(
                "\u2022 Known player",
                NoteTextLayout.normalizeForEditor(
                        "   \u2022    Known player   "));
    }

    @Test
    public void editorNormalizationPreservesLogicalLines()
    {
        Assert.assertEquals(
                "\u2022 First\n\u2022 Second\n\u2022 Third",
                NoteTextLayout.normalizeForEditor(
                        "First\nSecond\nThird"));
    }

    @Test
    public void editorNormalizationConvertsWindowsLineEndings()
    {
        Assert.assertEquals(
                "\u2022 First\n\u2022 Second",
                NoteTextLayout.normalizeForEditor(
                        "First\r\nSecond"));
    }

    @Test
    public void editorNormalizationConvertsCarriageReturns()
    {
        Assert.assertEquals(
                "\u2022 First\n\u2022 Second",
                NoteTextLayout.normalizeForEditor(
                        "First\rSecond"));
    }

    @Test
    public void editorNormalizationSkipsBlankLogicalLines()
    {
        Assert.assertEquals(
                "\u2022 First\n\u2022 Second",
                NoteTextLayout.normalizeForEditor(
                        "First\n\n   \nSecond"));
    }

    @Test
    public void nullStorageValueRemainsNull()
    {
        Assert.assertNull(
                NoteTextLayout.normalizeForStorage(
                        null));
    }

    @Test
    public void emptyBulletStorageValueBecomesNull()
    {
        Assert.assertNull(
                NoteTextLayout.normalizeForStorage(
                        NoteTextLayout.BULLET_PREFIX));
    }

    @Test
    public void blankStorageValueBecomesNull()
    {
        Assert.assertNull(
                NoteTextLayout.normalizeForStorage(
                        "   "));
    }

    @Test
    public void storageNormalizationPreservesBulletFormatting()
    {
        Assert.assertEquals(
                "\u2022 Known player",
                NoteTextLayout.normalizeForStorage(
                        "Known player"));
    }

    @Test
    public void storageNormalizationRemovesEmptyBulletLines()
    {
        Assert.assertEquals(
                "\u2022 First\n\u2022 Second",
                NoteTextLayout.normalizeForStorage(
                        "\u2022 First\n\u2022 \n\u2022 Second"));
    }

    @Test
    public void storageNormalizationConvertsMixedLineEndings()
    {
        Assert.assertEquals(
                "\u2022 First\n\u2022 Second\n\u2022 Third",
                NoteTextLayout.normalizeForStorage(
                        "First\r\nSecond\rThird"));
    }

    @Test
    public void editorAndStorageNormalizationAgreeOnContent()
    {
        final String editor =
                NoteTextLayout.normalizeForEditor(
                        "First\r\nSecond");

        Assert.assertEquals(
                editor,
                NoteTextLayout.normalizeForStorage(
                        editor));
    }

    @Test
    public void logicalLineCountReturnsZeroForNull()
    {
        Assert.assertEquals(
                0,
                NoteTextLayout.logicalLineCount(
                        null));
    }

    @Test
    public void logicalLineCountReturnsZeroForEmptyString()
    {
        Assert.assertEquals(
                0,
                NoteTextLayout.logicalLineCount(
                        ""));
    }

    @Test
    public void logicalLineCountReturnsOneForSingleLine()
    {
        Assert.assertEquals(
                1,
                NoteTextLayout.logicalLineCount(
                        "\u2022 Santa"));
    }

    @Test
    public void logicalLineCountCountsExplicitNewlines()
    {
        Assert.assertEquals(
                3,
                NoteTextLayout.logicalLineCount(
                        "\u2022 First\n\u2022 Second\n\u2022 Third"));
    }

    @Test
    public void logicalLineCountNormalizesWindowsLineEndings()
    {
        Assert.assertEquals(
                3,
                NoteTextLayout.logicalLineCount(
                        "First\r\nSecond\r\nThird"));
    }

    @Test
    public void logicalLineCountCountsTrailingLogicalLine()
    {
        Assert.assertEquals(
                2,
                NoteTextLayout.logicalLineCount(
                        "First\n"));
    }

    @Test
    public void widestLogicalLineWidthReturnsZeroForNullMetrics()
    {
        Assert.assertEquals(
                0,
                NoteTextLayout.widestLogicalLineWidth(
                        null,
                        "Santa"));
    }

    @Test
    public void widestLogicalLineWidthReturnsZeroForNullText()
    {
        Assert.assertEquals(
                0,
                NoteTextLayout.widestLogicalLineWidth(
                        METRICS,
                        null));
    }

    @Test
    public void widestLogicalLineWidthReturnsZeroForBlankText()
    {
        Assert.assertEquals(
                0,
                NoteTextLayout.widestLogicalLineWidth(
                        METRICS,
                        "   "));
    }

    @Test
    public void widestLogicalLineWidthUsesLongestLogicalLine()
    {
        Assert.assertEquals(
                48,
                NoteTextLayout.widestLogicalLineWidth(
                        METRICS,
                        "Santa\nZezima"));
    }

    @Test
    public void widestLogicalLineWidthTrimsLogicalLines()
    {
        Assert.assertEquals(
                48,
                NoteTextLayout.widestLogicalLineWidth(
                        METRICS,
                        "   Santa   \n   Zezima   "));
    }

    @Test
    public void invalidLayoutArgumentsReturnEmptyResult()
    {
        assertEmpty(
                NoteTextLayout.layout(
                        null,
                        "Santa",
                        100,
                        4));

        assertEmpty(
                NoteTextLayout.layout(
                        METRICS,
                        null,
                        100,
                        4));

        assertEmpty(
                NoteTextLayout.layout(
                        METRICS,
                        "Santa",
                        0,
                        4));

        assertEmpty(
                NoteTextLayout.layout(
                        METRICS,
                        "Santa",
                        100,
                        0));
    }

    @Test
    public void blankLayoutTextReturnsEmptyResult()
    {
        assertEmpty(
                NoteTextLayout.layout(
                        METRICS,
                        "   ",
                        100,
                        4));
    }

    @Test
    public void fittingTextRemainsSingleRow()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa Clause",
                        200,
                        4);

        Assert.assertEquals(
                1,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "Santa Clause",
                result.getRows()
                        .get(0));

        Assert.assertFalse(
                result.isOverflow());
    }

    @Test
    public void wrappingPrefersSpaceBoundary()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa Clause",
                        48,
                        4);

        Assert.assertEquals(
                2,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "Santa",
                result.getRows()
                        .get(0));

        Assert.assertEquals(
                "Clause",
                result.getRows()
                        .get(1));

        Assert.assertFalse(
                result.isOverflow());
    }

    @Test
    public void longWordWrapsAtLargestFittingPrefix()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "abcdefghij",
                        32,
                        4);

        Assert.assertEquals(
                3,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "abcd",
                result.getRows()
                        .get(0));

        Assert.assertEquals(
                "efgh",
                result.getRows()
                        .get(1));

        Assert.assertEquals(
                "ij",
                result.getRows()
                        .get(2));

        Assert.assertFalse(
                result.isOverflow());
    }

    @Test
    public void explicitLogicalLinesRemainSeparateRows()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa\nZezima",
                        200,
                        4);

        Assert.assertEquals(
                2,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "Santa",
                result.getRows()
                        .get(0));

        Assert.assertEquals(
                "Zezima",
                result.getRows()
                        .get(1));

        Assert.assertFalse(
                result.isOverflow());
    }

    @Test
    public void explicitLinesCanAlsoSoftWrap()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa Clause\nParty Hat",
                        48,
                        4);

        Assert.assertEquals(
                4,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "Santa",
                result.getRows()
                        .get(0));

        Assert.assertEquals(
                "Clause",
                result.getRows()
                        .get(1));

        Assert.assertEquals(
                "Party",
                result.getRows()
                        .get(2));

        Assert.assertEquals(
                "Hat",
                result.getRows()
                        .get(3));

        Assert.assertFalse(
                result.isOverflow());
    }

    @Test
    public void exceedingMaximumRowsSetsOverflow()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa Clause Party Hat",
                        48,
                        2);

        Assert.assertEquals(
                2,
                result.getRows()
                        .size());

        Assert.assertTrue(
                result.isOverflow());
    }

    @Test
    public void exactMaximumRowsDoesNotOverflow()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa Clause",
                        48,
                        2);

        Assert.assertEquals(
                2,
                result.getRows()
                        .size());

        Assert.assertFalse(
                result.isOverflow());
    }

    @Test
    public void impossibleSingleCharacterWidthSetsOverflow()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa",
                        4,
                        4);

        Assert.assertTrue(
                result.getRows()
                        .isEmpty());

        Assert.assertTrue(
                result.isOverflow());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void layoutRowsAreUnmodifiable()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa Clause",
                        48,
                        4);

        result.getRows()
                .add(
                        "Injected");
    }

    @Test
    public void layoutNormalizesWindowsLineEndings()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa\r\nZezima",
                        200,
                        4);

        Assert.assertEquals(
                2,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "Santa",
                result.getRows()
                        .get(0));

        Assert.assertEquals(
                "Zezima",
                result.getRows()
                        .get(1));
    }

    @Test
    public void blankLogicalLinesAreSkippedDuringLayout()
    {
        final NoteTextLayout.Result result =
                NoteTextLayout.layout(
                        METRICS,
                        "Santa\n\n   \nZezima",
                        200,
                        4);

        Assert.assertEquals(
                2,
                result.getRows()
                        .size());

        Assert.assertEquals(
                "Santa",
                result.getRows()
                        .get(0));

        Assert.assertEquals(
                "Zezima",
                result.getRows()
                        .get(1));
    }

    @Test
    public void constantsMatchEditorContract()
    {
        Assert.assertEquals(
                "\u2022",
                NoteTextLayout.BULLET);

        Assert.assertEquals(
                "\u2022 ",
                NoteTextLayout.BULLET_PREFIX);

        Assert.assertEquals(
                3,
                NoteTextLayout.MAX_LOGICAL_LINES);

        Assert.assertEquals(
                4,
                NoteTextLayout.MAX_RENDERED_ROWS);
    }

    @Test
    public void layoutPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final String shortNote =
                "\u2022 Santa is a trusted player";

        final String wrappedNote =
                "\u2022 Santa Clause is a trusted player with several useful notes"
                        + "\n\u2022 Party Hat is another known player";

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    NoteTextLayout.layout(
                            METRICS,
                            shortNote,
                            240,
                            NoteTextLayout.MAX_RENDERED_ROWS));

            consume(
                    NoteTextLayout.layout(
                            METRICS,
                            wrappedNote,
                            160,
                            NoteTextLayout.MAX_RENDERED_ROWS));
        }

        final long shortStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    NoteTextLayout.layout(
                            METRICS,
                            shortNote,
                            240,
                            NoteTextLayout.MAX_RENDERED_ROWS));
        }

        final long shortElapsed =
                System.nanoTime()
                        - shortStart;

        final long wrappedStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    NoteTextLayout.layout(
                            METRICS,
                            wrappedNote,
                            160,
                            NoteTextLayout.MAX_RENDERED_ROWS));
        }

        final long wrappedElapsed =
                System.nanoTime()
                        - wrappedStart;

        printPerformance(
                "SingleRowLayout",
                shortElapsed,
                "WrappedLayout",
                wrappedElapsed);
    }

    /*
     * HELPERS
     */

    private static void assertEmpty(
            NoteTextLayout.Result result)
    {
        Assert.assertNotNull(
                result);

        Assert.assertTrue(
                result.getRows()
                        .isEmpty());

        Assert.assertFalse(
                result.isOverflow());
    }

    private static final class FixedWidthFontMetrics
            extends FontMetrics
    {
        private final int characterWidth;

        private FixedWidthFontMetrics(
                int characterWidth)
        {
            super(
                    new Font(
                            Font.MONOSPACED,
                            Font.PLAIN,
                            12));

            this.characterWidth =
                    characterWidth;
        }

        @Override
        public int stringWidth(
                String value)
        {
            if (value == null)
            {
                return 0;
            }

            return value.length()
                    * characterWidth;
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

    private static void printPerformance(
            String firstLabel,
            long firstElapsedNanos,
            String secondLabel,
            long secondElapsedNanos)
    {
        final double firstTotalMillis =
                firstElapsedNanos
                        / 1_000_000.0;

        final double secondTotalMillis =
                secondElapsedNanos
                        / 1_000_000.0;

        final double firstAverageMillis =
                firstTotalMillis
                        / PERFORMANCE_ITERATIONS;

        final double secondAverageMillis =
                secondTotalMillis
                        / PERFORMANCE_ITERATIONS;

        System.out.printf(
                "[RuneTags][NoteTextLayoutTest] Performance= "
                        + "%s: %.3fms (%.6fms) | "
                        + "%s: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                firstLabel,
                firstTotalMillis,
                firstAverageMillis,
                secondLabel,
                secondTotalMillis,
                secondAverageMillis,
                PERFORMANCE_ITERATIONS);
    }
}