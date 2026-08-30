package com.runetags.context;

import lombok.Value;

/**
 * One context-relevant Hiscore value.
 *
 * Despite the Quick Profile setting being called "Show Killcount", a few
 * minigames expose an activity score/rank rather than a literal kill count.
 * RuneTags keeps the model generic so labels remain accurate.
 */
@Value
public class ContextMetric
{
    String label;
    String hiscoreSkillName;
}
