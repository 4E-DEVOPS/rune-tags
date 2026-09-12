package com.runetags.input;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared local-Note normalization and wrapping rules.
 *
 * Quick-Card rendering and Note-save validation use the same hard-line and
 * soft-wrap behavior: explicit newlines remain logical bullet boundaries while
 * ordinary text wraps by pixel width.
 */
public final class NoteTextLayout
{
    public static final String BULLET = "\u2022";
    public static final String BULLET_PREFIX = BULLET + " ";

    public static final int MAX_LOGICAL_LINES = 3;
    public static final int MAX_RENDERED_ROWS = 4;

    private NoteTextLayout()
    {
    }

    public static String normalizeForEditor(
            String note)
    {
        if (note == null
                || note.trim().isEmpty())
        {
            return BULLET_PREFIX;
        }

        final String normalized =
                normalizeLineEndings(note);

        final String[] logicalLines =
                normalized.split("\\n", -1);

        final List<String> editorLines =
                new ArrayList<>();

        for (String logicalLine : logicalLines)
        {
            final String clean =
                    normalizeBulletLine(logicalLine);

            if (clean == null)
            {
                continue;
            }

            editorLines.add(clean);
        }

        if (editorLines.isEmpty())
        {
            return BULLET_PREFIX;
        }

        return String.join(
                "\n",
                editorLines);
    }

    public static String normalizeForStorage(
            String editorValue)
    {
        if (editorValue == null)
        {
            return null;
        }

        final String normalized =
                normalizeLineEndings(editorValue);

        final String[] logicalLines =
                normalized.split("\\n", -1);

        final List<String> storedLines =
                new ArrayList<>();

        for (String logicalLine : logicalLines)
        {
            final String clean =
                    normalizeBulletLine(logicalLine);

            if (clean == null
                    || BULLET.equals(clean))
            {
                continue;
            }

            storedLines.add(clean);
        }

        if (storedLines.isEmpty())
        {
            return null;
        }

        return String.join(
                "\n",
                storedLines);
    }

    public static int logicalLineCount(
            String value)
    {
        if (value == null
                || value.isEmpty())
        {
            return 0;
        }

        final String normalized =
                normalizeLineEndings(value);

        int count = 1;

        for (int index = 0;
             index < normalized.length();
             ++index)
        {
            if (normalized.charAt(index) == '\n')
            {
                ++count;
            }
        }

        return count;
    }

    public static int widestLogicalLineWidth(
            FontMetrics metrics,
            String text)
    {
        if (metrics == null
                || text == null
                || text.trim().isEmpty())
        {
            return 0;
        }

        final String normalized =
                normalizeLineEndings(text);

        int widest = 0;

        for (String logicalLine
                : normalized.split("\\n", -1))
        {
            widest =
                    Math.max(
                            widest,
                            metrics.stringWidth(
                                    logicalLine.trim()));
        }

        return widest;
    }

    public static Result layout(
            FontMetrics metrics,
            String text,
            int maxWidth,
            int maxRows)
    {
        if (metrics == null
                || text == null
                || maxWidth <= 0
                || maxRows <= 0)
        {
            return Result.empty();
        }

        final String normalized =
                normalizeLineEndings(text);

        if (normalized.trim().isEmpty())
        {
            return Result.empty();
        }

        final List<String> rows =
                new ArrayList<>();

        boolean overflow = false;

        final String[] logicalLines =
                normalized.split("\\n", -1);

        outer:
        for (String logicalLine : logicalLines)
        {
            String remaining =
                    logicalLine.trim();

            if (remaining.isEmpty())
            {
                continue;
            }

            while (!remaining.isEmpty())
            {
                if (rows.size() >= maxRows)
                {
                    overflow = true;
                    break outer;
                }

                if (metrics.stringWidth(remaining)
                        <= maxWidth)
                {
                    rows.add(remaining);
                    remaining = "";
                    continue;
                }

                final int fittingEnd =
                        largestFittingPrefix(
                                metrics,
                                remaining,
                                maxWidth);

                if (fittingEnd <= 0)
                {
                    overflow = true;
                    break outer;
                }

                int breakAt =
                        remaining.lastIndexOf(
                                ' ',
                                Math.max(
                                        0,
                                        fittingEnd - 1));

                if (breakAt <= 0)
                {
                    breakAt =
                            fittingEnd;
                }

                final String row =
                        remaining.substring(
                                        0,
                                        breakAt)
                                .trim();

                if (!row.isEmpty())
                {
                    rows.add(row);
                }

                remaining =
                        remaining.substring(
                                        breakAt)
                                .trim();
            }
        }

        return new Result(
                Collections.unmodifiableList(rows),
                overflow);
    }

    private static int largestFittingPrefix(
            FontMetrics metrics,
            String value,
            int maxWidth)
    {
        int low = 1;
        int high =
                value.length();
        int end = 0;

        while (low <= high)
        {
            final int middle =
                    (low + high) >>> 1;

            if (metrics.stringWidth(
                    value.substring(
                            0,
                            middle)) <= maxWidth)
            {
                end = middle;
                low = middle + 1;
            }
            else
            {
                high = middle - 1;
            }
        }

        return end;
    }

    private static String normalizeBulletLine(
            String value)
    {
        if (value == null)
        {
            return null;
        }

        String clean =
                value.trim();

        if (clean.isEmpty())
        {
            return null;
        }

        if (clean.charAt(0) == BULLET.charAt(0))
        {
            clean =
                    clean.substring(1)
                            .trim();
        }

        if (clean.isEmpty())
        {
            return BULLET;
        }

        return BULLET_PREFIX
                + clean;
    }

    private static String normalizeLineEndings(
            String value)
    {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    public static final class Result
    {
        private static final Result EMPTY =
                new Result(
                        Collections.emptyList(),
                        false);

        private final List<String> rows;
        private final boolean overflow;

        private Result(
                List<String> rows,
                boolean overflow)
        {
            this.rows = rows;
            this.overflow = overflow;
        }

        public static Result empty()
        {
            return EMPTY;
        }

        public List<String> getRows()
        {
            return rows;
        }

        public boolean isOverflow()
        {
            return overflow;
        }
    }
}
