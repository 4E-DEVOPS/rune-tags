package com.runetags.context;

import java.util.Collections;
import java.util.List;

/**
 * Central resolver for RuneTags contextual metrics.
 *
 * Location/context providers should determine WHERE a player is.
 * This resolver determines WHICH contextual metrics belong to that context.
 *
 * This keeps ContextMetricCatalog access centralized so local, Party,
 * and future remote context sources all use the same rules.
 */
public final class ContextMetricResolver
{
    /**
     * Resolve a normal location-based context.
     */
    public PlayerContext resolveLocation(
            String locationName,
            int regionId)
    {
        final List<ContextMetric> metrics =
                locationName != null
                        ? ContextMetricCatalog.metricsForLocation(
                        locationName)
                        : Collections.emptyList();

        return new PlayerContext(
                locationName,
                regionId,
                metrics);
    }

    /**
     * Resolve an NPC-specific fallback context.
     *
     * NPC overrides remain distinct from normal location mappings because
     * an NPC may intentionally expose a narrower metric set than the broader
     * surrounding location.
     */
    public PlayerContext resolveNpc(
            String npcName,
            int regionId)
    {
        if (npcName == null)
        {
            return null;
        }

        final ContextMetricCatalog.ContextOverride override =
                ContextMetricCatalog.overrideForNpc(
                        npcName);

        if (override == null)
        {
            return null;
        }

        return new PlayerContext(
                override.getLocationName(),
                regionId,
                override.getMetrics());
    }

    /**
     * Produce an unknown/unmapped context while retaining the observed region.
     */
    public PlayerContext unknown(int regionId)
    {
        return new PlayerContext(
                null,
                regionId,
                Collections.emptyList());
    }
}