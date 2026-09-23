package com.runetags.context;

import com.runetags.location.PlayerLocation;

import java.util.Collections;
import java.util.List;

/**
 * Resolves location and NPC context into ProfileMetricCatalog metrics.
 */
public final class ProfileMetricResolver {
	public PlayerContext resolveLocation(PlayerLocation location) {
		if (location == null) {
			return PlayerContext.unknown();
		}

		final List<ProfileMetric> metrics = location.getLocationName() != null
				? ProfileMetricCatalog.metricsForLocation(location.getLocationName())
				: Collections.emptyList();

		return new PlayerContext(location, metrics);
	}

	/*
	 * Resolves an NPC-specific context override independently from the broader location mapping.
	 */
	public PlayerContext resolveNpc(String npcName, PlayerLocation location) {
		if (npcName == null) {
			return null;
		}

		final ProfileMetricCatalog.ContextOverride override = ProfileMetricCatalog.overrideForNpc(npcName);
		if (override == null) {
			return null;
		}

		final int regionId = location != null
				? location.getRegionId()
				: -1;
		final PlayerLocation resolvedLocation = new PlayerLocation(override.getLocationName(), regionId);

		return new PlayerContext(resolvedLocation, override.getMetrics());
	}

	public PlayerContext unknown(PlayerLocation location) {
		return new PlayerContext(location, Collections.emptyList());
	}
}
