package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ReferenceHitbox;
import com.runetags.chat.ReferenceLayoutService;
import com.runetags.chat.ReferenceLayoutService.LayoutResult;
import com.runetags.chat.ReferenceLayoutService.LocalHighlight;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Renders chat-reference decorations and publishes their current interaction
 * hitboxes across CHATBOX and SPLIT_PRIVATE surfaces.
 *
 * Also renders local-reference backgrounds and unresolved-tag underlines.
 */
public class ChatReferenceOverlay extends Overlay
{
    private static final int BACKGROUND_HORIZONTAL_PADDING = 1;
    private static final int BACKGROUND_VERTICAL_PADDING = 0;

    private static final int MAX_OCCLUSION_WIDGET_DEPTH = 12;

    private final ReferenceLayoutService layoutService;
    private final ChatHitboxRegistry registry;
    private final Client client;
    private final Configurations config;
    private final LocalMentionMatcher localMentionMatcher;

    public ChatReferenceOverlay(
            ReferenceLayoutService layoutService,
            ChatHitboxRegistry registry,
            Client client,
            Configurations config,
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
         * Native chat text is clipped to the chatbox, but ABOVE_WIDGETS overlays are
         * not. Use the visible chat area to constrain RuneTags rendering and input.
         */
        final Widget chatbox =
                client.getWidget(InterfaceID.Chatbox.SCROLLAREA);

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
        final LayoutResult layoutResult =
                layoutService.layout();

        final List<ReferenceHitbox> hitboxes =
                layoutResult.getHitboxes();

        final List<LocalHighlight> localHighlights =
                layoutResult.getLocalHighlights();

        /*
         * CHATBOX references are clipped to the visible chat history, including
         * partially scrolled rows. SPLIT_PRIVATE references come from visible PmChat
         * widgets outside CHATBOX_MESSAGE_LINES and must not use that clip.
         */
        final Widget splitPrivateRoot =
                client.getWidget(InterfaceID.PmChat.CONTAINER);

        /*
         * ABOVE_WIDGETS is not automatically obscured by interfaces covering chat, so
         * respect RuneScape's native no-click-through regions explicitly.
         */
        final Rectangle interactionBounds =
                unionInteractionBounds(
                        hitboxes,
                        localHighlights);

        final List<Rectangle> blockingBounds =
                collectBlockingWidgetBounds(
                        chatbox,
                        splitPrivateRoot,
                        interactionBounds);

        final List<ReferenceHitbox> visibleHitboxes =
                new ArrayList<>();

        for (ReferenceHitbox hitbox : hitboxes)
        {
            if (hitbox == null
                    || hitbox.getBounds() == null)
            {
                continue;
            }

            Rectangle visibleBounds =
                    new Rectangle(
                            hitbox.getBounds());

            switch (hitbox.getSurface())
            {
                case CHATBOX:
                    if (!hasVisibleChatbox
                            || !chatboxBounds.intersects(
                            visibleBounds))
                    {
                        continue;
                    }

                    visibleBounds =
                            chatboxBounds.intersection(
                                    visibleBounds);
                    break;

                case SPLIT_PRIVATE:
                    break;

                default:
                    continue;
            }

            if (visibleBounds.isEmpty())
            {
                continue;
            }

            /*
             * Preserve visible fragments when an interface
             * obscures only part of a reference.
             */
            for (Rectangle fragment
                    : subtractBlockingBounds(
                    visibleBounds,
                    blockingBounds))
            {
                if (fragment == null
                        || fragment.isEmpty())
                {
                    continue;
                }

                visibleHitboxes.add(
                        new ReferenceHitbox(
                                hitbox.getMessageId(),
                                fragment,
                                hitbox.getReference(),
                                hitbox.getSurface()));
            }
        }

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

        final Shape unobscuredClip =
                buildUnobscuredClip(
                        originalClip,
                        blockingBounds);

        try
        {
            /*
             * Draw non-clickable local/self backgrounds before clickable reference
             * decorations so reference decoration remains visually above them.
             */
            drawLocalHighlights(
                    graphics,
                    localHighlights,
                    chatboxBounds,
                    hasVisibleChatbox,
                    unobscuredClip);

            graphics.setColor(
                    originalColor);

            /*
             * Render each reference independently because CHATBOX
             * and SPLIT_PRIVATE use different clipping rules.
             */
            for (ReferenceHitbox hitbox : visibleHitboxes)
            {
                final PlayerReference reference =
                        hitbox.getReference();

                if (reference == null)
                {
                    continue;
                }

                graphics.setClip(unobscuredClip);

                if (hitbox.getSurface()
                        == ReferenceLayoutService.Surface.CHATBOX)
                {
                    /*
                     * Clip CHATBOX decorations so ABOVE_WIDGETS rendering
                     * cannot escape the visible chat history.
                     */
                    graphics.clip(chatboxBounds);
                }

                /*
                 * SENDER is an interaction target, not a semantic mention,
                 * so clickable sender names must not receive mention highlighting.
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
                 * Unresolved tags retain their dotted underline on either chat surface.
                 */
                if (config.underlineMentions()
                        && reference.getType() == ReferenceType.TAG
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

    /**
     * Build the smallest canvas region needed for RuneTags chat rendering and
     * interaction so native widget traversal can ignore unrelated interfaces.
     */
    private static Rectangle unionInteractionBounds(
            List<ReferenceHitbox> hitboxes,
            List<LocalHighlight> localHighlights)
    {
        Rectangle result =
                null;

        if (hitboxes != null)
        {
            for (ReferenceHitbox hitbox : hitboxes)
            {
                if (hitbox == null
                        || hitbox.getBounds() == null
                        || hitbox.getBounds().isEmpty())
                {
                    continue;
                }

                result =
                        result == null
                                ? new Rectangle(
                                hitbox.getBounds())
                                : result.union(
                                hitbox.getBounds());
            }
        }

        if (localHighlights != null)
        {
            for (LocalHighlight highlight : localHighlights)
            {
                if (highlight == null
                        || highlight.getBounds() == null
                        || highlight.getBounds().isEmpty())
                {
                    continue;
                }

                result =
                        result == null
                                ? new Rectangle(
                                highlight.getBounds())
                                : result.union(
                                highlight.getBounds());
            }
        }

        if (result != null)
        {
            result.grow(
                    BACKGROUND_HORIZONTAL_PADDING,
                    BACKGROUND_VERTICAL_PADDING);
        }

        return result;
    }

    /**
     * Find visible native widgets that prevent interaction with widgets beneath
     * them, excluding the chat surfaces RuneTags deliberately decorates.
     */
    private List<Rectangle> collectBlockingWidgetBounds(
            Widget chatbox,
            Widget splitPrivateRoot,
            Rectangle interactionBounds)
    {
        if (interactionBounds == null
                || interactionBounds.isEmpty())
        {
            return Collections.emptyList();
        }

        final Widget[] roots =
                client.getWidgetRoots();

        if (roots == null
                || roots.length == 0)
        {
            return Collections.emptyList();
        }

        final List<Rectangle> blockingBounds =
                new ArrayList<>();

        final Set<Widget> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        for (Widget root : roots)
        {
            collectBlockingWidgetBounds(
                    root,
                    chatbox,
                    splitPrivateRoot,
                    interactionBounds,
                    blockingBounds,
                    visited,
                    0);
        }

        return blockingBounds;
    }

    private void collectBlockingWidgetBounds(
            Widget widget,
            Widget chatbox,
            Widget splitPrivateRoot,
            Rectangle interactionBounds,
            List<Rectangle> blockingBounds,
            Set<Widget> visited,
            int depth)
    {
        if (widget == null
                || depth > MAX_OCCLUSION_WIDGET_DEPTH
                || !visited.add(
                widget)
                || widget.isHidden())
        {
            return;
        }

        final Rectangle bounds =
                widget.getBounds();

        /*
         * Prune widget subtrees outside the RuneTags interaction region.
         */
        if (bounds != null
                && !bounds.isEmpty()
                && !interactionBounds.intersects(
                bounds))
        {
            return;
        }

        /*
         * Once a no-click-through widget blocks this region,
         * its descendants cannot expose the chat beneath it.
         */
        if (bounds != null
                && !bounds.isEmpty()
                && widget.getNoClickThrough()
                && !belongsToChatPresentation(
                widget,
                chatbox,
                splitPrivateRoot))
        {
            blockingBounds.add(
                    interactionBounds.intersection(
                            bounds));

            return;
        }

        collectBlockingWidgetChildren(
                widget.getChildren(),
                chatbox,
                splitPrivateRoot,
                interactionBounds,
                blockingBounds,
                visited,
                depth + 1);

        collectBlockingWidgetChildren(
                widget.getStaticChildren(),
                chatbox,
                splitPrivateRoot,
                interactionBounds,
                blockingBounds,
                visited,
                depth + 1);

        collectBlockingWidgetChildren(
                widget.getNestedChildren(),
                chatbox,
                splitPrivateRoot,
                interactionBounds,
                blockingBounds,
                visited,
                depth + 1);
    }

    private void collectBlockingWidgetChildren(
            Widget[] children,
            Widget chatbox,
            Widget splitPrivateRoot,
            Rectangle interactionBounds,
            List<Rectangle> blockingBounds,
            Set<Widget> visited,
            int depth)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collectBlockingWidgetBounds(
                    child,
                    chatbox,
                    splitPrivateRoot,
                    interactionBounds,
                    blockingBounds,
                    visited,
                    depth);
        }
    }

    /**
     * A no-click-through Widget belonging to either native chat presentation must
     * not hide RuneTags from the very chat surface it is decorating.
     */
    private static boolean belongsToChatPresentation(
            Widget widget,
            Widget chatbox,
            Widget splitPrivateRoot)
    {
        return sharesWidgetBranch(
                widget,
                chatbox)
                || sharesWidgetBranch(
                widget,
                splitPrivateRoot);
    }

    private static boolean sharesWidgetBranch(
            Widget first,
            Widget second)
    {
        if (first == null
                || second == null)
        {
            return false;
        }

        return isAncestorOrSelf(
                first,
                second)
                || isAncestorOrSelf(
                second,
                first);
    }

    private static boolean isAncestorOrSelf(
            Widget ancestor,
            Widget widget)
    {
        for (Widget current = widget;
             current != null;
             current = current.getParent())
        {
            if (current == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Build the RuneTags paint region by subtracting native no-click-through
     * interface bounds from the current graphics clip.
     */
    private Shape buildUnobscuredClip(
            Shape originalClip,
            List<Rectangle> blockingBounds)
    {
        final Area visibleArea =
                originalClip != null
                        ? new Area(
                        originalClip)
                        : new Area(
                        new Rectangle(
                                0,
                                0,
                                client.getCanvasWidth(),
                                client.getCanvasHeight()));

        if (blockingBounds != null)
        {
            for (Rectangle blocker : blockingBounds)
            {
                if (blocker == null
                        || blocker.isEmpty())
                {
                    continue;
                }

                visibleArea.subtract(
                        new Area(
                                blocker));
            }
        }

        return visibleArea;
    }

    /**
     * Remove native interface regions from one clickable reference rectangle.
     *
     * Rectangle fragments are retained individually because ChatHitboxRegistry
     * stores rectangular hit targets.
     */
    private static List<Rectangle> subtractBlockingBounds(
            Rectangle source,
            List<Rectangle> blockingBounds)
    {
        if (source == null
                || source.isEmpty())
        {
            return Collections.emptyList();
        }

        List<Rectangle> fragments =
                new ArrayList<>();

        fragments.add(
                new Rectangle(
                        source));

        if (blockingBounds == null
                || blockingBounds.isEmpty())
        {
            return fragments;
        }

        for (Rectangle blocker : blockingBounds)
        {
            if (blocker == null
                    || blocker.isEmpty()
                    || fragments.isEmpty())
            {
                continue;
            }

            final List<Rectangle> next =
                    new ArrayList<>();

            for (Rectangle fragment : fragments)
            {
                subtractRectangle(
                        fragment,
                        blocker,
                        next);
            }

            fragments =
                    next;
        }

        return fragments;
    }

    private static void subtractRectangle(
            Rectangle source,
            Rectangle blocker,
            List<Rectangle> output)
    {
        if (source == null
                || blocker == null
                || output == null)
        {
            return;
        }

        final Rectangle intersection =
                source.intersection(
                        blocker);

        if (intersection.isEmpty())
        {
            output.add(
                    source);
            return;
        }

        final int sourceRight =
                source.x
                        + source.width;

        final int sourceBottom =
                source.y
                        + source.height;

        final int intersectionRight =
                intersection.x
                        + intersection.width;

        final int intersectionBottom =
                intersection.y
                        + intersection.height;

        /*
         * Area above the blocker.
         */
        if (intersection.y > source.y)
        {
            output.add(
                    new Rectangle(
                            source.x,
                            source.y,
                            source.width,
                            intersection.y
                                    - source.y));
        }

        /*
         * Area below the blocker.
         */
        if (intersectionBottom < sourceBottom)
        {
            output.add(
                    new Rectangle(
                            source.x,
                            intersectionBottom,
                            source.width,
                            sourceBottom
                                    - intersectionBottom));
        }

        /*
         * Area left of the blocker within the blocker's vertical band.
         */
        if (intersection.x > source.x)
        {
            output.add(
                    new Rectangle(
                            source.x,
                            intersection.y,
                            intersection.x
                                    - source.x,
                            intersection.height));
        }

        /*
         * Area right of the blocker within the blocker's vertical band.
         */
        if (intersectionRight < sourceRight)
        {
            output.add(
                    new Rectangle(
                            intersectionRight,
                            intersection.y,
                            sourceRight
                                    - intersectionRight,
                            intersection.height));
        }
    }

    private void drawLocalHighlights(
            Graphics2D graphics,
            List<LocalHighlight> highlights,
            Rectangle chatboxBounds,
            boolean hasVisibleChatbox,
            Shape unobscuredClip)
    {
        if (highlights == null
                || highlights.isEmpty()
                || !config.highlightBackground())
        {
            return;
        }

        final Color backgroundColor =
                config.selfBackgroundColor();

        if (backgroundColor == null
                || backgroundColor.getAlpha() == 0)
        {
            return;
        }

        graphics.setColor(
                backgroundColor);

        for (LocalHighlight highlight : highlights)
        {
            if (highlight == null
                    || highlight.getBounds() == null)
            {
                continue;
            }

            graphics.setClip(
                    unobscuredClip);

            Rectangle visibleBounds =
                    highlight.getBounds();

            switch (highlight.getSurface())
            {
                case CHATBOX:
                    if (!hasVisibleChatbox
                            || !chatboxBounds.intersects(
                            visibleBounds))
                    {
                        continue;
                    }

                    graphics.clip(
                            chatboxBounds);

                    visibleBounds =
                            chatboxBounds.intersection(
                                    visibleBounds);

                    if (visibleBounds.isEmpty())
                    {
                        continue;
                    }

                    break;

                case SPLIT_PRIVATE:
                    break;

                default:
                    continue;
            }

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

        graphics.setClip(
                unobscuredClip);
    }

    private void drawReferenceBackground(
            Graphics2D graphics,
            ReferenceHitbox hitbox,
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
         * Reference backgrounds are independent from foreground mention coloring and
         * apply only to the exact mention/tag hitbox. Mention Whole Message therefore
         * does not affect this path.
         */
        final Color backgroundColor =
                isSelf
                        ? config.selfBackgroundColor()
                        : config.otherBackgroundColor();

        /*
         * A fully transparent color disables this reference background.
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

            /*
             * Segmented Dots
             */
            for (int x = bounds.x;
                 x < bounds.x + bounds.width;
                 x += 3)
            {
//          — 1px dotted segments —
                graphics.fillRect(
                        x,
                        y,
                        1,
                        1);
//          — 2px horizontal segments —
//                graphics.drawLine(
//                        x,
//                        y,
//                        Math.min(x + 1, bounds.x + bounds.width - 1), y);
            }
        }
        finally
        {
            graphics.setColor(previousColor);
        }
    }
}