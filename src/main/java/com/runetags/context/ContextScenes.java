package com.runetags.context;

import static com.runetags.context.ContextMetrics.CONSTRUCTION;

import com.runetags.location.LocationIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Value;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Resolves instanced-area and template-region context for the local player.
 */
public class ContextScenes {
	private static final String PLAYER_OWNED_HOUSE = "Player-owned House";

	private final Client client;
	private final LocationIndex locationIndex;
	private final ContextObjects contextObjects;

	public ContextScenes(Client client, LocationIndex locationIndex, ContextObjects contextObjects) {
		this.client = client;
		this.locationIndex = locationIndex;
		this.contextObjects = contextObjects;
	}

	public SceneContext resolve(Player player) {
		if (player == null || player.getWorldView() == null) {
			return SceneContext.empty();
		}

		final WorldView worldView = player.getWorldView();
		if (!worldView.isInstance()) {
			return SceneContext.empty();
		}

		if (contextObjects != null && contextObjects.hasPohExitPortal(worldView)) {
			return new SceneContext(true, PLAYER_OWNED_HOUSE, -1, Collections.singletonList(CONSTRUCTION));
		}

		final LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null) {
			return new SceneContext(true, null, -1, Collections.emptyList());
		}

		final WorldPoint templatePoint = WorldPoint.fromLocalInstance(client, localPoint);
		if (templatePoint == null) {
			return new SceneContext(true, null, -1, Collections.emptyList());
		}

		final int templateRegionId = templatePoint.getRegionID();
		final String locationName = locationIndex != null
				? locationIndex.findName(templateRegionId)
				: null;
		return new SceneContext(true, locationName, templateRegionId, ContextRegions.metricsForLocation(locationName));
	}

	@Value
	public static class SceneContext {
		boolean instance;
		String locationName;
		int regionId;
		List<ProfileMetric> metrics;

		public SceneContext(boolean instance, String locationName, int regionId, List<ProfileMetric> metrics) {
			this.instance = instance;
			this.locationName = locationName;
			this.regionId = regionId;
			this.metrics = metrics == null
					? Collections.emptyList()
					: Collections.unmodifiableList(new ArrayList<>(metrics));
		}

		public static SceneContext empty() {
			return new SceneContext(false, null, -1, Collections.emptyList());
		}
	}
}
