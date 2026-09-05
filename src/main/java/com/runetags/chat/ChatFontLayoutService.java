package com.runetags.chat;

import com.runetags.RuneTagsConfig;
import com.runetags.config.MentionFont;
import com.runetags.model.TaggedMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

/**
 * Coordinates native chat construction for RuneTags mention fonts.
 *
 * Responsibilities:
 *
 * PRE-CONSTRUCTION:
 *
 * - identify RuneTags messages which require a custom font;
 * - measure native/custom wrapping;
 * - adjust RuneScape's native row allocation before construction.
 *
 * END OF CLIENT TICK:
 *
 * - coalesce every row reconstruction into one final font synchronization;
 * - apply FontId only after RuneScape has completed clientscript execution.
 *
 * ChatReferenceLayoutService remains authoritative for semantic
 * message -> physical-widget ownership and actual FontId mutation.
 *
 * ---------------------------------------------------------------------
 * CONSTRUCTION PATHS
 * ---------------------------------------------------------------------
 *
 * Script 203:
 *
 * - Public chat
 * - Friends Chat
 * - normal-chatbox private messages
 * - Split Private messages
 *
 * Script 4483:
 *
 * - Clan Chat
 * - Guest Clan Chat
 *
 * Both expose the same trailing eleven-value row-construction payload.
 * 4483 simply has three additional leading values.
 *
 * Relevant values are therefore addressed relative to intStackSize:
 *
 *     size - 10 -> LINE widget ID
 *     size - 9  -> parent widget ID
 *     size - 8  -> right boundary
 *     size - 7  -> left boundary
 *     size - 6  -> native vertical/line-height input
 *
 * ---------------------------------------------------------------------
 * HEIGHT MODEL
 * ---------------------------------------------------------------------
 *
 * RuneScape first wraps using its native Plain-12 font.
 *
 * RuneTags may subsequently replace that font with Bold or Verdana.
 *
 * We calculate:
 *
 *     nativeLines
 *         number of lines Font 495 requires
 *
 *     customLines
 *         number of lines the configured RuneTags font requires
 *
 *     nativeLineHeight
 *         the value RuneScape itself supplied at PRE construction
 *
 *     desiredHeight
 *         customLines * nativeLineHeight
 *
 * Because the construction script itself multiplies its supplied vertical
 * value by its native wrapped-line count:
 *
 *     injectedValue =
 *         ceil(desiredHeight / nativeLines)
 *
 * This preserves RuneScape's native cadence automatically:
 *
 *     normal chatbox currently supplies 14
 *     Split Private currently supplies 13
 *
 * No surface-specific height is hardcoded.
 */
public class ChatFontLayoutService
{
    private static final int CHAT_BODY_SCRIPT = 203;
    private static final int CLAN_BODY_SCRIPT = 4483;

    private static final int BODY_GAP = 3;

    private final Client client;
    private final RuneTagsConfig config;
    private final TaggedMessageRepository repository;
    private final ChatReferenceLayoutService referenceLayoutService;

    /*
     * Multiple 203 / 4483 constructors may execute during one client tick.
     *
     * They all collapse into one final semantic -> physical font synchronization
     * at PostClientTick.
     */
    private boolean fontsDirty;

    /*
     * RuneTags only measures a very small fixed set of fonts.
     *
     * Resolve each FontTypeFace once rather than temporarily changing
     * CHATBOX_INPUT for every reconstructed row.
     */
    private final Map<Integer, FontTypeFace> fontCache =
            new HashMap<>();

    public ChatFontLayoutService(
            Client client,
            RuneTagsConfig config,
            TaggedMessageRepository repository,
            ChatReferenceLayoutService referenceLayoutService)
    {
        this.client = client;
        this.config = config;
        this.repository = repository;
        this.referenceLayoutService = referenceLayoutService;
    }

    /**
     * Request one final font synchronization at the end of the current client
     * tick.
     *
     * Repeated requests are intentionally coalesced into one boolean state.
     */
    public void markFontsDirty()
    {
        fontsDirty = true;
    }

    /**
     * Synchronize fonts only when native chat construction or semantic chat state
     * changed during this client tick.
     *
     * PostClientTick occurs after clientscript execution, so physical chat widgets
     * have reached their final ownership positions before RuneTags mutates them.
     */
    public void onPostClientTick()
    {
        if (!fontsDirty)
        {
            return;
        }

        /*
         * Clear first so any future construction creates a new request rather than
         * being swallowed by the current synchronization.
         */
        fontsDirty = false;

        referenceLayoutService.syncMentionFonts();
    }

    /**
     * Intercept only the native row-construction calls which we have
     * regression-tested.
     */
    public void onScriptPreFired(
            ScriptPreFired event)
    {
        if (event == null
                || !isSupportedConstructionScript(
                event.getScriptId()))
        {
            return;
        }

        /*
         * Any native chat-row reconstruction can recycle a physical Widget which
         * previously carried a RuneTags font.
         *
         * Mark the final font state dirty even when this particular row is
         * ordinary. One PostClientTick synchronization will resolve all rows after
         * the entire reconstruction sequence has completed.
         */
        markFontsDirty();

        final Object[] objectStack =
                client.getObjectStack();

        final int objectStackSize =
                client.getObjectStackSize();

        /*
         * Take one semantic repository snapshot for this entire construction
         * attempt.
         *
         * The previous implementation independently snapshotted inside findBody()
         * and findTaggedMessage().
         */
        final List<TaggedMessage> messages =
                repository.snapshot();

        if (messages.isEmpty())
        {
            return;
        }

        final String rawBody =
                findBody(
                        objectStack,
                        objectStackSize,
                        messages);

        if (rawBody == null)
        {
            return;
        }

        final String semanticBody =
                ChatText.toSemanticPlain(
                        rawBody);

        if (semanticBody == null
                || semanticBody.isEmpty())
        {
            return;
        }

        /*
         * Do not alter unrelated RuneScape messages.
         *
         * Only a TaggedMessage which RuneTags itself recognized as containing
         * a reference/local mention qualifies for mention-font treatment.
         */
        final TaggedMessage taggedMessage =
                findTaggedMessage(
                        semanticBody,
                        objectStack,
                        objectStackSize,
                        messages);

        if (taggedMessage == null
                || !hasMentionFontTreatment(
                taggedMessage))
        {
            return;
        }

        final MentionFont mentionFont =
                config.fontMentions();

        /*
         * NORMAL requires no compensation.
         *
         * RuneScape is already constructing the row with its native font and
         * native height.
         */
        if (mentionFont == null
                || mentionFont == MentionFont.NORMAL)
        {
            return;
        }

        final int[] intStack =
                client.getIntStack();

        final int intStackSize =
                client.getIntStackSize();

        /*
         * Both known construction scripts expose the same trailing
         * eleven-value payload.
         */
        if (intStack == null
                || intStackSize < 11
                || intStackSize > intStack.length)
        {
            return;
        }

        final int lineWidgetIndex =
                intStackSize - 10;

        final int rightBoundaryIndex =
                intStackSize - 8;

        final int verticalValueIndex =
                intStackSize - 6;

        final int lineWidgetId =
                intStack[lineWidgetIndex];

        final int rightBoundary =
                intStack[rightBoundaryIndex];

        /*
         * IMPORTANT:
         *
         * This is RuneScape's own native value.
         *
         * Observed examples:
         *
         *     normal chatbox = 14
         *     Split Private  = 13
         *
         * Preserve it instead of choosing a line-height ourselves.
         */
        final int nativeLineHeight =
                intStack[verticalValueIndex];

        if (nativeLineHeight <= 0)
        {
            return;
        }

        final Widget lineWidget =
                client.getWidget(
                        lineWidgetId);

        if (lineWidget == null)
        {
            return;
        }

        final FontTypeFace nativeFont =
                resolveFont(
                        FontID.PLAIN_12);

        final int selectedFontId =
                fontIdFor(
                        mentionFont);

        final FontTypeFace selectedFont =
                resolveFont(
                        selectedFontId);

        if (nativeFont == null
                || selectedFont == null)
        {
            return;
        }

        /*
         * Determine how much horizontal space RuneScape has actually left for
         * the message body.
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
            return;
        }

        final int bodyX =
                lineWidget.getOriginalX()
                        + prefix.width
                        + BODY_GAP;

        final int bodyWidth =
                rightBoundary
                        - bodyX;

        if (bodyWidth <= 0)
        {
            return;
        }

        /*
         * Reuse ChatReferenceLayoutService's existing wrapping engine.
         *
         * Do not maintain a second approximation here.
         */
        final int nativeLines =
                referenceLayoutService.measureWrappedLineCount(
                        rawBody,
                        nativeFont,
                        bodyWidth);

        final int customLines =
                referenceLayoutService.measureWrappedLineCount(
                        rawBody,
                        selectedFont,
                        bodyWidth);

        if (nativeLines <= 0
                || customLines <= 0)
        {
            return;
        }

        final int desiredHeight =
                customLines
                        * nativeLineHeight;

        final int injectedValue =
                ceilDiv(
                        desiredHeight,
                        nativeLines);

        if (injectedValue <= 0)
        {
            return;
        }

        /*
         * Mutate only the native construction argument.
         *
         * Do NOT:
         *
         * - resize the created Widget afterward;
         * - move rows manually;
         * - change Y coordinates;
         * - call revalidate here.
         *
         * RuneScape receives the correct native allocation before it constructs
         * and positions the row.
         */
        if (intStack[verticalValueIndex]
                != injectedValue)
        {
            intStack[verticalValueIndex] =
                    injectedValue;
        }
    }

    private boolean isSupportedConstructionScript(
            int scriptId)
    {
        return scriptId
                == CHAT_BODY_SCRIPT
                || scriptId
                == CLAN_BODY_SCRIPT;
    }

    /**
     * Font treatment belongs only to a message which contains a RuneTags
     * PlayerReference or matched the local player.
     *
     * This mirrors ChatReferenceLayoutService.applyMentionFont().
     */
    private boolean hasMentionFontTreatment(
            TaggedMessage message)
    {
        if (message == null)
        {
            return false;
        }

        final boolean hasPlayerReference =
                message.getReferences() != null
                        && !message.getReferences().isEmpty();

        final boolean hasLocalMention =
                message.getLocalMentionMatch() != null
                        && message.getLocalMentionMatch()
                        .isMatchesLocalPlayer();

        return hasPlayerReference
                || hasLocalMention;
    }

    /**
     * Resolve the TaggedMessage represented by this construction call.
     *
     * Newest messages are preferred because chat reconstruction is
     * newest-first and identical body text can legitimately occur multiple
     * times.
     *
     * Sender/prefix matching is used when possible to disambiguate identical
     * message bodies across players/channels.
     */
    private TaggedMessage findTaggedMessage(
            String semanticBody,
            Object[] objectStack,
            int objectStackSize,
            List<TaggedMessage> messages)
    {
        if (messages == null
                || messages.isEmpty())
        {
            return null;
        }

        TaggedMessage bodyFallback =
                null;

        for (int i = messages.size() - 1;
             i >= 0;
             i--)
        {
            final TaggedMessage message =
                    messages.get(
                            i);

            if (message == null
                    || message.getOriginalMessage() == null
                    || !message.getOriginalMessage()
                    .equals(
                            semanticBody))
            {
                continue;
            }

            if (!hasMentionFontTreatment(
                    message))
            {
                continue;
            }

            /*
             * Preserve the newest semantic body match as a fallback.
             */
            if (bodyFallback == null)
            {
                bodyFallback =
                        message;
            }

            final String sender =
                    message.getCanonicalSender();

            if (sender == null
                    || sender.isEmpty())
            {
                return message;
            }

            if (prefixContainsSender(
                    objectStack,
                    objectStackSize,
                    semanticBody,
                    sender))
            {
                return message;
            }
        }

        return bodyFallback;
    }

    private boolean prefixContainsSender(
            Object[] stack,
            int size,
            String semanticBody,
            String sender)
    {
        if (stack == null
                || size <= 0
                || sender == null
                || sender.isEmpty())
        {
            return false;
        }

        final String comparableSender =
                comparable(
                        sender);

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
                    ChatText.toSemanticPlain(
                            raw);

            if (semantic == null
                    || semantic.equals(
                    semanticBody))
            {
                continue;
            }

            final String comparablePrefix =
                    comparable(
                            semantic);

            if (comparablePrefix.contains(
                    comparableSender))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Locate the rendered body argument on the current script object stack.
     *
     * We identify it semantically by matching against RuneTags' retained
     * TaggedMessageRepository rather than by assuming a fixed object-stack
     * position.
     */
    private String findBody(
            Object[] stack,
            int size,
            List<TaggedMessage> messages)
    {
        if (stack == null
                || size <= 0
                || messages == null
                || messages.isEmpty())
        {
            return null;
        }

        final int safeSize =
                Math.min(
                        size,
                        stack.length);

        for (int i = safeSize - 1;
             i >= 0;
             i--)
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
                    ChatText.toSemanticPlain(
                            raw);

            if (semantic == null
                    || semantic.isEmpty())
            {
                continue;
            }

            for (int messageIndex = messages.size() - 1;
                 messageIndex >= 0;
                 messageIndex--)
            {
                final TaggedMessage message =
                        messages.get(
                                messageIndex);

                if (message == null
                        || message.getOriginalMessage() == null)
                {
                    continue;
                }

                if (message.getOriginalMessage()
                        .equals(
                                semantic))
                {
                    return raw;
                }
            }
        }

        return null;
    }

    /**
     * Determine the rendered prefix width immediately preceding the body.
     *
     * Script 203 supplies one complete prefix String:
     *
     *     Vaganeto:
     *     [Nerds] Vaganeto:
     *     To Player:
     *
     * Script 4483 supplies Clan/Guest-Clan components separately:
     *
     *     [Clan]
     *     Vaganeto:
     *     body
     *
     * plus the small account/channel decoration represented by its leading
     * dimensions.
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
        final List<String> components =
                findPrefixComponents(
                        objectStack,
                        objectStackSize,
                        semanticBody);

        if (components.isEmpty())
        {
            return null;
        }

        if (scriptId == CHAT_BODY_SCRIPT)
        {
            /*
             * All observed 203 calls provide the complete rendered prefix as
             * one String immediately before the body.
             *
             * Preserve markup so <img=...>, colors, timestamps, etc. can
             * contribute their real FontTypeFace width.
             */
            final String rawPrefix =
                    normalizeRawForMeasurement(
                            components.get(
                                    components.size() - 1));

            return new PrefixMeasurement(
                    nativeFont.getTextWidth(
                            rawPrefix));
        }

        if (scriptId == CLAN_BODY_SCRIPT)
        {
            /*
             * 4483 gives us multiple textual prefix components.
             */
            final StringBuilder prefixText =
                    new StringBuilder();

            for (int i = 0;
                 i < components.size();
                 i++)
            {
                if (i > 0)
                {
                    prefixText.append(
                            ' ');
                }

                prefixText.append(
                        normalizeRawForMeasurement(
                                components.get(
                                        i)));
            }

            final int textWidth =
                    nativeFont.getTextWidth(
                            prefixText.toString());

            int decorationWidth =
                    0;

            /*
             * Regression testing of 4483 consistently exposed:
             *
             *     [..., 13, 13, common payload]
             *
             * while the decoration's physical horizontal contribution was
             * 12px.
             *
             * Keep the tested adjustment isolated here instead of spreading
             * channel-specific offsets through the rest of the service.
             */
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
                    decorationWidth =
                            Math.max(
                                    0,
                                    widthCandidate - 1);
                }
            }

            return new PrefixMeasurement(
                    textWidth
                            + decorationWidth);
        }

        return null;
    }

    /**
     * Collect every String preceding the semantic body.
     */
    private List<String> findPrefixComponents(
            Object[] stack,
            int size,
            String semanticBody)
    {
        final List<String> output =
                new ArrayList<>();

        if (stack == null
                || size <= 0
                || semanticBody == null)
        {
            return output;
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
                    ChatText.toSemanticPlain(
                            raw);

            if (semantic == null)
            {
                continue;
            }

            if (semantic.equals(
                    semanticBody))
            {
                break;
            }

            output.add(
                    raw);
        }

        return output;
    }

    /**
     * Resolve an actual Jagex FontTypeFace for an arbitrary FontID.
     *
     * The chat-input widget is used only as a temporary font probe.
     * Its original FontId is immediately restored and no revalidation occurs.
     */
    private FontTypeFace resolveFont(
            int fontId)
    {
        final FontTypeFace cached =
                fontCache.get(
                        fontId);

        if (cached != null)
        {
            return cached;
        }

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

            final FontTypeFace resolved =
                    probe.getFont();

            if (resolved != null)
            {
                fontCache.put(
                        fontId,
                        resolved);
            }

            return resolved;
        }
        finally
        {
            probe.setFontId(
                    originalFontId);
        }
    }

    private int fontIdFor(
            MentionFont font)
    {
        if (font == null)
        {
            return FontID.PLAIN_12;
        }

        switch (font)
        {
            case BOLD:
                return FontID.BOLD_12;

            case VERDANA:
                return FontID.VERDANA_13_BOLD;

            case NORMAL:
            default:
                return FontID.PLAIN_12;
        }
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

    private String normalizeRawForMeasurement(
            String value)
    {
        if (value == null)
        {
            return "";
        }

        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');
    }

    private String comparable(
            String value)
    {
        if (value == null)
        {
            return "";
        }

        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .toLowerCase();
    }

    private static final class PrefixMeasurement
    {
        private final int width;

        private PrefixMeasurement(
                int width)
        {
            this.width =
                    width;
        }
    }
}