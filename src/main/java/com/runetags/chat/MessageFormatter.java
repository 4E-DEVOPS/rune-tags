package com.runetags.chat;

import com.runetags.RuneTagsConfig;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.model.LocalMentionMatch;
import com.runetags.model.MatchReason;
import com.runetags.model.PlayerReference;
import com.runetags.model.ReferenceType;
import com.runetags.model.TaggedMessage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.runelite.client.chat.ChatColorType;
import net.runelite.client.util.ColorUtil;

/**
 * Applies RuneTags semantic foreground styling while preserving the sender's
 * original text and existing RuneLite/Jagex markup.
 *
 * Foreground styling:
 *
 * - local account references        -> Self Mention Color
 * - Unique Highlights               -> Self Mention Color
 * - normalized self-name matches    -> Self Mention Color
 * - other player references         -> Other Mention Color
 *
 * Whole-message highlighting is NOT performed here. That is handled
 * independently by ChatMessageHighlightOverlay as a translucent background.
 */
public class MessageFormatter
{
    private final RuneTagsConfig config;
    private final LocalMentionMatcher localMentionMatcher;

    public MessageFormatter(
            RuneTagsConfig config,
            LocalMentionMatcher localMentionMatcher)
    {
        this.config = config;
        this.localMentionMatcher = localMentionMatcher;
    }

    public String format(
            TaggedMessage message,
            String rawMessage,
            String localPlayerName)
    {
        if (message == null || rawMessage == null)
        {
            return rawMessage;
        }

        final MessageMarkupMap markupMap =
                MessageMarkupMap.create(rawMessage);

        if (!markupMap.matchesPlain(message.getOriginalMessage()))
        {
            /*
             * Never risk corrupting a message whose semantic/plain mapping no
             * longer matches the raw RuneLite message.
             */
            return rawMessage;
        }

        /*
         * A local match can come from:
         *
         * - the local player's account name
         * - a normalized account-name form
         * - a Unique Mention
         *
         * If Mention Whole Message is enabled, the entire message foreground
         * uses Self Mention Color.
         */
        final boolean mentionWholeMessage =
                config.mentionWholeMessage()
                        && config.mentionSelf()
                        && message.getLocalMentionMatch().isMatchesLocalPlayer();

        final String wholeMessageColorTag =
                mentionWholeMessage
                        ? ColorUtil.colorTag(config.selfMentionColor())
                        : null;

        final List<StyleSpan> spans = new ArrayList<>();

        /*
         * Player references.
         *
         * These remain independently styled even when the whole message has a
         * self color. This is important for messages such as:
         *
         * "test says @Mielu is here"
         *
         * where the whole message may be red, but @Mielu should still use the
         * Others Mention Color and then restore back to red afterward.
         */
        for (PlayerReference reference : message.getReferences())
        {
            final LocalMentionMatch localMatch =
                    localMentionMatcher.match(
                            reference,
                            localPlayerName);

            final boolean isSelf =
                    localMatch.isMatchesLocalPlayer();

            final boolean unresolvedTag =
                    reference.getType() == ReferenceType.TAG
                            && !reference.isLocallyResolved();

            final boolean shouldColor =
                    isSelf
                            ? config.mentionSelf()
                            : config.mentionOthers();

            final boolean underline =
                    unresolvedTag
                            || (config.underlineMentions()
                            && shouldColor);

            if (!shouldColor && !underline)
            {
                continue;
            }

            spans.add(
                    new StyleSpan(
                            reference.getStartOffset(),
                            reference.getEndOffset(),
                            shouldColor
                                    ? (isSelf
                                    ? config.selfMentionColor()
                                    : config.otherMentionColor())
                                    : null,
                            underline,
                            config.shadowMentions()
                                    ? config.shadowMentionColor()
                                    : null));
        }

        /*
         * Add message-level local spans.
         *
         * This handles Unique Mentions and normalized local-account-name matches
         * which may not exist as PlayerReference objects.
         *
         * Example:
         *
         * Unique Mentions = test
         *
         * "I'm gonna test this"
         *
         * Mention Whole Message ON:
         *     entire message = Self Mention Color
         *     "test" may still receive mention decoration such as underline
         *
         * Mention Whole Message OFF:
         *     only "test" = Self Mention Color
         */
        addLocalMessageHighlightSpans(
                message,
                spans);

        /*
         * Right-to-left insertion keeps the original semantic/raw offsets valid.
         */
        spans.sort(
                Comparator.comparingInt(StyleSpan::getStartOffset)
                        .reversed());

        String formatted = rawMessage;

        for (StyleSpan span : spans)
        {
            final int rawStart =
                    markupMap.rawBoundary(
                            span.getStartOffset());

            final int rawEnd =
                    markupMap.rawBoundary(
                            span.getEndOffset());

            if (rawStart < 0
                    || rawEnd < rawStart
                    || rawEnd > formatted.length())
            {
                continue;
            }

            /*
             * If the complete message is using Self Mention Color, temporarily
             * styled spans must restore that color afterward.
             *
             * Otherwise restore whatever RuneLite/Jagex color was active before
             * the span.
             */
            final String restoreColor =
                    wholeMessageColorTag != null
                            ? wholeMessageColorTag
                            : getLastColor(
                            formatted.substring(
                                    0,
                                    rawStart));

            final String openingColor =
                    span.getColor() != null
                            ? ColorUtil.colorTag(
                            span.getColor())
                            : "";

            final String closingColor =
                    span.getColor() != null
                            ? restoreColor
                            : "";

            final String openingUnderline =
                    span.isUnderline()
                            ? "<u>"
                            : "";

            final String closingUnderline =
                    span.isUnderline()
                            ? "</u>"
                            : "";

            final String openingShadow =
                    span.getShadowColor() != null
                            ? "<shad="
                            + String.format(
                            "%06x",
                            span.getShadowColor().getRGB() & 0xFFFFFF)
                            + ">"
                            : "";

            final String closingShadow =
                    span.getShadowColor() != null
                            ? "</shad>"
                            : "";

            formatted =
                    formatted.substring(0, rawStart)
                            + openingColor
                            + openingShadow
                            + openingUnderline
                            + formatted.substring(
                            rawStart,
                            rawEnd)
                            + closingUnderline
                            + closingShadow
                            + closingColor
                            + formatted.substring(rawEnd);
        }

        /*
         * Apply the complete-message foreground color last.
         *
         * Individual span colors inserted above still override it locally, then
         * restore back to Self Mention Color.
         */
        if (wholeMessageColorTag != null)
        {
            formatted =
                    wholeMessageColorTag
                            + formatted
                            + ColorUtil.CLOSING_COLOR_TAG;
        }

        return formatted;
    }

    /**
     * Add non-player local-highlight spans.
     *
     * These spans affect foreground presentation only. They deliberately do
     * not create PlayerReference objects, click targets, profile identities,
     * or alias associations.
     */
    private void addLocalMessageHighlightSpans(
            TaggedMessage message,
            List<StyleSpan> spans)
    {
        if (!config.mentionSelf())
        {
            return;
        }

        final LocalMentionMatch localMatch =
                message.getLocalMentionMatch();

        if (localMatch == null
                || !localMatch.isMatchesLocalPlayer())
        {
            return;
        }

        /*
         * ACCOUNT_NAME normally came from an existing PlayerReference and is
         * already represented above.
         *
         * Message-level spans are needed for normalized account variants and
         * Unique Highlights.
         */
        if (localMatch.getReason()
                != MatchReason.NORMALIZED_ACCOUNT_NAME
                && localMatch.getReason()
                != MatchReason.UNIQUE_HIGHLIGHT)
        {
            return;
        }

        final String token =
                localMatch.getMatchedToken();

        if (token == null
                || token.trim().isEmpty())
        {
            return;
        }

        final String messageText =
                message.getOriginalMessage();

        if (messageText == null
                || messageText.isEmpty())
        {
            return;
        }

        final String loweredMessage =
                messageText.toLowerCase(
                        Locale.ROOT);

        final String loweredToken =
                token.toLowerCase(
                        Locale.ROOT);

        int from = 0;

        while (from <= loweredMessage.length()
                - loweredToken.length())
        {
            final int start =
                    loweredMessage.indexOf(
                            loweredToken,
                            from);

            if (start < 0)
            {
                break;
            }

            final int end =
                    start + loweredToken.length();

            if (hasBoundaries(
                    loweredMessage,
                    start,
                    end)
                    && !overlapsExistingSpan(
                    start,
                    end,
                    spans))
            {
                spans.add(
                        new StyleSpan(
                                start,
                                end,
                                config.selfMentionColor(),
                                config.underlineMentions(),
                                config.shadowMentions()
                                        ? config.shadowMentionColor()
                                        : null));
            }

            from = start + 1;
        }
    }

    private static boolean overlapsExistingSpan(
            int start,
            int end,
            List<StyleSpan> spans)
    {
        for (StyleSpan span : spans)
        {
            if (start < span.getEndOffset()
                    && end > span.getStartOffset())
            {
                return true;
            }
        }

        return false;
    }

    private static boolean hasBoundaries(
            String text,
            int start,
            int end)
    {
        final boolean leftBoundary =
                start == 0
                        || !isNameChar(
                        text.charAt(start - 1));

        final boolean rightBoundary =
                end == text.length()
                        || !isNameChar(
                        text.charAt(end));

        return leftBoundary
                && rightBoundary;
    }

    private static boolean isNameChar(char c)
    {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '-';
    }

    /**
     * Determine which chat color was active before RuneTags temporarily
     * changed the foreground of a span.
     */
    private static String getLastColor(
            String text)
    {
        if (text == null || text.isEmpty())
        {
            return normalColorTag();
        }

        final int colorStart =
                text.lastIndexOf("<col=");

        final int colorEnd =
                text.lastIndexOf("</col>");

        if (colorEnd > colorStart)
        {
            return normalColorTag();
        }

        if (colorStart < 0)
        {
            return normalColorTag();
        }

        final int tagEnd =
                text.indexOf(
                        '>',
                        colorStart);

        if (tagEnd < 0)
        {
            return normalColorTag();
        }

        return text.substring(
                colorStart,
                tagEnd + 1);
    }

    private static String normalColorTag()
    {
        return "<col"
                + ChatColorType.NORMAL
                + ">";
    }

    /**
     * Internal formatting span only.
     *
     * This is deliberately NOT a PlayerReference.
     */
    private static final class StyleSpan
    {
        private final int startOffset;
        private final int endOffset;
        private final Color color;
        private final boolean underline;
        private final Color shadowColor;

        private StyleSpan(
                int startOffset,
                int endOffset,
                Color color,
                boolean underline,
                Color shadowColor)
        {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.color = color;
            this.underline = underline;
            this.shadowColor = shadowColor;
        }

        private int getStartOffset()
        {
            return startOffset;
        }

        private int getEndOffset()
        {
            return endOffset;
        }

        private Color getColor()
        {
            return color;
        }

        private boolean isUnderline()
        {
            return underline;
        }

        private Color getShadowColor()
        {
            return shadowColor;
        }
    }
}