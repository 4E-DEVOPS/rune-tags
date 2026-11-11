package com.runetags.context;

import static com.runetags.context.ContextSkills.WOODCUTTING;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;

/**
 * Resolves observable player animations into active contextual skill metrics.
 */
public class ContextAnimations {
	private static final Map<Integer, ContextDefinition> BY_ANIMATION = buildAnimations();

	public ContextDefinition resolve(Player player) {
		if (player == null) {
			return ContextDefinition.of(null);
		}

		final ContextDefinition context = BY_ANIMATION.get(player.getAnimation());
		return context != null
				? context
				: ContextDefinition.of(null);
	}

	private static Map<Integer, ContextDefinition> buildAnimations() {
		final Map<Integer, ContextDefinition> map = new HashMap<>();

		/*
		 * Keep this list aligned with RuneLite's WoodcuttingPlugin.WOODCUTTING_ANIMS.
		 * Animation context is intentionally activity-specific: nearby trees alone do
		 * not imply Woodcutting.
		 */
		animation(map, WOODCUTTING,
				AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE,
				AnimationID.HUMAN_WOODCUTTING_IRON_AXE,
				AnimationID.HUMAN_WOODCUTTING_STEEL_AXE,
				AnimationID.HUMAN_WOODCUTTING_BLACK_AXE,
				AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE,
				AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE,
				AnimationID.HUMAN_WOODCUTTING_RUNE_AXE,
				AnimationID.HUMAN_WOODCUTTING_GILDED_AXE,
				AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE,
				AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL,
				AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE,
				AnimationID.HUMAN_WOODCUTTING_3A_AXE,
				AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE,
				AnimationID.HUMAN_OPENHEAVYCHEST,
				AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL,
				AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE,
				AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE,
				AnimationID.FORESTRY_2H_AXE_CHOPPING_3A);

		return Collections.unmodifiableMap(map);
	}

	private static void animation(Map<Integer, ContextDefinition> map, ProfileMetric metric, int... animationIds) {
		final ContextDefinition context = ContextDefinition.metrics(metric);
		for (int animationId : animationIds) {
			map.put(animationId, context);
		}
	}
}
