package com.runetags.context;

import java.util.Collections;
import java.util.List;

/**
 * Backward-compatible facade over the contextual signal catalogs.
 */
public final class ProfileMetricCatalog {
	private ProfileMetricCatalog() {
	}

	public static List<ProfileMetric> metricsForLocation(String locationName) {
		final List<ProfileMetric> regionMetrics = ContextRegions.metricsForLocation(locationName);
		return !regionMetrics.isEmpty()
				? regionMetrics
				: ContextScenes.metricsForInstance(locationName);
	}

	public static ContextOverride overrideForNpc(String npcName) {
		final ContextDefinition definition = ContextNPCs.contextForName(npcName);
		if (definition == null) {
			return null;
		}

		return new ContextOverride(definition.getLocationName(), definition.getMetrics());
	}

	@lombok.Value
	public static class ContextOverride {
		String locationName;
		List<ProfileMetric> metrics;

		public ContextOverride(String locationName, List<ProfileMetric> metrics) {
			this.locationName = locationName;
			this.metrics = metrics == null
					? Collections.emptyList()
					: metrics;
		}
	}
}
