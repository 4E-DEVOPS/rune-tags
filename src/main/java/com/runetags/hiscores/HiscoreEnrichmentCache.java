package com.runetags.hiscores;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class HiscoreEnrichmentCache
{
    private static final Duration SUCCESS_TTL = Duration.ofMinutes(10);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(60);

    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public HiscoreEnrichmentCache()
    {
        this(
                Clock.systemUTC());
    }

    public HiscoreEnrichmentCache(
            Clock clock)
    {
        this.clock =
                clock != null
                        ? clock
                        : Clock.systemUTC();
    }

    public Optional<CachedProfileEnrichment> get(String playerName)
    {
        final String key = key(playerName);

        if (key.isEmpty())
        {
            return Optional.empty();
        }

        final CacheEntry entry = entries.get(key);

        if (entry == null)
        {
            return Optional.empty();
        }

        if (!clock.instant().isBefore(entry.expiresAt))
        {
            entries.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(entry.value);
    }

    public void putSuccess(String playerName, HiscoreProfileData data)
    {
        put(
            playerName,
            new CachedProfileEnrichment(HiscoreEnrichmentState.LOADED, data),
            SUCCESS_TTL);
    }

    public void putNotFound(String playerName)
    {
        put(
            playerName,
            new CachedProfileEnrichment(HiscoreEnrichmentState.NOT_FOUND, null),
            NEGATIVE_TTL);
    }

    public void clear()
    {
        entries.clear();
    }

    private void put(
        String playerName,
        CachedProfileEnrichment value,
        Duration ttl)
    {
        final String key = key(playerName);

        if (key.isEmpty())
        {
            return;
        }

        entries.put(
            key,
            new CacheEntry(
                value,
                    clock.instant().plus(ttl)));
    }

    static String key(String playerName)
    {
        if (playerName == null)
        {
            return "";
        }

        return playerName
            .trim()
            .replace('_', ' ')
            .replace('-', ' ')
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    @lombok.Value
    public static class CachedProfileEnrichment
    {
        HiscoreEnrichmentState state;
        HiscoreProfileData data;
    }

    @lombok.Value
    private static class CacheEntry
    {
        CachedProfileEnrichment value;
        Instant expiresAt;
    }
}
