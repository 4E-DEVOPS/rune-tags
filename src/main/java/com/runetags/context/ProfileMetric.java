package com.runetags.context;

import lombok.Value;

/**
 * Describes one context-relevant hiscore metric by display label and hiscore skill name.
 */
@Value
public class ProfileMetric {
	String label;
	String hiscoreSkillName;
}
