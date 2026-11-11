package com.runetags.location;

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

import javax.inject.Inject;

/**
 * Loads RuneTags' instanced-area template map from the bundled Instances.json.
 *
 * JSON values are template region-grid coordinates [instanceX, instanceY].
 */
public class InstanceIndex {
	private static final String RESOURCE = "/com/runetags/context/Instances.json";

	private final Map<Integer, String> byInstance;

	@Inject
	public InstanceIndex(Gson gson) {
		this.byInstance = load(gson);
	}

	public String findName(int instanceId) {
		return byInstance.get(instanceId);
	}

	public int size() {
		return byInstance.size();
	}

	private static Map<Integer, String> load(Gson gson) {
		final InputStream stream = InstanceIndex.class.getResourceAsStream(RESOURCE);
		if (stream == null) {
			throw new IllegalStateException("Missing RuneTags instance resource: " + RESOURCE);
		}

		final Type type = new TypeToken<LinkedHashMap<String, List<List<Integer>>>>() {
		}.getType();

		final Map<String, List<List<Integer>>> source = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), type);
		final Map<Integer, String> result = new LinkedHashMap<>();

		for (Map.Entry<String, List<List<Integer>>> entry : source.entrySet()) {
			for (List<Integer> pair : entry.getValue()) {
				if (pair == null || pair.size() < 2) {
					continue;
				}

				final int instanceX = pair.get(0);
				final int instanceY = pair.get(1);
				final int instanceId = (instanceX << 8) | instanceY;
				result.put(instanceId, entry.getKey());
			}
		}

		return Collections.unmodifiableMap(result);
	}
}
