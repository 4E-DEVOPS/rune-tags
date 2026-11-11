package com.runetags.context;

import static com.runetags.context.ContextMetrics.COOKING;
import static com.runetags.context.ContextMetrics.SMITHING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lombok.Value;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Tracks context-relevant scene objects through spawn/despawn events.
 */
public class ContextObjects {
	public static final int CONTEXT_RADIUS = 20;

	private static final Map<Integer, ObjectDefinition> EXACT_OBJECTS = buildExactObjects();

	private final Client client;
	private final Map<TileObject, ObjectDefinition> trackedObjects = new IdentityHashMap<>();
	private final Map<Integer, ObjectDefinition> stableMatches = new HashMap<>();
	private final Set<Integer> stableMisses = new HashSet<>();

	public ContextObjects(Client client) {
		this.client = client;
	}

	public void clear() {
		trackedObjects.clear();
	}

	/*
	 * Bootstrap only when RuneTags starts with an already populated scene.
	 * Normal scene changes remain event-driven.
	 */
	public void bootstrap(WorldView worldView) {
		clear();

		if (worldView == null) {
			return;
		}

		final Scene scene = worldView.getScene();
		if (scene == null || scene.getTiles() == null) {
			return;
		}

		for (Tile[][] plane : scene.getTiles()) {
			if (plane == null) {
				continue;
			}

			for (Tile[] column : plane) {
				if (column == null) {
					continue;
				}

				for (Tile tile : column) {
					trackTile(tile);
				}
			}
		}
	}

	public void onSpawn(TileObject object) {
		track(object);
	}

	public void onDespawn(TileObject object) {
		if (object != null) {
			trackedObjects.remove(object);
		}
	}

	public boolean hasPohExitPortal(WorldView worldView) {
		if (worldView == null) {
			return false;
		}

		for (Map.Entry<TileObject, ObjectDefinition> entry : trackedObjects.entrySet()) {
			final TileObject object = entry.getKey();
			final ObjectDefinition definition = entry.getValue();
			if (object != null && definition != null && definition.isPohMarker() && sameWorldView(worldView, object.getWorldView())) {
				return true;
			}
		}

		return false;
	}

	public ContextDefinition resolveNearby(Player player) {
		if (player == null || player.getWorldView() == null || player.getWorldLocation() == null) {
			return ContextDefinition.of(null);
		}

		final WorldView playerWorldView = player.getWorldView();
		final WorldPoint playerPoint = player.getWorldLocation();
		final LinkedHashSet<String> locationNames = new LinkedHashSet<>();
		final LinkedHashMap<String, ProfileMetric> metrics = new LinkedHashMap<>();

		for (Map.Entry<TileObject, ObjectDefinition> entry : trackedObjects.entrySet()) {
			final TileObject object = entry.getKey();
			final ObjectDefinition definition = entry.getValue();
			if (object == null || definition == null || definition.isPohMarker() || !sameWorldView(playerWorldView, object.getWorldView())) {
				continue;
			}

			final WorldPoint objectPoint = object.getWorldLocation();
			if (objectPoint == null || playerPoint.distanceTo(objectPoint) > CONTEXT_RADIUS) {
				continue;
			}

			final ContextDefinition context = definition.getContext();
			if (context.getLocationName() != null && !context.getLocationName().trim().isEmpty()) {
				locationNames.add(context.getLocationName());
			}
			addMetrics(metrics, context.getMetrics());
		}

		final String locationName = locationNames.size() == 1
				? locationNames.iterator().next()
				: null;
		return new ContextDefinition(locationName, new ArrayList<>(metrics.values()));
	}

	private void trackTile(Tile tile) {
		if (tile == null) {
			return;
		}

		track(tile.getWallObject());
		track(tile.getDecorativeObject());
		track(tile.getGroundObject());

		final GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects == null) {
			return;
		}

		for (GameObject gameObject : gameObjects) {
			track(gameObject);
		}
	}

	private void track(TileObject object) {
		if (object == null) {
			return;
		}

		final ObjectDefinition definition = classify(object.getId());
		if (definition == null) {
			trackedObjects.remove(object);
			return;
		}

		trackedObjects.put(object, definition);
	}

	private ObjectDefinition classify(int objectId) {
		final ObjectDefinition exact = EXACT_OBJECTS.get(objectId);
		if (exact != null) {
			return exact;
		}

		final ObjectDefinition cached = stableMatches.get(objectId);
		if (cached != null) {
			return cached;
		}
		if (stableMisses.contains(objectId)) {
			return null;
		}

		final ObjectComposition base = client != null
				? client.getObjectDefinition(objectId)
				: null;
		if (base == null) {
			return null;
		}

		final boolean dynamic = base.getImpostorIds() != null;
		final ObjectComposition active = dynamic
				? base.getImpostor()
				: base;
		final ObjectDefinition definition = classify(active);

		if (!dynamic) {
			if (definition == null) {
				stableMisses.add(objectId);
			} else {
				stableMatches.put(objectId, definition);
			}
		}

		return definition;
	}

	private static ObjectDefinition classify(ObjectComposition composition) {
		if (composition == null) {
			return null;
		}

		final String name = normalize(composition.getName());
		final String[] actions = composition.getActions();

		if (isSmithingStation(name) && containsAction(actions, "smith")) {
			return ObjectDefinition.context(null, SMITHING);
		}

		if (isCookingStation(name) && containsAction(actions, "cook")) {
			return ObjectDefinition.context(null, COOKING);
		}

		return null;
	}

	private static Map<Integer, ObjectDefinition> buildExactObjects() {
		final Map<Integer, ObjectDefinition> map = new HashMap<>();

		object(map, ObjectID.ANVIL, null, SMITHING);
		object(map, ObjectID.RANGE, null, COOKING);
		map.put(ObjectID.POH_EXIT_PORTAL, ObjectDefinition.pohMarker());

		/*
		 * Exact location overrides belong here when an object uniquely identifies a room/activity.
		 * object(map, SOME_UNIQUE_OBJECT_ID, "Specific Location", SMITHING);
		 */

		return Collections.unmodifiableMap(map);
	}

	private static void object(Map<Integer, ObjectDefinition> map, int objectId, String locationName, ProfileMetric... metrics) {
		map.put(objectId, ObjectDefinition.context(locationName, metrics));
	}

	private static void addMetrics(Map<String, ProfileMetric> target, List<ProfileMetric> metrics) {
		if (metrics == null) {
			return;
		}

		for (ProfileMetric metric : metrics) {
			if (metric != null && metric.getHiscoreSkillName() != null) {
				target.putIfAbsent(metric.getHiscoreSkillName(), metric);
			}
		}
	}

	private static boolean isCookingStation(String normalizedName) {
		return normalizedName.contains("range")
				|| normalizedName.contains("stove")
				|| normalizedName.contains("oven");
	}

	private static boolean isSmithingStation(String normalizedName) {
		return normalizedName.contains("anvil")
				|| normalizedName.contains("furnace");
	}

	private static boolean containsAction(String[] actions, String expected) {
		if (actions == null || expected == null) {
			return false;
		}

		final String needle = expected.toLowerCase(Locale.ROOT);
		for (String action : actions) {
			if (action != null && action.toLowerCase(Locale.ROOT).contains(needle)) {
				return true;
			}
		}

		return false;
	}

	private static boolean sameWorldView(WorldView first, WorldView second) {
		return first != null && second != null && first.getId() == second.getId();
	}

	private static String normalize(String value) {
		return value == null
				? ""
				: value.trim().toLowerCase(Locale.ROOT);
	}

	@Value
	private static class ObjectDefinition {
		ContextDefinition context;
		boolean pohMarker;

		static ObjectDefinition context(String locationName, ProfileMetric... metrics) {
			return new ObjectDefinition(ContextDefinition.of(locationName, metrics), false);
		}

		static ObjectDefinition pohMarker() {
			return new ObjectDefinition(ContextDefinition.of(null), true);
		}
	}
}
