package com.runetags.debug;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.util.Text;

/**
 * Optional RuneTags chat/performance diagnostic.
 *
 * This class is deliberately observational:
 *
 * - it never mutates script stacks;
 * - it never changes Widget FontId;
 * - it never moves/resizes Widgets;
 * - it never calls revalidate();
 *
 * Normal operation emits one compact performance summary every few seconds.
 *
 * The historical font-regression messages are retained as optional targeted
 * probes so the known Script 203 / 4483 construction payload can be inspected
 * again later without rebuilding a large diagnostic harness.
 */
@Slf4j
public class ChatLayoutDiagnostic
{
    private static final int CHAT_BODY_SCRIPT = 203;
    private static final int CLAN_BODY_SCRIPT = 4483;

    /*
     * Known RuneScape chat fonts retained for future regression work.
     */
    private static final int NORMAL_FONT_ID = 495;
    private static final int BOLD_FONT_ID = 496;
    private static final int VERDANA_FONT_ID = 1446;

    /*
     * Known native construction cadence.
     */
    private static final int CHATBOX_LINE_HEIGHT = 14;
    private static final int SPLIT_PRIVATE_LINE_HEIGHT = 13;

    private static final int BODY_GAP = 3;

    /*
     * Historical targeted font-layout probes.
     *
     * These cost nothing unless one of the exact messages is sent.
     */
    private static final String TARGET_SUFFIX =
            "the quick brown fox jumped over the lazy brown dog runetags";

    private static final String NORMAL_TARGET =
            "1" + TARGET_SUFFIX;

    private static final String BOLD_TARGET =
            "2" + TARGET_SUFFIX;

    private static final String VERDANA_TARGET =
            "3" + TARGET_SUFFIX;

    private static final long REPORT_INTERVAL_NANOS =
            5_000_000_000L;

    private enum Surface
    {
        CHATBOX,
        SPLIT_PRIVATE
    }

    private final Client client;

    /*
     * Event/call counters for the current reporting interval.
     */
    private long chatMessages;
    private long script203Calls;
    private long script4483Calls;
    private long postClientTicks;

    /*
     * Workload size.
     */
    private long referenceHitboxesProduced;
    private long referenceHitboxesVisible;

    /*
     * Performance timings.
     */
    private final TimingMetric directoryRebuild =
            new TimingMetric();

    private final TimingMetric chatProcessing =
            new TimingMetric();

    private final TimingMetric messageFormatting =
            new TimingMetric();

    private final TimingMetric fontPostClientTick =
            new TimingMetric();

    private final TimingMetric referenceLayout =
            new TimingMetric();

    private final TimingMetric referenceOverlay =
            new TimingMetric();

    private final TimingMetric localHighlightOverlay =
            new TimingMetric();

    /*
     * Optional historical regression target.
     *
     * Once armed, retained-row reconstruction of that exact target can continue
     * to be observed while subsequent chat messages arrive.
     */
    private String targetText;
    private int targetFontId = -1;

    private long reportStartedAt =
            System.nanoTime();

    public ChatLayoutDiagnostic(
            Client client)
    {
        this.client = client;
    }

    public void onChatMessage(
            ChatMessage event)
    {
        if (event == null)
        {
            return;
        }

        chatMessages++;

        final String semantic =
                normalizeSemantic(
                        event.getMessage());

        final int fontId =
                fontIdForTarget(
                        semantic);

        if (fontId == -1)
        {
            return;
        }

        targetText = semantic;
        targetFontId = fontId;

        log.debug(
                "[RuneTags][Diag] TARGET"
                        + " | selector={}"
                        + " | font={}({})"
                        + " | type={}"
                        + " | text='{}'",
                targetSelector(
                        semantic),
                fontName(
                        fontId),
                fontId,
                event.getType(),
                semantic);
    }

    /**
     * Observe native construction calls.
     *
     * No values are mutated.
     */
    public void onScriptPreFired(
            ScriptPreFired event)
    {
        if (event == null)
        {
            return;
        }

        final int scriptId =
                event.getScriptId();

        if (scriptId == CHAT_BODY_SCRIPT)
        {
            script203Calls++;
        }
        else if (scriptId == CLAN_BODY_SCRIPT)
        {
            script4483Calls++;
        }
        else
        {
            return;
        }

        /*
         * Detailed construction inspection is performed only while one of the
         * historical target messages is armed.
         *
         * Normal performance testing therefore avoids object-stack scanning.
         */
        if (targetText == null)
        {
            return;
        }

        final Object[] objectStack =
                client.getObjectStack();

        final int objectStackSize =
                client.getObjectStackSize();

        if (!stackContainsTarget(
                objectStack,
                objectStackSize,
                targetText))
        {
            return;
        }

        final int[] intStack =
                client.getIntStack();

        final int intStackSize =
                client.getIntStackSize();

        if (intStack == null
                || intStackSize < 11
                || intStackSize > intStack.length)
        {
            log.debug(
                    "[RuneTags][Diag] PRE"
                            + " | script={}"
                            + " | target={}"
                            + " | invalid-int-stack={}",
                    scriptId,
                    targetSelector(
                            targetText),
                    intStackSize);

            return;
        }

        /*
         * Shared trailing payload for Script 203 / 4483.
         */
        final int lineWidgetId =
                intStack[intStackSize - 10];

        final int parentWidgetId =
                intStack[intStackSize - 9];

        final int rightBoundary =
                intStack[intStackSize - 8];

        final int leftBoundary =
                intStack[intStackSize - 7];

        final int nativeVerticalValue =
                intStack[intStackSize - 6];

        final Widget lineWidget =
                client.getWidget(
                        lineWidgetId);

        final Surface surface =
                determineSurface(
                        scriptId,
                        parentWidgetId);

        final int nativeLineHeight =
                surface == Surface.SPLIT_PRIVATE
                        ? SPLIT_PRIVATE_LINE_HEIGHT
                        : CHATBOX_LINE_HEIGHT;

        log.debug(
                "[RuneTags][Diag] PRE"
                        + " | script={}"
                        + " | target={}"
                        + " | font={}({})"
                        + " | surface={}"
                        + " | line={}"
                        + " | parent={}"
                        + " | left={}"
                        + " | right={}"
                        + " | nativeVertical={}"
                        + " | knownLineHeight={}"
                        + " | bodyGap={}"
                        + " | lineX={}"
                        + " | originalY={}"
                        + " | relativeY={}",
                scriptId,
                targetSelector(
                        targetText),
                fontName(
                        targetFontId),
                targetFontId,
                surface,
                lineWidgetId,
                parentWidgetId,
                leftBoundary,
                rightBoundary,
                nativeVerticalValue,
                nativeLineHeight,
                BODY_GAP,
                lineWidget != null
                        ? lineWidget.getOriginalX()
                        : -1,
                lineWidget != null
                        ? lineWidget.getOriginalY()
                        : -1,
                lineWidget != null
                        ? lineWidget.getRelativeY()
                        : -1);
    }

    /**
     * Called once at PostClientTick when diagnostics are enabled.
     *
     * Reporting is interval-based so diagnostics do not flood the client log.
     */
    public void onPostClientTick()
    {
        postClientTicks++;

        final long now =
                System.nanoTime();

        if (now - reportStartedAt
                < REPORT_INTERVAL_NANOS)
        {
            return;
        }

        logPerformanceSummary();

        resetInterval();

        reportStartedAt = now;
    }

    public void recordDirectoryRebuild(
            long elapsedNanos)
    {
        directoryRebuild.add(
                elapsedNanos);
    }

    public void recordChatProcessing(
            long elapsedNanos)
    {
        chatProcessing.add(
                elapsedNanos);
    }

    public void recordMessageFormatting(
            long elapsedNanos)
    {
        messageFormatting.add(
                elapsedNanos);
    }

    public void recordFontPostClientTick(
            long elapsedNanos)
    {
        fontPostClientTick.add(
                elapsedNanos);
    }

    public void recordReferenceLayout(
            long elapsedNanos,
            int hitboxes)
    {
        referenceLayout.add(
                elapsedNanos);

        referenceHitboxesProduced +=
                Math.max(
                        0,
                        hitboxes);
    }

    public void recordReferenceOverlay(
            long elapsedNanos,
            int visibleHitboxes)
    {
        referenceOverlay.add(
                elapsedNanos);

        referenceHitboxesVisible +=
                Math.max(
                        0,
                        visibleHitboxes);
    }

    public void recordLocalHighlightOverlay(
            long elapsedNanos)
    {
        localHighlightOverlay.add(
                elapsedNanos);
    }

    private void logPerformanceSummary()
    {
        log.debug(
                "[RuneTags][Perf]"
                        + " | chat={}"
                        + " | scripts[203={},4483={}]"
                        + " | ticks={}"
                        + " | directory[count={},avgMs={},maxMs={}]"
                        + " | process[count={},avgMs={},maxMs={}]"
                        + " | format[count={},avgMs={},maxMs={}]"
                        + " | fontTick[count={},avgMs={},maxMs={}]"
                        + " | refLayout[count={},avgMs={},maxMs={},hitboxes={}]"
                        + " | refOverlay[count={},avgMs={},maxMs={},visible={}]"
                        + " | localOverlay[count={},avgMs={},maxMs={}]",
                chatMessages,
                script203Calls,
                script4483Calls,
                postClientTicks,

                directoryRebuild.count,
                directoryRebuild.averageMillis(),
                directoryRebuild.maxMillis(),

                chatProcessing.count,
                chatProcessing.averageMillis(),
                chatProcessing.maxMillis(),

                messageFormatting.count,
                messageFormatting.averageMillis(),
                messageFormatting.maxMillis(),

                fontPostClientTick.count,
                fontPostClientTick.averageMillis(),
                fontPostClientTick.maxMillis(),

                referenceLayout.count,
                referenceLayout.averageMillis(),
                referenceLayout.maxMillis(),
                referenceHitboxesProduced,

                referenceOverlay.count,
                referenceOverlay.averageMillis(),
                referenceOverlay.maxMillis(),
                referenceHitboxesVisible,

                localHighlightOverlay.count,
                localHighlightOverlay.averageMillis(),
                localHighlightOverlay.maxMillis());
    }

    private void resetInterval()
    {
        chatMessages = 0;
        script203Calls = 0;
        script4483Calls = 0;
        postClientTicks = 0;

        referenceHitboxesProduced = 0;
        referenceHitboxesVisible = 0;

        directoryRebuild.reset();
        chatProcessing.reset();
        messageFormatting.reset();
        fontPostClientTick.reset();
        referenceLayout.reset();
        referenceOverlay.reset();
        localHighlightOverlay.reset();
    }

    private Surface determineSurface(
            int scriptId,
            int parentWidgetId)
    {
        if (scriptId == CLAN_BODY_SCRIPT)
        {
            return Surface.CHATBOX;
        }

        final Widget splitPrivate =
                client.getWidget(
                        WidgetInfo.PRIVATE_CHAT_MESSAGE);

        if (splitPrivate != null
                && parentWidgetId
                == splitPrivate.getId())
        {
            return Surface.SPLIT_PRIVATE;
        }

        return Surface.CHATBOX;
    }

    private boolean stackContainsTarget(
            Object[] stack,
            int size,
            String expected)
    {
        if (stack == null
                || size <= 0
                || expected == null)
        {
            return false;
        }

        final int safeSize =
                Math.min(
                        size,
                        stack.length);

        for (int i = 0;
             i < safeSize;
             i++)
        {
            final Object value =
                    stack[i];

            if (!(value instanceof String))
            {
                continue;
            }

            final String semantic =
                    normalizeSemantic(
                            (String) value);

            if (expected.equalsIgnoreCase(
                    semantic))
            {
                return true;
            }
        }

        return false;
    }

    private int fontIdForTarget(
            String text)
    {
        if (text == null)
        {
            return -1;
        }

        if (NORMAL_TARGET.equalsIgnoreCase(
                text))
        {
            return NORMAL_FONT_ID;
        }

        if (BOLD_TARGET.equalsIgnoreCase(
                text))
        {
            return BOLD_FONT_ID;
        }

        if (VERDANA_TARGET.equalsIgnoreCase(
                text))
        {
            return VERDANA_FONT_ID;
        }

        return -1;
    }

    private int targetSelector(
            String text)
    {
        if (text == null
                || text.isEmpty()
                || fontIdForTarget(
                text) == -1)
        {
            return -1;
        }

        final char selector =
                text.charAt(
                        0);

        if (selector >= '1'
                && selector <= '3')
        {
            return selector - '0';
        }

        return -1;
    }

    private String fontName(
            int fontId)
    {
        switch (fontId)
        {
            case NORMAL_FONT_ID:
                return "Normal";

            case BOLD_FONT_ID:
                return "Bold";

            case VERDANA_FONT_ID:
                return "Verdana";

            default:
                return "Unknown";
        }
    }

    private String normalizeSemantic(
            String text)
    {
        if (text == null)
        {
            return null;
        }

        return Text.removeTags(
                        text)
                .replace(
                        '\u00A0',
                        ' ')
                .replace(
                        '\u202F',
                        ' ')
                .trim();
    }

    private static final class TimingMetric
    {
        private long count;
        private long totalNanos;
        private long maxNanos;

        private void add(
                long elapsedNanos)
        {
            if (elapsedNanos < 0)
            {
                return;
            }

            count++;
            totalNanos += elapsedNanos;

            if (elapsedNanos > maxNanos)
            {
                maxNanos = elapsedNanos;
            }
        }

        private double averageMillis()
        {
            if (count <= 0)
            {
                return 0.0;
            }

            return (totalNanos / 1_000_000.0)
                    / count;
        }

        private double maxMillis()
        {
            return maxNanos
                    / 1_000_000.0;
        }

        private void reset()
        {
            count = 0;
            totalNanos = 0;
            maxNanos = 0;
        }
    }
}