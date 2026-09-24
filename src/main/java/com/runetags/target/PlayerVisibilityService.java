package com.runetags.target;

import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.client.callback.Hooks;

/**
 * Hides every player except the active RuneTags target while Hide All Others is active.
 *
 * Player models and player UI renderables are filtered; non-player renderables are unchanged.
 */
public class PlayerVisibilityService {
	private final Hooks hooks;
	private final TargetController targetController;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private boolean registered;

	public PlayerVisibilityService(Hooks hooks, TargetController targetController) {
		this.hooks = hooks;
		this.targetController = targetController;
	}

	public void start() {
		if (registered) {
			return;
		}

		hooks.registerRenderableDrawListener(drawListener);
		registered = true;
	}

	public void stop() {
		if (!registered) {
			return;
		}

		hooks.unregisterRenderableDrawListener(drawListener);
		registered = false;
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUi) {
		// RuneTags only filters Player renderables.
		if (!(renderable instanceof Player)) {
			return true;
		}

		// Leave rendering untouched while Hide All Others is inactive.
		if (!targetController.shouldHideOtherPlayers()) {
			return true;
		}

		final Player player = (Player) renderable;
		final String playerName = player.getName();

		// Leave unidentified players visible.
		if (playerName == null || playerName.isEmpty()) {
			return true;
		}

		/*
		 * Keep only the active target visible. drawingUi is intentionally ignored so
		 * 3D models and 2D player UI follow the same visibility rule.
		 */
		return targetController.isTargetingName(playerName);
	}
}
