package com.runetags.overlay;

import com.runetags.RuneTagsConfig;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ChatReferenceHitbox;
import com.runetags.chat.ChatReferenceLayoutService;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.model.LocalMentionMatch;
import com.runetags.model.PlayerReference;
import com.runetags.model.ReferenceType;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import java.util.stream.Collectors;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Custom chat-reference decoration overlay.
 *
 * Responsibilities:
 *
 * - refresh exact per-reference hitboxes every render frame
 * - restrict CHATBOX references to the visible chatbox
 * - preserve references rendered on the SPLIT_PRIVATE surface
 * - draw per-reference background highlights
 * - draw dotted underlines for unresolved explicit tags
 *
 * Whole-message background highlighting remains the responsibility of
 * ChatMessageHighlightOverlay.
 */
public class ChatReferenceOverlay extends Overlay
{
    private static final int BACKGROUND_HORIZONTAL_PADDING = 1;
    private static final int BACKGROUND_VERTICAL_PADDING = 0;

    private final ChatReferenceLayoutService layoutService;
    private final ChatHitboxRegistry registry;
    private final Client client;
    private final RuneTagsConfig config;
    private final LocalMentionMatcher localMentionMatcher;

    public ChatReferenceOverlay(
            ChatReferenceLayoutService layoutService,
            ChatHitboxRegistry registry,
            Client client,
            RuneTagsConfig config,
            LocalMentionMatcher localMentionMatcher)
    {
        this.layoutService = layoutService;
        this.registry = registry;
        this.client = client;
        this.config = config;
        this.localMentionMatcher = localMentionMatcher;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        /*
         * RuneLite/Jagex clips native chat text to the chatbox, but custom
         * ABOVE_WIDGETS overlays are not automatically clipped with it.
         *
         * Resolve the visible chat-message area first so both rendering and
         * interaction are restricted to that region.
         */
        final Widget chatbox =
                client.getWidget(
                        WidgetInfo.CHATBOX_MESSAGE_LINES);

        final Rectangle chatboxBounds =
                chatbox != null
                        && !chatbox.isHidden()
                        ? chatbox.getBounds()
                        : null;

        final boolean hasVisibleChatbox =
                chatboxBounds != null
                        && chatboxBounds.width > 0
                        && chatboxBounds.height > 0;

        /*
         * Calculate semantic/reference hitboxes from the rendered chat.
         */
        final List<ChatReferenceHitbox> hitboxes =
                layoutService.layout();

        /*
         * Restrict CHATBOX hitboxes to the actual visible portion of the
         * normal chatbox.
         *
         * SPLIT_PRIVATE hitboxes are already derived from RuneScape's visible
         * PmChat text widgets and must not be clipped against CHATBOX_MESSAGE_LINES,
         * because split private chat is physically rendered outside that surface.
         *
         * For CHATBOX references, Rectangle.intersection(...) remains important:
         * if a message is partially scrolled above or below the chatbox, the
         * invisible portion is removed from the registered click target.
         */
        final List<ChatReferenceHitbox> visibleHitboxes =
                hitboxes.stream()
                        .filter(hitbox ->
                        {
                            if (hitbox.getBounds() == null)
                            {
                                return false;
                            }

                            switch (hitbox.getSurface())
                            {
                                case CHATBOX:
                                    return hasVisibleChatbox
                                            && chatboxBounds.intersects(
                                            hitbox.getBounds());

                                case SPLIT_PRIVATE:
                                    return true;

                                default:
                                    return false;
                            }
                        })
                        .map(hitbox ->
                        {
                            if (hitbox.getSurface()
                                    == ChatReferenceLayoutService.Surface.CHATBOX)
                            {
                                final Rectangle visibleBounds =
                                        chatboxBounds.intersection(
                                                hitbox.getBounds());

                                return new ChatReferenceHitbox(
                                        hitbox.getMessageId(),
                                        visibleBounds,
                                        hitbox.getReference(),
                                        hitbox.getSurface());
                            }

                            /*
                             * SPLIT_PRIVATE already uses the bounds of the actual
                             * visible PmChat text widget. Preserve those coordinates
                             * exactly rather than clipping them against the chatbox.
                             */
                            return hitbox;
                        })
                        .collect(Collectors.toList());

        /*
         * Keep the interaction registry synchronized with only what is
         * actually visible/clickable.
         */
        registry.replace(visibleHitboxes);

        final Player localPlayer =
                client.getLocalPlayer();

        final String localPlayerName =
                localPlayer != null
                        ? localPlayer.getName()
                        : null;

        final Color originalColor =
                graphics.getColor();

        final Shape originalClip =
                graphics.getClip();

        try
        {
            /*
             * Draw each physical reference independently so CHATBOX decorations
             * retain normal chatbox clipping while SPLIT_PRIVATE decorations are
             * free to render at their actual PmChat coordinates.
             */
            for (ChatReferenceHitbox hitbox : visibleHitboxes)
            {
                final PlayerReference reference =
                        hitbox.getReference();

                if (reference == null)
                {
                    continue;
                }

                /*
                 * Restore the incoming RuneLite clip before configuring the
                 * clipping rule for this individual hitbox.
                 */
                graphics.setClip(originalClip);

                if (hitbox.getSurface()
                        == ChatReferenceLayoutService.Surface.CHATBOX)
                {
                    /*
                     * Preserve the existing RuneTags chatbox clipping behavior.
                     *
                     * This prevents custom ABOVE_WIDGETS decorations from leaking
                     * above or below the visible chat history.
                     */
                    graphics.clip(chatboxBounds);
                }

                /*
                 * SENDER is an interaction target, not a semantic mention.
                 *
                 * Clickable Players = ALL should make sender names clickable
                 * without painting every sender as though they were mentioned.
                 */
                if (reference.getType() != ReferenceType.SENDER
                        && config.highlightBackground())
                {
                    drawReferenceBackground(
                            graphics,
                            hitbox,
                            localPlayerName);
                }

                /*
                 * Explicit tags which could not be locally resolved retain their
                 * dotted underline on either physical rendering surface.
                 */
                if (reference.getType() == ReferenceType.TAG
                        && !reference.isLocallyResolved())
                {
                    drawDottedUnderline(
                            graphics,
                            hitbox.getBounds(),
                            config.otherMentionColor());
                }
            }
        }
        finally
        {
            /*
             * Always restore Graphics2D state because RuneLite shares this
             * graphics context with other overlay rendering.
             */
            graphics.setClip(originalClip);
            graphics.setColor(originalColor);
        }

        return null;
    }

    private void drawReferenceBackground(
            Graphics2D graphics,
            ChatReferenceHitbox hitbox,
            String localPlayerName)
    {
        final PlayerReference reference =
                hitbox.getReference();

        final Rectangle bounds =
                hitbox.getBounds();

        if (reference == null || bounds == null)
        {
            return;
        }

        final LocalMentionMatch localMatch =
                localMentionMatcher.match(
                        reference,
                        localPlayerName);

        final boolean isSelf =
                localMatch.isMatchesLocalPlayer();

        /*
         * Respect mention enablement.
         */
        if (isSelf && !config.mentionSelf())
        {
            return;
        }

        if (!isSelf && !config.mentionOthers())
        {
            return;
        }

        /*
         * Background highlighting applies only to the exact mention/tag hitbox.
         *
         * Mention Whole Message controls foreground coloring only and must not
         * suppress the self-reference highlight.
         */
        final Color backgroundColor =
                isSelf
                        ? config.selfBackgroundColor()
                        : config.otherBackgroundColor();

        /*
         * Fully transparent means background highlighting is disabled for
         * this reference type.
         */
        if (backgroundColor == null
                || backgroundColor.getAlpha() == 0)
        {
            return;
        }

        final Color previousColor =
                graphics.getColor();

        try
        {
            graphics.setColor(backgroundColor);

            graphics.fillRect(
                    bounds.x - BACKGROUND_HORIZONTAL_PADDING,
                    bounds.y - BACKGROUND_VERTICAL_PADDING,
                    bounds.width + (BACKGROUND_HORIZONTAL_PADDING * 2),
                    bounds.height + (BACKGROUND_VERTICAL_PADDING * 2));
        }
        finally
        {
            graphics.setColor(previousColor);
        }
    }

    private static void drawDottedUnderline(
            Graphics2D graphics,
            Rectangle bounds,
            Color color)
    {
        if (bounds == null || bounds.width <= 0)
        {
            return;
        }

        final Color previousColor =
                graphics.getColor();

        try
        {
            if (color != null)
            {
                graphics.setColor(color);
            }

            final int y =
                    bounds.y + bounds.height - 2;

            for (int x = bounds.x;
                 x < bounds.x + bounds.width;
                 x += 3)
            {
                graphics.drawLine(
                        x,
                        y,
                        Math.min(
                                x + 1,
                                bounds.x + bounds.width - 1),
                        y);
            }
        }
        finally
        {
            graphics.setColor(previousColor);
        }
    }
}