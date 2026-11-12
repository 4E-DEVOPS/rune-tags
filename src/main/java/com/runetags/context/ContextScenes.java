package com.runetags.context;

import static com.runetags.context.ContextMetrics.CM;
import static com.runetags.context.ContextMetrics.COX;
import static com.runetags.context.ContextMetrics.HMT;
import static com.runetags.context.ContextMetrics.TOA;
import static com.runetags.context.ContextMetrics.TOA_EXPERT;
import static com.runetags.context.ContextMetrics.TOB;
import static com.runetags.context.ContextSkills.CONSTRUCTION;

import com.runetags.location.InstanceIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.Value;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Resolves instanced-area context from template instance IDs.
 */
public class ContextScenes {
	private static final String PLAYER_OWNED_HOUSE = "Player-owned House";
	private static final Map<String, List<ProfileMetric>> BY_INSTANCE = buildInstanceMetrics();

	private final Client client;
	private final InstanceIndex instanceIndex;
	private final ContextObjects contextObjects;

	public ContextScenes(Client client, InstanceIndex instanceIndex, ContextObjects contextObjects) {
		this.client = client;
		this.instanceIndex = instanceIndex;
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

		final int instanceId = templatePoint.getRegionID();
		final String locationName = instanceIndex != null
				? instanceIndex.findName(instanceId)
				: null;
		return new SceneContext(true, locationName, instanceId, metricsForInstance(locationName));
	}

	public static List<ProfileMetric> metricsForInstance(String locationName) {
		if (locationName == null) {
			return Collections.emptyList();
		}

		return BY_INSTANCE.getOrDefault(locationName.toLowerCase(Locale.ROOT), Collections.emptyList());
	}

	private static Map<String, List<ProfileMetric>> buildInstanceMetrics() {
		final Map<String, List<ProfileMetric>> map = new HashMap<>();

		put(map, PLAYER_OWNED_HOUSE, CONSTRUCTION);

		put(map, "Great Olm", COX, CM);

		put(map, "Maiden of Sugadinti", TOB, HMT);
		put(map, "The Maiden of Sugadinti", TOB, HMT);
		put(map, "Nylocas Vasilias", TOB, HMT);
		put(map, "Pestilent Bloat", TOB, HMT);
		put(map, "Sotetseg", TOB, HMT);
		put(map, "Verzik Vitur", TOB, HMT);
		put(map, "Xarpus", TOB, HMT);

		put(map, "Akkha", TOA, TOA_EXPERT);
		put(map, "Ba-Ba", TOA, TOA_EXPERT);
		put(map, "Kephri", TOA, TOA_EXPERT);
		put(map, "Osmumten's Burial Chamber", TOA, TOA_EXPERT);
		put(map, "Path of Apmeken", TOA, TOA_EXPERT);
		put(map, "Path of Crondis", TOA, TOA_EXPERT);
		put(map, "Path of Het", TOA, TOA_EXPERT);
		put(map, "Path of Scabaras", TOA, TOA_EXPERT);
		put(map, "The Nexus", TOA, TOA_EXPERT);
		put(map, "The Wardens", TOA, TOA_EXPERT);
		put(map, "Zebak", TOA, TOA_EXPERT);

		return Collections.unmodifiableMap(map);
	}

	private static void put(Map<String, List<ProfileMetric>> map, String location, ProfileMetric... metrics) {
		map.put(location.toLowerCase(Locale.ROOT), Collections.unmodifiableList(Arrays.asList(metrics)));
	}

	@Value
	public static class SceneContext {
		boolean instance;
		String locationName;
		int instanceId;
		List<ProfileMetric> metrics;

		public SceneContext(boolean instance, String locationName, int instanceId, List<ProfileMetric> metrics) {
			this.instance = instance;
			this.locationName = locationName;
			this.instanceId = instanceId;
			this.metrics = metrics == null
					? Collections.emptyList()
					: Collections.unmodifiableList(new ArrayList<>(metrics));
		}

		public static SceneContext empty() {
			return new SceneContext(false, null, -1, Collections.emptyList());
		}
	}
}
