package com.runetags.overlay;

import com.runetags.RuneTagsConfig;
import com.runetags.chat.ChatReferenceLayoutService;
import com.runetags.chat.ChatText;
import com.runetags.chat.TaggedMessageRepository;
import com.runetags.model.LocalMentionMatch;
import com.runetags.model.MatchReason;
import com.runetags.model.PlayerReference;
import com.runetags.model.TaggedMessage;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Draws non-clickable Self Highlight backgrounds for local-match tokens which
 * are not already represented by PlayerReference objects.
 *
 * Responsibilities:
 *
 * - Unique Highlight aliases, such as "mark"
 * - normalized local-account-name matches which do not already have a
 *   PlayerReference
 * - CHATBOX rendering
 * - SPLIT_PRIVATE rendering
 * - exact multiline geometry shared with ChatReferenceLayoutService
 *
 * This overlay deliberately does NOT:
 *
 * - color message foreground text;
 * - implement Mention Whole Message;
 * - highlight the complete message;
 * - redraw normal PlayerReference highlights;
 * - create clickable hitboxes.
 *
 * Normal self/other mentions and explicit tags are highlighted by
 * ChatReferenceOverlay using ChatReferenceHitbox objects.
 *
 * Mention Whole Message remains solely a foreground-color feature implemented
 * by MessageFormatter.
 */
public class ChatMessageHighlightOverlay extends Overlay
{
    private static final int MAX_WIDGET_DEPTH = 4;

    /*
     * Keep the visual padding identical to ChatReferenceOverlay so local alias
     * highlights and normal player-reference highlights look like the same
     * feature.
     */
    private static final int BACKGROUND_HORIZONTAL_PADDING = 1;
    private static final int BACKGROUND_VERTICAL_PADDING = 0;

    private final Client client;
    private final RuneTagsConfig config;
    private final TaggedMessageRepository repository;
    private final ChatReferenceLayoutService layoutService;

    public ChatMessageHighlightOverlay(
            Client client,
            RuneTagsConfig config,
            TaggedMessageRepository repository,
            ChatReferenceLayoutService layoutService)
    {
        this.client = client;
        this.config = config;
        this.repository = repository;
        this.layoutService = layoutService;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(
            Graphics2D graphics)
    {
        /*
         * Highlight Backgrounds is completely independent from Mention Whole
         * Message.
         *
         * Mention Whole Message controls foreground coloring only.
         */
        if (!config.highlightBackground()
                || !config.mentionSelf())
        {
            return null;
        }

        final Color backgroundColor =
                config.selfBackgroundColor();

        if (backgroundColor == null
                || backgroundColor.getAlpha() == 0)
        {
            return null;
        }

        final List<TaggedMessage> messages =
                new ArrayList<>(
                        repository.snapshot());

        /*
         * Visible RuneScape chat is newest-first.
         */
        Collections.reverse(
                messages);

        final Color originalColor =
                graphics.getColor();

        final Shape originalClip =
                graphics.getClip();

        try
        {
            graphics.setColor(
                    backgroundColor);

            /*
             * Process both physical presentations independently.
             *
             * A private message may legitimately exist in both locations when
             * Split Private Chat is enabled while the Private tab is selected.
             */
            renderSurface(
                    graphics,
                    client.getWidget(
                            WidgetInfo.PRIVATE_CHAT_MESSAGE),
                    ChatReferenceLayoutService.Surface.SPLIT_PRIVATE,
                    messages,
                    originalClip);

            renderSurface(
                    graphics,
                    client.getWidget(
                            WidgetInfo.CHATBOX_MESSAGE_LINES),
                    ChatReferenceLayoutService.Surface.CHATBOX,
                    messages,
                    originalClip);
        }
        finally
        {
            graphics.setClip(
                    originalClip);

            graphics.setColor(
                    originalColor);
        }

        return null;
    }

    /**
     * Draw the non-PlayerReference local-match tokens present on one physical
     * RuneScape chat surface.
     */
    private void renderSurface(
            Graphics2D graphics,
            Widget surfaceWidget,
            ChatReferenceLayoutService.Surface surface,
            List<TaggedMessage> messages,
            Shape originalClip)
    {
        if (surfaceWidget == null
                || surfaceWidget.isHidden())
        {
            return;
        }

        final Rectangle surfaceBounds =
                surfaceWidget.getBounds();

        if (surfaceBounds == null
                || surfaceBounds.width <= 0
                || surfaceBounds.height <= 0)
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
         * Widget ownership is local to this physical surface.
         *
         * The same TaggedMessage may therefore resolve once in SPLIT_PRIVATE
         * and once in CHATBOX.
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
             * PmChat contains only private messages.
             *
             * Restrict semantic matching so an identical public/clan message
             * cannot accidentally claim a split-PM body widget.
             */
            if (surface
                    == ChatReferenceLayoutService.Surface.SPLIT_PRIVATE
                    && !isPrivateMessage(
                    message))
            {
                continue;
            }

            final LocalMentionMatch localMatch =
                    message.getLocalMentionMatch();

            if (!shouldDrawLocalToken(
                    localMatch))
            {
                continue;
            }

            final Widget widget =
                    layoutService.findRenderedMessageWidget(
                            message,
                            textWidgets,
                            usedWidgets);

            if (widget == null)
            {
                continue;
            }

            usedWidgets.add(
                    widget);

            final String rawWidgetText =
                    widget.getText();

            if (rawWidgetText == null
                    || rawWidgetText.isEmpty())
            {
                continue;
            }

            final String semanticWidgetText =
                    ChatText.toSemanticPlain(
                            rawWidgetText);

            final String originalMessage =
                    message.getOriginalMessage();

            if (originalMessage == null
                    || originalMessage.isEmpty())
            {
                continue;
            }

            final int messageStart =
                    semanticWidgetText.indexOf(
                            originalMessage);

            if (messageStart < 0)
            {
                continue;
            }

            final String token =
                    localMatch.getMatchedToken();

            if (token == null
                    || token.trim().isEmpty())
            {
                continue;
            }

            /*
             * Mirror MessageFormatter's local-token matching behavior:
             *
             * - case-insensitive
             * - name-boundary aware
             * - every occurrence of the matched token
             * - do not duplicate a PlayerReference span
             */
            final String loweredMessage =
                    originalMessage.toLowerCase(
                            Locale.ROOT);

            final String loweredToken =
                    token.toLowerCase(
                            Locale.ROOT);

            int from =
                    0;

            while (from
                    <= loweredMessage.length()
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
                        start
                                + loweredToken.length();

                if (hasBoundaries(
                        loweredMessage,
                        start,
                        end)
                        && !overlapsPlayerReference(
                        start,
                        end,
                        message))
                {
                    drawSemanticSpan(
                            graphics,
                            widget,
                            messageStart + start,
                            messageStart + end,
                            surface,
                            surfaceBounds,
                            originalClip);
                }

                /*
                 * Match MessageFormatter behavior and allow discovery of later
                 * occurrences without skipping overlapping search positions.
                 */
                from =
                        start + 1;
            }
        }
    }

    /**
     * Draw one exact semantic local-match span.
     *
     * The geometry is supplied by ChatReferenceLayoutService, so aliases use
     * precisely the same line wrapping and row placement as clickable player
     * references.
     */
    private void drawSemanticSpan(
            Graphics2D graphics,
            Widget widget,
            int semanticStart,
            int semanticEnd,
            ChatReferenceLayoutService.Surface surface,
            Rectangle surfaceBounds,
            Shape originalClip)
    {
        final List<Rectangle> rectangles =
                layoutService.layoutSemanticSpan(
                        widget,
                        semanticStart,
                        semanticEnd);

        if (rectangles == null
                || rectangles.isEmpty())
        {
            return;
        }

        for (Rectangle bounds : rectangles)
        {
            if (bounds == null
                    || bounds.width <= 0
                    || bounds.height <= 0)
            {
                continue;
            }

            graphics.setClip(
                    originalClip);

            Rectangle visibleBounds =
                    bounds;

            if (surface
                    == ChatReferenceLayoutService.Surface.CHATBOX)
            {
                /*
                 * Above-widgets overlays do not automatically inherit the
                 * chatbox's native clipping region.
                 */
                graphics.clip(
                        surfaceBounds);

                visibleBounds =
                        surfaceBounds.intersection(
                                bounds);

                if (visibleBounds.isEmpty())
                {
                    continue;
                }
            }

            /*
             * SPLIT_PRIVATE uses the actual physical PmChat body widget and is
             * therefore intentionally not clipped against CHATBOX.
             */
            graphics.fillRect(
                    visibleBounds.x
                            - BACKGROUND_HORIZONTAL_PADDING,
                    visibleBounds.y
                            - BACKGROUND_VERTICAL_PADDING,
                    visibleBounds.width
                            + (BACKGROUND_HORIZONTAL_PADDING * 2),
                    visibleBounds.height
                            + (BACKGROUND_VERTICAL_PADDING * 2));
        }
    }

    /**
     * Only local matches which lack their own normal PlayerReference need this
     * overlay.
     *
     * ACCOUNT_NAME is deliberately excluded because the normal self
     * PlayerReference is already handled by ChatReferenceOverlay.
     */
    private static boolean shouldDrawLocalToken(
            LocalMentionMatch localMatch)
    {
        if (localMatch == null
                || !localMatch.isMatchesLocalPlayer()
                || localMatch.getReason() == null)
        {
            return false;
        }

        return localMatch.getReason()
                == MatchReason.UNIQUE_HIGHLIGHT
                || localMatch.getReason()
                == MatchReason.NORMALIZED_ACCOUNT_NAME;
    }

    /**
     * Do not paint a second self-background over an existing PlayerReference.
     *
     * ChatReferenceOverlay is already responsible for those spans and also
     * owns their clickable hitboxes.
     */
    private static boolean overlapsPlayerReference(
            int start,
            int end,
            TaggedMessage message)
    {
        if (message == null
                || message.getReferences() == null)
        {
            return false;
        }

        for (PlayerReference reference
                : message.getReferences())
        {
            if (reference == null)
            {
                continue;
            }

            if (start
                    < reference.getEndOffset()
                    && end
                    > reference.getStartOffset())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Match MessageFormatter's token-boundary rules exactly.
     */
    private static boolean hasBoundaries(
            String text,
            int start,
            int end)
    {
        final boolean leftBoundary =
                start == 0
                        || !isNameChar(
                        text.charAt(
                                start - 1));

        final boolean rightBoundary =
                end == text.length()
                        || !isNameChar(
                        text.charAt(
                                end));

        return leftBoundary
                && rightBoundary;
    }

    private static boolean isNameChar(
            char c)
    {
        return Character.isLetterOrDigit(
                c)
                || c == '_'
                || c == '-';
    }

    /**
     * The floating PmChat surface may only claim semantic private-message
     * records.
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

        return type
                == ChatMessageType.PRIVATECHAT
                || type
                == ChatMessageType.MODPRIVATECHAT
                || type
                == ChatMessageType.PRIVATECHATOUT;
    }

    /**
     * Recursively discover rendered text widgets beneath one physical chat
     * surface.
     */
    private void collectTextWidgets(
            Widget widget,
            List<Widget> output,
            Set<Widget> visited,
            int depth)
    {
        if (widget == null
                || depth > MAX_WIDGET_DEPTH
                || !visited.add(
                widget))
        {
            return;
        }

        final String text =
                widget.getText();

        if (!widget.isHidden()
                && text != null
                && !ChatText.toSemanticPlain(
                        text)
                .trim()
                .isEmpty()
                && widget.getFont() != null)
        {
            final Rectangle bounds =
                    widget.getBounds();

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