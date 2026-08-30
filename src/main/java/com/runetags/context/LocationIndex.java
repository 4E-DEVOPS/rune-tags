package com.runetags.context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads RuneTags' complete coarse-location map from the bundled Locations.json.
 *
 * JSON values are RuneScape region-grid coordinates [regionX, regionY].
 */
public class LocationIndex
{
    private static final String RESOURCE =
        "/com/runetags/context/Locations.json";

    private final Map<Integer, String> byRegion;

    public LocationIndex()
    {
        this.byRegion = load();
    }

    public String findName(int regionId)
    {
        return byRegion.get(regionId);
    }

    public int size()
    {
        return byRegion.size();
    }

    private static Map<Integer, String> load()
    {
        final InputStream stream =
            LocationIndex.class.getResourceAsStream(RESOURCE);

        if (stream == null)
        {
            throw new IllegalStateException(
                "Missing RuneTags location resource: " + RESOURCE);
        }

        final Type type =
            new TypeToken<LinkedHashMap<String, List<List<Integer>>>>() { }
                .getType();

        final Map<String, List<List<Integer>>> source =
            new Gson().fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                type);

        final Map<Integer, String> result =
            new LinkedHashMap<>();

        for (Map.Entry<String, List<List<Integer>>> entry : source.entrySet())
        {
            for (List<Integer> pair : entry.getValue())
            {
                if (pair == null || pair.size() < 2)
                {
                    continue;
                }

                final int regionX = pair.get(0);
                final int regionY = pair.get(1);
                final int regionId = (regionX << 8) | regionY;

                result.put(regionId, entry.getKey());
            }
        }

        return Collections.unmodifiableMap(result);
    }
}
