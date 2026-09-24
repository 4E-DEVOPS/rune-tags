package com.runetags.location;

import lombok.Value;

/**
 * Immutable coarse-location snapshot for a player.
 *
 * Contextual metrics and encounter semantics remain owned by the context layer.
 */
@Value
public class PlayerLocation {
	String locationName;
	int regionId;

	public static PlayerLocation unknown() {
		return new PlayerLocation(null, -1);
	}

	public boolean hasLocation() {
		return locationName != null && !locationName.trim().isEmpty();
	}

	public boolean hasRegion() {
		return regionId >= 0;
	}
}
