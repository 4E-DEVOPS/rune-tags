package com.runetags.hiscores;

import com.google.gson.Gson;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches EHP/EHB profile values from Wise Old Man for Quick-Card hiscores.
 *
 * Requests are lazy. Successful results are cached for ten minutes and negative
 * results briefly to avoid repeated lookups for unavailable or untracked players.
 */
@Slf4j
public class EfficiencyMetricService
{
    private static final String PLAYER_API_URL = "https://api.wiseoldman.net/v2/players";
    private static final Duration SUCCESS_TTL = Duration.ofMinutes(10);
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(1);

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<EfficiencyProfileData>> inFlight = new ConcurrentHashMap<>();
    private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public EfficiencyMetricService(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public CompletableFuture<EfficiencyProfileData> lookup(String playerName)
    {
        final String key = key(playerName);

        if (closed || key.isEmpty())
        {
            return CompletableFuture.completedFuture(null);
        }

        final CacheEntry cached = cache.get(key);

        if (cached != null)
        {
            if (Instant.now().isBefore(cached.getExpiresAt()))
            {
                return CompletableFuture.completedFuture(cached.getData());
            }

            cache.remove(key, cached);
        }

        return inFlight.computeIfAbsent(key, ignored -> request(playerName.trim(), key));
    }

    public void clear()
    {
        cache.clear();
    }

    public void shutdown()
    {
        closed = true;
        cache.clear();

        for (Call call : activeCalls)
        {
            call.cancel();
        }

        activeCalls.clear();

        for (CompletableFuture<EfficiencyProfileData> future : inFlight.values())
        {
            future.cancel(false);
        }

        inFlight.clear();
    }

    private CompletableFuture<EfficiencyProfileData> request(String playerName, String key)
    {
        final CompletableFuture<EfficiencyProfileData> future = new CompletableFuture<>();
        final HttpUrl baseUrl = HttpUrl.parse(PLAYER_API_URL);

        if (baseUrl == null)
        {
            future.complete(null);
            return future;
        }

        final HttpUrl url = baseUrl.newBuilder().addPathSegment(playerName).build();
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "RuneTags RuneLite plugin")
                .build();

        final Call call = httpClient.newCall(request);
        activeCalls.add(call);

        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call failedCall, IOException exception)
            {
                activeCalls.remove(failedCall);
                complete(key, future, null, NEGATIVE_TTL);

                //log.debug("[RuneTags][WOM] Efficiency Lookup Failed for Player='{}' | ERROR: {}", playerName, exception.getMessage());
            }

            @Override
            public void onResponse(Call completedCall, Response response)
            {
                activeCalls.remove(completedCall);
                EfficiencyProfileData data = null;

                try (Response closedResponse = response)
                {
                    if (!closedResponse.isSuccessful())
                    {
                        //log.debug("[RuneTags][WOM] Efficiency Lookup for Player='{}' returned HTTP {}", playerName, closedResponse.code());
                        return;
                    }

                    final ResponseBody body = closedResponse.body();

                    if (body == null)
                    {
                        return;
                    }

                    final WiseOldManPlayer responseData = gson.fromJson(body.charStream(), WiseOldManPlayer.class);

                    if (responseData != null)
                    {
                        data = new EfficiencyProfileData(valid(responseData.getEhp()), valid(responseData.getEhb()));

                        if (data.getEhp() == null && data.getEhb() == null)
                        {
                            data = null;
                        }
                    }
                }
                catch (RuntimeException exception)
                {
                    log.debug("[RuneTags][WOM] Unable to Parse Efficiency Response for Player='{}' | ERROR: {}", playerName, exception.getMessage());
                }
                finally
                {
                    complete(key, future, data, data != null ? SUCCESS_TTL : NEGATIVE_TTL);
                }
            }
        });

        return future;
    }

    private void complete(
            String key,
            CompletableFuture<EfficiencyProfileData> future,
            EfficiencyProfileData data,
            Duration ttl)
    {
        if (!closed)
        {
            cache.put(key, new CacheEntry(data, Instant.now().plus(ttl)));
        }

        inFlight.remove(key, future);
        future.complete(data);
    }

    private static Double valid(Double value)
    {
        return value != null && Double.isFinite(value) && value >= 0d ? value : null;
    }

    private static String key(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return playerName.trim()
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    @Value
    private static class CacheEntry
    {
        EfficiencyProfileData data;
        Instant expiresAt;
    }

    @Value
    private static class WiseOldManPlayer
    {
        Double ehp;
        Double ehb;
    }
}
