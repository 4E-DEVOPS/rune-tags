package com.runetags.quickprofile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

@Value
public class HiscoreProfileData
{
    Integer combatLevel;
    Integer totalLevel;
    Map<String, Integer> contextValues;

    public HiscoreProfileData(
            Integer combatLevel,
            Integer totalLevel,
            Map<String, Integer> contextValues)
    {
        this.combatLevel = combatLevel;
        this.totalLevel = totalLevel;
        this.contextValues = contextValues == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(
                new LinkedHashMap<>(contextValues));
    }

    public Integer getContextValue(String hiscoreSkillName)
    {
        if (hiscoreSkillName == null)
        {
            return null;
        }

        return contextValues.get(hiscoreSkillName);
    }
}
