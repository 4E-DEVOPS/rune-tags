package com.runetags.context;

import lombok.Value;

/**
 * One context-relevant Hiscore value.
 *
 * Contextual values may represent killcounts, activity scores, ranks, or
 * skills, so the metric model remains generic.
 */
@Value
public class ProfileMetric
{
    String label;
    String hiscoreSkillName;
}
