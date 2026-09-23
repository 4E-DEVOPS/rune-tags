package com.runetags.context;

import lombok.Value;

/**
 * Resolved display value for one contextual profile metric.
 */
@Value
public class ProfileMetricValue {
	String label;
	int value;
}
