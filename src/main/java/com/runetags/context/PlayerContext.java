package com.runetags.context;

import com.runetags.location.PlayerLocation;

import java.util.Collections;
import java.util.List;

import lombok.Value;

/**
 * Resolved contextual metric state for a player.
 *
 * PlayerLocation answers WHERE the player is.
 * PlayerContext answers WHICH profile metrics are relevant there.
 */
@Value
public class PlayerContext
{
    PlayerLocation location;
    List<ProfileMetric> metrics;

    public PlayerContext(
            PlayerLocation location,
            List<ProfileMetric> metrics)
    {
        this.location =
                location != null
                        ? location
                        : PlayerLocation.unknown();

        this.metrics =
                metrics == null
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(
                        metrics);
    }

    public static PlayerContext unknown()
    {
        return new PlayerContext(
                PlayerLocation.unknown(),
                Collections.emptyList());
    }

    public static PlayerContext unknown(
            int regionId)
    {
        return new PlayerContext(
                new PlayerLocation(
                        null,
                        regionId),
                Collections.emptyList());
    }

    public String getLocationName()
    {
        return location.getLocationName();
    }

    public int getRegionId()
    {
        return location.getRegionId();
    }

    public boolean hasLocation()
    {
        return location.hasLocation();
    }

    public boolean hasMetrics()
    {
        return !metrics.isEmpty();
    }
}