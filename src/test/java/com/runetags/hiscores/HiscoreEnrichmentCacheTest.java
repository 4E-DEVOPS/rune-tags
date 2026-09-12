package com.runetags.hiscores;

import com.runetags.player.AccountType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HiscoreEnrichmentCacheTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    private MutableClock clock;
    private HiscoreEnrichmentCache cache;

    @Before
    public void setUp()
    {
        clock =
                new MutableClock(
                        Instant.parse(
                                "2026-09-10T00:00:00Z"));

        cache =
                new HiscoreEnrichmentCache(
                        clock);
    }

    /*
     * TESTS
     */

    @Test
    public void successCacheIsImmediatelyAvailable()
    {
        final HiscoreProfileData data =
                createProfileData();

        cache.putSuccess(
                "Santa Clause",
                data);

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        "Santa Clause");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                cached.get()
                        .getState());

        Assert.assertSame(
                data,
                cached.get()
                        .getData());
    }

    @Test
    public void successCacheRemainsValidBeforeTenMinutes()
    {
        final HiscoreProfileData data =
                createProfileData();

        cache.putSuccess(
                "Santa Clause",
                data);

        clock.advance(
                Duration.ofMinutes(9)
                        .plusSeconds(59)
                        .plusMillis(999));

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        "Santa Clause");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                cached.get()
                        .getState());

        Assert.assertSame(
                data,
                cached.get()
                        .getData());
    }

    @Test
    public void successCacheExpiresAtExactlyTenMinutes()
    {
        cache.putSuccess(
                "Santa Clause",
                createProfileData());

        clock.advance(
                Duration.ofMinutes(10));

        Assert.assertFalse(
                cache.get(
                                "Santa Clause")
                        .isPresent());
    }

    @Test
    public void successCacheRemainsExpiredAfterBoundary()
    {
        cache.putSuccess(
                "Santa Clause",
                createProfileData());

        clock.advance(
                Duration.ofMinutes(11));

        Assert.assertFalse(
                cache.get(
                                "Santa Clause")
                        .isPresent());

        Assert.assertFalse(
                cache.get(
                                "Santa Clause")
                        .isPresent());
    }

    @Test
    public void negativeCacheIsImmediatelyAvailable()
    {
        cache.putNotFound(
                "definitelyunknown123");

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        "definitelyunknown123");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.NOT_FOUND,
                cached.get()
                        .getState());

        Assert.assertNull(
                cached.get()
                        .getData());
    }

    @Test
    public void negativeCacheRemainsValidBeforeSixtySeconds()
    {
        cache.putNotFound(
                "definitelyunknown123");

        clock.advance(
                Duration.ofSeconds(59)
                        .plusMillis(999));

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        "definitelyunknown123");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.NOT_FOUND,
                cached.get()
                        .getState());

        Assert.assertNull(
                cached.get()
                        .getData());
    }

    @Test
    public void negativeCacheExpiresAtExactlySixtySeconds()
    {
        cache.putNotFound(
                "definitelyunknown123");

        clock.advance(
                Duration.ofSeconds(60));

        Assert.assertFalse(
                cache.get(
                                "definitelyunknown123")
                        .isPresent());
    }

    @Test
    public void negativeCacheRemainsExpiredAfterBoundary()
    {
        cache.putNotFound(
                "definitelyunknown123");

        clock.advance(
                Duration.ofSeconds(61));

        Assert.assertFalse(
                cache.get(
                                "definitelyunknown123")
                        .isPresent());

        Assert.assertFalse(
                cache.get(
                                "definitelyunknown123")
                        .isPresent());
    }

    @Test
    public void cacheUsesSameIdentityForSeparatorAndCaseVariants()
    {
        final HiscoreProfileData data =
                createProfileData();

        cache.putSuccess(
                "Santa Clause",
                data);

        assertCachedData(
                "santa_clause",
                data);

        assertCachedData(
                "SANTA-CLAUSE",
                data);

        assertCachedData(
                "  santa   clause  ",
                data);
    }

    @Test
    public void normalizedLookupWorksForNegativeCache()
    {
        cache.putNotFound(
                "Santa Clause");

        assertCachedState(
                "santa_clause",
                HiscoreEnrichmentState.NOT_FOUND);

        assertCachedState(
                "SANTA-CLAUSE",
                HiscoreEnrichmentState.NOT_FOUND);

        assertCachedState(
                "  santa   clause  ",
                HiscoreEnrichmentState.NOT_FOUND);
    }

    @Test
    public void laterSuccessReplacesNegativeEntry()
    {
        final HiscoreProfileData data =
                createProfileData();

        cache.putNotFound(
                "Santa Clause");

        assertCachedState(
                "Santa Clause",
                HiscoreEnrichmentState.NOT_FOUND);

        cache.putSuccess(
                "santa_clause",
                data);

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        "SANTA-CLAUSE");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                cached.get()
                        .getState());

        Assert.assertSame(
                data,
                cached.get()
                        .getData());
    }

    @Test
    public void laterNegativeEntryReplacesSuccess()
    {
        cache.putSuccess(
                "Santa Clause",
                createProfileData());

        assertCachedState(
                "Santa Clause",
                HiscoreEnrichmentState.LOADED);

        cache.putNotFound(
                "santa_clause");

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        "SANTA-CLAUSE");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.NOT_FOUND,
                cached.get()
                        .getState());

        Assert.assertNull(
                cached.get()
                        .getData());
    }

    @Test
    public void expiredEntryCanBeReplaced()
    {
        final HiscoreProfileData replacement =
                createProfileData();

        cache.putNotFound(
                "Santa Clause");

        clock.advance(
                Duration.ofSeconds(60));

        Assert.assertFalse(
                cache.get(
                                "Santa Clause")
                        .isPresent());

        cache.putSuccess(
                "Santa Clause",
                replacement);

        assertCachedData(
                "Santa Clause",
                replacement);
    }

    @Test
    public void clearRemovesSuccessAndNegativeEntries()
    {
        cache.putSuccess(
                "Santa Clause",
                createProfileData());

        cache.putNotFound(
                "definitelyunknown123");

        Assert.assertTrue(
                cache.get(
                                "Santa Clause")
                        .isPresent());

        Assert.assertTrue(
                cache.get(
                                "definitelyunknown123")
                        .isPresent());

        cache.clear();

        Assert.assertFalse(
                cache.get(
                                "Santa Clause")
                        .isPresent());

        Assert.assertFalse(
                cache.get(
                                "definitelyunknown123")
                        .isPresent());
    }

    @Test
    public void blankAndNullNamesAreIgnored()
    {
        cache.putSuccess(
                null,
                createProfileData());

        cache.putSuccess(
                "   ",
                createProfileData());

        cache.putNotFound(
                null);

        cache.putNotFound(
                "   ");

        Assert.assertFalse(
                cache.get(
                                null)
                        .isPresent());

        Assert.assertFalse(
                cache.get(
                                "   ")
                        .isPresent());
    }

    @Test
    public void whitespaceOnlyVariantsRemainInvalid()
    {
        Assert.assertFalse(
                cache.get(
                                "")
                        .isPresent());

        Assert.assertFalse(
                cache.get(
                                " ")
                        .isPresent());

        Assert.assertFalse(
                cache.get(
                                "     ")
                        .isPresent());
    }

    @Test
    public void normalizedKeyCollapsesRepeatedWhitespaceAndSeparators()
    {
        Assert.assertEquals(
                HiscoreEnrichmentCache.key(
                        "Santa Clause"),
                HiscoreEnrichmentCache.key(
                        "  SANTA___CLAUSE  "));

        Assert.assertEquals(
                HiscoreEnrichmentCache.key(
                        "Santa Clause"),
                HiscoreEnrichmentCache.key(
                        "Santa---Clause"));
    }

    @Test
    public void nullClockFallsBackToSystemClock()
    {
        final HiscoreEnrichmentCache systemCache =
                new HiscoreEnrichmentCache(
                        null);

        final HiscoreProfileData data =
                createProfileData();

        systemCache.putSuccess(
                "Zezima",
                data);

        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                systemCache.get(
                        "Zezima");

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertSame(
                data,
                cached.get()
                        .getData());
    }

    @Test
    public void cacheLookupPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final HiscoreProfileData data =
                createProfileData();

        cache.putSuccess(
                "Santa Clause",
                data);

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    cache.get(
                            "Santa Clause"));

            consume(
                    cache.get(
                            "santa_clause"));
        }

        final long exactStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    cache.get(
                            "Santa Clause"));
        }

        final long exactElapsed =
                System.nanoTime()
                        - exactStart;

        final long normalizedStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    cache.get(
                            "  SANTA_CLAUSE  "));
        }

        final long normalizedElapsed =
                System.nanoTime()
                        - normalizedStart;

        printPerformance(
                "ExactHit",
                exactElapsed,
                "NormalizedHit",
                normalizedElapsed);
    }

    /*
     * HELPERS
     */

    private void assertCachedState(
            String playerName,
            HiscoreEnrichmentState expectedState)
    {
        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        playerName);

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                expectedState,
                cached.get()
                        .getState());
    }

    private void assertCachedData(
            String playerName,
            HiscoreProfileData expectedData)
    {
        final Optional<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                cached =
                cache.get(
                        playerName);

        Assert.assertTrue(
                cached.isPresent());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                cached.get()
                        .getState());

        Assert.assertSame(
                expectedData,
                cached.get()
                        .getData());
    }

    private static HiscoreProfileData createProfileData()
    {
        return new HiscoreProfileData(
                126,
                2277,
                AccountType.NORMAL,
                Collections.emptyMap());
    }

    private static final class MutableClock extends Clock
    {
        private Instant instant;

        private MutableClock(
                Instant instant)
        {
            this.instant =
                    instant;
        }

        private void advance(
                Duration duration)
        {
            instant =
                    instant.plus(
                            duration);
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(
                ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return instant;
        }
    }

    /*
     * PERFORMANCE
     */

    private static volatile Object performanceSink;

    private static void consume(
            Object value)
    {
        performanceSink =
                value;
    }

    private static void printPerformance(
            String firstLabel,
            long firstElapsedNanos,
            String secondLabel,
            long secondElapsedNanos)
    {
        final double firstTotalMillis =
                firstElapsedNanos
                        / 1_000_000.0;

        final double secondTotalMillis =
                secondElapsedNanos
                        / 1_000_000.0;

        final double firstAverageMillis =
                firstTotalMillis
                        / PERFORMANCE_ITERATIONS;

        final double secondAverageMillis =
                secondTotalMillis
                        / PERFORMANCE_ITERATIONS;

        System.out.printf(
                "[RuneTags][HiscoreEnrichmentCacheTest] Performance= "
                        + "%s: %.3fms (%.6fms) | "
                        + "%s: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                firstLabel,
                firstTotalMillis,
                firstAverageMillis,
                secondLabel,
                secondTotalMillis,
                secondAverageMillis,
                PERFORMANCE_ITERATIONS);
    }
}