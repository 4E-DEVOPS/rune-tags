package com.runetags.context;

/**
 * Shared non-skill profile metric definitions used by contextual signal providers.
 */
public final class ContextMetrics {
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

	private ContextMetrics() {
	}

	public static ProfileMetric metric(String label, String hiscoreSkillName) {
		return new ProfileMetric(label, hiscoreSkillName);
	}
}
