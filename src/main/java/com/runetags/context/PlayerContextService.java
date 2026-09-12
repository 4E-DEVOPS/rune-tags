package com.runetags.context;

import com.runetags.location.PlayerLocation;
import com.runetags.location.PlayerLocationService;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;

/**
 * Client-thread snapshot of the local world's coarse and encounter context.
 *
 * The local player's coarse location is resolved first, then a loaded
 * high-confidence NPC may refine that context to the active encounter.
 * ProfileMetricResolver remains the authority for the contextual hiscores
 * associated with either source.
 */
public class PlayerContextService
{
    private final Client client;
    private final PlayerLocationService playerLocationService;
    private final ProfileMetricResolver profileMetricResolver;

    private volatile PlayerContext current =
            PlayerContext.unknown();

    public PlayerContextService(
            Client client,
            PlayerLocationService playerLocationService,
            ProfileMetricResolver profileMetricResolver)
    {
        this.client =
                client;

        this.playerLocationService =
                playerLocationService;

        this.profileMetricResolver =
                profileMetricResolver;
    }

    public PlayerContext getCurrent()
    {
        return current;
    }

    public void clear()
    {
        current = PlayerContext.unknown();
    }

    /**
     * Refresh the local world context.
     *
     * Must run on RuneLite's client thread.
     */
    public void refresh()
    {
        final PlayerLocation location =
                playerLocationService.getCurrent();

        if (location == null
                || !location.hasRegion())
        {
            clear();
            return;
        }

        final PlayerContext npcContext =
                findNpcContext(
                        location);

        if (npcContext != null)
        {
            current =
                    npcContext;

            return;
        }

        current =
                profileMetricResolver.resolveLocation(
                        location);
    }

    /**
     * Find the first loaded NPC with a contextual override.
     *
     * Overrides are deliberately sparse so ordinary NPCs
     * cannot alter the coarse location context.
     */
    private PlayerContext findNpcContext(
            PlayerLocation location)
    {
        final WorldView worldView =
                client.getTopLevelWorldView();

        if (worldView == null)
        {
            return null;
        }

        for (NPC npc : worldView.npcs())
        {
            if (npc == null)
            {
                continue;
            }

            final String npcName =
                    npc.getName();

            if (npcName == null
                    || npcName.isEmpty())
            {
                continue;
            }

            final PlayerContext context =
                    profileMetricResolver.resolveNpc(
                            npcName,
                            location);

            if (context != null)
            {
                return context;
            }
        }

        return null;
    }
}