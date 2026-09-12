package com.runetags.context;

import com.runetags.location.PlayerLocation;

import java.util.Collections;
import java.util.List;

/**
 * Central resolver for RuneTags contextual hiscores.
 *
 * Context providers determine where a player is; this resolver determines
 * which ProfileMetricCatalog metrics belong to that context.
 */
public final class ProfileMetricResolver
{
    public PlayerContext resolveLocation(
            PlayerLocation location)
    {
        if (location == null)
        {
            return PlayerContext.unknown();
        }

        final List<ProfileMetric> metrics =
                location.getLocationName() != null
                        ? ProfileMetricCatalog.metricsForLocation(
                        location.getLocationName())
                        : Collections.emptyList();

        return new PlayerContext(
                location,
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
            PlayerLocation location)
    {
        if (npcName == null)
        {
            return null;
        }

        final ProfileMetricCatalog.ContextOverride override =
                ProfileMetricCatalog.overrideForNpc(
                        npcName);

        if (override == null)
        {
            return null;
        }

        final int regionId =
                location != null
                        ? location.getRegionId()
                        : -1;

        final PlayerLocation resolvedLocation =
                new PlayerLocation(
                        override.getLocationName(),
                        regionId);

        return new PlayerContext(
                resolvedLocation,
                override.getMetrics());
    }

    public PlayerContext unknown(
            PlayerLocation location)
    {
        return new PlayerContext(
                location,
                Collections.emptyList());
    }
}