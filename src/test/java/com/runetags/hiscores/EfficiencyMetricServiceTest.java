package com.runetags.hiscores;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class EfficiencyMetricServiceTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void nullNameReturnsNullWithoutRequest()
            throws Exception
    {
        final OkHttpClient httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        final EfficiencyMetricService service =
                new EfficiencyMetricService(
                        httpClient,
                        new Gson());

        Assert.assertNull(
                await(
                        service.lookup(
                                null)));

        Mockito.verifyNoInteractions(
                httpClient);
    }

    @Test
    public void blankNameReturnsNullWithoutRequest()
            throws Exception
    {
        final OkHttpClient httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        final EfficiencyMetricService service =
                new EfficiencyMetricService(
                        httpClient,
                        new Gson());

        Assert.assertNull(
                await(
                        service.lookup(
                                "   ")));

        Mockito.verifyNoInteractions(
                httpClient);
    }

    @Test
    public void successfulResponseReturnsEhpAndEhb()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":1234.5,\"ehb\":678.25}");

        final EfficiencyProfileData data =
                await(
                        future);

        Assert.assertNotNull(
                data);

        Assert.assertEquals(
                Double.valueOf(1234.5),
                data.getEhp());

        Assert.assertEquals(
                Double.valueOf(678.25),
                data.getEhb());
    }

    @Test
    public void ehpOnlyResponseIsValid()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":500.25}");

        final EfficiencyProfileData data =
                await(
                        future);

        Assert.assertNotNull(
                data);

        Assert.assertEquals(
                Double.valueOf(500.25),
                data.getEhp());

        Assert.assertNull(
                data.getEhb());
    }

    @Test
    public void ehbOnlyResponseIsValid()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehb\":250.75}");

        final EfficiencyProfileData data =
                await(
                        future);

        Assert.assertNotNull(
                data);

        Assert.assertNull(
                data.getEhp());

        Assert.assertEquals(
                Double.valueOf(250.75),
                data.getEhb());
    }

    @Test
    public void zeroEfficiencyValuesAreValid()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":0.0,\"ehb\":0.0}");

        final EfficiencyProfileData data =
                await(
                        future);

        Assert.assertNotNull(
                data);

        Assert.assertEquals(
                Double.valueOf(0.0),
                data.getEhp());

        Assert.assertEquals(
                Double.valueOf(0.0),
                data.getEhb());
    }

    @Test
    public void negativeEhpIsDiscarded()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":-1.0,\"ehb\":100.0}");

        final EfficiencyProfileData data =
                await(
                        future);

        Assert.assertNotNull(
                data);

        Assert.assertNull(
                data.getEhp());

        Assert.assertEquals(
                Double.valueOf(100.0),
                data.getEhb());
    }

    @Test
    public void negativeEhbIsDiscarded()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":100.0,\"ehb\":-1.0}");

        final EfficiencyProfileData data =
                await(
                        future);

        Assert.assertNotNull(
                data);

        Assert.assertEquals(
                Double.valueOf(100.0),
                data.getEhp());

        Assert.assertNull(
                data.getEhb());
    }

    @Test
    public void bothNegativeValuesProduceNullResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":-1.0,\"ehb\":-1.0}");

        Assert.assertNull(
                await(
                        future));
    }

    @Test
    public void missingEfficiencyValuesProduceNullResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{}");

        Assert.assertNull(
                await(
                        future));
    }

    @Test
    public void literalNullResponseProducesNullResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "null");

        Assert.assertNull(
                await(
                        future));
    }

    @Test
    public void malformedJsonProducesNullResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{ definitely not json");

        Assert.assertNull(
                await(
                        future));
    }

    @Test
    public void unsuccessfulHttpResponseProducesNullResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "definitelyunknown123");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                404,
                "{\"message\":\"Not found\"}");

        Assert.assertNull(
                await(
                        future));
    }

    @Test
    public void transportFailureProducesNullResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Zezima");

        final Callback callback =
                captureCallback(
                        harness.call);

        callback.onFailure(
                harness.call,
                new IOException(
                        "Forced network failure"));

        Assert.assertNull(
                await(
                        future));
    }

    @Test
    public void successfulResultIsCached()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "Santa");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":400.0,\"ehb\":200.0}");

        final EfficiencyProfileData firstData =
                await(
                        first);

        final EfficiencyProfileData secondData =
                await(
                        harness.service.lookup(
                                "Santa"));

        Assert.assertNotNull(
                firstData);

        Assert.assertSame(
                firstData,
                secondData);

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void negativeResultIsCached()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "definitelyunknown123");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                404,
                "");

        Assert.assertNull(
                await(
                        first));

        Assert.assertNull(
                await(
                        harness.service.lookup(
                                "definitelyunknown123")));

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void malformedResponseIsNegativeCached()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "definitelyunknown123");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{ malformed");

        Assert.assertNull(
                await(
                        first));

        Assert.assertNull(
                await(
                        harness.service.lookup(
                                "definitelyunknown123")));

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void transportFailureIsNegativeCached()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "definitelyunknown123");

        final Callback callback =
                captureCallback(
                        harness.call);

        callback.onFailure(
                harness.call,
                new IOException(
                        "Forced network failure"));

        Assert.assertNull(
                await(
                        first));

        Assert.assertNull(
                await(
                        harness.service.lookup(
                                "definitelyunknown123")));

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void normalizedNamesShareCachedResult()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "Santa Clause");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":100.0,\"ehb\":50.0}");

        final EfficiencyProfileData firstData =
                await(
                        first);

        Assert.assertSame(
                firstData,
                await(
                        harness.service.lookup(
                                "santa_clause")));

        Assert.assertSame(
                firstData,
                await(
                        harness.service.lookup(
                                "SANTA-CLAUSE")));

        Assert.assertSame(
                firstData,
                await(
                        harness.service.lookup(
                                "  santa   clause  ")));

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void normalizedConcurrentRequestsShareSameFuture()
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "Santa Clause");

        final CompletableFuture<EfficiencyProfileData> second =
                harness.service.lookup(
                        "santa_clause");

        final CompletableFuture<EfficiencyProfileData> third =
                harness.service.lookup(
                        "SANTA-CLAUSE");

        Assert.assertSame(
                first,
                second);

        Assert.assertSame(
                first,
                third);

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void completedRequestIsRemovedFromInFlight()
            throws Exception
    {
        final OkHttpClient httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        final Call firstCall =
                Mockito.mock(
                        Call.class);

        final Call secondCall =
                Mockito.mock(
                        Call.class);

        Mockito.when(
                        httpClient.newCall(
                                Mockito.any(
                                        Request.class)))
                .thenReturn(
                        firstCall,
                        secondCall);

        final EfficiencyMetricService service =
                new EfficiencyMetricService(
                        httpClient,
                        new Gson());

        final CompletableFuture<EfficiencyProfileData> first =
                service.lookup(
                        "Santa");

        final Callback firstCallback =
                captureCallback(
                        firstCall);

        final Request firstRequest =
                captureFirstRequest(
                        httpClient);

        firstCallback.onResponse(
                firstCall,
                response(
                        firstRequest,
                        200,
                        "{\"ehp\":100.0,\"ehb\":50.0}"));

        Assert.assertNotNull(
                await(
                        first));

        service.clear();

        final CompletableFuture<EfficiencyProfileData> second =
                service.lookup(
                        "Santa");

        Assert.assertNotSame(
                first,
                second);

        Mockito.verify(
                        httpClient,
                        Mockito.times(
                                2))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void clearInvalidatesSuccessfulCache()
            throws Exception
    {
        final OkHttpClient httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        final Call firstCall =
                Mockito.mock(
                        Call.class);

        final Call secondCall =
                Mockito.mock(
                        Call.class);

        Mockito.when(
                        httpClient.newCall(
                                Mockito.any(
                                        Request.class)))
                .thenReturn(
                        firstCall,
                        secondCall);

        final EfficiencyMetricService service =
                new EfficiencyMetricService(
                        httpClient,
                        new Gson());

        final CompletableFuture<EfficiencyProfileData> first =
                service.lookup(
                        "Santa");

        final Request request =
                captureFirstRequest(
                        httpClient);

        final Callback callback =
                captureCallback(
                        firstCall);

        callback.onResponse(
                firstCall,
                response(
                        request,
                        200,
                        "{\"ehp\":100.0,\"ehb\":50.0}"));

        Assert.assertNotNull(
                await(
                        first));

        service.clear();

        service.lookup(
                "Santa");

        Mockito.verify(
                        httpClient,
                        Mockito.times(
                                2))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void clearInvalidatesNegativeCache()
            throws Exception
    {
        final OkHttpClient httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        final Call firstCall =
                Mockito.mock(
                        Call.class);

        final Call secondCall =
                Mockito.mock(
                        Call.class);

        Mockito.when(
                        httpClient.newCall(
                                Mockito.any(
                                        Request.class)))
                .thenReturn(
                        firstCall,
                        secondCall);

        final EfficiencyMetricService service =
                new EfficiencyMetricService(
                        httpClient,
                        new Gson());

        final CompletableFuture<EfficiencyProfileData> first =
                service.lookup(
                        "definitelyunknown123");

        final Request request =
                captureFirstRequest(
                        httpClient);

        final Callback callback =
                captureCallback(
                        firstCall);

        callback.onResponse(
                firstCall,
                response(
                        request,
                        404,
                        ""));

        Assert.assertNull(
                await(
                        first));

        service.clear();

        service.lookup(
                "definitelyunknown123");

        Mockito.verify(
                        httpClient,
                        Mockito.times(
                                2))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void requestUsesExpectedWiseOldManUrl()
    {
        final RequestHarness harness =
                createHarness();

        harness.service.lookup(
                "Santa Clause");

        final Request request =
                captureRequest(
                        harness);

        Assert.assertEquals(
                "https://api.wiseoldman.net/v2/players/Santa%20Clause",
                request.url()
                        .toString());
    }

    @Test
    public void requestTrimsPlayerNameBeforeUrlConstruction()
    {
        final RequestHarness harness =
                createHarness();

        harness.service.lookup(
                "   Santa Clause   ");

        final Request request =
                captureRequest(
                        harness);

        Assert.assertEquals(
                "https://api.wiseoldman.net/v2/players/Santa%20Clause",
                request.url()
                        .toString());
    }

    @Test
    public void requestPreservesUnderscoresInActualPlayerPath()
    {
        final RequestHarness harness =
                createHarness();

        harness.service.lookup(
                "Santa_Clause");

        final Request request =
                captureRequest(
                        harness);

        Assert.assertEquals(
                "https://api.wiseoldman.net/v2/players/Santa_Clause",
                request.url()
                        .toString());
    }

    @Test
    public void requestSetsJsonAcceptHeader()
    {
        final RequestHarness harness =
                createHarness();

        harness.service.lookup(
                "Santa");

        final Request request =
                captureRequest(
                        harness);

        Assert.assertEquals(
                "application/json",
                request.header(
                        "Accept"));
    }

    @Test
    public void requestSetsRuneTagsUserAgent()
    {
        final RequestHarness harness =
                createHarness();

        harness.service.lookup(
                "Santa");

        final Request request =
                captureRequest(
                        harness);

        Assert.assertEquals(
                "RuneTags RuneLite plugin",
                request.header(
                        "User-Agent"));
    }

    @Test
    public void shutdownCancelsActiveCall()
    {
        final RequestHarness harness =
                createHarness();

        harness.service.lookup(
                "Santa");

        harness.service.shutdown();

        Mockito.verify(
                        harness.call)
                .cancel();
    }

    @Test
    public void shutdownCancelsInFlightFuture()
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Santa");

        harness.service.shutdown();

        Assert.assertTrue(
                future.isCancelled());
    }

    @Test
    public void shutdownPreventsFutureRequests()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        harness.service.shutdown();

        Assert.assertNull(
                await(
                        harness.service.lookup(
                                "Santa")));

        Mockito.verifyNoInteractions(
                harness.httpClient);
    }

    @Test
    public void shutdownClearsPreviouslyCachedData()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> first =
                harness.service.lookup(
                        "Santa");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":100.0,\"ehb\":50.0}");

        Assert.assertNotNull(
                await(
                        first));

        harness.service.shutdown();

        Assert.assertNull(
                await(
                        harness.service.lookup(
                                "Santa")));
    }

    @Test
    public void callbackAfterShutdownDoesNotRepopulateCache()
            throws Exception
    {
        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> future =
                harness.service.lookup(
                        "Santa");

        final Request request =
                captureRequest(
                        harness);

        final Callback callback =
                captureCallback(
                        harness.call);

        harness.service.shutdown();

        callback.onResponse(
                harness.call,
                response(
                        request,
                        200,
                        "{\"ehp\":100.0,\"ehb\":50.0}"));

        Assert.assertTrue(
                future.isCancelled());

        Assert.assertNull(
                await(
                        harness.service.lookup(
                                "Santa")));

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    @Test
    public void cachedLookupPerformance()
            throws Exception
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final RequestHarness harness =
                createHarness();

        final CompletableFuture<EfficiencyProfileData> initial =
                harness.service.lookup(
                        "Santa Clause");

        final Request request =
                captureRequest(
                        harness);

        respond(
                harness,
                request,
                200,
                "{\"ehp\":1000.0,\"ehb\":500.0}");

        Assert.assertNotNull(
                await(
                        initial));

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    harness.service.lookup(
                            "Santa Clause"));

            consume(
                    harness.service.lookup(
                            "santa_clause"));
        }

        final long exactStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    harness.service.lookup(
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
                    harness.service.lookup(
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

        Mockito.verify(
                        harness.httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any(
                                Request.class));
    }

    /*
     * HELPERS
     */

    private static RequestHarness createHarness()
    {
        final OkHttpClient httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        final Call call =
                Mockito.mock(
                        Call.class);

        Mockito.when(
                        httpClient.newCall(
                                Mockito.any(
                                        Request.class)))
                .thenReturn(
                        call);

        return new RequestHarness(
                httpClient,
                call,
                new EfficiencyMetricService(
                        httpClient,
                        new Gson()));
    }

    private static Request captureRequest(
            RequestHarness harness)
    {
        final ArgumentCaptor<Request> captor =
                ArgumentCaptor.forClass(
                        Request.class);

        Mockito.verify(
                        harness.httpClient)
                .newCall(
                        captor.capture());

        return captor.getValue();
    }

    private static Request captureFirstRequest(
            OkHttpClient httpClient)
    {
        final ArgumentCaptor<Request> captor =
                ArgumentCaptor.forClass(
                        Request.class);

        Mockito.verify(
                        httpClient)
                .newCall(
                        captor.capture());

        return captor.getValue();
    }

    private static Callback captureCallback(
            Call call)
    {
        final ArgumentCaptor<Callback> captor =
                ArgumentCaptor.forClass(
                        Callback.class);

        Mockito.verify(
                        call)
                .enqueue(
                        captor.capture());

        return captor.getValue();
    }

    private static void respond(
            RequestHarness harness,
            Request request,
            int code,
            String body)
            throws IOException
    {
        captureCallback(
                harness.call)
                .onResponse(
                        harness.call,
                        response(
                                request,
                                code,
                                body));
    }

    private static Response response(
            Request request,
            int code,
            String body)
    {
        return new Response.Builder()
                .request(
                        request)
                .protocol(
                        Protocol.HTTP_1_1)
                .code(
                        code)
                .message(
                        code >= 200 && code < 300
                                ? "OK"
                                : "ERROR")
                .body(
                        ResponseBody.create(
                                MediaType.parse(
                                        "application/json"),
                                body))
                .build();
    }

    private static EfficiencyProfileData await(
            CompletableFuture<EfficiencyProfileData> future)
            throws Exception
    {
        return future.get(
                5,
                TimeUnit.SECONDS);
    }

    private static final class RequestHarness
    {
        private final OkHttpClient httpClient;
        private final Call call;
        private final EfficiencyMetricService service;

        private RequestHarness(
                OkHttpClient httpClient,
                Call call,
                EfficiencyMetricService service)
        {
            this.httpClient =
                    httpClient;

            this.call =
                    call;

            this.service =
                    service;
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
                "[RuneTags][EfficiencyMetricServiceTest] Performance= "
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