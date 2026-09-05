package com.runetags.debug;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.util.Text;

/**
 * Temporary RuneTags chat font / wrapping regression diagnostic.
 *
 * RuneTags Mention Font should remain NORMAL while testing.
 *
 * Regression targets:
 *
 * 1the quick brown fox jumped over the lazy brown dog runetags
 *      -> Normal / 495
 *
 * 2the quick brown fox jumped over the lazy brown dog runetags
 *      -> Bold / 496
 *
 * 3the quick brown fox jumped over the lazy brown dog runetags
 *      -> Verdana / 1446
 *
 * ---------------------------------------------------------------------
 * CURRENTLY IDENTIFIED CONSTRUCTION PATHS
 * ---------------------------------------------------------------------
 *
 * Script 203:
 *
 *      Public
 *      Friends Chat
 *      normal chatbox Private Messages
 *      Split Private Messages
 *
 * Script 4483:
 *
 *      Clan Chat
 *      Guest Clan Chat
 *
 * ---------------------------------------------------------------------
 * IMPORTANT SURFACE DIFFERENCE
 * ---------------------------------------------------------------------
 *
 * Ordinary chatbox:
 *
 *      parent  = Chatbox message area
 *      cadence = 14px
 *
 * Split Private:
 *
 *      parent  = PRIVATE_CHAT_MESSAGE / PM_CONTAINER
 *      cadence = 13px
 *
 * Example Split PM PRE-203:
 *
 *      lineWidget   = 10682369
 *      parentWidget = 10682368
 *      bodyX        = 74
 *      right        = 519
 *      bodyWidth    = 445
 *      nativeInput  = 13
 *
 * Therefore Split PM must NOT inherit the 14px chatbox cadence.
 *
 * ---------------------------------------------------------------------
 * HEIGHT MODEL
 * ---------------------------------------------------------------------
 *
 * Construction performs native wrapping using Font 495.
 *
 * Measure:
 *
 *      nativeLines
 *          Font 495
 *
 *      customLines
 *          selected regression font
 *
 * Desired final height:
 *
 *      customLines * surfaceLineHeight
 *
 * where:
 *
 *      chatbox      = 14
 *      split PM     = 13
 *
 * Compensate for native construction wrapping:
 *
 *      injectedValue =
 *          ceil(desiredHeight / nativeLines)
 *
 * ---------------------------------------------------------------------
 * POST CORRELATION
 * ---------------------------------------------------------------------
 *
 * Never search every chat surface by text alone.
 *
 * PRE determines which physical surface owns the construction:
 *
 *      CHATBOX
 *      SPLIT_PRIVATE
 *
 * POST searches only that same surface.
 *
 * This prevents a Split PM PRE-203 call from accidentally modifying an
 * identical copy of the message in normal chat history.
 */
@Slf4j
public class ChatLayoutDiagnostic
{
    private static final int CHAT_BODY_SCRIPT = 203;
    private static final int CLAN_BODY_SCRIPT = 4483;

    private static final int NORMAL_FONT_ID = 495;
    private static final int BOLD_FONT_ID = 496;
    private static final int VERDANA_FONT_ID = 1446;

    private static final int CHATBOX_LINE_HEIGHT = 14;
    private static final int SPLIT_PRIVATE_LINE_HEIGHT = 13;

    private static final int BODY_GAP = 3;

    private static final String TARGET_SUFFIX =
            "the quick brown fox jumped over the lazy brown dog runetags";

    private static final String NORMAL_TARGET =
            "1" + TARGET_SUFFIX;

    private static final String BOLD_TARGET =
            "2" + TARGET_SUFFIX;

    private static final String VERDANA_TARGET =
            "3" + TARGET_SUFFIX;

    private enum Surface
    {
        CHATBOX,
        SPLIT_PRIVATE
    }

    private final Client client;

    private boolean armed;

    /*
     * True only between one matching construction PRE and its exact POST.
     */
    private boolean targetConstructionInProgress;

    private int expectedConstructionScriptId = -1;

    private int reconstructionCount;

    /*
     * ------------------------------------------------------------
     * CURRENT PRE -> POST EXPECTED STATE
     * ------------------------------------------------------------
     */
    private String expectedTargetText;

    private int expectedFontId;

    private Surface expectedSurface =
            Surface.CHATBOX;

    private int expectedSurfaceLineHeight =
            CHATBOX_LINE_HEIGHT;

    private int expectedLineWidgetId = -1;
    private int expectedParentWidgetId = -1;

    private int expectedBodyX;
    private int expectedBodyWidth;

    private int expectedNativeLines;
    private int expectedCustomLines;

    private int expectedDesiredHeight;

    private int expectedInjectedValue;
    private int expectedAllocatedHeight;

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

        final String message =
                normalizeSemantic(
                        event.getMessage());

        if (message == null)
        {
            return;
        }

        final int fontId =
                fontIdForTarget(
                        message);

        if (fontId != -1)
        {
            armed = true;

            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " ========================================");

            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " REGRESSION TARGET"
                            + " | selector={}"
                            + " | font={}"
                            + " | fontId={}"
                            + " | type={}"
                            + " | text='{}'",
                    targetSelector(
                            message),
                    fontName(
                            fontId),
                    fontId,
                    event.getType(),
                    message);

            return;
        }

        if (armed)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " CHATBOX UPDATE"
                            + " | type={}"
                            + " | text='{}'",
                    event.getType(),
                    message);
        }
    }

    public void onScriptPreFired(
            ScriptPreFired event)
    {
        if (!armed
                || event == null
                || !isSupportedConstructionScript(
                event.getScriptId()))
        {
            return;
        }

        final Object[] objectStack =
                client.getObjectStack();

        final int objectStackSize =
                client.getObjectStackSize();

        final String rawBody =
                findRegressionBody(
                        objectStack,
                        objectStackSize);

        if (rawBody == null)
        {
            return;
        }

        final String semanticBody =
                normalizeSemantic(
                        rawBody);

        if (semanticBody == null)
        {
            return;
        }

        final int selectedFontId =
                fontIdForTarget(
                        semanticBody);

        if (selectedFontId == -1)
        {
            return;
        }

        final int[] intStack =
                client.getIntStack();

        final int intStackSize =
                client.getIntStackSize();

        /*
         * Both 203 and 4483 contain the same trailing eleven-value
         * construction payload.
         */
        if (intStack == null
                || intStackSize < 11
                || intStackSize > intStack.length)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE ABORTED"
                            + " | scriptId={}"
                            + " | reason=invalid-int-stack"
                            + " | intStackSize={}",
                    event.getScriptId(),
                    intStackSize);

            return;
        }

        /*
         * ------------------------------------------------------------
         * COMMON TAIL-RELATIVE PAYLOAD
         * ------------------------------------------------------------
         *
         * Script 203 size 11:
         *
         *      size - 10 = LINE
         *      size - 9  = parent
         *      size - 8  = right boundary
         *      size - 7  = left boundary
         *      size - 6  = vertical input
         *
         * Script 4483 size 14:
         *
         * same payload, shifted by three leading values.
         */
        final int lineWidgetIndex =
                intStackSize - 10;

        final int parentWidgetIndex =
                intStackSize - 9;

        final int rightBoundaryIndex =
                intStackSize - 8;

        final int leftBoundaryIndex =
                intStackSize - 7;

        final int verticalValueIndex =
                intStackSize - 6;

        final int lineWidgetId =
                intStack[lineWidgetIndex];

        final int parentWidgetId =
                intStack[parentWidgetIndex];

        final int rightBoundary =
                intStack[rightBoundaryIndex];

        final int leftBoundary =
                intStack[leftBoundaryIndex];

        final int nativeVerticalValue =
                intStack[verticalValueIndex];

        final Widget lineWidget =
                client.getWidget(
                        lineWidgetId);

        if (lineWidget == null)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE ABORTED"
                            + " | scriptId={}"
                            + " | reason=line-widget-not-found"
                            + " | lineWidgetId={}"
                            + " | parentWidgetId={}",
                    event.getScriptId(),
                    lineWidgetId,
                    parentWidgetId);

            return;
        }

        /*
         * ------------------------------------------------------------
         * DETERMINE PHYSICAL SURFACE
         * ------------------------------------------------------------
         */
        final Surface surface =
                determineSurface(
                        event.getScriptId(),
                        parentWidgetId);

        final int surfaceLineHeight =
                surface == Surface.SPLIT_PRIVATE
                        ? SPLIT_PRIVATE_LINE_HEIGHT
                        : CHATBOX_LINE_HEIGHT;

        /*
         * ------------------------------------------------------------
         * FONTS
         * ------------------------------------------------------------
         */
        final FontTypeFace nativeFont =
                resolveFont(
                        NORMAL_FONT_ID);

        final FontTypeFace selectedFont =
                resolveFont(
                        selectedFontId);

        if (nativeFont == null
                || selectedFont == null)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE ABORTED"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | reason=font-resolution",
                    event.getScriptId(),
                    surface);

            return;
        }

        /*
         * ------------------------------------------------------------
         * PREFIX
         * ------------------------------------------------------------
         */
        final PrefixMeasurement prefix =
                measurePrefix(
                        event.getScriptId(),
                        nativeFont,
                        objectStack,
                        objectStackSize,
                        semanticBody,
                        intStack,
                        intStackSize);

        if (prefix == null)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE ABORTED"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | reason=prefix-measurement",
                    event.getScriptId(),
                    surface);

            return;
        }

        final int lineX =
                lineWidget.getOriginalX();

        final int bodyX =
                lineX
                        + prefix.getWidth()
                        + BODY_GAP;

        final int bodyWidth =
                rightBoundary
                        - bodyX;

        if (bodyWidth <= 0)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE ABORTED"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | reason=invalid-body-width"
                            + " | lineX={}"
                            + " | prefixWidth={}"
                            + " | rightBoundary={}"
                            + " | bodyX={}"
                            + " | bodyWidth={}",
                    event.getScriptId(),
                    surface,
                    lineX,
                    prefix.getWidth(),
                    rightBoundary,
                    bodyX,
                    bodyWidth);

            return;
        }

        /*
         * ------------------------------------------------------------
         * WRAPPING
         * ------------------------------------------------------------
         */
        final int nativeLines =
                calculateWrappedLineCount(
                        nativeFont,
                        semanticBody,
                        bodyWidth);

        final int customLines =
                calculateWrappedLineCount(
                        selectedFont,
                        semanticBody,
                        bodyWidth);

        final int desiredHeight =
                Math.max(
                        1,
                        customLines)
                        * surfaceLineHeight;

        /*
         * Compensate for construction's own native wrapping.
         */
        final int injectedValue =
                ceilDiv(
                        desiredHeight,
                        Math.max(
                                1,
                                nativeLines));

        final int allocatedHeight =
                Math.max(
                        1,
                        nativeLines)
                        * injectedValue;

        /*
         * ------------------------------------------------------------
         * SAVE EXACT PRE -> POST STATE
         * ------------------------------------------------------------
         */
        expectedConstructionScriptId =
                event.getScriptId();

        expectedTargetText =
                semanticBody;

        expectedFontId =
                selectedFontId;

        expectedSurface =
                surface;

        expectedSurfaceLineHeight =
                surfaceLineHeight;

        expectedLineWidgetId =
                lineWidgetId;

        expectedParentWidgetId =
                parentWidgetId;

        expectedBodyX =
                bodyX;

        expectedBodyWidth =
                bodyWidth;

        expectedNativeLines =
                nativeLines;

        expectedCustomLines =
                customLines;

        expectedDesiredHeight =
                desiredHeight;

        expectedInjectedValue =
                injectedValue;

        expectedAllocatedHeight =
                allocatedHeight;

        reconstructionCount++;

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " ========================================");

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " >>> TARGET PRE"
                        + " | scriptId={}"
                        + " | reconstruction={}"
                        + " | surface={}"
                        + " | selector={}"
                        + " | font={}"
                        + " | fontId={}"
                        + " | lineWidget={}"
                        + " | parentWidget={}"
                        + " | lineOriginalY={}"
                        + " | lineRelativeY={}",
                event.getScriptId(),
                reconstructionCount,
                surface,
                targetSelector(
                        semanticBody),
                fontName(
                        selectedFontId),
                selectedFontId,
                lineWidgetId,
                parentWidgetId,
                lineWidget.getOriginalY(),
                lineWidget.getRelativeY());

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " PREFIX"
                        + " | scriptId={}"
                        + " | surface={}"
                        + " | components={}"
                        + " | textWidth={}"
                        + " | decorationWidth={}"
                        + " | totalWidth={}"
                        + " | lineX={}"
                        + " | gap={}",
                event.getScriptId(),
                surface,
                prefix.getDescription(),
                prefix.getTextWidth(),
                prefix.getDecorationWidth(),
                prefix.getWidth(),
                lineX,
                BODY_GAP);

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " GEOMETRY"
                        + " | scriptId={}"
                        + " | surface={}"
                        + " | leftBoundary={}"
                        + " | bodyX={}"
                        + " | rightBoundary={}"
                        + " | bodyWidth={}",
                event.getScriptId(),
                surface,
                leftBoundary,
                bodyX,
                rightBoundary,
                bodyWidth);

        final int nativeTextWidth =
                nativeFont.getTextWidth(
                        semanticBody);

        final int selectedTextWidth =
                selectedFont.getTextWidth(
                        semanticBody);

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " WRAP MEASUREMENT"
                        + " | surface={}"
                        + " | nativeFontId={}"
                        + " | nativeTextWidth={}"
                        + " | nativeLines={}"
                        + " | selectedFontId={}"
                        + " | selectedTextWidth={}"
                        + " | customLines={}"
                        + " | availableWidth={}",
                surface,
                NORMAL_FONT_ID,
                nativeTextWidth,
                nativeLines,
                selectedFontId,
                selectedTextWidth,
                customLines,
                bodyWidth);

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " HEIGHT CALCULATION"
                        + " | surface={}"
                        + " | surfaceLineHeight={}"
                        + " | nativeLines={}"
                        + " | customLines={}"
                        + " | desiredHeight={}"
                        + " | nativeInput={}"
                        + " | injectedValue={}"
                        + " | expectedAllocatedHeight={}",
                surface,
                surfaceLineHeight,
                nativeLines,
                customLines,
                desiredHeight,
                nativeVerticalValue,
                injectedValue,
                allocatedHeight);

        /*
         * ------------------------------------------------------------
         * PRE MUTATION
         * ------------------------------------------------------------
         *
         * Split PM example:
         *
         *      one line Normal
         *
         *      nativeInput = 13
         *      desired     = 13
         *
         * therefore it remains 13.
         *
         * We no longer incorrectly force 13 -> 14.
         */
        if (nativeVerticalValue
                != injectedValue)
        {
            intStack[verticalValueIndex] =
                    injectedValue;

            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE MUTATION"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | index={}"
                            + " | {} -> {}",
                    event.getScriptId(),
                    surface,
                    verticalValueIndex,
                    nativeVerticalValue,
                    injectedValue);
        }
        else
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " PRE MUTATION"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | index={}"
                            + " | unchanged={}",
                    event.getScriptId(),
                    surface,
                    verticalValueIndex,
                    nativeVerticalValue);
        }

        targetConstructionInProgress = true;
    }

    public void onScriptPostFired(
            ScriptPostFired event)
    {
        if (!armed
                || event == null
                || !targetConstructionInProgress
                || event.getScriptId()
                != expectedConstructionScriptId)
        {
            return;
        }

        /*
         * Consume this exact PRE -> POST pair.
         */
        targetConstructionInProgress = false;

        final Widget lineWidget =
                client.getWidget(
                        expectedLineWidgetId);

        if (lineWidget == null)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " TARGET POST ABORTED"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | reason=line-widget-disappeared"
                            + " | lineWidget={}",
                    event.getScriptId(),
                    expectedSurface,
                    expectedLineWidgetId);

            return;
        }

        /*
         * ------------------------------------------------------------
         * SURFACE-AWARE BODY CORRELATION
         * ------------------------------------------------------------
         */
        final Widget target =
                findTargetWidgetForLine(
                        expectedTargetText,
                        lineWidget,
                        expectedSurface);

        if (target == null)
        {
            log.debug(
                    "[RuneTags][ChatLayoutDiagnostic]"
                            + " TARGET POST ABORTED"
                            + " | scriptId={}"
                            + " | surface={}"
                            + " | unable to correlate body"
                            + " | lineWidget={}"
                            + " | parentWidget={}"
                            + " | lineOriginalY={}"
                            + " | lineRelativeY={}"
                            + " | selector={}",
                    event.getScriptId(),
                    expectedSurface,
                    expectedLineWidgetId,
                    expectedParentWidgetId,
                    lineWidget.getOriginalY(),
                    lineWidget.getRelativeY(),
                    targetSelector(
                            expectedTargetText));

            logSurfaceCandidates(
                    expectedSurface,
                    expectedTargetText);

            return;
        }

        final int[] intStack =
                client.getIntStack();

        final int intStackSize =
                client.getIntStackSize();

        final Integer returnedHeight =
                intStack != null
                        && intStackSize > 0
                        && intStackSize <= intStack.length
                        ? intStack[intStackSize - 1]
                        : null;

        logWidget(
                "POST CONSTRUCTION",
                expectedSurface,
                target);

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " ROW CORRELATION"
                        + " | scriptId={}"
                        + " | surface={}"
                        + " | lineWidget={}"
                        + " | parentWidget={}"
                        + " | lineOriginalY={}"
                        + " | lineRelativeY={}"
                        + " | bodyOriginalY={}"
                        + " | bodyRelativeY={}"
                        + " | selector={}",
                event.getScriptId(),
                expectedSurface,
                expectedLineWidgetId,
                expectedParentWidgetId,
                lineWidget.getOriginalY(),
                lineWidget.getRelativeY(),
                target.getOriginalY(),
                target.getRelativeY(),
                targetSelector(
                        expectedTargetText));

        final int actualBodyX =
                target.getOriginalX();

        final int actualBodyWidth =
                target.getOriginalWidth();

        final boolean xMatches =
                actualBodyX
                        == expectedBodyX;

        final boolean widthMatches =
                actualBodyWidth
                        == expectedBodyWidth;

        final boolean allocationMatches =
                target.getOriginalHeight()
                        == expectedAllocatedHeight
                        && target.getHeight()
                        == expectedAllocatedHeight;

        final boolean returnMatches =
                returnedHeight != null
                        && returnedHeight
                        == expectedAllocatedHeight;

        final boolean enoughForCustomFont =
                target.getOriginalHeight()
                        >= expectedDesiredHeight;

        final boolean constructionPass =
                xMatches
                        && widthMatches
                        && allocationMatches
                        && returnMatches
                        && enoughForCustomFont;

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " CONSTRUCTION RESULT"
                        + " | scriptId={}"
                        + " | surface={}"
                        + " | expectedBodyX={}"
                        + " | actualBodyX={}"
                        + " | bodyXDelta={}"
                        + " | expectedWidth={}"
                        + " | actualWidth={}"
                        + " | widthDelta={}"
                        + " | nativeLines={}"
                        + " | customLines={}"
                        + " | lineHeight={}"
                        + " | injectedValue={}"
                        + " | desiredHeight={}"
                        + " | expectedAllocatedHeight={}"
                        + " | actualHeight={}"
                        + " | returnedHeight={}"
                        + " | PASS={}",
                event.getScriptId(),
                expectedSurface,
                expectedBodyX,
                actualBodyX,
                actualBodyX
                        - expectedBodyX,
                expectedBodyWidth,
                actualBodyWidth,
                actualBodyWidth
                        - expectedBodyWidth,
                expectedNativeLines,
                expectedCustomLines,
                expectedSurfaceLineHeight,
                expectedInjectedValue,
                expectedDesiredHeight,
                expectedAllocatedHeight,
                target.getOriginalHeight(),
                returnedHeight,
                constructionPass);

        /*
         * ------------------------------------------------------------
         * POST FONT SETTER
         * ------------------------------------------------------------
         */
        final int beforeFontId =
                target.getFontId();

        target.setFontId(
                expectedFontId);

        /*
         * Retained during regression testing.
         */
        target.revalidate();

        final int afterFontId =
                target.getFontId();

        logWidget(
                "POST CUSTOM FONT",
                expectedSurface,
                target);

        final FontTypeFace actualFont =
                target.getFont();

        final String actualSemanticText =
                normalizeSemantic(
                        target.getText());

        final int actualTextWidth =
                actualFont != null
                        ? actualFont.getTextWidth(
                        actualSemanticText)
                        : -1;

        final int actualCustomLines =
                actualFont != null
                        ? calculateWrappedLineCount(
                        actualFont,
                        actualSemanticText,
                        target.getOriginalWidth())
                        : -1;

        final int actualRequiredHeight =
                actualCustomLines > 0
                        ? actualCustomLines
                        * expectedSurfaceLineHeight
                        : -1;

        final boolean fontMatches =
                afterFontId
                        == expectedFontId;

        final boolean finalHeightEnough =
                actualRequiredHeight > 0
                        && target.getOriginalHeight()
                        >= actualRequiredHeight;

        final boolean finalPass =
                fontMatches
                        && actualCustomLines > 0
                        && finalHeightEnough;

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " FINAL RESULT"
                        + " | scriptId={}"
                        + " | surface={}"
                        + " | selector={}"
                        + " | font={}"
                        + " | beforeFontId={}"
                        + " | expectedFontId={}"
                        + " | afterFontId={}"
                        + " | textWidth={}"
                        + " | actualAvailableWidth={}"
                        + " | predictedLines={}"
                        + " | actualLines={}"
                        + " | lineHeight={}"
                        + " | predictedHeight={}"
                        + " | actualRequiredHeight={}"
                        + " | actualHeight={}"
                        + " | geometryPass={}"
                        + " | PASS={}",
                event.getScriptId(),
                expectedSurface,
                targetSelector(
                        expectedTargetText),
                fontName(
                        expectedFontId),
                beforeFontId,
                expectedFontId,
                afterFontId,
                actualTextWidth,
                target.getOriginalWidth(),
                expectedCustomLines,
                actualCustomLines,
                expectedSurfaceLineHeight,
                expectedDesiredHeight,
                actualRequiredHeight,
                target.getOriginalHeight(),
                constructionPass,
                finalPass);

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " ========================================");
    }

    /*
     * =================================================================
     * SURFACE IDENTIFICATION
     * =================================================================
     */

    private Surface determineSurface(
            int scriptId,
            int parentWidgetId)
    {
        /*
         * 4483 is always normal chatbox presentation.
         */
        if (scriptId == CLAN_BODY_SCRIPT)
        {
            return Surface.CHATBOX;
        }

        /*
         * Script 203 can construct both normal chat and Split PM.
         *
         * Compare the PRE parent ID against RuneLite's Split Private root.
         */
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

    /*
     * =================================================================
     * PREFIX MEASUREMENT
     * =================================================================
     */

    private PrefixMeasurement measurePrefix(
            int scriptId,
            FontTypeFace nativeFont,
            Object[] objectStack,
            int objectStackSize,
            String semanticBody,
            int[] intStack,
            int intStackSize)
    {
        final List<String> rawPrefixComponents =
                findPrefixComponents(
                        objectStack,
                        objectStackSize,
                        semanticBody);

        if (rawPrefixComponents.isEmpty())
        {
            return null;
        }

        /*
         * ------------------------------------------------------------
         * SCRIPT 203
         * ------------------------------------------------------------
         *
         * Public / FC / Private / Split Private normally provide one
         * complete sender/prefix string before the body.
         *
         * Examples:
         *
         *      Vaganeto:
         *      [Nerds] Vaganeto:
         *      To BESTCASE:
         */
        if (scriptId == CHAT_BODY_SCRIPT)
        {
            final String rawPrefix =
                    normalizeRawForMeasurement(
                            rawPrefixComponents.get(
                                    0));

            final int width =
                    nativeFont.getTextWidth(
                            rawPrefix);

            return new PrefixMeasurement(
                    width,
                    0,
                    width,
                    describePrefixComponents(
                            rawPrefixComponents));
        }

        /*
         * ------------------------------------------------------------
         * SCRIPT 4483
         * ------------------------------------------------------------
         *
         * Clan / Guest Clan split the displayed prefix across multiple
         * objects:
         *
         *      [clan]
         *      username:
         *      body
         *
         * The observed 13x13 decoration input produces a physical width
         * of 12px in our tests, so use widthCandidate - 1.
         */
        if (scriptId == CLAN_BODY_SCRIPT)
        {
            final StringBuilder prefixText =
                    new StringBuilder();

            for (int i = 0;
                 i < rawPrefixComponents.size();
                 i++)
            {
                if (i > 0)
                {
                    prefixText.append(
                            ' ');
                }

                prefixText.append(
                        normalizeRawForMeasurement(
                                rawPrefixComponents.get(
                                        i)));
            }

            final int textWidth =
                    nativeFont.getTextWidth(
                            prefixText.toString());

            int decorationWidth = 0;

            final int commonPayloadStart =
                    intStackSize - 11;

            if (commonPayloadStart >= 3)
            {
                final int widthCandidate =
                        intStack[1];

                final int heightCandidate =
                        intStack[2];

                if (widthCandidate > 0
                        && widthCandidate <= 32
                        && heightCandidate > 0
                        && heightCandidate <= 32)
                {
                    /*
                     * Observed:
                     *
                     * logical candidate = 13
                     * physical contribution = 12
                     */
                    decorationWidth =
                            Math.max(
                                    0,
                                    widthCandidate - 1);
                }
            }

            final int totalWidth =
                    textWidth
                            + decorationWidth;

            return new PrefixMeasurement(
                    textWidth,
                    decorationWidth,
                    totalWidth,
                    describePrefixComponents(
                            rawPrefixComponents));
        }

        return null;
    }

    private List<String> findPrefixComponents(
            Object[] stack,
            int size,
            String semanticBody)
    {
        final List<String> result =
                new ArrayList<>();

        if (stack == null
                || size <= 0
                || semanticBody == null)
        {
            return result;
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

            final String raw =
                    (String) value;

            final String semantic =
                    normalizeSemantic(
                            raw);

            if (semantic == null)
            {
                continue;
            }

            if (semantic.equalsIgnoreCase(
                    semanticBody))
            {
                break;
            }

            result.add(
                    raw);
        }

        return result;
    }

    private String describePrefixComponents(
            List<String> components)
    {
        if (components == null
                || components.isEmpty())
        {
            return "[]";
        }

        final StringBuilder result =
                new StringBuilder(
                        "[");

        for (int i = 0;
             i < components.size();
             i++)
        {
            if (i > 0)
            {
                result.append(
                        ", ");
            }

            result.append('\'')
                    .append(
                            normalizeSemantic(
                                    components.get(
                                            i)))
                    .append('\'');
        }

        result.append(
                ']');

        return result.toString();
    }

    private static final class PrefixMeasurement
    {
        private final int textWidth;
        private final int decorationWidth;
        private final int width;
        private final String description;

        private PrefixMeasurement(
                int textWidth,
                int decorationWidth,
                int width,
                String description)
        {
            this.textWidth =
                    textWidth;

            this.decorationWidth =
                    decorationWidth;

            this.width =
                    width;

            this.description =
                    description;
        }

        private int getTextWidth()
        {
            return textWidth;
        }

        private int getDecorationWidth()
        {
            return decorationWidth;
        }

        private int getWidth()
        {
            return width;
        }

        private String getDescription()
        {
            return description;
        }
    }

    /*
     * =================================================================
     * EXACT SURFACE / ROW CORRELATION
     * =================================================================
     */

    private Widget findTargetWidgetForLine(
            String targetText,
            Widget lineWidget,
            Surface surface)
    {
        if (targetText == null
                || lineWidget == null
                || surface == null)
        {
            return null;
        }

        final List<Widget> matches =
                findAllTargetWidgets(
                        targetText,
                        surface);

        if (matches.isEmpty())
        {
            return null;
        }

        final int lineOriginalY =
                lineWidget.getOriginalY();

        final int lineRelativeY =
                lineWidget.getRelativeY();

        /*
         * Strongest match:
         *
         * both physical Y representations agree.
         */
        for (Widget widget : matches)
        {
            if (widget.getOriginalY()
                    == lineOriginalY
                    && widget.getRelativeY()
                    == lineRelativeY)
            {
                return widget;
            }
        }

        /*
         * OriginalY-only fallback when unique.
         */
        Widget originalYMatch = null;

        int originalYMatches = 0;

        for (Widget widget : matches)
        {
            if (widget.getOriginalY()
                    == lineOriginalY)
            {
                originalYMatch =
                        widget;

                originalYMatches++;
            }
        }

        if (originalYMatches == 1)
        {
            return originalYMatch;
        }

        /*
         * RelativeY-only fallback when unique.
         */
        Widget relativeYMatch = null;

        int relativeYMatches = 0;

        for (Widget widget : matches)
        {
            if (widget.getRelativeY()
                    == lineRelativeY)
            {
                relativeYMatch =
                        widget;

                relativeYMatches++;
            }
        }

        if (relativeYMatches == 1)
        {
            return relativeYMatch;
        }

        /*
         * Because we are now already restricted to one physical surface,
         * a single remaining text match is safe.
         */
        if (matches.size() == 1)
        {
            return matches.get(
                    0);
        }

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " AMBIGUOUS TARGET"
                        + " | surface={}"
                        + " | selector={}"
                        + " | candidates={}"
                        + " | lineOriginalY={}"
                        + " | lineRelativeY={}",
                surface,
                targetSelector(
                        targetText),
                matches.size(),
                lineOriginalY,
                lineRelativeY);

        return null;
    }

    private List<Widget> findAllTargetWidgets(
            String target,
            Surface surface)
    {
        final List<Widget> matches =
                new ArrayList<>();

        if (target == null
                || surface == null)
        {
            return matches;
        }

        final Widget root;

        if (surface == Surface.SPLIT_PRIVATE)
        {
            root =
                    client.getWidget(
                            WidgetInfo.PRIVATE_CHAT_MESSAGE);
        }
        else
        {
            root =
                    client.getWidget(
                            WidgetInfo.CHATBOX_MESSAGE_LINES);
        }

        if (root == null)
        {
            return matches;
        }

        /*
         * Root itself can theoretically carry text, so inspect it too.
         */
        collectTargetWidget(
                matches,
                root,
                target);

        collectTargetWidgets(
                matches,
                root.getDynamicChildren(),
                target);

        collectTargetWidgets(
                matches,
                root.getStaticChildren(),
                target);

        collectTargetWidgets(
                matches,
                root.getNestedChildren(),
                target);

        return matches;
    }

    private void collectTargetWidgets(
            List<Widget> matches,
            Widget[] children,
            String targetText)
    {
        if (matches == null
                || children == null
                || targetText == null)
        {
            return;
        }

        for (Widget widget : children)
        {
            collectTargetWidget(
                    matches,
                    widget,
                    targetText);
        }
    }

    private void collectTargetWidget(
            List<Widget> matches,
            Widget widget,
            String targetText)
    {
        if (matches == null
                || widget == null
                || targetText == null)
        {
            return;
        }

        final String semantic =
                normalizeSemantic(
                        widget.getText());

        if (semantic == null
                || !semantic.equalsIgnoreCase(
                targetText))
        {
            return;
        }

        if (!matches.contains(
                widget))
        {
            matches.add(
                    widget);
        }
    }

    private void logSurfaceCandidates(
            Surface surface,
            String targetText)
    {
        final List<Widget> matches =
                findAllTargetWidgets(
                        targetText,
                        surface);

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic]"
                        + " SURFACE CANDIDATES"
                        + " | surface={}"
                        + " | selector={}"
                        + " | count={}",
                surface,
                targetSelector(
                        targetText),
                matches.size());

        for (int i = 0;
             i < matches.size();
             i++)
        {
            final Widget widget =
                    matches.get(
                            i);

            logWidget(
                    "CANDIDATE " + i,
                    surface,
                    widget);
        }
    }

    /*
     * =================================================================
     * TARGET / FONT HELPERS
     * =================================================================
     */

    private boolean isSupportedConstructionScript(
            int scriptId)
    {
        return scriptId
                == CHAT_BODY_SCRIPT
                || scriptId
                == CLAN_BODY_SCRIPT;
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
            return selector
                    - '0';
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

    private FontTypeFace resolveFont(
            int fontId)
    {
        final Widget probe =
                client.getWidget(
                        WidgetInfo.CHATBOX_INPUT);

        if (probe == null)
        {
            return null;
        }

        final int originalFontId =
                probe.getFontId();

        try
        {
            probe.setFontId(
                    fontId);

            return probe.getFont();
        }
        finally
        {
            probe.setFontId(
                    originalFontId);
        }
    }

    /*
     * =================================================================
     * SCRIPT STACK HELPERS
     * =================================================================
     */

    private String findRegressionBody(
            Object[] stack,
            int size)
    {
        if (stack == null
                || size <= 0)
        {
            return null;
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

            final String raw =
                    (String) value;

            final String semantic =
                    normalizeSemantic(
                            raw);

            if (semantic != null
                    && fontIdForTarget(
                    semantic) != -1)
            {
                return raw;
            }
        }

        return null;
    }

    private String normalizeRawForMeasurement(
            String text)
    {
        if (text == null)
        {
            return "";
        }

        /*
         * Preserve RuneScape markup.
         */
        return text
                .replace(
                        '\u00A0',
                        ' ')
                .replace(
                        '\u202F',
                        ' ');
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

    /*
     * =================================================================
     * WRAPPING
     * =================================================================
     */

    private int calculateWrappedLineCount(
            FontTypeFace font,
            String text,
            int maxWidth)
    {
        if (font == null
                || maxWidth <= 0
                || text == null
                || text.isEmpty())
        {
            return 1;
        }

        final String normalized =
                text
                        .replace(
                                "\r\n",
                                "\n")
                        .replace(
                                '\r',
                                '\n');

        final String[] explicitLines =
                normalized.split(
                        "\n",
                        -1);

        int totalLines = 0;

        for (String explicitLine
                : explicitLines)
        {
            totalLines +=
                    calculateSingleParagraphLines(
                            font,
                            explicitLine,
                            maxWidth);
        }

        return Math.max(
                1,
                totalLines);
    }

    private int calculateSingleParagraphLines(
            FontTypeFace font,
            String text,
            int maxWidth)
    {
        if (text == null
                || text.isEmpty())
        {
            return 1;
        }

        if (font.getTextWidth(
                text) <= maxWidth)
        {
            return 1;
        }

        final String trimmed =
                text.trim();

        if (trimmed.isEmpty())
        {
            return 1;
        }

        final String[] words =
                trimmed.split(
                        "\\s+");

        int lines = 1;

        String currentLine = "";

        for (String word : words)
        {
            if (word.isEmpty())
            {
                continue;
            }

            /*
             * Handle a single token wider than the available body.
             */
            if (font.getTextWidth(
                    word) > maxWidth)
            {
                if (!currentLine.isEmpty())
                {
                    lines++;

                    currentLine = "";
                }

                final StringBuilder segment =
                        new StringBuilder();

                for (int i = 0;
                     i < word.length();
                     i++)
                {
                    final char ch =
                            word.charAt(
                                    i);

                    final String candidate =
                            segment.toString()
                                    + ch;

                    if (segment.length() > 0
                            && font.getTextWidth(
                            candidate) > maxWidth)
                    {
                        lines++;

                        segment.setLength(
                                0);
                    }

                    segment.append(
                            ch);
                }

                currentLine =
                        segment.toString();

                continue;
            }

            if (currentLine.isEmpty())
            {
                currentLine =
                        word;

                continue;
            }

            final String candidate =
                    currentLine
                            + " "
                            + word;

            if (font.getTextWidth(
                    candidate) <= maxWidth)
            {
                currentLine =
                        candidate;
            }
            else
            {
                lines++;

                currentLine =
                        word;
            }
        }

        return Math.max(
                1,
                lines);
    }

    private int ceilDiv(
            int numerator,
            int denominator)
    {
        if (denominator <= 0)
        {
            return numerator;
        }

        return (numerator
                + denominator
                - 1)
                / denominator;
    }

    /*
     * =================================================================
     * LOGGING
     * =================================================================
     */

    private void logWidget(
            String phase,
            Surface surface,
            Widget widget)
    {
        if (widget == null)
        {
            return;
        }

        final Rectangle bounds =
                widget.getBounds();

        log.debug(
                "[RuneTags][ChatLayoutDiagnostic] {}"
                        + " | surface={}"
                        + " | id={}"
                        + " | parentId={}"
                        + " | fontId={}"
                        + " | lineHeight={}"
                        + " | original=[x={}, y={}, w={}, h={}]"
                        + " | relative=[x={}, y={}]"
                        + " | right={}"
                        + " | calculated=[w={}, h={}]"
                        + " | bounds={}"
                        + " | text='{}'",
                phase,
                surface,
                widget.getId(),
                widget.getParentId(),
                widget.getFontId(),
                widget.getLineHeight(),
                widget.getOriginalX(),
                widget.getOriginalY(),
                widget.getOriginalWidth(),
                widget.getOriginalHeight(),
                widget.getRelativeX(),
                widget.getRelativeY(),
                widget.getOriginalX()
                        + widget.getOriginalWidth(),
                widget.getWidth(),
                widget.getHeight(),
                bounds,
                normalizeSemantic(
                        widget.getText()));
    }
}