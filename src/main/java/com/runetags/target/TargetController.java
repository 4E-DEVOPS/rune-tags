package com.runetags.target;

import com.runetags.Configurations;
import com.runetags.config.HideOthersMode;
import com.runetags.quickprofile.QuickProfileModel;

import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

@Slf4j
public class TargetController
{
    private final Client client;
    private final Configurations config;

    private volatile String targetName;
    private volatile Player targetPlayer;
    private volatile long targetStartedNanos;
    private volatile long hideOthersStartedNanos;

    public TargetController(Client client, Configurations config)
    {
        this.client = client;
        this.config = config;
    }

    public boolean toggleTarget(QuickProfileModel model)
    {
        if (model == null
                || !model.isNearby()
                || model.getDisplayName() == null
                || model.getDisplayName().trim().isEmpty())
        {
            return isTargeting();
        }

        return toggleTarget(
                model.getDisplayName());
    }

    /**
     * Toggle a target selected from RuneLite's native player menu.
     *
     * The live Player object is deliberately reduced back to the canonical
     * player name and re-resolved against the current scene. This keeps native
     * player-menu targeting and Quick-Card/chat targeting on the same nearby
     * validation path and avoids retaining a stale actor from an open menu.
     */
    public boolean toggleTarget(Player player)
    {
        if (player == null
                || player.getName() == null
                || player.getName().trim().isEmpty())
        {
            return isTargeting();
        }

        return toggleTarget(
                player.getName());
    }

    /**
     * Toggle a nearby player by name.
     *
     * This is the shared entry point used when the caller has semantic player
     * identity but no guaranteed-live Player object, such as a chat mention/tag
     * context-menu action.
     */
    public boolean toggleTarget(String playerName)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            return isTargeting();
        }

        if (!config.targetPlayerOption())
        {
            if (isTargeting())
            {
                clear("targeting disabled");
            }

            return false;
        }

        final String requestedName =
                playerName.trim();

        if (isTargetingName(requestedName))
        {
            clear("manually untargeted");
            return false;
        }

        final Player resolved =
                findNearbyPlayer(
                        requestedName);

        if (resolved == null
                || resolved.getName() == null
                || resolved.getName().trim().isEmpty())
        {
            return isTargeting();
        }

        final long now =
                System.nanoTime();

        targetName = resolved.getName();
        targetPlayer = resolved;
        targetStartedNanos = now;
        hideOthersStartedNanos = now;

        //log.debug("[RuneTags][Target] Targeted Player='{}'", targetName);

        return true;
    }

    public void refresh()
    {
        final String currentName = targetName;

        if (currentName == null)
        {
            targetPlayer = null;
            return;
        }

        if (!config.targetPlayerOption())
        {
            clear("targeting disabled");
            return;
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            clear("not logged in");
            return;
        }

        final int timeoutSeconds = Math.max(0, config.targetTimeout());

        if (timeoutSeconds > 0
                && targetStartedNanos > 0
                && System.nanoTime() - targetStartedNanos
                >= timeoutSeconds * 1_000_000_000L)
        {
            clear("timeout");
            return;
        }

        final Player resolved = findNearbyPlayer(currentName);

        if (resolved == null)
        {
            clear("player left scene");
            return;
        }

        targetPlayer = resolved;
    }

    public void clear()
    {
        clear("cleared");
    }

    public void clear(String reason)
    {
        final String oldName = targetName;

        targetName = null;
        targetPlayer = null;
        targetStartedNanos = 0L;
        hideOthersStartedNanos = 0L;

        if (oldName != null)
        {
            //log.debug("[RuneTags][Target] Untargeted Player='{}' | Reason='{}'", oldName, reason);
        }
    }

    public boolean isTargeting()
    {
        return targetName != null;
    }

    public boolean isTargetingName(String playerName)
    {
        final String currentName = targetName;

        return currentName != null
                && normalize(currentName).equals(normalize(playerName));
    }

    public Player getTargetPlayer()
    {
        return targetPlayer;
    }

    private Player findNearbyPlayer(String playerName)
    {
        final WorldView worldView = client.getTopLevelWorldView();

        if (worldView == null)
        {
            return null;
        }

        final String wanted = normalize(playerName);

        for (Player player : worldView.players())
        {
            if (player == null || player.getName() == null)
            {
                continue;
            }

            if (normalize(player.getName()).equals(wanted))
            {
                return player;
            }
        }

        return null;
    }

    public boolean shouldHideOtherPlayers()
    {
        if (!isTargeting())
        {
            return false;
        }

        final HideOthersMode mode =
                config.hideAllOthers();

        if (mode == null || !mode.isEnabled())
        {
            return false;
        }

        if (mode.isPersistent())
        {
            return true;
        }

        if (!mode.isTimed()
                || hideOthersStartedNanos <= 0L)
        {
            return false;
        }

        final long elapsedNanos =
                System.nanoTime()
                        - hideOthersStartedNanos;

        final long durationNanos =
                mode.getDurationSeconds()
                        * 1_000_000_000L;

        return elapsedNanos < durationNanos;
    }

    private static String normalize(String value)
    {
        return value == null
                ? ""
                : value.trim()
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
