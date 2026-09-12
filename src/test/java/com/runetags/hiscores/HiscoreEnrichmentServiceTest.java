package com.runetags.hiscores;

import com.google.gson.Gson;
import com.runetags.player.AccountType;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.runelite.api.Experience;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;

import okhttp3.OkHttpClient;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class HiscoreEnrichmentServiceTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * Keep false for routine development and CI. When enabled, the suite also
     * performs a real Jagex HiScore smoke test and therefore depends on external
     * service availability and network conditions.
     */
    private static final boolean USE_REAL_JAGEX_HISCORES = false;

    private static final String REAL_PLAYER_NAME = "FasT 07";

    /*
     * TESTS
     */

    @Test
    public void nullNameFailsWithoutLookup()
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentService.EnrichmentRequest request =
                service.enrich(
                        null);

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                request.getInitialState());

        Assert.assertNull(
                request.getInitialData());

        Assert.assertNull(
                request.getFuture());

        Mockito.verifyNoInteractions(
                client);
    }

    @Test
    public void blankNameFailsWithoutLookup()
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentService.EnrichmentRequest request =
                service.enrich(
                        "   ");

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                request.getInitialState());

        Assert.assertNull(
                request.getInitialData());

        Assert.assertNull(
                request.getFuture());

        Mockito.verifyNoInteractions(
                client);
    }

    @Test
    public void successIsCachedAndDoesNotRepeatAutomaticLookup()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final HiscoreResult normalResult =
                createSuccessfulResult(
                        "Santa Clause");

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        normalResult));

        stubAbsentIronman(
                client);

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Santa Clause");

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                first.getInitialState());

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        first);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                completed.getState());

        Assert.assertNotNull(
                completed.getData());

        Assert.assertEquals(
                Integer.valueOf(2277),
                completed.getData()
                        .getTotalLevel());

        Assert.assertEquals(
                AccountType.UNKNOWN,
                completed.getData()
                        .getAccountType());

        final HiscoreEnrichmentService.EnrichmentRequest second =
                service.enrich(
                        "santa_clause");

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                second.getInitialState());

        Assert.assertSame(
                completed.getData(),
                second.getInitialData());

        Assert.assertNull(
                second.getFuture());

        Mockito.verify(
                        client,
                        Mockito.times(
                                1))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));
    }

    @Test
    public void notFoundIsNegativeCached()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        null));

        stubAbsentIronman(
                client);

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "definitelyunknown123");

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        first);

        Assert.assertEquals(
                HiscoreEnrichmentState.NOT_FOUND,
                completed.getState());

        Assert.assertNull(
                completed.getData());

        final HiscoreEnrichmentService.EnrichmentRequest second =
                service.enrich(
                        "definitelyunknown123");

        Assert.assertEquals(
                HiscoreEnrichmentState.NOT_FOUND,
                second.getInitialState());

        Assert.assertNull(
                second.getInitialData());

        Assert.assertNull(
                second.getFuture());

        Mockito.verify(
                        client,
                        Mockito.times(
                                1))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));
    }

    @Test
    public void synchronousNormalLookupFailureIsNotCached()
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.NORMAL)))
                .thenThrow(
                        new RuntimeException(
                                "Forced HiScore startup failure"));

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Santa");

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                first.getInitialState());

        Assert.assertNull(
                first.getFuture());

        Assert.assertFalse(
                cache.get(
                                "Santa")
                        .isPresent());

        service.enrich(
                "Santa");

        Mockito.verify(
                        client,
                        Mockito.times(
                                2))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));
    }

    @Test
    public void exceptionalNormalLookupIsNotCached()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                failedFuture(
                        new RuntimeException(
                                "Forced HiScore failure")));

        stubAbsentIronman(
                client);

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Santa");

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        first);

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                completed.getState());

        Assert.assertNull(
                completed.getData());

        Assert.assertFalse(
                cache.get(
                                "Santa")
                        .isPresent());

        service.enrich(
                "Santa");

        Mockito.verify(
                        client,
                        Mockito.times(
                                2))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));
    }

    @Test
    public void nullNormalFutureFailsImmediatelyAndIsNotCached()
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.NORMAL)))
                .thenReturn(
                        null);

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Santa");

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                first.getInitialState());

        Assert.assertNull(
                first.getInitialData());

        Assert.assertNull(
                first.getFuture());

        Assert.assertFalse(
                cache.get(
                                "Santa")
                        .isPresent());

        service.enrich(
                "Santa");

        Mockito.verify(
                        client,
                        Mockito.times(
                                2))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));
    }

    @Test
    public void concurrentNormalizedRequestsShareSameInFlightFuture()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final CompletableFuture<HiscoreResult> normalFuture =
                new CompletableFuture<>();

        final CompletableFuture<HiscoreResult> ironmanFuture =
                new CompletableFuture<>();

        stubNormalLookup(
                client,
                normalFuture);

        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                ironmanFuture);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Santa Clause");

        final HiscoreEnrichmentService.EnrichmentRequest second =
                service.enrich(
                        "santa_clause");

        final HiscoreEnrichmentService.EnrichmentRequest third =
                service.enrich(
                        "SANTA-CLAUSE");

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                first.getInitialState());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                second.getInitialState());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                third.getInitialState());

        Assert.assertSame(
                first.getFuture(),
                second.getFuture());

        Assert.assertSame(
                first.getFuture(),
                third.getFuture());

        Mockito.verify(
                        client,
                        Mockito.times(
                                1))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));

        Mockito.verify(
                        client,
                        Mockito.times(
                                1))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.IRONMAN));

        normalFuture.complete(
                createSuccessfulResult(
                        "Santa Clause"));

        ironmanFuture.complete(
                null);

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        first);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                completed.getState());
    }

    @Test
    public void completedRequestIsRemovedFromInFlightAfterError()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final CompletableFuture<HiscoreResult> firstNormal =
                new CompletableFuture<>();

        final CompletableFuture<HiscoreResult> firstIronman =
                new CompletableFuture<>();

        final CompletableFuture<HiscoreResult> secondNormal =
                new CompletableFuture<>();

        final CompletableFuture<HiscoreResult> secondIronman =
                new CompletableFuture<>();

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.NORMAL)))
                .thenReturn(
                        firstNormal,
                        secondNormal);

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.IRONMAN)))
                .thenReturn(
                        firstIronman,
                        secondIronman);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Party Hat");

        firstNormal.completeExceptionally(
                new RuntimeException(
                        "Forced failure"));

        firstIronman.complete(
                null);

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                await(
                        first)
                        .getState());

        final HiscoreEnrichmentService.EnrichmentRequest second =
                service.enrich(
                        "Party Hat");

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                second.getInitialState());

        Assert.assertNotSame(
                first.getFuture(),
                second.getFuture());

        Mockito.verify(
                        client,
                        Mockito.times(
                                2))
                .lookupAsync(
                        Mockito.anyString(),
                        Mockito.eq(
                                HiscoreEndpoint.NORMAL));

        secondNormal.complete(
                createSuccessfulResult(
                        "Party Hat"));

        secondIronman.complete(
                null);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                await(
                        second)
                        .getState());
    }

    @Test
    public void completedSuccessfulRequestIsRemovedFromInFlight()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final CompletableFuture<HiscoreResult> firstNormal =
                new CompletableFuture<>();

        final CompletableFuture<HiscoreResult> firstIronman =
                new CompletableFuture<>();

        stubNormalLookup(
                client,
                firstNormal);

        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                firstIronman);

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest first =
                service.enrich(
                        "Santa");

        firstNormal.complete(
                createSuccessfulResult(
                        "Santa"));

        firstIronman.complete(
                null);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                await(
                        first)
                        .getState());

        cache.clear();

        final CompletableFuture<HiscoreResult> secondNormal =
                new CompletableFuture<>();

        final CompletableFuture<HiscoreResult> secondIronman =
                new CompletableFuture<>();

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.NORMAL)))
                .thenReturn(
                        secondNormal);

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.IRONMAN)))
                .thenReturn(
                        secondIronman);

        final HiscoreEnrichmentService.EnrichmentRequest second =
                service.enrich(
                        "Santa");

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                second.getInitialState());

        Assert.assertNotSame(
                first.getFuture(),
                second.getFuture());

        secondNormal.complete(
                createSuccessfulResult(
                        "Santa"));

        secondIronman.complete(
                null);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                await(
                        second)
                        .getState());
    }

    @Test
    public void absentIronmanResultLeavesAccountTypeUnknown()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichWithAccountEndpoints(
                        null,
                        null,
                        null);

        Assert.assertEquals(
                AccountType.UNKNOWN,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void ironmanResultIdentifiesIronman()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichWithAccountEndpoints(
                        createSuccessfulResult(
                                "FasT 07"),
                        null,
                        null);

        Assert.assertEquals(
                AccountType.IRONMAN,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void hardcoreResultIdentifiesHardcore()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichWithAccountEndpoints(
                        createSuccessfulResult(
                                "FasT 07"),
                        createSuccessfulResult(
                                "FasT 07"),
                        null);

        Assert.assertEquals(
                AccountType.HARDCORE,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void ultimateResultIdentifiesUltimate()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichWithAccountEndpoints(
                        createSuccessfulResult(
                                "FasT 07"),
                        null,
                        createSuccessfulResult(
                                "FasT 07"));

        Assert.assertEquals(
                AccountType.ULTIMATE,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void hardcoreWinsIfBothSpecializedEndpointsReturnResults()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichWithAccountEndpoints(
                        createSuccessfulResult(
                                "FasT 07"),
                        createSuccessfulResult(
                                "FasT 07"),
                        createSuccessfulResult(
                                "FasT 07"));

        Assert.assertEquals(
                AccountType.HARDCORE,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void ironmanLookupExceptionDegradesToUnknown()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "Zezima")));

        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                failedFuture(
                        new RuntimeException(
                                "Forced Ironman failure")));

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        service.enrich(
                                "Zezima"));

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                completed.getState());

        Assert.assertEquals(
                AccountType.UNKNOWN,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void nullIronmanFutureDegradesToUnknown()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "Zezima")));

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.IRONMAN)))
                .thenReturn(
                        null);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        service.enrich(
                                "Zezima"));

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                completed.getState());

        Assert.assertEquals(
                AccountType.UNKNOWN,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void specializedEndpointExceptionsDegradeToIronman()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "FasT 07")));

        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "FasT 07")));

        stubEndpointLookup(
                client,
                HiscoreEndpoint.HARDCORE_IRONMAN,
                failedFuture(
                        new RuntimeException(
                                "Forced Hardcore failure")));

        stubEndpointLookup(
                client,
                HiscoreEndpoint.ULTIMATE_IRONMAN,
                failedFuture(
                        new RuntimeException(
                                "Forced Ultimate failure")));

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        service.enrich(
                                "FasT 07"));

        Assert.assertEquals(
                AccountType.IRONMAN,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void nullSpecializedEndpointFuturesDegradeToIronman()
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "FasT 07")));

        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "FasT 07")));

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.HARDCORE_IRONMAN)))
                .thenReturn(
                        null);

        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        HiscoreEndpoint.ULTIMATE_IRONMAN)))
                .thenReturn(
                        null);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        service.enrich(
                                "FasT 07"));

        Assert.assertEquals(
                AccountType.IRONMAN,
                completed.getData()
                        .getAccountType());
    }

    @Test
    public void successfulResultExtractsTotalLevel()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        createSuccessfulResult(
                                "Zezima"));

        Assert.assertEquals(
                Integer.valueOf(2277),
                completed.getData()
                        .getTotalLevel());
    }

    @Test
    public void missingOverallProducesNullTotalLevel()
            throws Exception
    {
        final HiscoreResult result =
                createSuccessfulResult(
                        "Zezima");

        removeSkill(
                result,
                HiscoreSkill.OVERALL);

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        result);

        Assert.assertNull(
                completed.getData()
                        .getTotalLevel());
    }

    @Test
    public void negativeOverallProducesNullTotalLevel()
            throws Exception
    {
        final HiscoreResult result =
                createSuccessfulResult(
                        "Zezima");

        replaceSkill(
                result,
                HiscoreSkill.OVERALL,
                new Skill(
                        -1,
                        -1,
                        -1L));

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        result);

        Assert.assertNull(
                completed.getData()
                        .getTotalLevel());
    }

    @Test
    public void successfulResultCalculatesCombatLevel()
            throws Exception
    {
        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        createSuccessfulResult(
                                "Zezima"));

        final int expected =
                Experience.getCombatLevel(
                        99,
                        99,
                        99,
                        99,
                        99,
                        99,
                        99);

        Assert.assertEquals(
                Integer.valueOf(
                        expected),
                completed.getData()
                        .getCombatLevel());
    }

    @Test
    public void missingCombatSkillProducesNullCombatLevel()
            throws Exception
    {
        final HiscoreResult result =
                createSuccessfulResult(
                        "Zezima");

        removeSkill(
                result,
                HiscoreSkill.PRAYER);

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        result);

        Assert.assertNull(
                completed.getData()
                        .getCombatLevel());
    }

    @Test
    public void invalidCombatSkillProducesNullCombatLevel()
            throws Exception
    {
        final HiscoreResult result =
                createSuccessfulResult(
                        "Zezima");

        replaceSkill(
                result,
                HiscoreSkill.ATTACK,
                new Skill(
                        -1,
                        0,
                        0L));

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        result);

        Assert.assertNull(
                completed.getData()
                        .getCombatLevel());
    }

    @Test
    public void validSkillValuesAreCopiedIntoContextValues()
            throws Exception
    {
        final HiscoreResult result =
                createSuccessfulResult(
                        "Zezima");

        replaceSkill(
                result,
                HiscoreSkill.AGILITY,
                new Skill(
                        1,
                        85,
                        3_000_000L));

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        result);

        Assert.assertEquals(
                Integer.valueOf(85),
                completed.getData()
                        .getContextValue(
                                "AGILITY"));
    }

    @Test
    public void negativeSkillValuesAreExcludedFromContextValues()
            throws Exception
    {
        final HiscoreResult result =
                createSuccessfulResult(
                        "Zezima");

        replaceSkill(
                result,
                HiscoreSkill.AGILITY,
                new Skill(
                        -1,
                        -1,
                        -1L));

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                enrichNormalResult(
                        result);

        Assert.assertNull(
                completed.getData()
                        .getContextValue(
                                "AGILITY"));
    }

    @Test
    public void cachedEnrichmentPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        cache.putSuccess(
                "Santa Clause",
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        java.util.Collections.emptyMap()));

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        cache);

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    service.enrich(
                            "Santa Clause"));

            consume(
                    service.enrich(
                            "santa_clause"));
        }

        final long exactStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    service.enrich(
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
                    service.enrich(
                            "santa_clause"));
        }

        final long normalizedElapsed =
                System.nanoTime()
                        - normalizedStart;

        printPerformance(
                "CachedHit",
                exactElapsed,
                "NormalizedCachedHit",
                normalizedElapsed);

        Mockito.verifyNoInteractions(
                client);
    }

    @Test
    public void realJagexHiscoreSmokeTest()
            throws Exception
    {
        if (!USE_REAL_JAGEX_HISCORES)
        {
            return;
        }

        final HiscoreClient realClient =
                createRealHiscoreClient();

        final HiscoreEnrichmentCache cache =
                new HiscoreEnrichmentCache();

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        realClient,
                        cache);

        final HiscoreEnrichmentService.EnrichmentRequest request =
                service.enrich(
                        REAL_PLAYER_NAME);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                request.getInitialState());

        final HiscoreEnrichmentCache.CachedProfileEnrichment result =
                await(
                        request);

        Assert.assertEquals(
                "Expected real Jagex HiScores to return a ranked player",
                HiscoreEnrichmentState.LOADED,
                result.getState());

        Assert.assertNotNull(
                result.getData());

        Assert.assertNotNull(
                result.getData()
                        .getTotalLevel());

        System.out.println(
                "[RuneTags][HiscoreEnrichmentServiceTest] RealJagex= "
                        + "Player: "
                        + REAL_PLAYER_NAME
                        + " | Total: "
                        + result.getData()
                        .getTotalLevel()
                        + " | Combat: "
                        + result.getData()
                        .getCombatLevel()
                        + " | AccountType: "
                        + result.getData()
                        .getAccountType());
    }

    /*
     * HELPERS
     */

    private static HiscoreEnrichmentCache.CachedProfileEnrichment
    enrichNormalResult(
            HiscoreResult normalResult)
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        normalResult));

        stubAbsentIronman(
                client);

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        return await(
                service.enrich(
                        "Zezima"));
    }

    private static HiscoreEnrichmentCache.CachedProfileEnrichment
    enrichWithAccountEndpoints(
            HiscoreResult ironmanResult,
            HiscoreResult hardcoreResult,
            HiscoreResult ultimateResult)
            throws Exception
    {
        final HiscoreClient client =
                Mockito.mock(
                        HiscoreClient.class);

        stubNormalLookup(
                client,
                CompletableFuture.completedFuture(
                        createSuccessfulResult(
                                "FasT 07")));

        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                CompletableFuture.completedFuture(
                        ironmanResult));

        if (ironmanResult != null)
        {
            stubEndpointLookup(
                    client,
                    HiscoreEndpoint.HARDCORE_IRONMAN,
                    CompletableFuture.completedFuture(
                            hardcoreResult));

            stubEndpointLookup(
                    client,
                    HiscoreEndpoint.ULTIMATE_IRONMAN,
                    CompletableFuture.completedFuture(
                            ultimateResult));
        }

        final HiscoreEnrichmentService service =
                new HiscoreEnrichmentService(
                        client,
                        new HiscoreEnrichmentCache());

        final HiscoreEnrichmentCache.CachedProfileEnrichment completed =
                await(
                        service.enrich(
                                "FasT 07"));

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                completed.getState());

        Assert.assertNotNull(
                completed.getData());

        return completed;
    }

    private static void stubNormalLookup(
            HiscoreClient client,
            CompletableFuture<HiscoreResult> future)
    {
        stubEndpointLookup(
                client,
                HiscoreEndpoint.NORMAL,
                future);
    }

    private static void stubAbsentIronman(
            HiscoreClient client)
    {
        stubEndpointLookup(
                client,
                HiscoreEndpoint.IRONMAN,
                CompletableFuture.completedFuture(
                        null));
    }

    private static void stubEndpointLookup(
            HiscoreClient client,
            HiscoreEndpoint endpoint,
            CompletableFuture<HiscoreResult> future)
    {
        Mockito.when(
                        client.lookupAsync(
                                Mockito.anyString(),
                                Mockito.eq(
                                        endpoint)))
                .thenReturn(
                        future);
    }

    private static HiscoreEnrichmentCache.CachedProfileEnrichment await(
            HiscoreEnrichmentService.EnrichmentRequest request)
            throws Exception
    {
        Assert.assertNotNull(
                "Expected an asynchronous enrichment future",
                request.getFuture());

        return request.getFuture()
                .get(
                        30,
                        TimeUnit.SECONDS);
    }

    private static CompletableFuture<HiscoreResult> failedFuture(
            Throwable throwable)
    {
        final CompletableFuture<HiscoreResult> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                throwable);

        return future;
    }

    private static HiscoreResult createSuccessfulResult(
            String playerName)
    {
        final Map<HiscoreSkill, Skill> skills =
                new EnumMap<>(
                        HiscoreSkill.class);

        skills.put(
                HiscoreSkill.OVERALL,
                new Skill(
                        1,
                        2277,
                        0L));

        skills.put(
                HiscoreSkill.ATTACK,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        skills.put(
                HiscoreSkill.STRENGTH,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        skills.put(
                HiscoreSkill.DEFENCE,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        skills.put(
                HiscoreSkill.HITPOINTS,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        skills.put(
                HiscoreSkill.MAGIC,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        skills.put(
                HiscoreSkill.RANGED,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        skills.put(
                HiscoreSkill.PRAYER,
                new Skill(
                        1,
                        99,
                        13_034_431L));

        return new HiscoreResult(
                playerName,
                skills);
    }

    @SuppressWarnings("unchecked")
    private static void removeSkill(
            HiscoreResult result,
            HiscoreSkill skill)
            throws Exception
    {
        final java.lang.reflect.Field field =
                HiscoreResult.class
                        .getDeclaredField(
                                "skills");

        field.setAccessible(
                true);

        final Map<HiscoreSkill, Skill> skills =
                (Map<HiscoreSkill, Skill>) field.get(
                        result);

        skills.remove(
                skill);
    }

    @SuppressWarnings("unchecked")
    private static void replaceSkill(
            HiscoreResult result,
            HiscoreSkill skill,
            Skill value)
            throws Exception
    {
        final java.lang.reflect.Field field =
                HiscoreResult.class
                        .getDeclaredField(
                                "skills");

        field.setAccessible(
                true);

        final Map<HiscoreSkill, Skill> skills =
                (Map<HiscoreSkill, Skill>) field.get(
                        result);

        skills.put(
                skill,
                value);
    }

    private static HiscoreClient createRealHiscoreClient()
            throws Exception
    {
        final Constructor<HiscoreClient> constructor =
                HiscoreClient.class
                        .getDeclaredConstructor(
                                OkHttpClient.class,
                                Gson.class);

        constructor.setAccessible(
                true);

        final OkHttpClient httpClient =
                new OkHttpClient.Builder()
                        .callTimeout(
                                20,
                                TimeUnit.SECONDS)
                        .build();

        return constructor.newInstance(
                httpClient,
                new Gson());
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
                "[RuneTags][HiscoreEnrichmentServiceTest] Performance= "
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