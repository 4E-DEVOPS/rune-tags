package com.runetags.reports;

import com.google.gson.Gson;
import com.runetags.Configurations;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.runelite.client.callback.ClientThread;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class ReportCaseServiceTest
{
    private OkHttpClient httpClient;
    private ClientThread clientThread;
    private Configurations config;

    private ReportCaseService service;
    private Path reportFile;

    @Before
    public void setUp()
            throws Exception
    {
        httpClient =
                Mockito.mock(
                        OkHttpClient.class);

        clientThread =
                Mockito.mock(
                        ClientThread.class);

        config =
                Mockito.mock(
                        Configurations.class);

        Mockito.when(
                        config.showReports())
                .thenReturn(
                        true);

        Mockito.doAnswer(invocation ->
                {
                    final Runnable runnable =
                            invocation.getArgument(
                                    0);

                    runnable.run();

                    return null;
                })
                .when(
                        clientThread)
                .invokeLater(
                        Mockito.any(
                                Runnable.class));

        reportFile =
                Files.createTempDirectory(
                                "runetags-reports-test")
                        .resolve(
                                "mixedlist.json");

        service =
                new ReportCaseService(
                        httpClient,
                        new Gson(),
                        clientThread,
                        config,
                        reportFile);
    }

    @After
    public void tearDown()
    {
        service.shutdown();
    }

    /*
     * TESTS
     */

    @Test
    public void parserRejectsNullBlankMalformedAndNonArrayDatasets()
            throws Exception
    {
        Assert.assertTrue(
                parseDataset(
                        null)
                        .isEmpty());

        Assert.assertTrue(
                parseDataset(
                        "")
                        .isEmpty());

        Assert.assertTrue(
                parseDataset(
                        "   ")
                        .isEmpty());

        Assert.assertTrue(
                parseDataset(
                        "{broken")
                        .isEmpty());

        Assert.assertTrue(
                parseDataset(
                        "{\"accused_rsn\":\"Santa\"}")
                        .isEmpty());
    }

    @Test
    public void parserAcceptsRwWdrAndDefaultRwButRejectsUnknownSources()
            throws Exception
    {
        final Map<String, List<ReportSummary>> reports =
                parseDataset(
                        "["
                                + "{"
                                + "\"accused_rsn\":\"Santa\","
                                + "\"hash\":\"rw-1\","
                                + "\"source\":\"RW\""
                                + "},"
                                + "{"
                                + "\"accused_rsn\":\"Zezima\","
                                + "\"hash\":\"wdr-1\","
                                + "\"source\":\"WDR\""
                                + "},"
                                + "{"
                                + "\"accused_rsn\":\"Party Hat\","
                                + "\"hash\":\"default-1\""
                                + "},"
                                + "{"
                                + "\"accused_rsn\":\"FasT 07\","
                                + "\"hash\":\"unknown-1\","
                                + "\"source\":\"OTHER\""
                                + "}"
                                + "]");

        Assert.assertEquals(
                3,
                reports.size());

        Assert.assertEquals(
                "RW",
                reports.get(
                                "santa")
                        .get(
                                0)
                        .getSource());

        Assert.assertEquals(
                "WDR",
                reports.get(
                                "zezima")
                        .get(
                                0)
                        .getSource());

        Assert.assertEquals(
                "RW",
                reports.get(
                                "party hat")
                        .get(
                                0)
                        .getSource());

        Assert.assertFalse(
                reports.containsKey(
                        "fast 07"));
    }

    @Test
    public void parserAggregatesCountAndUsesNewestDatedReport()
            throws Exception
    {
        final List<ReportSummary> reports =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                "2026-01-01 10:00:00",
                                "old-case",
                                null,
                                "Old reason",
                                "Low",
                                "RW")
                                + ","
                                + report(
                                "Santa",
                                "2026-08-15 12:30:00",
                                "new-case",
                                null,
                                "Newest reason",
                                "High",
                                "WDR")
                                + ","
                                + report(
                                "Santa",
                                "2026-04-10 09:00:00",
                                "middle-case",
                                null,
                                "Middle reason",
                                "Medium",
                                "RW")
                                + "]")
                        .get(
                                "santa");

        Assert.assertNotNull(
                reports);

        Assert.assertEquals(
                1,
                reports.size());

        final ReportSummary summary =
                reports.get(
                        0);

        Assert.assertEquals(
                3,
                summary.getCaseCount());

        Assert.assertEquals(
                "WDR",
                summary.getSource());

        Assert.assertEquals(
                "new-case",
                summary.getCaseId());

        Assert.assertEquals(
                "Newest reason",
                summary.getReason());

        Assert.assertEquals(
                "High",
                summary.getEvidenceRating());

        Assert.assertEquals(
                "August 15, 2026",
                summary.getFormattedDate());
    }

    @Test
    public void datedReportOutranksLaterUndatedReport()
            throws Exception
    {
        final ReportSummary summary =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                "2026-08-15 12:30:00",
                                "dated-case",
                                null,
                                "Dated",
                                "High",
                                "RW")
                                + ","
                                + report(
                                "Santa",
                                null,
                                "undated-case",
                                null,
                                "Undated",
                                "Low",
                                "WDR")
                                + "]")
                        .get(
                                "santa")
                        .get(
                                0);

        Assert.assertEquals(
                2,
                summary.getCaseCount());

        Assert.assertEquals(
                "dated-case",
                summary.getCaseId());

        Assert.assertEquals(
                "Dated",
                summary.getReason());
    }

    @Test
    public void laterFeedEntryWinsWhenAllReportsAreUndated()
            throws Exception
    {
        final ReportSummary summary =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                null,
                                "first-case",
                                null,
                                "First",
                                "Low",
                                "RW")
                                + ","
                                + report(
                                "Santa",
                                null,
                                "second-case",
                                null,
                                "Second",
                                "High",
                                "WDR")
                                + "]")
                        .get(
                                "santa")
                        .get(
                                0);

        Assert.assertEquals(
                2,
                summary.getCaseCount());

        Assert.assertEquals(
                "second-case",
                summary.getCaseId());

        Assert.assertEquals(
                "Second",
                summary.getReason());

        Assert.assertEquals(
                "WDR",
                summary.getSource());

        Assert.assertNull(
                summary.getPublishedDate());
    }

    @Test
    public void malformedPublishedDateBehavesAsUndated()
            throws Exception
    {
        final ReportSummary summary =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                "not-a-date",
                                "bad-date",
                                null,
                                "Bad date",
                                "Low",
                                "RW")
                                + "]")
                        .get(
                                "santa")
                        .get(
                                0);

        Assert.assertNull(
                summary.getPublishedDate());

        Assert.assertEquals(
                "",
                summary.getFormattedDate());
    }

    @Test
    public void hashTakesPriorityOverLegacyShortCode()
            throws Exception
    {
        final ReportSummary summary =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                "2026-08-15 12:30:00",
                                "current-hash",
                                "legacy-code",
                                "Reason",
                                "High",
                                "RW")
                                + "]")
                        .get(
                                "santa")
                        .get(
                                0);

        Assert.assertEquals(
                "current-hash",
                summary.getCaseId());
    }

    @Test
    public void shortCodeIsUsedWhenHashIsMissing()
            throws Exception
    {
        final ReportSummary summary =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                "2026-08-15 12:30:00",
                                null,
                                "legacy-code",
                                "Reason",
                                "High",
                                "RW")
                                + "]")
                        .get(
                                "santa")
                        .get(
                                0);

        Assert.assertEquals(
                "legacy-code",
                summary.getCaseId());
    }

    @Test
    public void parsedResultsAreUnmodifiable()
            throws Exception
    {
        final Map<String, List<ReportSummary>> reports =
                parseDataset(
                        "["
                                + report(
                                "Santa",
                                null,
                                "case-1",
                                null,
                                "Reason",
                                "High",
                                "RW")
                                + "]");

        try
        {
            reports.put(
                    "zezima",
                    Collections.emptyList());

            Assert.fail(
                    "Expected parsed report map to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }

        try
        {
            reports.get(
                            "santa")
                    .add(
                            ReportSummary.builder()
                                    .source(
                                            "RW")
                                    .build());

            Assert.fail(
                    "Expected player report list to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }
    }

    @Test
    public void parsedDatasetIsReusedUntilDatasetRevisionChanges()
            throws Exception
    {
        setField(
                "rawDataset",
                "["
                        + report(
                        "Santa",
                        null,
                        "case-1",
                        null,
                        "First",
                        "High",
                        "RW")
                        + "]");

        setLongField(
                "datasetRevision",
                1L);

        final Map<String, List<ReportSummary>> first =
                parsedReportsForCurrentDataset();

        final Map<String, List<ReportSummary>> second =
                parsedReportsForCurrentDataset();

        Assert.assertSame(
                first,
                second);

        Assert.assertEquals(
                "case-1",
                first.get(
                                "santa")
                        .get(
                                0)
                        .getCaseId());

        setField(
                "rawDataset",
                "["
                        + report(
                        "Santa",
                        null,
                        "case-2",
                        null,
                        "Second",
                        "High",
                        "RW")
                        + "]");

        setLongField(
                "datasetRevision",
                2L);

        final Map<String, List<ReportSummary>> third =
                parsedReportsForCurrentDataset();

        Assert.assertNotSame(
                first,
                third);

        Assert.assertEquals(
                "case-2",
                third.get(
                                "santa")
                        .get(
                                0)
                        .getCaseId());
    }

    @Test
    public void blankPlayerNameDeliversEmptyReportsWithoutNetworkRequest()
    {
        final AtomicReference<List<ReportSummary>> delivered =
                new AtomicReference<>();

        service.requestReports(
                "   ",
                delivered::set);

        Assert.assertEquals(
                Collections.emptyList(),
                delivered.get());

        Mockito.verifyNoInteractions(
                httpClient);
    }

    @Test
    public void disabledReportsDeliverEmptyResultWithoutNetworkRequest()
    {
        Mockito.when(
                        config.showReports())
                .thenReturn(
                        false);

        final AtomicReference<List<ReportSummary>> delivered =
                new AtomicReference<>();

        service.requestReports(
                "Santa",
                delivered::set);

        Assert.assertEquals(
                Collections.emptyList(),
                delivered.get());

        Mockito.verifyNoInteractions(
                httpClient);
    }

    @Test
    public void cachedDatasetDeliversReportWithoutNetworkRequest()
            throws Exception
    {
        setField(
                "rawDataset",
                "["
                        + report(
                        "Santa",
                        "2026-08-15 12:30:00",
                        "cached-case",
                        null,
                        "Cached reason",
                        "High",
                        "RW")
                        + "]");

        setField(
                "lastSuccessfulDownload",
                Instant.now());

        setLongField(
                "datasetRevision",
                1L);

        final CountDownLatch latch =
                new CountDownLatch(
                        1);

        final AtomicReference<List<ReportSummary>> delivered =
                new AtomicReference<>();

        service.requestReports(
                "Santa",
                reports ->
                {
                    delivered.set(
                            reports);

                    latch.countDown();
                });

        Assert.assertTrue(
                "Timed out waiting for cached report delivery.",
                latch.await(
                        2,
                        TimeUnit.SECONDS));

        Assert.assertNotNull(
                delivered.get());

        Assert.assertEquals(
                1,
                delivered.get()
                        .size());

        Assert.assertEquals(
                "cached-case",
                delivered.get()
                        .get(
                                0)
                        .getCaseId());

        Mockito.verifyNoInteractions(
                httpClient);
    }

    @Test
    public void concurrentMissingDatasetRequestsShareOneDownload()
    {
        final Call call =
                Mockito.mock(
                        Call.class);

        Mockito.when(
                        httpClient.newCall(
                                Mockito.any()))
                .thenReturn(
                        call);

        service.requestReports(
                "Santa",
                reports ->
                {
                });

        service.requestReports(
                "Zezima",
                reports ->
                {
                });

        Mockito.verify(
                        httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any());

        Mockito.verify(
                        call,
                        Mockito.times(
                                1))
                .enqueue(
                        Mockito.any(
                                Callback.class));
    }

    @Test
    public void failedDownloadIsRetryThrottled()
            throws IOException
    {
        final Call firstCall =
                Mockito.mock(
                        Call.class);

        final ArgumentCaptor<Callback> callbackCaptor =
                ArgumentCaptor.forClass(
                        Callback.class);

        Mockito.when(
                        httpClient.newCall(
                                Mockito.any()))
                .thenReturn(
                        firstCall);

        service.requestReports(
                "Santa",
                reports ->
                {
                });

        Mockito.verify(
                        firstCall)
                .enqueue(
                        callbackCaptor.capture());

        callbackCaptor
                .getValue()
                .onFailure(
                        firstCall,
                        new IOException(
                                "Test failure"));

        service.requestReports(
                "Santa",
                reports ->
                {
                });

        Mockito.verify(
                        httpClient,
                        Mockito.times(
                                1))
                .newCall(
                        Mockito.any());
    }

    @Test
    public void clearCancelsActiveCallAndResetsRuntimeState()
            throws Exception
    {
        final Call activeCall =
                Mockito.mock(
                        Call.class);

        setField(
                "activeCall",
                activeCall);

        setField(
                "rawDataset",
                "[]");

        setField(
                "lastSuccessfulDownload",
                Instant.now());

        setField(
                "lastDownloadAttempt",
                Instant.now());

        setBooleanField(
                "initializationInFlight",
                true);

        setBooleanField(
                "downloadInFlight",
                true);

        setField(
                "parsedReports",
                Collections.singletonMap(
                        "santa",
                        Collections.singletonList(
                                ReportSummary.builder()
                                        .source(
                                                "RW")
                                        .build())));

        final long revisionBefore =
                getLongField(
                        "datasetRevision");

        service.clear();

        Mockito.verify(
                        activeCall)
                .cancel();

        Assert.assertNull(
                getField(
                        "activeCall"));

        Assert.assertNull(
                getField(
                        "rawDataset"));

        Assert.assertNull(
                getField(
                        "lastSuccessfulDownload"));

        Assert.assertNull(
                getField(
                        "lastDownloadAttempt"));

        Assert.assertFalse(
                getBooleanField(
                        "initializationInFlight"));

        Assert.assertFalse(
                getBooleanField(
                        "downloadInFlight"));

        Assert.assertEquals(
                Collections.emptyMap(),
                getField(
                        "parsedReports"));

        Assert.assertEquals(
                -1L,
                getLongField(
                        "parsedRevision"));

        Assert.assertEquals(
                revisionBefore + 1L,
                getLongField(
                        "datasetRevision"));
    }

    @Test
    public void nullCompletionCallbackDoesNothing()
    {
        service.requestReports(
                "Santa",
                null);

        Mockito.verifyNoInteractions(
                httpClient);

        Mockito.verifyNoInteractions(
                clientThread);
    }

    /*
     * HELPERS
     */

    @SuppressWarnings("unchecked")
    private Map<String, List<ReportSummary>> parseDataset(
            String dataset)
            throws Exception
    {
        final Method method =
                ReportCaseService.class.getDeclaredMethod(
                        "parseDataset",
                        String.class);

        method.setAccessible(
                true);

        return (Map<String, List<ReportSummary>>)
                method.invoke(
                        service,
                        dataset);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<ReportSummary>> parsedReportsForCurrentDataset()
            throws Exception
    {
        final Method method =
                ReportCaseService.class.getDeclaredMethod(
                        "parsedReportsForCurrentDataset");

        method.setAccessible(
                true);

        return (Map<String, List<ReportSummary>>)
                method.invoke(
                        service);
    }

    private void setField(
            String name,
            Object value)
            throws Exception
    {
        final Field field =
                ReportCaseService.class.getDeclaredField(
                        name);

        field.setAccessible(
                true);

        field.set(
                service,
                value);
    }

    private Object getField(
            String name)
            throws Exception
    {
        final Field field =
                ReportCaseService.class.getDeclaredField(
                        name);

        field.setAccessible(
                true);

        return field.get(
                service);
    }

    private void setBooleanField(
            String name,
            boolean value)
            throws Exception
    {
        final Field field =
                ReportCaseService.class.getDeclaredField(
                        name);

        field.setAccessible(
                true);

        field.setBoolean(
                service,
                value);
    }

    private boolean getBooleanField(
            String name)
            throws Exception
    {
        final Field field =
                ReportCaseService.class.getDeclaredField(
                        name);

        field.setAccessible(
                true);

        return field.getBoolean(
                service);
    }

    private void setLongField(
            String name,
            long value)
            throws Exception
    {
        final Field field =
                ReportCaseService.class.getDeclaredField(
                        name);

        field.setAccessible(
                true);

        field.setLong(
                service,
                value);
    }

    private long getLongField(
            String name)
            throws Exception
    {
        final Field field =
                ReportCaseService.class.getDeclaredField(
                        name);

        field.setAccessible(
                true);

        return field.getLong(
                service);
    }

    private static String report(
            String rsn,
            String publishedDate,
            String hash,
            String shortCode,
            String reason,
            String evidenceRating,
            String source)
    {
        final StringBuilder json =
                new StringBuilder();

        json.append(
                "{");

        appendJson(
                json,
                "accused_rsn",
                rsn);

        appendJson(
                json,
                "published_date",
                publishedDate);

        appendJson(
                json,
                "hash",
                hash);

        appendJson(
                json,
                "short_code",
                shortCode);

        appendJson(
                json,
                "reason",
                reason);

        appendJson(
                json,
                "evidence_rating",
                evidenceRating);

        appendJson(
                json,
                "source",
                source);

        if (json.charAt(
                json.length() - 1) == ',')
        {
            json.setLength(
                    json.length() - 1);
        }

        json.append(
                "}");

        return json.toString();
    }

    private static void appendJson(
            StringBuilder output,
            String name,
            String value)
    {
        if (value == null)
        {
            return;
        }

        output.append(
                        "\"")
                .append(
                        name)
                .append(
                        "\":\"")
                .append(
                        escapeJson(
                                value))
                .append(
                        "\",");
    }

    private static String escapeJson(
            String value)
    {
        return value
                .replace(
                        "\\",
                        "\\\\")
                .replace(
                        "\"",
                        "\\\"");
    }
}