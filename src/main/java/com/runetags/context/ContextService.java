package com.runetags.context;

import com.runetags.location.LocationIndex;
import com.runetags.location.PlayerLocation;
import com.runetags.location.PlayerLocationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Couples NPC, object, animation, scene, and region signals into player context.
 */
public class ContextService {
	private final Client client;
	private final PlayerLocationService playerLocationService;
	private final LocationIndex locationIndex;
	private final ContextScenes contextScenes;
	private final ContextNPCs contextNPCs;
	private final ContextObjects contextObjects;
	private final ContextAnimations contextAnimations;

	private volatile PlayerContext current = PlayerContext.unknown();

	public ContextService(Client client, PlayerLocationService playerLocationService, LocationIndex locationIndex) {
		this.client = client;
		this.playerLocationService = playerLocationService;
		this.locationIndex = locationIndex;
		this.contextObjects = new ContextObjects(client);
		this.contextNPCs = new ContextNPCs();
		this.contextAnimations = new ContextAnimations();
		this.contextScenes = new ContextScenes(client, locationIndex, contextObjects);
	}

	ContextService(Client client, PlayerLocationService playerLocationService, LocationIndex locationIndex, ContextScenes contextScenes, ContextNPCs contextNPCs, ContextObjects contextObjects, ContextAnimations contextAnimations) {
		this.client = client;
		this.playerLocationService = playerLocationService;
		this.locationIndex = locationIndex;
		this.contextScenes = contextScenes;
		this.contextNPCs = contextNPCs;
		this.contextObjects = contextObjects;
		this.contextAnimations = contextAnimations;
	}

	public PlayerContext getCurrent() {
		return current;
	}

	/**
	 * Resolves context for a specific nearby player. Environmental and animation
	 * signals are evaluated against that player's own state and WorldView.
	 */
	public PlayerContext resolve(Player player) {
		if (player == null) {
			return PlayerContext.unknown();
		}

		final PlayerLocation regionLocation = resolveRegionLocation(player);
		final WorldView worldView = player.getWorldView();
		final List<ContextDefinition> npcContexts = contextNPCs.findAll(worldView);
		final ContextDefinition objectContext = contextObjects.resolveNearby(player);
		final ContextDefinition animationContext = contextAnimations.resolve(player);
		final ContextScenes.SceneContext sceneContext = contextScenes.resolve(player);

		final String locationName = firstNonBlank(
				uniqueLocation(npcContexts),
				objectContext.getLocationName(),
				sceneContext.getLocationName(),
				instanceFallback(sceneContext, isLocalPlayer(player)),
				!sceneContext.isInstance()
						? regionLocation.getLocationName()
						: null);
		final int regionId = sceneContext.isInstance()
				? sceneContext.getRegionId()
				: regionLocation.getRegionId();
		final PlayerLocation resolvedLocation = new PlayerLocation(locationName, regionId);

		final boolean hasNpcMetrics = hasMetrics(npcContexts);
		final LinkedHashMap<String, ProfileMetric> metrics = new LinkedHashMap<>();
		addDefinitions(metrics, npcContexts, false);
		addMetrics(metrics, objectContext.getMetrics(), false);
		addMetrics(metrics, animationContext.getMetrics(), false);
		addMetrics(metrics, sceneContext.getMetrics(), hasNpcMetrics);
		if (!sceneContext.isInstance()) {
			addMetrics(metrics, ContextRegions.metricsForLocation(regionLocation.getLocationName()), hasNpcMetrics);
		}

		return new PlayerContext(resolvedLocation, new ArrayList<>(metrics.values()));
	}

	public void clear() {
		current = PlayerContext.unknown();
		contextObjects.clear();
	}

	public void clearSceneObjects() {
		contextObjects.clear();
	}

	public void bootstrapSceneObjects() {
		final Player localPlayer = client != null
				? client.getLocalPlayer()
				: null;
		final WorldView worldView = localPlayer != null
				? localPlayer.getWorldView()
				: null;
		contextObjects.bootstrap(worldView);
	}

	public void onObjectSpawned(TileObject object) {
		contextObjects.onSpawn(object);
	}

	public void onObjectDespawned(TileObject object) {
		contextObjects.onDespawn(object);
	}

	/**
	 * Refreshes the local player's cached context. Nearby player cards call
	 * {@link #resolve(Player)} so object proximity and animations are evaluated per target.
	 */
	public void refresh() {
		final Player localPlayer = client != null
				? client.getLocalPlayer()
				: null;
		current = localPlayer != null
				? resolve(localPlayer)
				: PlayerContext.unknown();
	}

	private PlayerLocation resolveRegionLocation(Player player) {
		if (player == null) {
			return PlayerLocation.unknown();
		}

		if (isLocalPlayer(player) && playerLocationService != null && playerLocationService.getCurrent() != null) {
			return playerLocationService.getCurrent();
		}

		final WorldPoint worldPoint = player.getWorldLocation();
		if (worldPoint == null) {
			return PlayerLocation.unknown();
		}

		final int regionId = worldPoint.getRegionID();
		final String locationName = locationIndex != null
				? locationIndex.findName(regionId)
				: null;
		return new PlayerLocation(locationName, regionId);
	}

	private boolean isLocalPlayer(Player player) {
		return client != null && player != null && player == client.getLocalPlayer();
	}

	private static String instanceFallback(ContextScenes.SceneContext sceneContext, boolean localPlayer) {
		if (!localPlayer || sceneContext == null || !sceneContext.isInstance() || sceneContext.getRegionId() < 0) {
			return null;
		}

		final int regionId = sceneContext.getRegionId();
		final int regionX = (regionId >>> 8) & 0xFF;
		final int regionY = regionId & 0xFF;
		return "Instance: [" + regionX + ", " + regionY + "]";
	}

	private static boolean hasMetrics(List<ContextDefinition> definitions) {
		if (definitions == null) {
			return false;
		}

		for (ContextDefinition definition : definitions) {
			if (definition != null && definition.getMetrics() != null && !definition.getMetrics().isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private static String uniqueLocation(List<ContextDefinition> definitions) {
		if (definitions == null || definitions.isEmpty()) {
			return null;
		}

		final LinkedHashSet<String> names = new LinkedHashSet<>();
		for (ContextDefinition definition : definitions) {
			if (definition == null || definition.getLocationName() == null || definition.getLocationName().trim().isEmpty()) {
				continue;
			}
			names.add(definition.getLocationName());
		}

		return names.size() == 1
				? names.iterator().next()
				: null;
	}

	private static void addDefinitions(Map<String, ProfileMetric> target, List<ContextDefinition> definitions, boolean skillsOnly) {
		if (definitions == null) {
			return;
		}

		for (ContextDefinition definition : definitions) {
			if (definition != null) {
				addMetrics(target, definition.getMetrics(), skillsOnly);
			}
		}
	}

	private static void addMetrics(Map<String, ProfileMetric> target, List<ProfileMetric> metrics, boolean skillsOnly) {
		if (metrics == null) {
			return;
		}

		for (ProfileMetric metric : metrics) {
			if (metric == null || metric.getHiscoreSkillName() == null) {
				continue;
			}
			if (skillsOnly && !ContextMetrics.isSkillMetric(metric)) {
				continue;
			}

			addMetric(target, metric);
		}
	}

	private static void addMetric(Map<String, ProfileMetric> target, ProfileMetric metric) {
		if (metric != null && metric.getHiscoreSkillName() != null) {
			target.putIfAbsent(metric.getHiscoreSkillName(), metric);
		}
	}

	private static String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}

		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value;
			}
		}

		return null;
	}
}
