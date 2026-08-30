package com.runetags.context;

import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class PlayerContext
{
    String locationName;
    int regionId;
    List<ContextMetric> metrics;

    public PlayerContext(String locationName, int regionId, List<ContextMetric> metrics)
    {
        this.locationName = locationName;
        this.regionId = regionId;
        this.metrics = metrics == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(metrics);
    }

    public static PlayerContext unknown()
    {
        return new PlayerContext(null, -1, Collections.emptyList());
    }

    public boolean hasLocation()
    {
        return locationName != null && !locationName.isEmpty();
    }

    public boolean hasMetrics()
    {
        return !metrics.isEmpty();
    }
}
