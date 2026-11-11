package com.runetags.location;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Tracks the local player's coarse client-thread location.
 *
 * Contextual metric selection and encounter semantics remain owned by the context layer.
 */
public class PlayerLocationService {
	private final Client client;
	private final RegionIndex regionIndex;

	private volatile PlayerLocation current = PlayerLocation.unknown();

	public PlayerLocationService(Client client, RegionIndex regionIndex) {
		this.client = client;
		this.regionIndex = regionIndex;
	}

	public PlayerLocation getCurrent() {
		return current;
	}

	public void clear() {
		current = PlayerLocation.unknown();
	}

	/*
	 * Refreshes the local player's coarse location on RuneLite's client thread.
	 */
	public void refresh() {
		final Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) {
			clear();
			return;
		}

		final WorldPoint point = localPlayer.getWorldLocation();
		if (point == null) {
			clear();
			return;
		}

		final int regionId = point.getRegionID();
		final String locationName = regionIndex.findName(regionId);
		current = new PlayerLocation(locationName, regionId);
	}
}
