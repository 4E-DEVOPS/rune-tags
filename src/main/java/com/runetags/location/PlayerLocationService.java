package com.runetags.location;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Client-thread snapshot of the local player's coarse location.
 *
 * This service answers only WHERE the local player is.
 *
 * It deliberately does not:
 *
 * - inspect NPCs;
 * - choose profile metrics;
 * - resolve boss encounters;
 * - perform HiScore lookups;
 * - decide which KC/activity/skill values should be displayed.
 *
 * Those concerns belong to the contextual metric layer.
 */
public class PlayerLocationService
{
    private final Client client;
    private final LocationIndex locationIndex;

    private volatile PlayerLocation current =
            PlayerLocation.unknown();

    public PlayerLocationService(
            Client client,
            LocationIndex locationIndex)
    {
        this.client =
                client;

        this.locationIndex =
                locationIndex;
    }

    public PlayerLocation getCurrent()
    {
        return current;
    }

    public void clear()
    {
        current =
                PlayerLocation.unknown();
    }

    /**
     * Refresh the local player's coarse location.
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
                locationIndex.findName(
                        regionId);

        current =
                new PlayerLocation(
                        locationName,
                        regionId);
    }
}