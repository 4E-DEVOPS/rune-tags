package com.runetags.context;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Client-thread snapshot of the local world's coarse and encounter context.
 *
 * The package is named `context`, not `player`: these values describe the
 * environment around the local client. Quick Profiles may then use that
 * context for any nearby player.
 *
 * Resolution order:
 *
 * 1. Resolve the local player's coarse location from Locations.json.
 * 2. Inspect loaded NPCs for a high-confidence encounter override.
 * 3. If an NPC override exists, allow it to refine the coarse context.
 * 4. Otherwise preserve the coarse location and its contextual metrics.
 * 5. If the region is unmapped, an NPC override may still provide context.
 *
 * ContextMetricResolver is the single authority for deciding which
 * contextual KC/activity/skill metrics belong to a location or NPC.
 *
 * This allows:
 *
 * - ordinary locations to display only their configured skill/context metrics;
 * - boss staging areas to expose KC even when the boss is not loaded;
 * - shared boss areas to expose all relevant metrics outside encounters;
 * - loaded boss NPCs to refine shared/coarse areas to the active encounter;
 * - instanced or otherwise unmapped encounters to resolve through NPC fallback.
 */
public class PlayerContextService
{
    private final Client client;
    private final LocationIndex locationIndex;
    private final ContextMetricResolver contextMetricResolver;

    private volatile PlayerContext current =
            PlayerContext.unknown();

    public PlayerContextService(
            Client client,
            LocationIndex locationIndex,
            ContextMetricResolver contextMetricResolver)
    {
        this.client = client;
        this.locationIndex = locationIndex;
        this.contextMetricResolver = contextMetricResolver;
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
        final Player localPlayer =
                client.getLocalPlayer();

        if (localPlayer == null)
        {
            clear();
            return;
        }

        final WorldPoint point =
                localPlayer.getWorldLocation();

        if (point == null)
        {
            clear();
            return;
        }

        final int regionId =
                point.getRegionID();

        final String locationName =
                locationIndex.findName(regionId);

        /*
         * Inspect the active scene for a high-confidence boss/encounter NPC.
         *
         * NPC overrides are intentionally checked even when Locations.json
         * successfully resolves the coarse region.
         *
         * This allows a loaded encounter to refine a broader location:
         *
         * God Wars Dungeon
         * -> Graardor / Kree'Arra / K'ril / Zilyana / Nex
         *
         * General Graardor loaded
         * -> Graardor KC
         *
         * The same principle applies to any sparse NPC override configured
         * through ContextMetricCatalog.
         */
        final PlayerContext npcContext =
                findNpcContext(regionId);

        if (npcContext != null)
        {
            current = npcContext;
            return;
        }

        /*
         * No encounter refinement is active.
         *
         * Preserve the coarse location. ContextMetricResolver attaches all
         * KC/activity/skill metrics configured for that location.
         *
         * Examples:
         *
         * Zul-Andra
         * -> Zulrah KC
         *
         * Ver Sinhaza
         * -> ToB + HMT
         *
         * Chambers of Xeric
         * -> CoX + CM
         *
         * Varrock
         * -> Cooking + Smithing
         *
         * Woodcutting Guild
         * -> Woodcutting
         */
        if (locationName != null)
        {
            current =
                    contextMetricResolver.resolveLocation(
                            locationName,
                            regionId);

            return;
        }

        /*
         * Nothing in either the location index or NPC catalog describes the
         * current region.
         */
        current =
                contextMetricResolver.unknown(
                        regionId);
    }

    /**
     * Find the first loaded NPC with a high-confidence contextual override.
     *
     * NPC overrides are deliberately sparse. Ordinary NPCs do not appear in
     * the catalog and therefore cannot alter the coarse location context.
     */
    private PlayerContext findNpcContext(
            int regionId)
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
                    contextMetricResolver.resolveNpc(
                            npcName,
                            regionId);

            if (context != null)
            {
                return context;
            }
        }

        return null;
    }
}