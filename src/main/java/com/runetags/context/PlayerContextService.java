package com.runetags.context;

import com.runetags.location.PlayerLocation;
import com.runetags.location.PlayerLocationService;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;

/**
 * Maintains the local player's current location-derived or NPC-overridden profile context.
 */
public class PlayerContextService {
	private final Client client;
	private final PlayerLocationService playerLocationService;
	private final ProfileMetricResolver profileMetricResolver;

	private volatile PlayerContext current = PlayerContext.unknown();

	public PlayerContextService(
			Client client,
			PlayerLocationService playerLocationService,
			ProfileMetricResolver profileMetricResolver) {
		this.client = client;
		this.playerLocationService = playerLocationService;
		this.profileMetricResolver = profileMetricResolver;
	}

	public PlayerContext getCurrent() {
		return current;
	}

	public void clear() {
		current = PlayerContext.unknown();
	}

	/*
	 * Refreshes coarse location context, preferring the first loaded NPC override.
	 * Must run on RuneLite's client thread.
	 */
	public void refresh() {
		final PlayerLocation location = playerLocationService.getCurrent();
		if (location == null || !location.hasRegion()) {
			clear();
			return;
		}

		final PlayerContext npcContext = findNpcContext(location);
		if (npcContext != null) {
			current = npcContext;
			return;
		}

		current = profileMetricResolver.resolveLocation(location);
	}

	/*
	 * Returns the first loaded NPC with a contextual catalog override.
	 */
	private PlayerContext findNpcContext(PlayerLocation location) {
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null) {
			return null;
		}

		for (NPC npc : worldView.npcs()) {
			if (npc == null) {
				continue;
			}

			final String npcName = npc.getName();
			if (npcName == null || npcName.isEmpty()) {
				continue;
			}

			final PlayerContext context = profileMetricResolver.resolveNpc(npcName, location);
			if (context != null) {
				return context;
			}
		}

		return null;
	}
}
