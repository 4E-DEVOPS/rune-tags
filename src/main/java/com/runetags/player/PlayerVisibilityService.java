package com.runetags.player;

import com.runetags.context.TargetController;

import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.client.callback.Hooks;

/**
 * Controls visibility of player renderables while RuneTags' "Hide All Others" mode is active.
 *
 * When active:
 * - current target = visible
 * - every other player = hidden
 *
 * This applies to both:
 * - 3D player models
 * - 2D player UI elements such as overheads / health bars
 *
 * NPCs, projectiles, graphics objects, etc. are unaffected.
 */
public class PlayerVisibilityService
{
    private final Hooks hooks;
    private final TargetController targetController;

    private final Hooks.RenderableDrawListener drawListener =
            this::shouldDraw;

    private boolean registered;

    public PlayerVisibilityService(
            Hooks hooks,
            TargetController targetController)
    {
        this.hooks = hooks;
        this.targetController = targetController;
    }

    public void start()
    {
        if (registered)
        {
            return;
        }

        hooks.registerRenderableDrawListener(
                drawListener);

        registered = true;
    }

    public void stop()
    {
        if (!registered)
        {
            return;
        }

        hooks.unregisterRenderableDrawListener(
                drawListener);

        registered = false;
    }

    private boolean shouldDraw(
            Renderable renderable,
            boolean drawingUi)
    {
        /*
         * RuneTags only modifies Player visibility.
         */
        if (!(renderable instanceof Player))
        {
            return true;
        }

        /*
         * When "Hide All Others" is inactive, RuneTags must not
         * interfere with normal RuneLite/Jagex rendering.
         */
        if (!targetController.shouldHideOtherPlayers())
        {
            return true;
        }

        final Player player =
                (Player) renderable;

        final String playerName =
                player.getName();

        /*
         * A null-name Player should be left alone rather than risk hiding
         * something whose identity RuneTags cannot safely determine.
         */
        if (playerName == null
                || playerName.isEmpty())
        {
            return true;
        }

        /*
         * Hide EVERYTHING except the active RuneTags target.
         *
         * This deliberately includes:
         *
         * - local player
         * - friends
         * - clan members
         * - friends-chat members
         * - party members
         * - strangers
         *
         * drawingUi is intentionally ignored so both the player model and
         * their 2D UI elements follow the same visibility rule.
         */
        return targetController.isTargetingName(
                playerName);
    }
}