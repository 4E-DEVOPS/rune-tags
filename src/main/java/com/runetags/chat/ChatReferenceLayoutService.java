package com.runetags.chat;

import com.runetags.RuneTagsConfig;
import com.runetags.config.MentionFont;
import com.runetags.model.PlayerReference;
import com.runetags.model.ReferenceType;
import com.runetags.model.TaggedMessage;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetTextAlignment;

/**
 * Maps semantic PlayerReference spans onto the actual rendered chat widgets.
 *
 * RuneScape can render chat references on two physically separate surfaces:
 *
 * CHATBOX
 * - normal chat
 * - private chat when Split Private Chat is disabled
 * - private-chat history while the Private chat tab is selected
 *
 * SPLIT_PRIVATE
 * - the floating private-chat rows rendered through the separate PmChat
 *   interface when Split Private Chat is enabled
 *
 * A single TaggedMessage may legitimately be rendered on both surfaces at
 * the same time. RuneTags therefore treats each surface independently and
 * creates hitboxes for every currently rendered physical representation.
 *
 * The service uses each widget's own Jagex FontTypeFace for width
 * measurements so markup and chat-font widths remain aligned with the
 * game renderer.
 */
public class ChatReferenceLayoutService
{
    /**
     * Physical surface on which RuneScape rendered a chat reference.
     *
     * This is deliberately separate from ChatMessageType.
     *
     * For example, PRIVATECHAT may render:
     *
     * - on CHATBOX,
     * - on SPLIT_PRIVATE,
     * - or on both simultaneously.
     */
    public enum Surface
    {
        CHATBOX,
        SPLIT_PRIVATE
    }

    private static final int MAX_WIDGET_DEPTH = 4;

    /**
     * One physical line produced by RuneScape's wrapped text widget.
     *
     * start/end are semantic plain-text offsets into the complete
     * widget text. Width is the rendered width of this individual
     * visual line.
     */
    private static final class WrappedLine
    {
        private final int start;
        private final int end;
        private final int width;

        private WrappedLine(
                int start,
                int end,
                int width)
        {
            this.start = start;
            this.end = end;
            this.width = width;
        }
    }

    private final Client client;
    private final RuneTagsConfig config;
    private final TaggedMessageRepository repository;

    private final Map<Widget, Integer> originalFontIds = new IdentityHashMap<>();

    public ChatReferenceLayoutService(
            Client client,
            RuneTagsConfig config,
            TaggedMessageRepository repository)
    {
        this.client = client;
        this.config = config;
        this.repository = repository;
    }

    /**
     * Synchronize RuneTags mention fonts against the final physical chat widgets
     * after RuneScape has completed its clientscript reconstruction for the
     * current client tick.
     *
     * Font mutation deliberately does not occur from the render overlays.
     *
     * One synchronization pass:
     *
     * - restores any physical Widget RuneTags previously modified;
     * - resolves current semantic-message -> physical-widget ownership;
     * - reapplies the configured font only to messages which currently own those
     *   final rendered Widgets.
     *
     * Because restoration and reapplication occur synchronously in one
     * PostClientTick callback, RuneScape cannot render the temporary native-font
     * state between those operations.
     */
    public void syncMentionFonts()
    {
        /*
         * Physical Widget instances are recycled by RuneScape.
         *
         * Always begin a reconstruction synchronization from native state so a
         * custom font can never follow a recycled Widget into another message.
         */
        restoreAllOriginalFonts();

        final MentionFont mentionFont =
                config.fontMentions();

        /*
         * NORMAL requires only restoration.
         *
         * Avoid traversing the rendered chat entirely when there is no custom font
         * to apply.
         */
        if (mentionFont == null
                || mentionFont == MentionFont.NORMAL)
        {
            return;
        }

        final List<TaggedMessage> messages =
                new ArrayList<>(
                        repository.snapshot());

        Collections.reverse(
                messages);

        /*
         * Resolve both physical surfaces independently because one private message
         * may legitimately be rendered in both locations.
         */
        syncSurfaceFonts(
                client.getWidget(
                        WidgetInfo.PRIVATE_CHAT_MESSAGE),
                Surface.SPLIT_PRIVATE,
                messages);

        syncSurfaceFonts(
                client.getWidget(
                        WidgetInfo.CHATBOX_MESSAGE_LINES),
                Surface.CHATBOX,
                messages);
    }

    /**
     * Restore every physical chat font currently owned by RuneTags.
     *
     * Used when the plugin is shutting down so RuneScape is never left displaying
     * a RuneTags FontId after the plugin has been disabled.
     */
    public void restoreMentionFonts()
    {
        restoreAllOriginalFonts();
    }

    /**
     * Resolve semantic message ownership for one physical surface and apply only
     * font treatment.
     *
     * This intentionally does not calculate reference hitboxes or sender geometry.
     * Those remain render-time responsibilities.
     */
    private void syncSurfaceFonts(
            Widget surfaceWidget,
            Surface surface,
            List<TaggedMessage> messages)
    {
        if (surfaceWidget == null
                || surfaceWidget.isHidden()
                || messages == null
                || messages.isEmpty())
        {
            return;
        }

        final List<Widget> textWidgets =
                new ArrayList<>();

        final Set<Widget> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        collectTextWidgets(
                surfaceWidget,
                textWidgets,
                visited,
                0);

        if (textWidgets.isEmpty())
        {
            return;
        }

        /*
         * Preserve the exact same per-surface semantic ownership rules used by the
         * hitbox layout.
         *
         * This is important for duplicate and short messages.
         */
        final Set<Widget> usedWidgets =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        for (TaggedMessage message : messages)
        {
            if (message == null)
            {
                continue;
            }

            if (surface == Surface.SPLIT_PRIVATE
                    && !isPrivateMessage(
                    message))
            {
                continue;
            }

            final Widget messageWidget =
                    findWidgetForMessage(
                            message,
                            textWidgets,
                            usedWidgets);

            if (messageWidget == null)
            {
                continue;
            }

            usedWidgets.add(
                    messageWidget);

            applyMentionFont(
                    messageWidget,
                    message);
        }
    }

    /**
     * Rebuild all currently rendered reference hitboxes.
     *
     * RuneTags does not need to query RuneScape's Split Private Chat setting
     * directly. Instead, the actual populated widget surfaces are the source
     * of truth.
     *
     * SPLIT_PRIVATE is checked first because it represents the immediately
     * visible floating private-chat presentation. CHATBOX is then processed
     * independently so the same private message may also remain interactive
     * in the selected Private chat tab.
     */
    public List<ChatReferenceHitbox> layout()
    {
        /*
         * Font ownership is synchronized once after native chat reconstruction.
         *
         * Render-time layout is read-only with respect to FontId. It may rebuild
         * transient hitbox geometry every frame, but it must never toggle native
         * chat fonts.
         */
        final List<TaggedMessage> messages =
                new ArrayList<>(
                        repository.snapshot());

        /*
         * Visible RuneScape chat is newest-first, so search semantic messages
         * in the same direction.
         */
        Collections.reverse(messages);

        final List<ChatReferenceHitbox> hitboxes =
                new ArrayList<>();

        /*
         * Split-private surface.
         *
         * WidgetInfo.PRIVATE_CHAT_MESSAGE resolves to PmChat.CONTAINER.
         *
         * When RuneScape's Split Private Chat setting is inactive, the useful
         * message children are absent/empty, so this surface naturally
         * contributes no hitboxes.
         */
        layoutSurface(
                client.getWidget(
                        WidgetInfo.PRIVATE_CHAT_MESSAGE),
                Surface.SPLIT_PRIVATE,
                messages,
                hitboxes);

        /*
         * Normal chatbox surface.
         *
         * This contains:
         *
         * - normal public/clan/channel/etc. chat,
         * - private chat when Split Private Chat is disabled,
         * - private-message history when the Private tab is selected.
         *
         * It is processed independently from SPLIT_PRIVATE because one
         * TaggedMessage may legitimately have a rendered instance on both.
         */
        layoutSurface(
                client.getWidget(
                        WidgetInfo.CHATBOX_MESSAGE_LINES),
                Surface.CHATBOX,
                messages,
                hitboxes);

        return hitboxes;
    }

    /**
     * Layout semantic messages against one physical RuneScape chat surface.
     *
     * Widget ownership is unique only within this surface pass. The same
     * TaggedMessage may therefore resolve independently against another
     * physical surface.
     */
    private void layoutSurface(
            Widget surfaceWidget,
            Surface surface,
            List<TaggedMessage> messages,
            List<ChatReferenceHitbox> output)
    {
        if (surfaceWidget == null
                || surfaceWidget.isHidden())
        {
            return;
        }

        final List<Widget> textWidgets =
                new ArrayList<>();

        final Set<Widget> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        collectTextWidgets(
                surfaceWidget,
                textWidgets,
                visited,
                0);

        if (textWidgets.isEmpty())
        {
            return;
        }

        /*
         * One physical text widget may represent only one TaggedMessage
         * during this surface pass.
         *
         * This is intentionally surface-local. A semantic message may have a
         * separate physical widget on another surface.
         */
        final Set<Widget> usedWidgets =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        for (TaggedMessage message : messages)
        {
            if (message == null)
            {
                continue;
            }

            /*
             * PmChat is specifically the floating split-private interface.
             *
             * Restrict semantic matching on this surface to actual private
             * message types so identical public/clan/etc. text cannot claim a
             * PmChat widget.
             */
            if (surface == Surface.SPLIT_PRIVATE
                    && !isPrivateMessage(message))
            {
                continue;
            }

            final Widget messageWidget =
                    findWidgetForMessage(
                            message,
                            textWidgets,
                            usedWidgets);

            if (messageWidget == null)
            {
                continue;
            }

            usedWidgets.add(
                    messageWidget);

            /*
             * FontId is intentionally not mutated here.
             *
             * ChatFontLayoutService schedules one final font synchronization after native
             * clientscript reconstruction. Render-time layout owns only geometry.
             */

            /*
             * Body references and sender interaction are deliberately laid
             * out independently.
             *
             * A message may contain no @tag or recognized mention and still
             * need a clickable SENDER when Clickable Players = ALL.
             */
            layoutMessage(
                    messageWidget,
                    message,
                    surface,
                    output);

            layoutSender(
                    messageWidget,
                    message,
                    surface,
                    textWidgets,
                    output);
        }
    }

    /**
     * Apply the configured font to a physical message body containing a RuneTags
     * mention.
     *
     * Jagex font selection belongs to the complete Widget, unlike color,
     * underline, and shadow markup which can be applied to individual spans.
     *
     * The original font ID is retained before RuneTags changes it so NORMAL can
     * restore the widget exactly instead of assuming every chat surface uses the
     * same default font.
     */
    private void applyMentionFont(
            Widget messageWidget,
            TaggedMessage message)
    {
        if (messageWidget == null
                || message == null)
        {
            return;
        }

        /*
         * Font treatment belongs only to messages RuneTags actually recognizes
         * as containing a mention/highlight. Sender-only interaction must not
         * change an otherwise ordinary chat message.
         */
        final boolean hasPlayerReference =
                message.getReferences() != null
                        && !message.getReferences().isEmpty();

        final boolean hasLocalMention =
                message.getLocalMentionMatch() != null
                        && message.getLocalMentionMatch()
                        .isMatchesLocalPlayer();

        if (!hasPlayerReference
                && !hasLocalMention)
        {
            restoreOriginalFont(
                    messageWidget);

            return;
        }

        final MentionFont mentionFont =
                config.fontMentions();

        if (mentionFont == null
                || mentionFont == MentionFont.NORMAL)
        {
            restoreOriginalFont(
                    messageWidget);

            return;
        }

        /*
         * Remember RuneScape's actual font before RuneTags mutates this widget.
         * IdentityHashMap ensures the physical Widget instance itself is the key.
         */
        originalFontIds.putIfAbsent(
                messageWidget,
                messageWidget.getFontId());

        final int fontId;

        switch (mentionFont)
        {
            case BOLD:
                fontId = FontID.BOLD_12;
                break;

            case VERDANA:
                fontId = FontID.VERDANA_13_BOLD;
                break;

            case NORMAL:
            default:
                restoreOriginalFont(
                        messageWidget);

                return;
        }

        if (messageWidget.getFontId() != fontId)
        {
            messageWidget.setFontId(
                    fontId);
        }
    }

    /**
     * Restore the font a physical RuneScape widget had before RuneTags changed
     * it.
     */
    private void restoreOriginalFont(
            Widget widget)
    {
        if (widget == null)
        {
            return;
        }

        final Integer originalFontId =
                originalFontIds.remove(
                        widget);

        if (originalFontId == null)
        {
            return;
        }

        if (widget.getFontId()
                != originalFontId)
        {
            widget.setFontId(
                    originalFontId);
        }
    }

    /**
     * Restore every physical Widget previously modified by RuneTags.
     *
     * RuneScape recycles chat widgets as messages shift and chat surfaces are
     * reconstructed. A Widget which represented a mention during the previous
     * pass may represent an unrelated message during the current pass.
     *
     * Resetting all tracked widgets before current semantic ownership is resolved
     * prevents mention fonts from following the physical row instead of the
     * TaggedMessage.
     */
    private void restoreAllOriginalFonts()
    {
        if (originalFontIds.isEmpty())
        {
            return;
        }

        final Map<Widget, Integer> fontsToRestore =
                new IdentityHashMap<>(
                        originalFontIds);

        originalFontIds.clear();

        for (Map.Entry<Widget, Integer> entry
                : fontsToRestore.entrySet())
        {
            final Widget widget =
                    entry.getKey();

            final Integer originalFontId =
                    entry.getValue();

            if (widget == null
                    || originalFontId == null)
            {
                continue;
            }

            if (widget.getFontId()
                    != originalFontId)
            {
                widget.setFontId(
                        originalFontId);
            }
        }
    }

    /**
     * Resolve the physical message-body widget for one semantic TaggedMessage.
     *
     * This exposes the same sender-aware association used by clickable reference
     * layout so other RuneTags rendering layers do not maintain a second,
     * potentially divergent widget-matching implementation.
     *
     * Widget ownership remains local to the caller's physical surface pass through
     * the supplied usedWidgets set.
     */
    public Widget findRenderedMessageWidget(
            TaggedMessage message,
            List<Widget> widgets,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || widgets == null
                || usedWidgets == null)
        {
            return null;
        }

        return findWidgetForMessage(
                message,
                widgets,
                usedWidgets);
    }

    /**
     * Find the rendered widget containing the semantic message body.
     */
    private Widget findWidgetForMessage(
            TaggedMessage message,
            List<Widget> widgets,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || widgets == null)
        {
            return null;
        }

        final String needle =
                message.getOriginalMessage();

        if (needle == null
                || needle.isEmpty())
        {
            return null;
        }

        final boolean whitespaceOnly =
                needle.trim().isEmpty();

        Widget bodyFallback =
                null;

        for (Widget widget : widgets)
        {
            if (widget == null
                    || usedWidgets.contains(widget)
                    || widget.isHidden())
            {
                continue;
            }

            final String raw =
                    widget.getText();

            if (raw == null
                    || raw.isEmpty())
            {
                continue;
            }

            final String semantic =
                    ChatText.toSemanticPlain(
                            raw);

            final boolean bodyMatches;

            /*
             * Whitespace-only messages require the entire rendered body to remain
             * whitespace. A normal contains() check would match almost every
             * sentence containing a space.
             */
            if (whitespaceOnly)
            {
                bodyMatches =
                        semantic != null
                                && !semantic.isEmpty()
                                && semantic.trim().isEmpty();
            }
            else
            {
                bodyMatches =
                        semantic != null
                                && semantic.equals(
                                needle);
            }

            if (!bodyMatches)
            {
                continue;
            }

            /*
             * Preserve the first body match as a compatibility fallback.
             *
             * Some native presentations may not expose a separately discoverable
             * sender widget. We therefore prefer sender-confirmed ownership without
             * making sender discovery an absolute requirement.
             */
            if (bodyFallback == null)
            {
                bodyFallback =
                        widget;
            }

            /*
             * Strong match:
             *
             * the expected semantic body and expected sender both belong to this
             * rendered row.
             *
             * Prefer this over body-only matching so identical/short messages,
             * messages retained across channel tabs, and reconstructed chat rows
             * cannot easily claim one another's physical widgets.
             */
            if (hasSenderOnRow(
                    widget,
                    message,
                    widgets))
            {
                return widget;
            }
        }

        /*
         * Fall back only when RuneScape did not expose enough row/sender structure
         * for positive sender confirmation.
         */
        return bodyFallback;
    }

    /**
     * Verify that a candidate message body shares its rendered row with the
     * expected TaggedMessage sender.
     *
     * This disambiguates whitespace-only message bodies without treating every
     * ordinary widget containing a space as a match.
     */
    private boolean hasSenderOnRow(
            Widget messageWidget,
            TaggedMessage message,
            List<Widget> widgets)
    {
        if (messageWidget == null
                || message == null
                || widgets == null)
        {
            return false;
        }

        final Rectangle messageBounds =
                messageWidget.getBounds();

        final String sender =
                message.getCanonicalSender();

        if (messageBounds == null
                || sender == null
                || sender.isEmpty())
        {
            return false;
        }

        for (Widget candidate : widgets)
        {
            if (candidate == null
                    || candidate == messageWidget
                    || candidate.isHidden())
            {
                continue;
            }

            final Rectangle candidateBounds =
                    candidate.getBounds();

            if (candidateBounds == null
                    || candidateBounds.width <= 0
                    || candidateBounds.height <= 0)
            {
                continue;
            }

            /*
             * Sender and body must occupy the same rendered chat row.
             */
            if (candidateBounds.y
                    != messageBounds.y)
            {
                continue;
            }

            /*
             * The sender begins at or to the left of the message body.
             */
            if (candidateBounds.x
                    > messageBounds.x)
            {
                continue;
            }

            final String rawCandidateText =
                    candidate.getText();

            if (rawCandidateText == null
                    || rawCandidateText.isEmpty())
            {
                continue;
            }

            final String semanticCandidateText =
                    ChatText.toSemanticPlain(
                            rawCandidateText);

            if (indexOfName(
                    semanticCandidateText,
                    sender) >= 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Layout explicit @tags and recognized ordinary-name mentions inside the
     * semantic message body.
     *
     * A RuneScape chat message may occupy multiple visual rows while remaining
     * one Widget. Reference geometry must therefore be resolved against the
     * widget's actual wrapping rather than treating the complete text as one
     * horizontal line.
     *
     * One PlayerReference may legitimately produce more than one physical
     * ChatReferenceHitbox if the reference itself crosses a visual line break.
     */
    private void layoutMessage(
            Widget widget,
            TaggedMessage message,
            Surface surface,
            List<ChatReferenceHitbox> output)
    {
        if (message.getReferences() == null
                || message.getReferences().isEmpty())
        {
            return;
        }

        final FontTypeFace font =
                widget.getFont();

        final Rectangle widgetBounds =
                widget.getBounds();

        final String rawWidgetText =
                widget.getText();

        if (font == null
                || widgetBounds == null
                || widgetBounds.width <= 0
                || widgetBounds.height <= 0
                || rawWidgetText == null)
        {
            return;
        }

        final String semanticWidgetText =
                ChatText.toSemanticPlain(
                        rawWidgetText);

        final String originalMessage =
                message.getOriginalMessage();

        if (originalMessage == null
                || originalMessage.isEmpty())
        {
            return;
        }

        final int messageStart =
                semanticWidgetText.indexOf(
                        originalMessage);

        if (messageStart < 0)
        {
            return;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return;
        }

        final List<WrappedLine> wrappedLines =
                wrapSemanticLines(
                        widget,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        widgetBounds);

        if (wrappedLines.isEmpty())
        {
            return;
        }

        for (PlayerReference reference
                : message.getReferences())
        {
            if (reference == null
                    || !ChatInteractionPolicy.isClickable(
                    reference,
                    config))
            {
                continue;
            }

            final int semanticStart =
                    messageStart
                            + reference.getStartOffset();

            final int semanticEnd =
                    messageStart
                            + reference.getEndOffset();

            if (semanticStart < 0
                    || semanticEnd <= semanticStart
                    || semanticEnd > semanticWidgetText.length())
            {
                continue;
            }

            addWrappedSemanticHitboxes(
                    widget,
                    rawWidgetText,
                    semanticWidgetText,
                    map,
                    font,
                    widgetBounds,
                    wrappedLines,
                    semanticStart,
                    semanticEnd,
                    message.getId(),
                    reference,
                    surface,
                    output);
        }
    }

    /**
     * Layout the author/player shown beside a rendered chat message.
     *
     * The synthesized reference deliberately uses ReferenceType.SENDER.
     *
     * Existing ChatInteractionPolicy behavior then gives us:
     *
     * ALL
     * - sender clickable
     *
     * MENTIONS
     * - sender not clickable
     *
     * TAGGED_ONLY
     * - sender not clickable
     *
     * Sender references are synthesized only for interaction and are not
     * inserted into TaggedMessage.references, preventing them from affecting
     * mention matching, notifications, or history.
     */
    private void layoutSender(
            Widget messageWidget,
            TaggedMessage message,
            Surface surface,
            List<Widget> textWidgets,
            List<ChatReferenceHitbox> output)
    {
        final String sender =
                message.getCanonicalSender();

        if (sender == null
                || sender.isEmpty())
        {
            return;
        }

        final PlayerReference senderReference =
                PlayerReference.builder()
                        .rawText(sender)
                        .normalizedToken(sender)
                        .lookupName(sender)
                        .startOffset(0)
                        .endOffset(sender.length())
                        .type(ReferenceType.SENDER)
                        .locallyResolved(false)
                        .identity(null)
                        .chatType(message.getType())
                        .build();

        if (!ChatInteractionPolicy.isClickable(
                senderReference,
                config))
        {
            return;
        }

        switch (surface)
        {
            case CHATBOX:
                /*
                 * The normal chatbox renders the sender and message body as
                 * separate text widgets.
                 *
                 * Find the sender dynamically from the other text widgets on
                 * the same rendered chat row.
                 */
                layoutChatboxSender(
                        messageWidget,
                        message,
                        senderReference,
                        surface,
                        textWidgets,
                        output);
                break;

            case SPLIT_PRIVATE:
                /*
                 * Split private chat renders sender/prefix and message body as
                 * separate PmChat dynamic children.
                 */
                layoutSplitPrivateSender(
                        messageWidget,
                        message,
                        senderReference,
                        surface,
                        textWidgets,
                        output);
                break;

            default:
                break;
        }
    }

    /**
     * The normal chatbox renders the sender and message body as separate
     * text widgets.
     *
     * Locate the sender from another visible text widget occupying the
     * same rendered chat row as the body.
     *
     * The sender widget is selected dynamically rather than relying on:
     *
     * - a fixed child index,
     * - a fixed X offset,
     * - sender length,
     * - channel-specific prefixes,
     * - or the sender being embedded inside the message body widget.
     *
     * Only the actual account-name span is converted into a hitbox.
     */
    private void layoutChatboxSender(
            Widget messageWidget,
            TaggedMessage message,
            PlayerReference senderReference,
            Surface surface,
            List<Widget> textWidgets,
            List<ChatReferenceHitbox> output)
    {
        final Rectangle messageBounds =
                messageWidget.getBounds();

        if (messageBounds == null)
        {
            return;
        }

        final String sender =
                senderReference.getLookupName();

        if (sender == null
                || sender.isEmpty())
        {
            return;
        }

        Widget senderWidget =
                null;

        int senderStart =
                -1;

        /*
         * If more than one same-row widget happens to contain the account
         * name, prefer the candidate whose left edge is nearest to the
         * message body.
         *
         * This makes the selection deterministic without depending on the
         * recursive widget-collection order.
         */
        int bestCandidateX =
                Integer.MIN_VALUE;

        for (Widget candidate : textWidgets)
        {
            if (candidate == null
                    || candidate == messageWidget
                    || candidate.isHidden())
            {
                continue;
            }

            final Rectangle candidateBounds =
                    candidate.getBounds();

            if (candidateBounds == null
                    || candidateBounds.width <= 0
                    || candidateBounds.height <= 0)
            {
                continue;
            }

            /*
             * Sender and body belong to the same rendered chat row.
             */
            if (candidateBounds.y
                    != messageBounds.y)
            {
                continue;
            }

            /*
             * The sender begins at or to the left of the message body.
             */
            if (candidateBounds.x
                    > messageBounds.x)
            {
                continue;
            }

            final String rawCandidateText =
                    candidate.getText();

            if (rawCandidateText == null
                    || rawCandidateText.isEmpty())
            {
                continue;
            }

            final String semanticCandidateText =
                    ChatText.toSemanticPlain(
                            rawCandidateText);

            final int candidateSenderStart =
                    indexOfName(
                            semanticCandidateText,
                            sender);

            if (candidateSenderStart < 0)
            {
                continue;
            }

            /*
             * Prefer the candidate positioned nearest to the message body.
             */
            if (senderWidget == null
                    || candidateBounds.x > bestCandidateX)
            {
                senderWidget =
                        candidate;

                senderStart =
                        candidateSenderStart;

                bestCandidateX =
                        candidateBounds.x;
            }
        }

        if (senderWidget == null
                || senderStart < 0)
        {
            return;
        }

        final String rawSenderText =
                senderWidget.getText();

        final String semanticSenderText =
                ChatText.toSemanticPlain(
                        rawSenderText);

        final int senderEnd =
                senderStart
                        + sender.length();

        addSemanticHitbox(
                senderWidget,
                rawSenderText,
                semanticSenderText,
                senderStart,
                senderEnd,
                message.getId(),
                senderReference,
                surface,
                output);
    }

    /**
     * Split private chat renders the sender/prefix and message body as
     * separate dynamic children beneath PmChat.CONTAINER.
     *
     * Widget Inspector testing established that the sender and body widgets
     * share the same rendered Y coordinate while the message body's X offset
     * varies with the sender/prefix width.
     *
     * We therefore locate the sender dynamically instead of depending on:
     *
     * - child indices such as [0]/[1], [4]/[5], ...
     * - PmChat.PM1 through PM5,
     * - a fixed message X position,
     * - a fixed account-name length,
     * - a fixed "To " / "From " prefix width.
     */
    private void layoutSplitPrivateSender(
            Widget messageWidget,
            TaggedMessage message,
            PlayerReference senderReference,
            Surface surface,
            List<Widget> textWidgets,
            List<ChatReferenceHitbox> output)
    {
        final Rectangle messageBounds =
                messageWidget.getBounds();

        if (messageBounds == null)
        {
            return;
        }

        final String sender =
                senderReference.getLookupName();

        if (sender == null
                || sender.isEmpty())
        {
            return;
        }

        Widget senderWidget =
                null;

        int senderStart =
                -1;

        for (Widget candidate : textWidgets)
        {
            if (candidate == null
                    || candidate == messageWidget
                    || candidate.isHidden())
            {
                continue;
            }

            final Rectangle candidateBounds =
                    candidate.getBounds();

            if (candidateBounds == null
                    || candidateBounds.width <= 0
                    || candidateBounds.height <= 0)
            {
                continue;
            }

            /*
             * Sender/prefix and message body occupy the same rendered PM row.
             */
            if (candidateBounds.y
                    != messageBounds.y)
            {
                continue;
            }

            /*
             * The sender/prefix begins at or to the left of the body widget.
             *
             * The sender widget itself may be much wider than the actual
             * rendered sender text, so width is not used for pairing.
             */
            if (candidateBounds.x
                    > messageBounds.x)
            {
                continue;
            }

            final String rawCandidateText =
                    candidate.getText();

            if (rawCandidateText == null
                    || rawCandidateText.isEmpty())
            {
                continue;
            }

            final String semanticCandidateText =
                    ChatText.toSemanticPlain(
                            rawCandidateText);

            final int candidateSenderStart =
                    indexOfName(
                            semanticCandidateText,
                            sender);

            if (candidateSenderStart < 0)
            {
                continue;
            }

            senderWidget =
                    candidate;

            senderStart =
                    candidateSenderStart;

            break;
        }

        if (senderWidget == null
                || senderStart < 0)
        {
            return;
        }

        final String rawSenderText =
                senderWidget.getText();

        final String semanticSenderText =
                ChatText.toSemanticPlain(
                        rawSenderText);

        final int senderEnd =
                senderStart
                        + sender.length();

        addSemanticHitbox(
                senderWidget,
                rawSenderText,
                semanticSenderText,
                senderStart,
                senderEnd,
                message.getId(),
                senderReference,
                surface,
                output);
    }

    /**
     * Convert a semantic plain-text span inside one rendered widget into one
     * or more exact physical hitboxes.
     *
     * Most sender widgets are one line, but using the same wrapping engine here
     * keeps sender and body geometry consistent and prevents a future multiline
     * widget from creating an oversized interaction target.
     */
    private void addSemanticHitbox(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            int semanticStart,
            int semanticEnd,
            long messageId,
            PlayerReference reference,
            Surface surface,
            List<ChatReferenceHitbox> output)
    {
        if (widget == null
                || rawWidgetText == null
                || semanticWidgetText == null
                || semanticStart < 0
                || semanticEnd <= semanticStart
                || semanticEnd > semanticWidgetText.length())
        {
            return;
        }

        final Rectangle widgetBounds =
                widget.getBounds();

        final FontTypeFace font =
                widget.getFont();

        if (widgetBounds == null
                || widgetBounds.width <= 0
                || widgetBounds.height <= 0
                || font == null)
        {
            return;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return;
        }

        final List<WrappedLine> wrappedLines =
                wrapSemanticLines(
                        widget,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        widgetBounds);

        if (wrappedLines.isEmpty())
        {
            return;
        }

        addWrappedSemanticHitboxes(
                widget,
                rawWidgetText,
                semanticWidgetText,
                map,
                font,
                widgetBounds,
                wrappedLines,
                semanticStart,
                semanticEnd,
                messageId,
                reference,
                surface,
                output);
    }

    /**
     * Measure how many visual rows a raw RuneScape text body will occupy when
     * rendered with the supplied Jagex font and available body width.
     *
     * This is the PRE-CONSTRUCTION counterpart to RuneTags' existing rendered
     * reference geometry.
     *
     * ChatFontLayoutService uses this method before RuneScape creates a body
     * widget so native row allocation and the later configured FontId agree.
     *
     * Keeping the wrapping implementation here is intentional:
     *
     * - clickable references,
     * - background highlights,
     * - and pre-construction font-height compensation
     *
     * must all use the exact same wrapping rules.
     */
    public int measureWrappedLineCount(
            String rawWidgetText,
            FontTypeFace font,
            int availableWidth)
    {
        if (rawWidgetText == null
                || rawWidgetText.isEmpty()
                || font == null
                || availableWidth <= 0)
        {
            return 1;
        }

        final String semanticWidgetText =
                ChatText.toSemanticPlain(
                        rawWidgetText);

        if (semanticWidgetText == null
                || semanticWidgetText.isEmpty())
        {
            return 1;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return 1;
        }

        /*
         * wrapSemanticLines only needs the available width from the Rectangle.
         *
         * No physical Widget exists yet during PRE construction, so null is
         * deliberately supplied for the unused Widget parameter.
         */
        final Rectangle measurementBounds =
                new Rectangle(
                        0,
                        0,
                        availableWidth,
                        1);

        final List<WrappedLine> lines =
                wrapSemanticLines(
                        null,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        measurementBounds);

        return Math.max(
                1,
                lines.size());
    }

    /**
     * Resolve one arbitrary semantic span inside a rendered text widget into its
     * physical multiline rectangles.
     *
     * This is the general geometry form of the PlayerReference layout used by
     * RuneTags. It allows non-clickable presentation features, such as Unique
     * Highlight backgrounds, to use exactly the same wrapping calculations as
     * clickable mention/tag hitboxes.
     */
    public List<Rectangle> layoutSemanticSpan(
            Widget widget,
            int semanticStart,
            int semanticEnd)
    {
        final List<Rectangle> output =
                new ArrayList<>();

        if (widget == null
                || semanticStart < 0
                || semanticEnd <= semanticStart)
        {
            return output;
        }

        final Rectangle widgetBounds =
                widget.getBounds();

        final FontTypeFace font =
                widget.getFont();

        final String rawWidgetText =
                widget.getText();

        if (widgetBounds == null
                || widgetBounds.width <= 0
                || widgetBounds.height <= 0
                || font == null
                || rawWidgetText == null)
        {
            return output;
        }

        final String semanticWidgetText =
                ChatText.toSemanticPlain(
                        rawWidgetText);

        if (semanticEnd > semanticWidgetText.length())
        {
            return output;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return output;
        }

        final List<WrappedLine> wrappedLines =
                wrapSemanticLines(
                        widget,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        widgetBounds);

        if (wrappedLines.isEmpty())
        {
            return output;
        }

        final int physicalLineHeight =
                Math.max(
                        1,
                        widgetBounds.height
                                / wrappedLines.size());

        for (int lineIndex = 0;
             lineIndex < wrappedLines.size();
             lineIndex++)
        {
            final WrappedLine line =
                    wrappedLines.get(
                            lineIndex);

            final int segmentStart =
                    Math.max(
                            semanticStart,
                            line.start);

            final int segmentEnd =
                    Math.min(
                            semanticEnd,
                            line.end);

            if (segmentStart >= segmentEnd)
            {
                continue;
            }

            /*
             * Preserve rendered leading markup on the first physical line.
             *
             * In particular, <img=...> tags occupy horizontal space even though they
             * do not exist in semantic plain text.
             */
            final int rawLineStart =
                    line.start == 0
                            ? 0
                            : map.rawBoundary(
                            line.start);

            final int rawSegmentStart =
                    map.rawBoundary(
                            segmentStart);

            final int rawSegmentEnd =
                    map.rawBoundary(
                            segmentEnd);

            if (rawLineStart < 0
                    || rawSegmentStart < rawLineStart
                    || rawSegmentEnd < rawSegmentStart
                    || rawSegmentEnd > rawWidgetText.length())
            {
                continue;
            }

            final int prefixWidth =
                    font.getTextWidth(
                            rawWidgetText.substring(
                                    rawLineStart,
                                    rawSegmentStart));

            final int segmentWidth =
                    Math.max(
                            1,
                            font.getTextWidth(
                                    rawWidgetText.substring(
                                            rawSegmentStart,
                                            rawSegmentEnd)));

            final int lineOriginX =
                    alignedLineX(
                            widget,
                            widgetBounds,
                            line.width);

            final int lineY =
                    widgetBounds.y
                            + (lineIndex
                            * physicalLineHeight);

            output.add(
                    new Rectangle(
                            lineOriginX + prefixWidth,
                            lineY,
                            segmentWidth,
                            physicalLineHeight));
        }

        return output;
    }

    /**
     * Add the physical pieces of one semantic span.
     *
     * The span is intersected independently with every visual line occupied by
     * the widget. If a name crosses a wrap boundary, each visible piece receives
     * its own ChatReferenceHitbox pointing to the same PlayerReference.
     */
    private void addWrappedSemanticHitboxes(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            MessageMarkupMap map,
            FontTypeFace font,
            Rectangle widgetBounds,
            List<WrappedLine> wrappedLines,
            int semanticStart,
            int semanticEnd,
            long messageId,
            PlayerReference reference,
            Surface surface,
            List<ChatReferenceHitbox> output)
    {
        if (wrappedLines == null
                || wrappedLines.isEmpty())
        {
            return;
        }

        /*
         * RuneScape expands the physical widget as lines are added.
         *
         * Widget Inspector testing showed:
         *
         * 1 line  -> approximately 14 px
         * 2 lines -> approximately 28 px
         *
         * Deriving the row height from the actual widget height gives us the
         * physical space RuneScape allocated rather than assuming Font baseline
         * or hardcoding 14/16 px.
         */
        final int physicalLineHeight =
                Math.max(
                        1,
                        widgetBounds.height
                                / wrappedLines.size());

        for (int lineIndex = 0;
             lineIndex < wrappedLines.size();
             lineIndex++)
        {
            final WrappedLine line =
                    wrappedLines.get(
                            lineIndex);

            /*
             * Intersect the semantic reference with this visual line.
             */
            final int segmentStart =
                    Math.max(
                            semanticStart,
                            line.start);

            final int segmentEnd =
                    Math.min(
                            semanticEnd,
                            line.end);

            if (segmentStart >= segmentEnd)
            {
                continue;
            }

            /*
             * The first semantic character may be preceded by rendered markup such as:
             *
             *     <img=...>
             *
             * MessageMarkupMap correctly maps semantic offset 0 to the first visible
             * text character, which is appropriate for formatting insertion.
             *
             * Geometry is different: leading image markup occupies real horizontal
             * space inside the Widget. For the first visual line, measure from the
             * beginning of the raw Widget text so FontTypeFace includes that rendered
             * prefix width.
             *
             * Wrapped continuation lines still begin at their semantic raw boundary.
             */
            final int rawLineStart =
                    line.start == 0
                            ? 0
                            : map.rawBoundary(
                            line.start);

            final int rawSegmentStart =
                    map.rawBoundary(
                            segmentStart);

            final int rawSegmentEnd =
                    map.rawBoundary(
                            segmentEnd);

            if (rawLineStart < 0
                    || rawSegmentStart < rawLineStart
                    || rawSegmentEnd < rawSegmentStart
                    || rawSegmentEnd > rawWidgetText.length())
            {
                continue;
            }

            /*
             * Width from the beginning of this visual line to the beginning of
             * this physical reference segment.
             */
            final int prefixWidth =
                    font.getTextWidth(
                            rawWidgetText.substring(
                                    rawLineStart,
                                    rawSegmentStart));

            final int segmentWidth =
                    Math.max(
                            1,
                            font.getTextWidth(
                                    rawWidgetText.substring(
                                            rawSegmentStart,
                                            rawSegmentEnd)));

            /*
             * Alignment is resolved independently for every visual line.
             *
             * CHATBOX is normally LEFT aligned, but handling CENTER/RIGHT here
             * costs very little and keeps this routine correct for any future
             * RuneScape presentation using the same service.
             */
            final int lineOriginX =
                    alignedLineX(
                            widget,
                            widgetBounds,
                            line.width);

            final int lineY =
                    widgetBounds.y
                            + (lineIndex
                            * physicalLineHeight);

            final Rectangle bounds =
                    new Rectangle(
                            lineOriginX + prefixWidth,
                            lineY,
                            segmentWidth,
                            physicalLineHeight);

            output.add(
                    new ChatReferenceHitbox(
                            messageId,
                            bounds,
                            reference,
                            surface));
        }
    }

    /**
     * Reproduce the visual line ranges used by a RuneScape text widget.
     *
     * RuneScape exposes the final widget width and total expanded height but does
     * not expose an API containing the semantic start/end offset of each rendered
     * line. Reconstruct those ranges using the same Jagex FontTypeFace used by the
     * widget.
     *
     * Wrapping prefers whitespace. If one unbroken token is wider than the
     * widget, it falls back to a hard character boundary so layout can still
     * progress.
     */
    private List<WrappedLine> wrapSemanticLines(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            MessageMarkupMap map,
            FontTypeFace font,
            Rectangle widgetBounds)
    {
        final List<WrappedLine> lines =
                new ArrayList<>();

        if (semanticWidgetText == null
                || semanticWidgetText.isEmpty()
                || widgetBounds.width <= 0)
        {
            return lines;
        }

        final int textLength =
                semanticWidgetText.length();

        int lineStart =
                0;

        while (lineStart < textLength)
        {
            /*
             * Explicit newlines begin a new physical line immediately.
             */
            if (isExplicitLineBreak(
                    semanticWidgetText.charAt(
                            lineStart)))
            {
                lineStart++;
                continue;
            }

            int cursor =
                    lineStart;

            int lastWhitespace =
                    -1;

            int acceptedEnd =
                    lineStart;

            while (cursor < textLength)
            {
                final char current =
                        semanticWidgetText.charAt(
                                cursor);

                if (isExplicitLineBreak(
                        current))
                {
                    acceptedEnd =
                            cursor;

                    break;
                }

                if (Character.isWhitespace(
                        current))
                {
                    lastWhitespace =
                            cursor;
                }

                final int candidateEnd =
                        cursor + 1;

                final int candidateWidth =
                        semanticWidth(
                                rawWidgetText,
                                map,
                                font,
                                lineStart,
                                candidateEnd);

                if (candidateWidth
                        <= widgetBounds.width)
                {
                    acceptedEnd =
                            candidateEnd;

                    cursor++;
                    continue;
                }

                /*
                 * The candidate no longer fits.
                 *
                 * Prefer the last whitespace belonging to this line. The space
                 * itself remains semantically between the two words but is not
                 * treated as visible leading content on the following line.
                 */
                if (lastWhitespace
                        >= lineStart)
                {
                    acceptedEnd =
                            lastWhitespace;
                }
                else if (acceptedEnd
                        <= lineStart)
                {
                    /*
                     * One token/character itself is wider than the widget.
                     * Force progress by allowing one semantic character.
                     */
                    acceptedEnd =
                            candidateEnd;
                }

                break;
            }

            if (cursor >= textLength)
            {
                acceptedEnd =
                        textLength;
            }

            if (acceptedEnd < lineStart)
            {
                acceptedEnd =
                        lineStart;
            }

            /*
             * Avoid zero-length visual lines caused by unusual whitespace.
             */
            if (acceptedEnd == lineStart)
            {
                acceptedEnd =
                        Math.min(
                                textLength,
                                lineStart + 1);
            }

            final int lineWidth =
                    semanticWidth(
                            rawWidgetText,
                            map,
                            font,
                            lineStart,
                            acceptedEnd);

            lines.add(
                    new WrappedLine(
                            lineStart,
                            acceptedEnd,
                            Math.max(
                                    0,
                                    lineWidth)));

            /*
             * Move to the first semantic character rendered on the following
             * visual line.
             */
            int nextStart =
                    acceptedEnd;

            while (nextStart < textLength)
            {
                final char next =
                        semanticWidgetText.charAt(
                                nextStart);

                if (isExplicitLineBreak(
                        next))
                {
                    nextStart++;
                    break;
                }

                /*
                 * RuneScape does not visually preserve the wrapping space at the
                 * start of the following line.
                 */
                if (Character.isWhitespace(
                        next))
                {
                    nextStart++;
                    continue;
                }

                break;
            }

            /*
             * Defensive progress guard.
             */
            if (nextStart <= lineStart)
            {
                nextStart =
                        Math.min(
                                textLength,
                                lineStart + 1);
            }

            lineStart =
                    nextStart;
        }

        /*
         * Normally the number of reconstructed lines will agree with the widget
         * height. If the widget reports one physical row, preserving a single
         * line avoids introducing artificial vertical geometry.
         */
        if (lines.isEmpty())
        {
            lines.add(
                    new WrappedLine(
                            0,
                            textLength,
                            semanticWidth(
                                    rawWidgetText,
                                    map,
                                    font,
                                    0,
                                    textLength)));
        }

        return lines;
    }

    /**
     * Measure one semantic range using the actual raw RuneScape markup and the
     * widget's own font.
     */
    private static int semanticWidth(
            String rawWidgetText,
            MessageMarkupMap map,
            FontTypeFace font,
            int semanticStart,
            int semanticEnd)
    {
        if (semanticStart < 0
                || semanticEnd < semanticStart)
        {
            return 0;
        }

        final int rawStart =
                map.rawBoundary(
                        semanticStart);

        final int rawEnd =
                map.rawBoundary(
                        semanticEnd);

        if (rawStart < 0
                || rawEnd < rawStart
                || rawEnd > rawWidgetText.length())
        {
            return 0;
        }

        return font.getTextWidth(
                rawWidgetText.substring(
                        rawStart,
                        rawEnd));
    }

    /**
     * Resolve horizontal alignment for one visual line rather than the complete
     * multiline widget.
     */
    private static int alignedLineX(
            Widget widget,
            Rectangle bounds,
            int lineWidth)
    {
        switch (widget.getXTextAlignment())
        {
            case WidgetTextAlignment.CENTER:
                return bounds.x
                        + Math.max(
                        0,
                        (bounds.width
                                - lineWidth) / 2);

            case WidgetTextAlignment.RIGHT:
                return bounds.x
                        + Math.max(
                        0,
                        bounds.width
                                - lineWidth);

            case WidgetTextAlignment.LEFT:
            default:
                return bounds.x;
        }
    }

    /**
     * Semantic line-break characters which should terminate the current visual
     * row immediately.
     */
    private static boolean isExplicitLineBreak(
            char value)
    {
        return value == '\n'
                || value == '\r';
    }

    /**
     * Find an account name without depending on capitalization or
     * RuneScape's alternate space characters.
     *
     * NBSP and narrow-NBSP replacement is length-preserving, so the returned
     * offset remains valid against the original semantic text and
     * MessageMarkupMap.
     */
    private static int indexOfName(
            String text,
            String name)
    {
        if (text == null
                || text.isEmpty()
                || name == null
                || name.isEmpty())
        {
            return -1;
        }

        final String comparableText =
                comparableNameText(
                        text);

        final String comparableName =
                comparableNameText(
                        name);

        if (comparableName.isEmpty()
                || comparableName.length()
                > comparableText.length())
        {
            return -1;
        }

        final int maxStart =
                comparableText.length()
                        - comparableName.length();

        for (int start = 0;
             start <= maxStart;
             start++)
        {
            if (comparableText.regionMatches(
                    true,
                    start,
                    comparableName,
                    0,
                    comparableName.length()))
            {
                return start;
            }
        }

        return -1;
    }

    private static String comparableNameText(
            String value)
    {
        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');
    }

    /**
     * The split-private PmChat surface must only match private-message
     * semantic records.
     */
    private static boolean isPrivateMessage(
            TaggedMessage message)
    {
        if (message == null
                || message.getType() == null)
        {
            return false;
        }

        final ChatMessageType type =
                message.getType();

        return type == ChatMessageType.PRIVATECHAT
                || type == ChatMessageType.MODPRIVATECHAT
                || type == ChatMessageType.PRIVATECHATOUT;
    }

    /**
     * Recursively collect actual rendered text widgets beneath a physical chat
     * surface.
     *
     * Hidden/empty PmChat slots naturally fail these checks, so RuneTags does
     * not need to know that RuneScape currently exposes only five floating PM
     * rows or that populated dynamic children commonly follow a
     * [0]/[1], [4]/[5], ... pattern.
     */
    private void collectTextWidgets(
            Widget widget,
            List<Widget> output,
            Set<Widget> visited,
            int depth)
    {
        if (widget == null
                || depth > MAX_WIDGET_DEPTH
                || !visited.add(widget))
        {
            return;
        }

        final String text =
                widget.getText();

        if (!widget.isHidden()
                && text != null
                && !text.isEmpty()
                && widget.getFont() != null)
        {
            final Rectangle bounds =
                    widget.getBounds();

            /*
             * Do not trim rendered text here. RuneScape can represent a real
             * chat message body as markup containing only whitespace
             *
             * Widget is still needed as the physical row anchor used to locate its sender.
             */
            if (bounds != null
                    && bounds.width > 0
                    && bounds.height > 0)
            {
                output.add(
                        widget);
            }
        }

        collectChildren(
                widget.getDynamicChildren(),
                output,
                visited,
                depth + 1);

        collectChildren(
                widget.getStaticChildren(),
                output,
                visited,
                depth + 1);

        collectChildren(
                widget.getNestedChildren(),
                output,
                visited,
                depth + 1);
    }

    private void collectChildren(
            Widget[] children,
            List<Widget> output,
            Set<Widget> visited,
            int depth)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collectTextWidgets(
                    child,
                    output,
                    visited,
                    depth);
        }
    }
}