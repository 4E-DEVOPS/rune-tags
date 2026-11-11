package com.runetags.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.Value;

/**
 * One contextual signal's optional location override and profile metrics.
 */
@Value
public class ContextDefinition {
	String locationName;
	List<ProfileMetric> metrics;

	public ContextDefinition(String locationName, List<ProfileMetric> metrics) {
		this.locationName = locationName;
		this.metrics = metrics == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(metrics));
	}

	public static ContextDefinition of(String locationName, ProfileMetric... metrics) {
		return new ContextDefinition(locationName, metrics == null
				? Collections.emptyList()
				: Arrays.asList(metrics));
	}

	public static ContextDefinition metrics(ProfileMetric... metrics) {
		return of(null, metrics);
	}
}
