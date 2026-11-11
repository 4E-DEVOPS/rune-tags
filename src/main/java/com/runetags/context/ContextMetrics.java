package com.runetags.context;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.runelite.api.Skill;

/**
 * Shared profile metric definitions used by the contextual signal providers.
 */
public final class ContextMetrics {
	public static final ProfileMetric ATTACK = metric("Attack", "ATTACK");
	public static final ProfileMetric DEFENCE = metric("Defence", "DEFENCE");
	public static final ProfileMetric STRENGTH = metric("Strength", "STRENGTH");
	public static final ProfileMetric HITPOINTS = metric("Hitpoints", "HITPOINTS");
	public static final ProfileMetric RANGED = metric("Ranged", "RANGED");
	public static final ProfileMetric PRAYER = metric("Prayer", "PRAYER");
	public static final ProfileMetric MAGIC = metric("Magic", "MAGIC");
	public static final ProfileMetric COOKING = metric("Cooking", "COOKING");
	public static final ProfileMetric WOODCUTTING = metric("Woodcutting", "WOODCUTTING");
	public static final ProfileMetric FLETCHING = metric("Fletching", "FLETCHING");
	public static final ProfileMetric FISHING = metric("Fishing", "FISHING");
	public static final ProfileMetric FIREMAKING = metric("Firemaking", "FIREMAKING");
	public static final ProfileMetric CRAFTING = metric("Crafting", "CRAFTING");
	public static final ProfileMetric SMITHING = metric("Smithing", "SMITHING");
	public static final ProfileMetric MINING = metric("Mining", "MINING");
	public static final ProfileMetric HERBLORE = metric("Herblore", "HERBLORE");
	public static final ProfileMetric AGILITY = metric("Agility", "AGILITY");
	public static final ProfileMetric THIEVING = metric("Thieving", "THIEVING");
	public static final ProfileMetric SLAYER = metric("Slayer", "SLAYER");
	public static final ProfileMetric FARMING = metric("Farming", "FARMING");
	public static final ProfileMetric RUNECRAFT = metric("Runecrafting", "RUNECRAFT");
	public static final ProfileMetric HUNTER = metric("Hunter", "HUNTER");
	public static final ProfileMetric CONSTRUCTION = metric("Construction", "CONSTRUCTION");
	public static final ProfileMetric SAILING = metric("Sailing", "SAILING");

	public static final ProfileMetric CM = metric("CM", "CHAMBERS_OF_XERIC_CHALLENGE_MODE");
	public static final ProfileMetric COX = metric("CoX", "CHAMBERS_OF_XERIC");
	public static final ProfileMetric HMT = metric("HMT", "THEATRE_OF_BLOOD_HARD_MODE");
	public static final ProfileMetric LMS = metric("LMS", "LAST_MAN_STANDING");
	public static final ProfileMetric LUNAR_CHESTS = metric("Lunar Chests", "LUNAR_CHESTS");
	public static final ProfileMetric NEX = metric("Nex", "NEX");
	public static final ProfileMetric TFA = metric("TFA", "THE_FRACTURED_ARCHIVE");
	public static final ProfileMetric TOA = metric("ToA", "TOMBS_OF_AMASCUT");
	public static final ProfileMetric TOA_EXPERT = metric("Expert", "TOMBS_OF_AMASCUT_EXPERT");
	public static final ProfileMetric TOB = metric("ToB", "THEATRE_OF_BLOOD");
	public static final ProfileMetric VORKATH = metric("Vorkath KC", "VORKATH");
	public static final ProfileMetric ZULRAH = metric("Zulrah KC", "ZULRAH");

	private static final Map<Skill, ProfileMetric> BY_SKILL = buildSkillMetrics();
	private static final Set<String> SKILL_KEYS = buildSkillKeys();

	private ContextMetrics() {
	}

	public static ProfileMetric forSkill(Skill skill) {
		return skill != null
				? BY_SKILL.get(skill)
				: null;
	}

	public static boolean isSkillMetric(ProfileMetric metric) {
		return metric != null && metric.getHiscoreSkillName() != null && SKILL_KEYS.contains(metric.getHiscoreSkillName());
	}

	public static ProfileMetric metric(String label, String hiscoreSkillName) {
		return new ProfileMetric(label, hiscoreSkillName);
	}

	private static Map<Skill, ProfileMetric> buildSkillMetrics() {
		final Map<Skill, ProfileMetric> map = new EnumMap<>(Skill.class);
		map.put(Skill.ATTACK, ATTACK);
		map.put(Skill.DEFENCE, DEFENCE);
		map.put(Skill.STRENGTH, STRENGTH);
		map.put(Skill.HITPOINTS, HITPOINTS);
		map.put(Skill.RANGED, RANGED);
		map.put(Skill.PRAYER, PRAYER);
		map.put(Skill.MAGIC, MAGIC);
		map.put(Skill.COOKING, COOKING);
		map.put(Skill.WOODCUTTING, WOODCUTTING);
		map.put(Skill.FLETCHING, FLETCHING);
		map.put(Skill.FISHING, FISHING);
		map.put(Skill.FIREMAKING, FIREMAKING);
		map.put(Skill.CRAFTING, CRAFTING);
		map.put(Skill.SMITHING, SMITHING);
		map.put(Skill.MINING, MINING);
		map.put(Skill.HERBLORE, HERBLORE);
		map.put(Skill.AGILITY, AGILITY);
		map.put(Skill.THIEVING, THIEVING);
		map.put(Skill.SLAYER, SLAYER);
		map.put(Skill.FARMING, FARMING);
		map.put(Skill.RUNECRAFT, RUNECRAFT);
		map.put(Skill.HUNTER, HUNTER);
		map.put(Skill.CONSTRUCTION, CONSTRUCTION);
		map.put(Skill.SAILING, SAILING);
		return Collections.unmodifiableMap(map);
	}

	private static Set<String> buildSkillKeys() {
		final Set<String> keys = new LinkedHashSet<>();
		for (ProfileMetric metric : BY_SKILL.values()) {
			keys.add(metric.getHiscoreSkillName());
		}
		return Collections.unmodifiableSet(keys);
	}
}
