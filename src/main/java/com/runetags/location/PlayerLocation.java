package com.runetags.location;

import lombok.Value;

/**
 * Immutable coarse location snapshot for a player.
 *
 * Represents only where the player is; contextual metrics and encounter
 * semantics belong to the context layer.
 */
@Value
public class PlayerLocation
{
    String locationName;
    int regionId;

    public static PlayerLocation unknown()
    {
        return new PlayerLocation(
                null,
                -1);
    }

    public boolean hasLocation()
    {
        return locationName != null
                && !locationName.trim().isEmpty();
    }

    public boolean hasRegion()
    {
        return regionId >= 0;
    }
}