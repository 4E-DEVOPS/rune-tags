package com.runetags.reports;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.runetags.Configurations;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public class ReportCaseService
{
    private static final String REPORT_LIST_URL =
            "https://raw.githubusercontent.com/while-loop/runelite-plugins/runewatch-updater/mixedlist.json";

    private static final Duration REFRESH_INTERVAL =
            Duration.ofMinutes(15);

    private static final Duration RETRY_INTERVAL =
            Duration.ofMinutes(1);

    private static final DateTimeFormatter FEED_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.ENGLISH);

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ClientThread clientThread;
    private final Configurations config;

    private final ExecutorService parserExecutor =
            Executors.newSingleThreadExecutor(runnable ->
            {
                final Thread thread =
                        new Thread(
                                runnable,
                                "RuneTags-ReportParser");

                thread.setDaemon(true);
                return thread;
            });

    private final Object stateLock =
            new Object();

    /*
     * Runtime-only report dataset for v1 launch.
     *
     * The downloaded feed is retained in memory for the current logged-in session.
     * No periodic refresh timer exists. Once the snapshot becomes 15 minutes old,
     * opening a Quick-Card is what requests a fresh copy.
     */
    private String rawDataset;
    private Instant lastSuccessfulDownload;
    private Instant lastDownloadAttempt;

    private long datasetRevision;
    private long parsedRevision = -1L;

    private Map<String, List<ReportSummary>> parsedReports =
            Collections.emptyMap();

    private boolean initializationInFlight;
    private boolean downloadInFlight;
    private Call activeCall;
    private long lifecycleEpoch;

    private final List<Runnable> successfulDownloadWaiters =
            new ArrayList<>();

    private boolean closed;

    public ReportCaseService(
            OkHttpClient httpClient,
            Gson gson,
            ClientThread clientThread,
            Configurations config)
    {
        this.httpClient = httpClient;
        this.gson = gson;
        this.clientThread = clientThread;
        this.config = config;
    }

    /**
     * Initialize Reports for the current logged-in session.
     *
     * v1 keeps the public report feed in memory only. The first initialization
     * downloads and parses the feed in the background. World hops retain the
     * current in-memory snapshot; logout clears it.
     */
    public void refreshInitialIfMissing()
    {
        if (!config.showReports())
        {
            return;
        }

        final long epoch;

        synchronized (stateLock)
        {
            if (closed
                    || initializationInFlight
                    || rawDataset != null
                    || downloadInFlight)
            {
                return;
            }

            initializationInFlight = true;
            epoch = lifecycleEpoch;
        }

        parserExecutor.execute(() ->
        {
            try
            {
                requestRefresh(
                        false,
                        () -> parsedReportsForCurrentDataset());
            }
            finally
            {
                synchronized (stateLock)
                {
                    if (epoch == lifecycleEpoch)
                    {
                        initializationInFlight = false;
                    }
                }
            }
        });
    }

    /**
     * Request the latest published report summary for one Quick-Card player.
     *
     * The downloaded dataset is parsed once and reused for local map lookups.
     *
     * If the in-memory dataset is 15+ minutes old, the current parsed snapshot
     * remains usable immediately while a fresh copy is downloaded and parsed in
     * the background.
     */
    public void requestReports(
            String playerName,
            Consumer<List<ReportSummary>> onComplete)
    {
        if (onComplete == null)
        {
            return;
        }

        final String playerKey =
                normalizePlayerName(
                        playerName);

        if (playerKey.isEmpty()
                || !config.showReports())
        {
            deliver(
                    onComplete,
                    Collections.emptyList());
            return;
        }

        final boolean hasDataset;
        final boolean stale;

        synchronized (stateLock)
        {
            if (closed)
            {
                return;
            }

            hasDataset =
                    rawDataset != null;

            stale =
                    isStaleLocked(
                            Instant.now());
        }

        /*
         * Serve the current parsed snapshot immediately.
         *
         * Once the current dataset has been parsed, Quick-Card report lookup is a
         * local HashMap lookup.
         */
        if (hasDataset)
        {
            parseAndDeliver(
                    playerKey,
                    onComplete);
        }

        /*
         * No periodic timer exists.
         *
         * Missing/stale data refreshes only because:
         *
         * - Reports were initialized at login; or
         * - a Quick-Card was opened after the 15-minute freshness window.
         *
         * The old parsed snapshot stays active during the refresh. Once the new
         * dataset has been parsed, deliver the replacement result to the still-open
         * card.
         */
        if (!hasDataset || stale)
        {
            requestRefresh(
                    false,
                    () -> parseAndDeliver(
                            playerKey,
                            onComplete));
        }
    }

    /**
     * Clear report data for logout/config-disable without permanently closing the
     * service. A later login/config-enable can download a fresh snapshot.
     */
    public void clear()
    {
        final Call callToCancel;

        synchronized (stateLock)
        {
            ++lifecycleEpoch;

            callToCancel =
                    activeCall;

            activeCall = null;
            initializationInFlight = false;
            downloadInFlight = false;

            successfulDownloadWaiters.clear();

            rawDataset = null;
            lastSuccessfulDownload = null;
            lastDownloadAttempt = null;

            ++datasetRevision;
            parsedRevision = -1L;
            parsedReports = Collections.emptyMap();
        }

        if (callToCancel != null)
        {
            callToCancel.cancel();
        }
    }

    public void shutdown()
    {
        synchronized (stateLock)
        {
            closed = true;
        }

        clear();
        parserExecutor.shutdownNow();
    }

    private void requestRefresh(
            boolean force,
            Runnable onSuccess)
    {
        final Instant now =
                Instant.now();

        final long epoch;
        final Call call;

        synchronized (stateLock)
        {
            if (closed
                    || !config.showReports())
            {
                return;
            }

            final boolean needsRefresh =
                    force
                            || rawDataset == null
                            || isStaleLocked(now);

            if (!needsRefresh)
            {
                if (onSuccess != null)
                {
                    parserExecutor.execute(
                            onSuccess);
                }

                return;
            }

            if (downloadInFlight)
            {
                if (onSuccess != null)
                {
                    successfulDownloadWaiters.add(
                            onSuccess);
                }

                return;
            }

            /*
             * During an outage, repeated Quick-Card opens should not hammer the
             * upstream feed. Retry at most once per minute until a download works.
             */
            if (!force
                    && lastDownloadAttempt != null
                    && Duration.between(
                    lastDownloadAttempt,
                    now).compareTo(RETRY_INTERVAL) < 0)
            {
                return;
            }

            if (onSuccess != null)
            {
                successfulDownloadWaiters.add(
                        onSuccess);
            }

            final Request request =
                    new Request.Builder()
                            .url(REPORT_LIST_URL)
                            .build();

            call =
                    httpClient.newCall(
                            request);

            activeCall = call;
            downloadInFlight = true;
            lastDownloadAttempt = now;
            epoch = lifecycleEpoch;
        }

        call.enqueue(
                new Callback()
                {
                    @Override
                    public void onFailure(
                            Call failedCall,
                            IOException exception)
                    {
                        log.debug("[RuneTags][Reports] Report-list Download Failed | ERROR: {}", exception.getMessage());

                        finishDownload(
                                epoch,
                                null,
                                false);
                    }

                    @Override
                    public void onResponse(
                            Call completedCall,
                            Response response)
                    {
                        String body = null;
                        boolean success = false;

                        try (Response closedResponse = response)
                        {
                            if (!closedResponse.isSuccessful())
                            {
                                //log.debug("[RuneTags][Reports] Report-list Download Returned HTTP {}", closedResponse.code());
                                return;
                            }

                            final ResponseBody responseBody =
                                    closedResponse.body();

                            if (responseBody == null)
                            {
                                log.debug("[RuneTags][Reports] Report-list Download Returned an Empty Body");
                                return;
                            }

                            body =
                                    responseBody.string();

                            success =
                                    body != null
                                            && !body.trim().isEmpty();
                        }
                        catch (IOException exception)
                        {
                            log.debug("[RuneTags][Reports] Unable to Read Report-list Response | ERROR: {}", exception.getMessage());
                        }
                        finally
                        {
                            finishDownload(
                                    epoch,
                                    body,
                                    success);
                        }
                    }
                });
    }

    private void finishDownload(
            long epoch,
            String downloadedBody,
            boolean success)
    {
        final List<Runnable> callbacks;

        synchronized (stateLock)
        {
            if (closed
                    || epoch != lifecycleEpoch)
            {
                return;
            }

            activeCall = null;
            downloadInFlight = false;

            if (success)
            {
                rawDataset =
                        downloadedBody;

                lastSuccessfulDownload =
                        Instant.now();

                ++datasetRevision;

                /*
                 * Do NOT clear parsedReports here.
                 *
                 * The existing parsed snapshot remains usable while the replacement feed is
                 * parsed in the background. parsedRevision no longer matches datasetRevision,
                 * so parsedReportsForCurrentDataset() knows that a replacement parse is due.
                 *
                 * Once that parse completes successfully, it atomically replaces the old map.
                 */
                callbacks =
                        new ArrayList<>(
                                successfulDownloadWaiters);
            }
            else if (rawDataset != null
                    && parsedRevision != datasetRevision)
            {
                /*
                 * The refresh failed, but an older downloaded dataset is still
                 * available and has not yet been parsed. Let the waiting card use
                 * that in-memory snapshot rather than showing nothing.
                 */
                callbacks =
                        new ArrayList<>(
                                successfulDownloadWaiters);
            }
            else
            {
                callbacks =
                        Collections.emptyList();
            }

            successfulDownloadWaiters.clear();
        }

        if (success)
        {
            //log.debug("[RuneTags][Reports] Report-list Dataset Downloaded | Background Parse Requested");
        }

        for (Runnable callback : callbacks)
        {
            parserExecutor.execute(
                    callback);
        }
    }

    private void parseAndDeliver(
            String playerKey,
            Consumer<List<ReportSummary>> onComplete)
    {
        parserExecutor.execute(() ->
        {
            final Map<String, List<ReportSummary>> reports =
                    parsedReportsForCurrentDataset();

            final List<ReportSummary> playerReports =
                    reports.get(
                            playerKey);

            final List<ReportSummary> safeReports =
                    playerReports != null
                            ? playerReports
                            : Collections.emptyList();

            deliver(
                    onComplete,
                    safeReports);
        });
    }

    private Map<String, List<ReportSummary>> parsedReportsForCurrentDataset()
    {
        while (true)
        {
            final String dataset;
            final long revision;

            synchronized (stateLock)
            {
                if (closed
                        || rawDataset == null)
                {
                    return Collections.emptyMap();
                }

                if (parsedRevision == datasetRevision)
                {
                    return parsedReports;
                }

                dataset = rawDataset;
                revision = datasetRevision;
            }

            final Map<String, List<ReportSummary>> parsed =
                    parseDataset(
                            dataset);

            synchronized (stateLock)
            {
                if (closed)
                {
                    return Collections.emptyMap();
                }

                /*
                 * If a new download replaced the raw snapshot while parsing was in
                 * progress, discard the stale parse and restart against the new data.
                 */
                if (revision != datasetRevision)
                {
                    continue;
                }

                parsedReports = parsed;
                parsedRevision = revision;

                return parsedReports;
            }
        }
    }

    private Map<String, List<ReportSummary>> parseDataset(
            String dataset)
    {
        if (dataset == null
                || dataset.trim().isEmpty())
        {
            return Collections.emptyMap();
        }

        final Map<String, PlayerAccumulator> accumulators =
                new HashMap<>();

        try
        {
            final JsonElement root =
                    new JsonParser().parse(
                            dataset);

            if (!root.isJsonArray())
            {
                log.debug("[RuneTags][Reports] Report-list Dataset was not a JSON Array");
                return Collections.emptyMap();
            }

            final JsonArray cases =
                    root.getAsJsonArray();

            for (JsonElement element : cases)
            {
                final FeedCase reportCase;

                try
                {
                    reportCase =
                            gson.fromJson(
                                    element,
                                    FeedCase.class);
                }
                catch (JsonParseException exception)
                {
                    continue;
                }

                if (reportCase == null)
                {
                    continue;
                }

                final String playerKey =
                        normalizePlayerName(
                                reportCase.rsn);

                final String source =
                        normalizeSource(
                                reportCase.source);

                if (playerKey.isEmpty()
                        || source == null)
                {
                    continue;
                }

                final LocalDateTime publishedDate =
                        parsePublishedDate(
                                reportCase.publishedDate);

                accumulators
                        .computeIfAbsent(
                                playerKey,
                                ignored -> new PlayerAccumulator())
                        .add(
                                source,
                                reportCase,
                                publishedDate);
            }
        }
        catch (JsonParseException exception)
        {
            log.debug("[RuneTags][Reports] Unable to Parse Report-list Dataset | ERROR: {}", exception.getMessage());

            return Collections.emptyMap();
        }

        final Map<String, List<ReportSummary>> summaries =
                new HashMap<>();

        for (Map.Entry<String, PlayerAccumulator> entry
                : accumulators.entrySet())
        {
            final ReportSummary playerSummary =
                    entry.getValue().toSummary();

            if (playerSummary != null)
            {
                /*
                 * QuickProfileModel currently stores report summaries as a List.
                 *
                 * Keep that public shape for now, but RuneTags intentionally presents
                 * only ONE latest published report block per player.
                 */
                summaries.put(
                        entry.getKey(),
                        Collections.singletonList(
                                playerSummary));
            }
        }

        //log.debug("[RuneTags][Reports] Parsed Report-list Dataset | Reports={}", summaries.size());

        return Collections.unmodifiableMap(
                summaries);
    }

    private void deliver(
            Consumer<List<ReportSummary>> onComplete,
            List<ReportSummary> reports)
    {
        final List<ReportSummary> safeReports =
                reports == null
                        ? Collections.emptyList()
                        : reports;

        clientThread.invokeLater(() ->
        {
            if (!closed)
            {
                onComplete.accept(
                        safeReports);
            }
        });
    }

    private boolean isStaleLocked(
            Instant now)
    {
        return lastSuccessfulDownload == null
                || Duration.between(
                lastSuccessfulDownload,
                now).compareTo(REFRESH_INTERVAL) >= 0;
    }

    private static String normalizePlayerName(
            String value)
    {
        if (value == null)
        {
            return "";
        }

        return Text.removeTags(
                        Text.toJagexName(
                                value))
                .trim()
                .toLowerCase(
                        Locale.ROOT);
    }

    private static String normalizeSource(
            String source)
    {
        if (source == null
                || source.trim().isEmpty()
                || "RW".equalsIgnoreCase(source.trim()))
        {
            return "RW";
        }

        if ("WDR".equalsIgnoreCase(
                source.trim()))
        {
            return "WDR";
        }

        return null;
    }

    private static LocalDateTime parsePublishedDate(
            String value)
    {
        if (value == null
                || value.trim().isEmpty())
        {
            return null;
        }

        try
        {
            return LocalDateTime.parse(
                    value.trim(),
                    FEED_DATE_FORMAT);
        }
        catch (DateTimeParseException exception)
        {
            return null;
        }
    }

    private static String caseIdentifier(
            FeedCase reportCase)
    {
        if (reportCase == null)
        {
            return null;
        }

        if (reportCase.hash != null
                && !reportCase.hash.trim().isEmpty())
        {
            return reportCase.hash.trim();
        }

        if (reportCase.shortCode != null
                && !reportCase.shortCode.trim().isEmpty())
        {
            return reportCase.shortCode.trim();
        }

        return null;
    }

    private static final class FeedCase
    {
        @SerializedName("accused_rsn")
        private String rsn;

        @SerializedName("published_date")
        private String publishedDate;

        /*
         * Current RuneWatch mixedlist uses "hash" for public case identifiers.
         */
        @SerializedName("hash")
        private String hash;

        /*
         * Retain compatibility with older/alternate mixedlist schemas which exposed
         * the same case identifier as short_code.
         */
        @SerializedName("short_code")
        private String shortCode;

        private String reason;

        @SerializedName("evidence_rating")
        private String evidenceRating;

        @SerializedName("source")
        private String source;
    }

    private static final class PlayerAccumulator
    {
        private int count;

        private String latestSource;
        private FeedCase latestCase;
        private LocalDateTime latestPublishedDate;

        private void add(
                String source,
                FeedCase reportCase,
                LocalDateTime publishedDate)
        {
            if (reportCase == null)
            {
                return;
            }

            ++count;

            /*
             * Prefer the newest dated report.
             *
             * A dated report always outranks an undated report because the latter
             * cannot establish that it is newer.
             *
             * If every report is undated, retain the latest entry encountered in
             * the feed.
             */
            if (latestCase == null
                    || isNewer(
                    publishedDate,
                    latestPublishedDate))
            {
                latestSource = source;
                latestCase = reportCase;
                latestPublishedDate = publishedDate;
            }
        }

        private ReportSummary toSummary()
        {
            if (latestCase == null
                    || count <= 0)
            {
                return null;
            }

            return ReportSummary.builder()
                    .source(latestSource)
                    .caseCount(count)
                    .rsn(latestCase.rsn)
                    .publishedDate(
                            latestPublishedDate)
                    .caseId(
                            caseIdentifier(
                                    latestCase))
                    .reason(latestCase.reason)
                    .evidenceRating(
                            latestCase.evidenceRating)
                    .build();
        }

        private static boolean isNewer(
                LocalDateTime candidate,
                LocalDateTime current)
        {
            /*
             * When neither case has a date, allow the later feed entry to replace
             * the previous one.
             */
            if (candidate == null)
            {
                return current == null;
            }

            /*
             * A dated record outranks an undated record.
             */
            return current == null
                    || candidate.isAfter(
                    current);
        }
    }
}
