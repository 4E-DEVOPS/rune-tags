package com.runetags.quickprofile;

import com.runetags.context.*;
import com.runetags.context.ProfileMetric;
import com.runetags.hiscores.HiscoreEnrichmentCache;
import com.runetags.hiscores.HiscoreEnrichmentService;
import com.runetags.hiscores.HiscoreEnrichmentState;
import com.runetags.hiscores.HiscoreProfileData;
import com.runetags.hiscores.PlayerLookupService;
import com.runetags.location.PlayerLocation;
import com.runetags.player.OnlineState;
import com.runetags.player.AccountType;
import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;
import com.runetags.reference.PlayerReference;
import com.runetags.target.TargetController;

import net.runelite.api.Client;
import net.runelite.api.Player;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class QuickProfileControllerTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    private Client client;
    private PlayerDirectory playerDirectory;
    private HiscoreEnrichmentService hiscoreEnrichmentService;
    private PlayerContextService playerContextService;
    private PartyContextService partyContextService;
    private ProfileMetricResolver profileMetricResolver;
    private TargetController targetController;
    private PlayerLookupService playerLookupService;
    private ClanLookupService clanLookupService;
    private QuickProfileController controller;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        hiscoreEnrichmentService =
                Mockito.mock(
                        HiscoreEnrichmentService.class);

        playerContextService =
                Mockito.mock(
                        PlayerContextService.class);

        partyContextService =
                Mockito.mock(
                        PartyContextService.class);

        profileMetricResolver =
                new ProfileMetricResolver();

        targetController =
                Mockito.mock(
                        TargetController.class);

        playerLookupService =
                Mockito.mock(
                        PlayerLookupService.class);

        clanLookupService =
                Mockito.mock(
                        ClanLookupService.class);

        Mockito.when(
                    hiscoreEnrichmentService.enrich(Mockito.anyString()))
                        .thenReturn(failedEnrichmentRequest());

        controller =
                new QuickProfileController(
                        client,
                        null,
                        playerDirectory,
                        hiscoreEnrichmentService,
                        null,
                        null,
                        playerContextService,
                        partyContextService,
                        profileMetricResolver,
                        targetController,
                        playerLookupService,
                        clanLookupService,
                        null,
                        null,
                        null);
    }

    /*
     * TESTS
     */

    @Test
    public void refreshWithNoOpenModelDoesNothing()
    {
        controller.refreshContext();

        Assert.assertNull(
                controller.getModel());

        Mockito.verifyNoInteractions(
                playerDirectory);
    }

    @Test
    public void missingLiveIdentityClearsOnlyLiveState()
            throws Exception
    {
        final PlayerIdentity oldIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        true,
                        "Old Clan",
                        "General",
                        PlayerSource.CLAN,
                        AccountType.IRONMAN);

        final QuickProfileModel original =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .accountType(
                                AccountType.IRONMAN)
                        .combatLevel(
                                126)
                        .totalLevel(
                                2277)
                        .efficientHoursPlayed(
                                500.0)
                        .efficientHoursBossed(
                                250.0)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .locationName(
                                "Grand Exchange")
                        .channelName(
                                "Old Clan")
                        .channelRank(
                                "General")
                        .channelSource(
                                PlayerSource.CLAN)
                        .previousRsns(
                                Collections.singletonList(
                                        "Santa Clause"))
                        .tags(
                                Arrays.asList(
                                        "friend",
                                        "trusted"))
                        .favorite(true)
                        .note(
                                "Known player")
                        .nearby(true)
                        .identity(
                                oldIdentity)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADED)
                        .build();

        setModel(
                original);

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.empty());

        controller.refreshContext();

        final QuickProfileModel refreshed =
                controller.getModel();

        Assert.assertNotNull(
                refreshed);

        /*
         * Live state must be cleared.
         */
        Assert.assertNull(
                refreshed.getWorld());

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                refreshed.getOnlineState());

        Assert.assertEquals(
                "Location: Unknown",
                refreshed.getLocationName());

        Assert.assertNull(
                refreshed.getChannelName());

        Assert.assertNull(
                refreshed.getChannelRank());

        Assert.assertNull(
                refreshed.getChannelSource());

        Assert.assertFalse(
                refreshed.isNearby());

        Assert.assertNull(
                refreshed.getIdentity());

        /*
         * Stable / enriched profile data must survive.
         */
        Assert.assertEquals(
                "Santa",
                refreshed.getDisplayName());

        Assert.assertTrue(
                refreshed.isResolved());

        Assert.assertEquals(
                AccountType.IRONMAN,
                refreshed.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(126),
                refreshed.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2277),
                refreshed.getTotalLevel());

        Assert.assertEquals(
                Double.valueOf(500.0),
                refreshed.getEfficientHoursPlayed());

        Assert.assertEquals(
                Double.valueOf(250.0),
                refreshed.getEfficientHoursBossed());

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa Clause"),
                refreshed.getPreviousRsns());

        Assert.assertEquals(
                Arrays.asList(
                        "friend",
                        "trusted"),
                refreshed.getTags());

        Assert.assertTrue(
                refreshed.isFavorite());

        Assert.assertEquals(
                "Known player",
                refreshed.getNote());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                refreshed.getEnrichmentState());
    }

    @Test
    public void refreshUsesCurrentLiveIdentity()
            throws Exception
    {
        final PlayerIdentity oldIdentity =
                identity(
                        "Party Hat",
                        301,
                        OnlineState.ONLINE,
                        true,
                        "Old Clan",
                        "Recruit",
                        PlayerSource.CLAN,
                        AccountType.NORMAL);

        final PlayerIdentity currentIdentity =
                identity(
                        "Party Hat",
                        302,
                        OnlineState.ONLINE,
                        false,
                        "Party",
                        null,
                        PlayerSource.PARTY,
                        AccountType.NORMAL);

        final QuickProfileModel original =
                QuickProfileModel.builder()
                        .displayName(
                                "Party Hat")
                        .resolved(true)
                        .accountType(
                                AccountType.NORMAL)
                        .combatLevel(
                                100)
                        .totalLevel(
                                2000)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .locationName(
                                "Grand Exchange")
                        .channelName(
                                "Old Clan")
                        .channelRank(
                                "Recruit")
                        .channelSource(
                                PlayerSource.CLAN)
                        .nearby(true)
                        .identity(
                                oldIdentity)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADED)
                        .build();

        setModel(
                original);

        Mockito.when(
                        playerDirectory.find(
                                "Party Hat"))
                .thenReturn(
                        Optional.of(
                                currentIdentity));

        controller.refreshContext();

        final QuickProfileModel refreshed =
                controller.getModel();

        Assert.assertEquals(
                Integer.valueOf(302),
                refreshed.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                refreshed.getOnlineState());

        Assert.assertFalse(
                refreshed.isNearby());

        Assert.assertSame(
                currentIdentity,
                refreshed.getIdentity());

        Assert.assertEquals(
                "Party",
                refreshed.getChannelName());

        Assert.assertNull(
                refreshed.getChannelRank());

        Assert.assertEquals(
                PlayerSource.PARTY,
                refreshed.getChannelSource());

        /*
         * No PartyContextService is supplied in this isolated test,
         * so a remote Party member has no resolved location.
         */
        Assert.assertEquals(
                "Location: Unknown",
                refreshed.getLocationName());

        /*
         * Existing enrichment is independent of the live refresh.
         */
        Assert.assertEquals(
                Integer.valueOf(100),
                refreshed.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2000),
                refreshed.getTotalLevel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                refreshed.getEnrichmentState());
    }

    @Test
    public void refreshUsesUnknownWhenLiveOnlineStateIsNull()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .accountType(
                                AccountType.NORMAL)
                        .world(
                                303)
                        .onlineState(null)
                        .sources(
                                Collections.singleton(
                                        PlayerSource.FRIEND))
                        .build();

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Zezima")
                        .resolved(true)
                        .onlineState(
                                OnlineState.ONLINE)
                        .world(
                                301)
                        .nearby(false)
                        .build());

        Mockito.when(
                        playerDirectory.find(
                                "Zezima"))
                .thenReturn(
                        Optional.of(
                                identity));

        controller.refreshContext();

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                controller.getModel()
                        .getOnlineState());

        Assert.assertEquals(
                Integer.valueOf(303),
                controller.getModel()
                        .getWorld());
    }

    @Test
    public void refreshUsesCurrentChannelMetadata()
            throws Exception
    {
        final PlayerIdentity identity =
                identity(
                        "Santa Clause",
                        304,
                        OnlineState.ONLINE,
                        false,
                        "Guest Clan",
                        "Captain",
                        PlayerSource.GUEST_CLAN,
                        AccountType.NORMAL);

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa Clause")
                        .resolved(true)
                        .channelName(
                                "Friends Chat")
                        .channelRank(
                                "Smiley")
                        .channelSource(
                                PlayerSource.FRIENDS_CHAT)
                        .onlineState(
                                OnlineState.ONLINE)
                        .build());

        Mockito.when(
                        playerDirectory.find(
                                "Santa Clause"))
                .thenReturn(
                        Optional.of(
                                identity));

        controller.refreshContext();

        final QuickProfileModel refreshed =
                controller.getModel();

        Assert.assertEquals(
                "Guest Clan",
                refreshed.getChannelName());

        Assert.assertEquals(
                "Captain",
                refreshed.getChannelRank());

        Assert.assertEquals(
                PlayerSource.GUEST_CLAN,
                refreshed.getChannelSource());
    }

    @Test
    public void missingIdentityRestoresOriginatingMessageChannel()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "FasT 07")
                        .resolved(true)
                        .channelName(
                                "Party")
                        .channelSource(
                                PlayerSource.PARTY)
                        .originatingChatType(
                                net.runelite.api.ChatMessageType.PUBLICCHAT)
                        .onlineState(
                                OnlineState.ONLINE)
                        .nearby(true)
                        .build());

        Mockito.when(
                        playerDirectory.find(
                                "FasT 07"))
                .thenReturn(
                        Optional.empty());

        controller.refreshContext();

        final QuickProfileModel refreshed =
                controller.getModel();

        Assert.assertEquals(
                "Public",
                refreshed.getChannelName());

        Assert.assertNull(
                refreshed.getChannelRank());

        Assert.assertNull(
                refreshed.getChannelSource());
    }

    @Test
    public void historicalNearbyIdentityDoesNotResurrectLivePresence()
            throws Exception
    {
        final PlayerIdentity historicalIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        true,
                        "Old Clan",
                        "General",
                        PlayerSource.CLAN,
                        AccountType.IRONMAN);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .chatType(
                                net.runelite.api.ChatMessageType.PUBLICCHAT)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.empty());

        openResolved(
                reference);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertNotNull(
                model);

        /*
         * Stable semantic information may survive from the
         * historical PlayerReference.
         */
        Assert.assertEquals(
                "Santa",
                model.getDisplayName());

        Assert.assertTrue(
                model.isResolved());

        Assert.assertEquals(
                AccountType.IRONMAN,
                model.getAccountType());

        /*
         * Historical live state must NOT be resurrected.
         */
        Assert.assertNull(
                model.getWorld());

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                model.getOnlineState());

        Assert.assertFalse(
                model.isNearby());

        Assert.assertNull(
                model.getIdentity());

        Assert.assertEquals(
                "Location: Unknown",
                model.getLocationName());

        /*
         * The old Clan must not survive. The originating
         * message channel becomes the presentation fallback.
         */
        Assert.assertEquals(
                "Public",
                model.getChannelName());

        Assert.assertNull(
                model.getChannelRank());

        Assert.assertNull(
                model.getChannelSource());
    }

    @Test
    public void currentDirectoryIdentityWinsOverHistoricalIdentity()
            throws Exception
    {
        final PlayerIdentity historicalIdentity =
                identity(
                        "Party Hat",
                        301,
                        OnlineState.ONLINE,
                        true,
                        "Old Clan",
                        "Recruit",
                        PlayerSource.CLAN,
                        AccountType.NORMAL);

        final PlayerIdentity liveIdentity =
                identity(
                        "Party Hat",
                        302,
                        OnlineState.ONLINE,
                        false,
                        "Party",
                        null,
                        PlayerSource.PARTY,
                        AccountType.IRONMAN);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Party Hat")
                        .lookupName(
                                "Party Hat")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .chatType(
                                net.runelite.api.ChatMessageType.CLAN_CHAT)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Party Hat"))
                .thenReturn(
                        Optional.of(
                                liveIdentity));

        openResolved(
                reference);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertNotNull(
                model);

        Assert.assertEquals(
                "Party Hat",
                model.getDisplayName());

        Assert.assertEquals(
                AccountType.IRONMAN,
                model.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(302),
                model.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                model.getOnlineState());

        Assert.assertFalse(
                model.isNearby());

        Assert.assertSame(
                liveIdentity,
                model.getIdentity());

        Assert.assertEquals(
                "Party",
                model.getChannelName());

        Assert.assertNull(
                model.getChannelRank());

        Assert.assertEquals(
                PlayerSource.PARTY,
                model.getChannelSource());

        /*
         * No PartyContextService exists in this isolated fixture,
         * so remote Party location remains unknown.
         */
        Assert.assertEquals(
                "Location: Unknown",
                model.getLocationName());
    }

    @Test
    public void historicalIdentityCanProvideStableSemanticIdentityOnly()
            throws Exception
    {
        final PlayerIdentity historicalIdentity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "FasT 07")
                        .accountType(
                                AccountType.HARDCORE)
                        .combatLevel(
                                88)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.NEARBY))
                        .build();

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "FasT 07")
                        .lookupName(
                                "FasT 07")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "FasT 07"))
                .thenReturn(
                        Optional.empty());

        openResolved(
                reference);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertEquals(
                "FasT 07",
                model.getDisplayName());

        Assert.assertTrue(
                model.isResolved());

        Assert.assertEquals(
                AccountType.HARDCORE,
                model.getAccountType());

        /*
         * Combat is stable enough to fall back from the semantic
         * historical identity during initial construction.
         */
        Assert.assertEquals(
                Integer.valueOf(88),
                model.getCombatLevel());

        /*
         * These fields represent current live presence and may not
         * fall back to the historical identity.
         */
        Assert.assertNull(
                model.getWorld());

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                model.getOnlineState());

        Assert.assertFalse(
                model.isNearby());

        Assert.assertNull(
                model.getIdentity());
    }

    @Test
    public void unresolvedReferenceDoesNotInheritHistoricalOrLocalLiveState()
            throws Exception
    {
        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "definitelyunknown123")
                        .lookupName(
                                "definitelyunknown123")
                        .locallyResolved(false)
                        .identity(null)
                        .chatType(
                                net.runelite.api.ChatMessageType.PRIVATECHAT)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "definitelyunknown123"))
                .thenReturn(
                        Optional.empty());

        openResolved(
                reference);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertNotNull(
                model);

        Assert.assertEquals(
                "definitelyunknown123",
                model.getDisplayName());

        Assert.assertFalse(
                model.isResolved());

        Assert.assertEquals(
                AccountType.UNKNOWN,
                model.getAccountType());

        Assert.assertNull(
                model.getWorld());

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                model.getOnlineState());

        Assert.assertFalse(
                model.isNearby());

        Assert.assertNull(
                model.getIdentity());

        Assert.assertEquals(
                "Location: Unknown",
                model.getLocationName());

        Assert.assertEquals(
                "Private",
                model.getChannelName());

        Assert.assertNull(
                model.getChannelRank());

        Assert.assertNull(
                model.getChannelSource());
    }

    @Test
    public void liveIdentityChannelOverridesOriginatingMessageChannel()
            throws Exception
    {
        final PlayerIdentity liveIdentity =
                identity(
                        "Santa Clause",
                        305,
                        OnlineState.ONLINE,
                        false,
                        "RuneTags Clan",
                        "Captain",
                        PlayerSource.CLAN,
                        AccountType.NORMAL);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa Clause")
                        .lookupName(
                                "Santa Clause")
                        .locallyResolved(true)
                        .identity(null)
                        .chatType(
                                net.runelite.api.ChatMessageType.PUBLICCHAT)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa Clause"))
                .thenReturn(
                        Optional.of(
                                liveIdentity));

        openResolved(
                reference);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertEquals(
                "RuneTags Clan",
                model.getChannelName());

        Assert.assertEquals(
                "Captain",
                model.getChannelRank());

        Assert.assertEquals(
                PlayerSource.CLAN,
                model.getChannelSource());
    }

    @Test
    public void openingProfileUsesProvidedAnchorPoint()
            throws Exception
    {
        final PlayerIdentity liveIdentity =
                identity(
                        "Zezima",
                        301,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        null,
                        AccountType.NORMAL);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Zezima")
                        .lookupName(
                                "Zezima")
                        .locallyResolved(true)
                        .identity(
                                liveIdentity)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Zezima"))
                .thenReturn(
                        Optional.of(
                                liveIdentity));

        openResolved(
                reference,
                new java.awt.Point(
                        250,
                        175));

        Assert.assertEquals(
                new java.awt.Point(
                        250,
                        175),
                controller.getAnchorPoint());
    }

    @Test
    public void currentGenerationAcceptsAsyncEnrichment()
            throws Exception
    {
        final CompletableFuture<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                future =
                new CompletableFuture<>();

        Mockito.when(
                        hiscoreEnrichmentService.enrich(
                                "Santa"))
                .thenReturn(loadingEnrichmentRequest(future));

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(false)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.empty());

        openResolved(
                reference);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                controller.getModel()
                        .getEnrichmentState());

        future.complete(
                new HiscoreEnrichmentCache.CachedProfileEnrichment(
                        HiscoreEnrichmentState.NOT_FOUND,
                        null));

        Assert.assertEquals(
                HiscoreEnrichmentState.NOT_FOUND,
                controller.getModel()
                        .getEnrichmentState());

        Assert.assertEquals(
                "Santa",
                controller.getModel()
                        .getDisplayName());
    }

    @Test
    public void lateEnrichmentFromPreviousPlayerCannotOverwriteCurrentProfile()
            throws Exception
    {
        final CompletableFuture<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                santaFuture =
                new CompletableFuture<>();

        Mockito.when(
                        hiscoreEnrichmentService.enrich(
                                "Santa"))
                .thenReturn(loadingEnrichmentRequest(santaFuture));

        Mockito.when(
                        hiscoreEnrichmentService.enrich(
                                "Party Hat"))
                .thenReturn(failedEnrichmentRequest());

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.empty());

        Mockito.when(
                        playerDirectory.find(
                                "Party Hat"))
                .thenReturn(
                        Optional.empty());

        openResolved(
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(false)
                        .build());

        Assert.assertEquals(
                "Santa",
                controller.getModel()
                        .getDisplayName());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                controller.getModel()
                        .getEnrichmentState());

        /*
         * Open another player before Santa's request completes.
         */
        openResolved(
                PlayerReference.builder()
                        .rawText(
                                "Party Hat")
                        .lookupName(
                                "Party Hat")
                        .locallyResolved(false)
                        .build());

        Assert.assertEquals(
                "Party Hat",
                controller.getModel()
                        .getDisplayName());

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                controller.getModel()
                        .getEnrichmentState());

        /*
         * Santa now completes late.
         */
        santaFuture.complete(
                new HiscoreEnrichmentCache.CachedProfileEnrichment(
                        HiscoreEnrichmentState.NOT_FOUND,
                        null));

        /*
         * Party Hat must remain completely unaffected.
         */
        Assert.assertEquals(
                "Party Hat",
                controller.getModel()
                        .getDisplayName());

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                controller.getModel()
                        .getEnrichmentState());
    }

    @Test
    public void closeInvalidatesPendingEnrichment()
            throws Exception
    {
        final CompletableFuture<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                future =
                new CompletableFuture<>();

        Mockito.when(
                        hiscoreEnrichmentService.enrich(
                                "Zezima"))
                .thenReturn(
                        loadingEnrichmentRequest(future));

        Mockito.when(
                        playerDirectory.find(
                                "Zezima"))
                .thenReturn(
                        Optional.empty());

        openResolved(
                PlayerReference.builder()
                        .rawText(
                                "Zezima")
                        .lookupName(
                                "Zezima")
                        .locallyResolved(false)
                        .build());

        Assert.assertNotNull(
                controller.getModel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                controller.getModel()
                        .getEnrichmentState());

        controller.close();

        Assert.assertNull(
                controller.getModel());

        /*
         * Completing the old request after close must not
         * resurrect the Quick-Card.
         */
        future.complete(
                new HiscoreEnrichmentCache.CachedProfileEnrichment(
                        HiscoreEnrichmentState.NOT_FOUND,
                        null));

        Assert.assertNull(
                controller.getModel());

        Assert.assertFalse(
                controller.isOpen());
    }

    @Test
    public void reopeningSamePlayerRejectsOlderGeneration()
            throws Exception
    {
        final CompletableFuture<
                HiscoreEnrichmentCache.CachedProfileEnrichment>
                firstFuture =
                new CompletableFuture<>();

        Mockito.when(
                        hiscoreEnrichmentService.enrich(
                                "Santa"))
                .thenReturn(
                        loadingEnrichmentRequest(firstFuture),
                        failedEnrichmentRequest());

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.empty());

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(false)
                        .build();

        /*
         * Generation 1.
         */
        openResolved(
                reference);

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                controller.getModel()
                        .getEnrichmentState());

        /*
         * Generation 2 for the exact same player.
         */
        openResolved(
                reference);

        Assert.assertEquals(
                "Santa",
                controller.getModel()
                        .getDisplayName());

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                controller.getModel()
                        .getEnrichmentState());

        /*
         * Generation 1 completes after generation 2 already owns
         * the card. The matching player name must not be enough
         * to allow this stale result through.
         */
        firstFuture.complete(
                new HiscoreEnrichmentCache.CachedProfileEnrichment(
                        HiscoreEnrichmentState.NOT_FOUND,
                        null));

        Assert.assertEquals(
                "Santa",
                controller.getModel()
                        .getDisplayName());

        Assert.assertEquals(
                HiscoreEnrichmentState.ERROR,
                controller.getModel()
                        .getEnrichmentState());
    }

    @Test
    public void loadedEnrichmentUpdatesOnlyEnrichmentOwnedFields()
            throws Exception
    {
        final PlayerIdentity identity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        true,
                        "RuneTags Clan",
                        "General",
                        PlayerSource.CLAN,
                        AccountType.UNKNOWN);

        final QuickProfileModel original =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .accountType(
                                AccountType.UNKNOWN)
                        .combatLevel(
                                90)
                        .totalLevel(
                                1800)
                        .efficientHoursPlayed(
                                325.5)
                        .efficientHoursBossed(
                                115.25)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .locationName(
                                "Grand Exchange")
                        .channelName(
                                "RuneTags Clan")
                        .channelRank(
                                "General")
                        .channelSource(
                                PlayerSource.CLAN)
                        .previousRsns(
                                Collections.singletonList(
                                        "Santa Clause"))
                        .tags(
                                Arrays.asList(
                                        "friend",
                                        "trusted"))
                        .favorite(true)
                        .note(
                                "Known player")
                        .nearby(true)
                        .identity(
                                identity)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADING)
                        .build();

        setModel(
                original);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.IRONMAN,
                        Collections.emptyMap());

        applyEnrichment(
                0L,
                "Santa",
                HiscoreEnrichmentState.LOADED,
                data);

        final QuickProfileModel model =
                controller.getModel();

        /*
         * Enrichment-owned fields update.
         */
        Assert.assertEquals(
                AccountType.IRONMAN,
                model.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(126),
                model.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2277),
                model.getTotalLevel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                model.getEnrichmentState());

        /*
         * No context was associated with this isolated reference,
         * so enrichment cannot invent contextual hiscores.
         */
        Assert.assertNotNull(
                model.getContextMetrics());

        Assert.assertTrue(
                model.getContextMetrics()
                        .isEmpty());

        /*
         * Live RuneLite state must remain untouched.
         */
        Assert.assertEquals(
                Integer.valueOf(301),
                model.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                model.getOnlineState());

        Assert.assertEquals(
                "Grand Exchange",
                model.getLocationName());

        Assert.assertEquals(
                "RuneTags Clan",
                model.getChannelName());

        Assert.assertEquals(
                "General",
                model.getChannelRank());

        Assert.assertEquals(
                PlayerSource.CLAN,
                model.getChannelSource());

        Assert.assertTrue(
                model.isNearby());

        Assert.assertSame(
                identity,
                model.getIdentity());

        /*
         * Independent WOM/local-record fields must also survive.
         */
        Assert.assertEquals(
                Double.valueOf(325.5),
                model.getEfficientHoursPlayed());

        Assert.assertEquals(
                Double.valueOf(115.25),
                model.getEfficientHoursBossed());

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa Clause"),
                model.getPreviousRsns());

        Assert.assertEquals(
                Arrays.asList(
                        "friend",
                        "trusted"),
                model.getTags());

        Assert.assertTrue(
                model.isFavorite());

        Assert.assertEquals(
                "Known player",
                model.getNote());
    }

    @Test
    public void nullEnrichmentDataDoesNotEraseExistingStats()
            throws Exception
    {
        final HiscoreEnrichmentState[] states =
                {
                        HiscoreEnrichmentState.ERROR,
                        HiscoreEnrichmentState.NOT_FOUND
                };

        for (HiscoreEnrichmentState state : states)
        {
            setModel(
                    QuickProfileModel.builder()
                            .displayName(
                                    "Zezima")
                            .resolved(true)
                            .accountType(
                                    AccountType.NORMAL)
                            .combatLevel(
                                    126)
                            .totalLevel(
                                    2277)
                            .world(
                                    301)
                            .onlineState(
                                    OnlineState.ONLINE)
                            .locationName(
                                    "Grand Exchange")
                            .enrichmentState(
                                    HiscoreEnrichmentState.LOADING)
                            .build());

            applyEnrichment(
                    0L,
                    "Zezima",
                    state,
                    null);

            final QuickProfileModel model =
                    controller.getModel();

            Assert.assertEquals(
                    state.name(),
                    Integer.valueOf(126),
                    model.getCombatLevel());

            Assert.assertEquals(
                    state.name(),
                    Integer.valueOf(2277),
                    model.getTotalLevel());

            Assert.assertEquals(
                    state.name(),
                    AccountType.NORMAL,
                    model.getAccountType());

            Assert.assertEquals(
                    state.name(),
                    Integer.valueOf(301),
                    model.getWorld());

            Assert.assertEquals(
                    state.name(),
                    OnlineState.ONLINE,
                    model.getOnlineState());

            Assert.assertEquals(
                    state.name(),
                    "Grand Exchange",
                    model.getLocationName());

            Assert.assertEquals(
                    state.name(),
                    state,
                    model.getEnrichmentState());
        }
    }

    @Test
    public void nativeModeratorAccountTypeBeatsHiscoreAccountType()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Party Hat")
                        .resolved(true)
                        .accountType(
                                AccountType.PLAYER_MODERATOR)
                        .combatLevel(
                                100)
                        .totalLevel(
                                2000)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADING)
                        .build());

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.IRONMAN,
                        Collections.emptyMap());

        applyEnrichment(
                0L,
                "Party Hat",
                HiscoreEnrichmentState.LOADED,
                data);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                model.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(126),
                model.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2277),
                model.getTotalLevel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                model.getEnrichmentState());
    }

    @Test
    public void refreshContextContinuesUsingRetainedEnrichmentData()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "FasT 07")
                        .resolved(true)
                        .accountType(
                                AccountType.IRONMAN)
                        .combatLevel(
                                100)
                        .totalLevel(
                                2000)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADING)
                        .build());

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        110,
                        2100,
                        AccountType.LEAGUES,
                        Collections.emptyMap());

        /*
         * Automatic enrichment establishes LEAGUES as the stronger
         * temporary account classification and stores this data in
         * controller.enrichmentData.
         */
        applyEnrichment(
                0L,
                "FasT 07",
                HiscoreEnrichmentState.LOADED,
                data);

        Assert.assertEquals(
                AccountType.LEAGUES,
                controller.getModel()
                        .getAccountType());

        Assert.assertEquals(
                Integer.valueOf(110),
                controller.getModel()
                        .getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2100),
                controller.getModel()
                        .getTotalLevel());

        /*
         * The current live directory still reports the player's
         * durable/native account classification as IRONMAN.
         */
        final PlayerIdentity refreshedIdentity =
                identity(
                        "FasT 07",
                        302,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        PlayerSource.FRIEND,
                        AccountType.IRONMAN);

        Mockito.when(
                        playerDirectory.find(
                                "FasT 07"))
                .thenReturn(
                        Optional.of(
                                refreshedIdentity));

        controller.refreshContext();

        final QuickProfileModel refreshed =
                controller.getModel();

        /*
         * refreshContext() must still combine the live identity with
         * the retained HiScore enrichment. If enrichmentData had been
         * discarded, this would incorrectly fall back to IRONMAN.
         */
        Assert.assertEquals(
                AccountType.LEAGUES,
                refreshed.getAccountType());

        /*
         * Live fields update independently.
         */
        Assert.assertEquals(
                Integer.valueOf(302),
                refreshed.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                refreshed.getOnlineState());

        Assert.assertFalse(
                refreshed.isNearby());

        Assert.assertSame(
                refreshedIdentity,
                refreshed.getIdentity());

        /*
         * Enrichment-owned numeric values remain intact because
         * refreshContext() does not own or replace them.
         */
        Assert.assertEquals(
                Integer.valueOf(110),
                refreshed.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2100),
                refreshed.getTotalLevel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                refreshed.getEnrichmentState());
    }

    @Test
    public void sameGenerationWrongPlayerEnrichmentIsRejected()
            throws Exception
    {
        final PlayerIdentity identity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        true,
                        "RuneTags Clan",
                        "General",
                        PlayerSource.CLAN,
                        AccountType.NORMAL);

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .accountType(
                                AccountType.NORMAL)
                        .combatLevel(
                                100)
                        .totalLevel(
                                2000)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .locationName(
                                "Grand Exchange")
                        .channelName(
                                "RuneTags Clan")
                        .channelRank(
                                "General")
                        .channelSource(
                                PlayerSource.CLAN)
                        .nearby(true)
                        .identity(
                                identity)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADING)
                        .build());

        final HiscoreProfileData wrongPlayerData =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.IRONMAN,
                        Collections.emptyMap());

        /*
         * Generation matches, but this enrichment belongs to another
         * player. The samePlayer guard must reject it independently
         * of openGeneration.
         */
        applyEnrichment(
                0L,
                "Party Hat",
                HiscoreEnrichmentState.LOADED,
                wrongPlayerData);

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertEquals(
                "Santa",
                model.getDisplayName());

        Assert.assertEquals(
                AccountType.NORMAL,
                model.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(100),
                model.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2000),
                model.getTotalLevel());

        Assert.assertEquals(
                Integer.valueOf(301),
                model.getWorld());

        Assert.assertEquals(
                "Grand Exchange",
                model.getLocationName());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADING,
                model.getEnrichmentState());

        Assert.assertSame(
                identity,
                model.getIdentity());
    }

    @Test
    public void contextMetricsRequireBothContextAndEnrichmentData()
            throws Exception
    {
        final PlayerContext contextWithMetrics =
                new PlayerContext(
                        new PlayerLocation(
                                "Ungael",
                                0),
                        Collections.singletonList(
                                new ProfileMetric(
                                        "Vorkath KC",
                                        "VORKATH")));

        Assert.assertTrue(
                buildContextMetrics(
                        null,
                        new HiscoreProfileData(
                                126,
                                2277,
                                AccountType.NORMAL,
                                Collections.emptyMap()))
                        .isEmpty());

        Assert.assertTrue(
                buildContextMetrics(
                        contextWithMetrics,
                        null)
                        .isEmpty());

        final PlayerContext contextWithoutMetrics =
                new PlayerContext(
                        new PlayerLocation(
                        "Ungael",
                        0),
                        Collections.emptyList());

        Assert.assertTrue(
                buildContextMetrics(
                        contextWithoutMetrics,
                        new HiscoreProfileData(
                                126,
                                2277,
                                AccountType.NORMAL,
                                Collections.emptyMap()))
                        .isEmpty());
    }

    @Test
    public void contextMetricsUseHiscoreValuesInContextOrder()
            throws Exception
    {
        final ProfileMetric zulrah =
                new ProfileMetric(
                        "Zulrah KC",
                        "ZULRAH");

        final ProfileMetric vorkath =
                new ProfileMetric(
                        "Vorkath KC",
                        "VORKATH");

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                        "Boss Area",
                        0),
                        Arrays.asList(
                                zulrah,
                                vorkath));

        final java.util.Map<String, Integer> contextValues =
                new java.util.LinkedHashMap<>();

        contextValues.put(
                "ZULRAH",
                850);

        contextValues.put(
                "VORKATH",
                1250);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        contextValues);

        final java.util.List<ProfileMetricValue> values =
                buildContextMetrics(
                        context,
                        data);

        Assert.assertEquals(
                2,
                values.size());

        Assert.assertEquals(
                new ProfileMetricValue(
                        "Zulrah KC",
                        850),
                values.get(0));

        Assert.assertEquals(
                new ProfileMetricValue(
                        "Vorkath KC",
                        1250),
                values.get(1));
    }

    @Test
    public void contextMetricsSkipUnavailableHiscoreValues()
            throws Exception
    {
        final ProfileMetric zulrah =
                new ProfileMetric(
                        "Zulrah KC",
                        "ZULRAH");

        final ProfileMetric vorkath =
                new ProfileMetric(
                        "Vorkath KC",
                        "VORKATH");

        final ProfileMetric yama =
                new ProfileMetric(
                        "Yama KC",
                        "YAMA");

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                        "Boss Area",
                        0),
                        Arrays.asList(
                                zulrah,
                                vorkath,
                                yama));

        final java.util.Map<String, Integer> contextValues =
                new java.util.LinkedHashMap<>();

        contextValues.put(
                "ZULRAH",
                500);

        /*
         * VORKATH is deliberately absent from the HiScore data.
         * Its ProfileMetric must therefore be skipped rather than
         * producing a null/placeholder metric.
         */
        contextValues.put(
                "YAMA",
                25);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        110,
                        2100,
                        AccountType.IRONMAN,
                        contextValues);

        final java.util.List<ProfileMetricValue> values =
                buildContextMetrics(
                        context,
                        data);

        Assert.assertEquals(
                2,
                values.size());

        Assert.assertEquals(
                new ProfileMetricValue(
                        "Zulrah KC",
                        500),
                values.get(0));

        Assert.assertEquals(
                new ProfileMetricValue(
                        "Yama KC",
                        25),
                values.get(1));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void contextMetricResultIsUnmodifiable()
            throws Exception
    {
        final ProfileMetric metric =
                new ProfileMetric(
                        "Zulrah KC",
                        "ZULRAH");

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                        "Zulrah",
                        0),
                        Collections.singletonList(
                                metric));

        final java.util.Map<String, Integer> contextValues =
                new java.util.LinkedHashMap<>();

        contextValues.put(
                "ZULRAH",
                100);

        final java.util.List<ProfileMetricValue> values =
                buildContextMetrics(
                        context,
                        new HiscoreProfileData(
                                100,
                                2000,
                                AccountType.NORMAL,
                                contextValues));

        Assert.assertEquals(
                1,
                values.size());

        Assert.assertEquals(
                new ProfileMetricValue(
                        "Zulrah KC",
                        100),
                values.get(0));

        /*
         * buildContextMetrics() deliberately returns an immutable
         * snapshot. Mutation must therefore throw.
         */
        values.add(
                new ProfileMetricValue(
                        "Injected",
                        999));
    }

    @Test
    public void enrichmentUsesCapturedProfileContextForMetrics()
            throws Exception
    {
        final ProfileMetric metric =
                new ProfileMetric(
                        "Vorkath KC",
                        "VORKATH");

        final PlayerContext capturedContext =
                new PlayerContext(
                        new PlayerLocation(
                        "Ungael",
                        0),
                        Collections.singletonList(
                                metric));

        /*
         * This is the context associated with Santa's Quick-Card.
         * applyEnrichment() must use this captured profile context
         * instead of consulting whatever local context exists when
         * the asynchronous result arrives.
         */
        setProfileContext(
                capturedContext);

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .accountType(
                                AccountType.NORMAL)
                        .combatLevel(
                                100)
                        .totalLevel(
                                2000)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .locationName(
                                "Ungael")
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADING)
                        .build());

        final java.util.Map<String, Integer> contextValues =
                new java.util.LinkedHashMap<>();

        contextValues.put(
                "VORKATH",
                750);

        applyEnrichment(
                0L,
                "Santa",
                HiscoreEnrichmentState.LOADED,
                new HiscoreProfileData(
                        110,
                        2100,
                        AccountType.NORMAL,
                        contextValues));

        final QuickProfileModel model =
                controller.getModel();

        Assert.assertEquals(
                1,
                model.getContextMetrics()
                        .size());

        Assert.assertEquals(
                new ProfileMetricValue(
                        "Vorkath KC",
                        750),
                model.getContextMetrics()
                        .get(0));

        /*
         * Contextual enrichment must not disturb the already
         * resolved live/profile state.
         */
        Assert.assertEquals(
                Integer.valueOf(301),
                model.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                model.getOnlineState());

        Assert.assertEquals(
                "Ungael",
                model.getLocationName());

        Assert.assertEquals(
                Integer.valueOf(110),
                model.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2100),
                model.getTotalLevel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                model.getEnrichmentState());
    }

    @Test
    public void nullIdentityHasNoProfileContext()
            throws Exception
    {
        final PlayerContext context =
                resolveProfileContext(
                        null);

        Assert.assertNull(
                context);

        Mockito.verifyNoInteractions(
                playerContextService,
                partyContextService);
    }

    @Test
    public void nearbyContextWinsOverPartyContext()
            throws Exception
    {
        final PlayerContext localContext =
                new PlayerContext(
                        new PlayerLocation(
                        "Grand Exchange",
                        12850),
                        Collections.singletonList(
                                new ProfileMetric(
                                        "Zulrah KC",
                                        "ZULRAH")));

        Mockito.when(
                        playerContextService.getCurrent())
                .thenReturn(
                        localContext);

        /*
         * Deliberately make this player both NEARBY and PARTY.
         * NEARBY must take priority before Party context is queried.
         */
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.NEARBY,
                                        PlayerSource.PARTY))
                        .build();

        final PlayerContext resolved =
                resolveProfileContext(
                        identity);

        Assert.assertSame(
                localContext,
                resolved);

        Mockito.verify(
                        playerContextService)
                .getCurrent();

        /*
         * The Party branch must never run once NEARBY has matched.
         */
        Mockito.verifyNoInteractions(
                partyContextService);
    }

    @Test
    public void ordinaryRemotePlayerNeverInheritsLocalContext()
            throws Exception
    {
        final PlayerContext localContext =
                new PlayerContext(
                        new PlayerLocation(
                        "Grand Exchange",
                        12850),
                        Collections.emptyList());

        Mockito.when(
                        playerContextService.getCurrent())
                .thenReturn(
                        localContext);

        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.FRIEND))
                        .build();

        final PlayerContext resolved =
                resolveProfileContext(
                        identity);

        Assert.assertNull(
                resolved);

        /*
         * A FRIEND-only remote identity is neither nearby nor Party.
         * It must never borrow our own local context.
         */
        Mockito.verifyNoInteractions(
                playerContextService,
                partyContextService);
    }

    @Test
    public void remotePartyWithoutPartyContextHasNoProfileContext()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Party Hat")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.PARTY))
                        .build();

        Mockito.when(
                        partyContextService.find(
                                "Party Hat"))
                .thenReturn(
                        null);

        final PlayerContext resolved =
                resolveProfileContext(
                        identity);

        Assert.assertNull(
                resolved);

        Mockito.verify(
                        partyContextService)
                .find(
                        "Party Hat");

        /*
         * Remote Party lookup must never fall back to our
         * own local scene.
         */
        Mockito.verifyNoInteractions(
                playerContextService);
    }

    @Test
    public void remotePartyUsesItsOwnResolvedPartyContext()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "FasT 07")
                        .accountType(
                                AccountType.IRONMAN)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.PARTY))
                        .build();

        /*
         * Ungael is deliberately used because the production
         * ProfileMetricCatalog maps it to the Vorkath metric.
         */
        final PartyContextService.PartyContext partyContext =
                new PartyContextService.PartyContext(
                        123L,
                        "FasT 07",
                        6727,
                        "Ungael",
                        System.currentTimeMillis());

        Mockito.when(
                        partyContextService.find(
                                "FasT 07"))
                .thenReturn(
                        partyContext);

        final PlayerContext resolved =
                resolveProfileContext(
                        identity);

        Assert.assertNotNull(
                resolved);

        Assert.assertEquals(
                "Ungael",
                resolved.getLocationName());

        Assert.assertEquals(
                6727,
                resolved.getRegionId());

        /*
         * The real ProfileMetricResolver must resolve Ungael
         * through the production ProfileMetricCatalog.
         */
        Assert.assertTrue(
                resolved.hasMetrics());

        Assert.assertEquals(
                1,
                resolved.getMetrics()
                        .size());

        Assert.assertEquals(
                new ProfileMetric(
                        "Vorkath KC",
                        "VORKATH"),
                resolved.getMetrics()
                        .get(0));

        Mockito.verify(
                        partyContextService)
                .find(
                        "FasT 07");

        /*
         * Remote Party context must come from that Party
         * member's reported location, never our own scene.
         */
        Mockito.verifyNoInteractions(
                playerContextService);
    }

    @Test
    public void mappedLocationAlwaysWins()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.NEARBY))
                        .build();

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                        "Grand Exchange",
                        12850),
                        Collections.emptyList());

        final String location =
                displayLocation(
                        "Santa",
                        identity,
                        context);

        Assert.assertEquals(
                "Grand Exchange",
                location);

        /*
         * A real mapped context is already authoritative.
         * No local-player lookup or fallback context should
         * be needed.
         */
        Mockito.verifyNoInteractions(
                client,
                playerContextService);
    }

    @Test
    public void unmappedNearbyPlayerDisplaysOnlyNearby()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa Clause")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.NEARBY))
                        .build();

        final PlayerContext unmappedContext =
                new PlayerContext(
                        new PlayerLocation(
                        null,
                        12850),
                        Collections.emptyList());

        /*
         * Santa Clause is not the local player.
         */
        final Player localPlayer =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        localPlayer.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        client.getLocalPlayer())
                .thenReturn(
                        localPlayer);

        final String location =
                displayLocation(
                        "Santa Clause",
                        identity,
                        unmappedContext);

        /*
         * Another nearby player's precise unmapped region-grid
         * must not be exposed as though RuneTags independently
         * knew their exact location.
         */
        Assert.assertEquals(
                "Nearby",
                location);
    }

    @Test
    public void unmappedRemotePlayerDisplaysUnknownLocation()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Party Hat")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.FRIEND))
                        .build();

        final PlayerContext unmappedContext =
                new PlayerContext(
                        new PlayerLocation(
                        null,
                        12850),
                        Collections.emptyList());

        final Player localPlayer =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        localPlayer.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        client.getLocalPlayer())
                .thenReturn(
                        localPlayer);

        final String location =
                displayLocation(
                        "Party Hat",
                        identity,
                        unmappedContext);

        Assert.assertEquals(
                "Location: Unknown",
                location);

        /*
         * A remote player receives neither our local region-grid
         * nor the generic Nearby fallback.
         */
        Mockito.verifyNoInteractions(
                playerContextService);
    }

    @Test
    public void localPlayerCanDisplayUnmappedRegionGrid()
            throws Exception
    {
        final Player localPlayer =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        localPlayer.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        client.getLocalPlayer())
                .thenReturn(
                        localPlayer);

        /*
         * 12850 == (50 << 8) | 50.
         */
        final PlayerContext unmappedContext =
                new PlayerContext(
                        new PlayerLocation(
                        null,
                        12850),
                        Collections.emptyList());

        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.NEARBY))
                        .build();

        final String location =
                displayLocation(
                        "Zezima",
                        identity,
                        unmappedContext);

        Assert.assertEquals(
                "Region: [50, 50]",
                location);

        Mockito.verify(
                        client)
                .getLocalPlayer();

        /*
         * The supplied profile context is sufficient, so there
         * is no reason to ask PlayerContextService again.
         */
        Mockito.verifyNoInteractions(
                playerContextService);
    }

    @Test
    public void localPlayerCanUseCurrentContextWhenIdentityContextIsMissing()
            throws Exception
    {
        final Player localPlayer =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        localPlayer.getName())
                .thenReturn(
                        "FasT 07");

        Mockito.when(
                        client.getLocalPlayer())
                .thenReturn(
                        localPlayer);

        final PlayerContext currentContext =
                new PlayerContext(
                        new PlayerLocation(
                        null,
                        12850),
                        Collections.emptyList());

        Mockito.when(
                        playerContextService.getCurrent())
                .thenReturn(
                        currentContext);

        /*
         * Use a separator variant deliberately. Local-player
         * recognition must use RuneTags same-player semantics,
         * not raw String equality.
         */
        final String location =
                displayLocation(
                        "FasT_07",
                        null,
                        null);

        Assert.assertEquals(
                "Region: [50, 50]",
                location);

        Mockito.verify(
                        client)
                .getLocalPlayer();

        Mockito.verify(
                        playerContextService)
                .getCurrent();
    }

    @Test
    public void publicAndModeratorChatDisplayAsPublic()
            throws Exception
    {
        Assert.assertEquals(
                "Public",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                "Public",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.MODCHAT));
    }

    @Test
    public void privateChatVariantsDisplayAsPrivate()
            throws Exception
    {
        Assert.assertEquals(
                "Private",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.PRIVATECHAT));

        Assert.assertEquals(
                "Private",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.MODPRIVATECHAT));

        Assert.assertEquals(
                "Private",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.PRIVATECHATOUT));
    }

    @Test
    public void sharedChannelChatTypesUseExpectedFallbackNames()
            throws Exception
    {
        Assert.assertEquals(
                "Friends Chat",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.FRIENDSCHAT));

        Assert.assertEquals(
                "Clan",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.CLAN_CHAT));

        Assert.assertEquals(
                "Clan",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.CLAN_GIM_CHAT));

        Assert.assertEquals(
                "Guest Clan",
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.CLAN_GUEST_CHAT));
    }

    @Test
    public void unsupportedOrNullChatTypeHasNoFallbackChannel()
            throws Exception
    {
        Assert.assertNull(
                displayMessageChannel(
                        null));

        Assert.assertNull(
                displayMessageChannel(
                        net.runelite.api.ChatMessageType.GAMEMESSAGE));
    }

    @Test
    public void liveChannelMetadataOverridesMessageFallback()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .accountType(
                                AccountType.NORMAL)
                        .channelName(
                                "RuneTags Clan")
                        .channelRank(
                                "General")
                        .channelSource(
                                PlayerSource.CLAN)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.CLAN))
                        .build();

        Assert.assertEquals(
                "RuneTags Clan",
                displayChannelName(
                        identity,
                        net.runelite.api.ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                "General",
                displayChannelRank(
                        identity));

        Assert.assertEquals(
                PlayerSource.CLAN,
                displayChannelSource(
                        identity));
    }

    @Test
    public void blankLiveChannelFallsBackToOriginatingMessageChannel()
            throws Exception
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Party Hat")
                        .accountType(
                                AccountType.NORMAL)
                        .channelName(
                                "   ")
                        .channelRank(
                                "Captain")
                        .channelSource(
                                PlayerSource.CLAN)
                        .sources(
                                EnumSet.of(
                                        PlayerSource.CLAN))
                        .build();

        /*
         * A blank live channel is not valid shared-channel metadata,
         * so presentation falls back to the originating message.
         */
        Assert.assertEquals(
                "Private",
                displayChannelName(
                        identity,
                        net.runelite.api.ChatMessageType.PRIVATECHAT));

        /*
         * Rank/source may only accompany a real nonblank live
         * channel. They must not leak alongside a message fallback.
         */
        Assert.assertNull(
                displayChannelRank(
                        identity));

        Assert.assertNull(
                displayChannelSource(
                        identity));
    }

    @Test
    public void samePlayerIgnoresCase()
            throws Exception
    {
        Assert.assertTrue(
                samePlayer(
                        "Zezima",
                        "zezima"));
    }

    @Test
    public void samePlayerTreatsSupportedSeparatorsAsEquivalent()
            throws Exception
    {
        Assert.assertTrue(
                samePlayer(
                        "FasT 07",
                        "FasT_07"));

        Assert.assertTrue(
                samePlayer(
                        "FasT 07",
                        "FasT-07"));

        Assert.assertTrue(
                samePlayer(
                        "FasT 07",
                        "FasT07"));
    }

    @Test
    public void samePlayerTreatsUnicodeSpacesAsEquivalent()
            throws Exception
    {
        Assert.assertTrue(
                samePlayer(
                        "Santa Clause",
                        "Santa\u00A0Clause"));

        Assert.assertTrue(
                samePlayer(
                        "Santa Clause",
                        "Santa\u202FClause"));
    }

    @Test
    public void samePlayerRejectsDifferentNames()
            throws Exception
    {
        Assert.assertFalse(
                samePlayer(
                        "Santa",
                        "Santa Clause"));

        Assert.assertFalse(
                samePlayer(
                        "Party Hat",
                        "Zezima"));
    }

    @Test
    public void samePlayerHandlesNullAndBlankInputsSafely()
            throws Exception
    {
        Assert.assertTrue(
                samePlayer(
                        null,
                        null));

        Assert.assertTrue(
                samePlayer(
                        null,
                        ""));

        Assert.assertTrue(
                samePlayer(
                        "   ",
                        null));

        Assert.assertFalse(
                samePlayer(
                        null,
                        "Santa"));
    }

    @Test
    public void formatRegionGridUnpacksRegionCoordinates()
            throws Exception
    {
        /*
         * (50 << 8) | 50 == 12850
         */
        Assert.assertEquals(
                "Region: [50, 50]",
                formatRegionGrid(
                        12850));

        /*
         * (1 << 8) | 255 == 511
         */
        Assert.assertEquals(
                "Region: [1, 255]",
                formatRegionGrid(
                        511));

        /*
         * Lowest representable packed pair.
         */
        Assert.assertEquals(
                "Region: [0, 0]",
                formatRegionGrid(
                        0));

        /*
         * Highest 8-bit pair.
         */
        Assert.assertEquals(
                "Region: [255, 255]",
                formatRegionGrid(
                        65535));
    }

    @Test
    public void resolveHistoryContextRejectsBlankAndUnknownPlayers()
    {
        final QuickProfileController.ProfileContextSnapshot nullResult =
                controller.resolveHistoryContext(
                        null);

        final QuickProfileController.ProfileContextSnapshot blankResult =
                controller.resolveHistoryContext(
                        "   ");

        Mockito.when(
                        playerDirectory.find(
                                "definitelyunknown123"))
                .thenReturn(
                        Optional.empty());

        final QuickProfileController.ProfileContextSnapshot unknownResult =
                controller.resolveHistoryContext(
                        "definitelyunknown123");

        Assert.assertNull(
                nullResult.getWorld());

        Assert.assertNull(
                nullResult.getLocationName());

        Assert.assertNull(
                nullResult.getChannelName());

        Assert.assertNull(
                nullResult.getChannelSource());

        Assert.assertEquals(
                nullResult,
                blankResult);

        Assert.assertEquals(
                nullResult,
                unknownResult);
    }

    @Test
    public void resolveHistoryContextUsesCurrentLiveIdentity()
    {
        final PlayerIdentity identity =
                identity(
                        "Santa",
                        302,
                        OnlineState.ONLINE,
                        true,
                        "RuneTags Clan",
                        "General",
                        PlayerSource.CLAN,
                        AccountType.NORMAL);

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Grand Exchange",
                                12850),
                        Collections.emptyList());

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.of(
                                identity));

        Mockito.when(
                        playerContextService.getCurrent())
                .thenReturn(
                        context);

        final QuickProfileController.ProfileContextSnapshot snapshot =
                controller.resolveHistoryContext(
                        "Santa");

        Assert.assertEquals(
                Integer.valueOf(
                        302),
                snapshot.getWorld());

        Assert.assertEquals(
                "Grand Exchange",
                snapshot.getLocationName());

        Assert.assertEquals(
                "RuneTags Clan",
                snapshot.getChannelName());

        Assert.assertEquals(
                PlayerSource.CLAN,
                snapshot.getChannelSource());
    }

    @Test
    public void layoutBoundsSupportAllHitTests()
    {
        final Rectangle card =
                new Rectangle(
                        100,
                        100,
                        300,
                        250);

        final Rectangle close =
                new Rectangle(
                        370,
                        110,
                        20,
                        20);

        final Rectangle tag =
                new Rectangle(
                        110,
                        310,
                        30,
                        20);

        final Rectangle note =
                new Rectangle(
                        150,
                        310,
                        30,
                        20);

        final Rectangle favorite =
                new Rectangle(
                        190,
                        310,
                        30,
                        20);

        final Rectangle target =
                new Rectangle(
                        230,
                        310,
                        30,
                        20);

        final Rectangle lookup =
                new Rectangle(
                        270,
                        310,
                        30,
                        20);

        final Rectangle clan =
                new Rectangle(
                        120,
                        220,
                        100,
                        18);

        final Rectangle report =
                new Rectangle(
                        120,
                        250,
                        100,
                        18);

        controller.updateLayoutBounds(
                card,
                close,
                tag,
                note,
                favorite,
                target,
                lookup,
                clan,
                report,
                Collections.emptyMap());

        Assert.assertTrue(
                controller.isInsideCard(
                        new Point(
                                150,
                                150)));

        Assert.assertTrue(
                controller.isCloseButton(
                        new Point(
                                375,
                                115)));

        Assert.assertTrue(
                controller.isTagButton(
                        new Point(
                                115,
                                315)));

        Assert.assertTrue(
                controller.isNoteButton(
                        new Point(
                                155,
                                315)));

        Assert.assertTrue(
                controller.isFavoriteButton(
                        new Point(
                                195,
                                315)));

        Assert.assertTrue(
                controller.isTargetButton(
                        new Point(
                                235,
                                315)));

        Assert.assertTrue(
                controller.isLookupButton(
                        new Point(
                                275,
                                315)));

        Assert.assertTrue(
                controller.isClanLink(
                        new Point(
                                125,
                                225)));

        Assert.assertTrue(
                controller.isReportCaseLink(
                        new Point(
                                125,
                                255)));

        Assert.assertFalse(
                controller.isInsideCard(
                        new Point(
                                50,
                                50)));

        Assert.assertFalse(
                controller.isCloseButton(
                        null));
    }

    @Test
    public void layoutBoundsAreDefensivelyCopied()
    {
        final Rectangle card =
                new Rectangle(
                        100,
                        100,
                        300,
                        250);

        final Rectangle close =
                new Rectangle(
                        370,
                        110,
                        20,
                        20);

        controller.updateLayoutBounds(
                card,
                close,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        card.setBounds(
                0,
                0,
                1,
                1);

        close.setBounds(
                0,
                0,
                1,
                1);

        Assert.assertEquals(
                new Rectangle(
                        100,
                        100,
                        300,
                        250),
                controller.getCardBounds());

        Assert.assertEquals(
                new Rectangle(
                        370,
                        110,
                        20,
                        20),
                controller.getCloseButtonBounds());

        final Rectangle returnedCard =
                controller.getCardBounds();

        returnedCard.setBounds(
                0,
                0,
                5,
                5);

        Assert.assertEquals(
                new Rectangle(
                        100,
                        100,
                        300,
                        250),
                controller.getCardBounds());
    }

    @Test
    public void tagRemovalBoundsAreCopiedAndNullEntriesIgnored()
    {
        final Rectangle friendBounds =
                new Rectangle(
                        100,
                        100,
                        20,
                        20);

        final Map<String, Rectangle> removalBounds =
                new LinkedHashMap<>();

        removalBounds.put(
                "friend",
                friendBounds);

        removalBounds.put(
                null,
                new Rectangle(
                        200,
                        200,
                        20,
                        20));

        removalBounds.put(
                "ignored",
                null);

        controller.updateLayoutBounds(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                removalBounds);

        friendBounds.setBounds(
                300,
                300,
                20,
                20);

        Assert.assertEquals(
                "friend",
                controller.tagRemovalAt(
                        new Point(
                                105,
                                105)));

        Assert.assertNull(
                controller.tagRemovalAt(
                        new Point(
                                305,
                                305)));

        Assert.assertNull(
                controller.tagRemovalAt(
                        new Point(
                                205,
                                205)));

        Assert.assertNull(
                controller.tagRemovalAt(
                        null));
    }

    @Test
    public void replacingLayoutBoundsRemovesOldTagRemovalBounds()
    {
        final Map<String, Rectangle> firstBounds =
                new LinkedHashMap<>();

        firstBounds.put(
                "friend",
                new Rectangle(
                        100,
                        100,
                        20,
                        20));

        controller.updateLayoutBounds(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                firstBounds);

        Assert.assertEquals(
                "friend",
                controller.tagRemovalAt(
                        new Point(
                                105,
                                105)));

        controller.updateLayoutBounds(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap());

        Assert.assertNull(
                controller.tagRemovalAt(
                        new Point(
                                105,
                                105)));
    }

    @Test
    public void targetCurrentProfileRequiresNearbyPlayer()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .nearby(false)
                        .build());

        controller.target();

        Mockito.verifyNoInteractions(
                targetController);

        final QuickProfileModel nearbyModel =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .nearby(true)
                        .build();

        setModel(
                nearbyModel);

        controller.target();

        Mockito.verify(
                        targetController)
                .toggleTarget(
                        nearbyModel);
    }

    @Test
    public void currentProfileTargetStateDelegatesByDisplayName()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Party Hat")
                        .resolved(true)
                        .build());

        Mockito.when(
                        targetController.isTargetingName(
                                "Party Hat"))
                .thenReturn(
                        true);

        Assert.assertTrue(
                controller.isCurrentProfileTargeted());

        Mockito.verify(
                        targetController)
                .isTargetingName(
                        "Party Hat");
    }

    @Test
    public void canTargetUsesCurrentLiveDirectoryIdentity()
    {
        final PlayerIdentity historicalIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        true,
                        null,
                        null,
                        PlayerSource.NEARBY,
                        AccountType.NORMAL);

        final PlayerIdentity currentIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        PlayerSource.FRIEND,
                        AccountType.NORMAL);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.of(
                                currentIdentity));

        Assert.assertFalse(
                controller.canTarget(
                        reference));
    }

    @Test
    public void targetReferenceUsesCurrentLiveCanonicalName()
    {
        final PlayerIdentity historicalIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        PlayerSource.FRIEND,
                        AccountType.NORMAL);

        final PlayerIdentity currentIdentity =
                identity(
                        "Santa Clause",
                        301,
                        OnlineState.ONLINE,
                        true,
                        null,
                        null,
                        PlayerSource.NEARBY,
                        AccountType.NORMAL);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.of(
                                currentIdentity));

        controller.target(
                reference);

        Mockito.verify(
                        targetController)
                .toggleTarget(
                        "Santa Clause");
    }

    @Test
    public void targetReferenceRejectsHistoricalNearbyStateWhenNoLongerLive()
    {
        final PlayerIdentity historicalIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        true,
                        null,
                        null,
                        PlayerSource.NEARBY,
                        AccountType.NORMAL);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.empty());

        controller.target(
                reference);

        Mockito.verifyNoInteractions(
                targetController);
    }

    @Test
    public void lookupCurrentProfileUsesDisplayName()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "FasT 07")
                        .resolved(true)
                        .build());

        controller.lookup();

        Mockito.verify(
                        playerLookupService)
                .lookup(
                        "FasT 07");
    }

    @Test
    public void lookupReferencePrefersCurrentLiveCanonicalName()
    {
        final PlayerIdentity historicalIdentity =
                identity(
                        "Santa",
                        301,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        PlayerSource.FRIEND,
                        AccountType.NORMAL);

        final PlayerIdentity currentIdentity =
                identity(
                        "Santa Clause",
                        302,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        PlayerSource.FRIEND,
                        AccountType.NORMAL);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Santa")
                        .lookupName(
                                "Santa")
                        .locallyResolved(true)
                        .identity(
                                historicalIdentity)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.of(
                                currentIdentity));

        controller.lookup(
                reference);

        Mockito.verify(
                        playerLookupService)
                .lookup(
                        "Santa Clause");
    }

    @Test
    public void lookupReferenceFallsBackToLookupName()
    {
        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "@Santa")
                        .lookupName(
                                " Santa ")
                        .locallyResolved(false)
                        .identity(null)
                        .build();

        Mockito.when(
                        playerDirectory.find(
                                " Santa "))
                .thenReturn(
                        Optional.empty());

        controller.lookup(
                reference);

        Mockito.verify(
                        playerLookupService)
                .lookup(
                        "Santa");
    }

    @Test
    public void lookupClanSupportsClanAndGuestClanOnly()
            throws Exception
    {
        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .channelName(
                                "RuneTags Clan")
                        .channelSource(
                                PlayerSource.CLAN)
                        .build());

        controller.lookupClan();

        Mockito.verify(
                        clanLookupService)
                .search(
                        "RuneTags Clan");

        Mockito.reset(
                clanLookupService);

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa Clause")
                        .resolved(true)
                        .channelName(
                                "Guest Clan")
                        .channelSource(
                                PlayerSource.GUEST_CLAN)
                        .build());

        controller.lookupClan();

        Mockito.verify(
                        clanLookupService)
                .search(
                        "Guest Clan");

        Mockito.reset(
                clanLookupService);

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Zezima")
                        .resolved(true)
                        .channelName(
                                "Friends Chat")
                        .channelSource(
                                PlayerSource.FRIENDS_CHAT)
                        .build());

        controller.lookupClan();

        Mockito.verifyNoInteractions(
                clanLookupService);
    }

    /*
     * HELPERS
     */

    private static HiscoreEnrichmentService.EnrichmentRequest failedEnrichmentRequest()
    {
        return new HiscoreEnrichmentService.EnrichmentRequest(
                HiscoreEnrichmentState.ERROR,
                null,
                null);
    }

    private static HiscoreEnrichmentService.EnrichmentRequest loadingEnrichmentRequest(
            CompletableFuture<
                    HiscoreEnrichmentCache.CachedProfileEnrichment> future)
    {
        return new HiscoreEnrichmentService.EnrichmentRequest(
                HiscoreEnrichmentState.LOADING,
                null,
                future);
    }

    private static boolean samePlayer(
            String left,
            String right)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "samePlayer",
                                String.class,
                                String.class);

        method.setAccessible(true);

        return (boolean)
                method.invoke(
                        null,
                        left,
                        right);
    }

    private static String formatRegionGrid(
            int regionId)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "formatRegionGrid",
                                int.class);

        method.setAccessible(true);

        return (String)
                method.invoke(
                        null,
                        regionId);
    }

    private static String displayMessageChannel(
            net.runelite.api.ChatMessageType chatType)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "displayMessageChannel",
                                net.runelite.api.ChatMessageType.class);

        method.setAccessible(true);

        return (String)
                method.invoke(
                        null,
                        chatType);
    }

    private static String displayChannelName(
            PlayerIdentity identity,
            net.runelite.api.ChatMessageType chatType)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "displayChannelName",
                                PlayerIdentity.class,
                                net.runelite.api.ChatMessageType.class);

        method.setAccessible(true);

        return (String)
                method.invoke(
                        null,
                        identity,
                        chatType);
    }

    private static String displayChannelRank(
            PlayerIdentity identity)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "displayChannelRank",
                                PlayerIdentity.class);

        method.setAccessible(true);

        return (String)
                method.invoke(
                        null,
                        identity);
    }

    private static PlayerSource displayChannelSource(
            PlayerIdentity identity)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "displayChannelSource",
                                PlayerIdentity.class);

        method.setAccessible(true);

        return (PlayerSource)
                method.invoke(
                        null,
                        identity);
    }

    private String displayLocation(
            String playerName,
            PlayerIdentity identity,
            PlayerContext context)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "displayLocation",
                                String.class,
                                PlayerIdentity.class,
                                PlayerContext.class);

        method.setAccessible(true);

        return (String)
                method.invoke(
                        controller,
                        playerName,
                        identity,
                        context);
    }

    private PlayerContext resolveProfileContext(
            PlayerIdentity identity)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "resolveProfileContext",
                                PlayerIdentity.class);

        method.setAccessible(true);

        return (PlayerContext)
                method.invoke(
                        controller,
                        identity);
    }

    private static java.util.List<ProfileMetricValue> buildContextMetrics(
            PlayerContext context,
            HiscoreProfileData data)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "buildContextMetrics",
                                PlayerContext.class,
                                HiscoreProfileData.class);

        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        final java.util.List<ProfileMetricValue> result =
                (java.util.List<ProfileMetricValue>)
                        method.invoke(
                                null,
                                context,
                                data);

        return result;
    }

    private void setProfileContext(
            PlayerContext context)
            throws Exception
    {
        final Field field =
                QuickProfileController.class
                        .getDeclaredField(
                                "profileContext");

        field.setAccessible(true);

        field.set(
                controller,
                context);
    }

    private void applyEnrichment(
            long generation,
            String playerName,
            HiscoreEnrichmentState state,
            HiscoreProfileData data)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "applyEnrichment",
                                long.class,
                                String.class,
                                HiscoreEnrichmentState.class,
                                HiscoreProfileData.class);

        method.setAccessible(true);

        method.invoke(
                controller,
                generation,
                playerName,
                state,
                data);
    }

    private void openResolved(
            PlayerReference reference)
            throws Exception
    {
        openResolved(
                reference,
                null);
    }

    private void openResolved(
            PlayerReference reference,
            java.awt.Point point)
            throws Exception
    {
        final Method method =
                QuickProfileController.class
                        .getDeclaredMethod(
                                "openResolved",
                                PlayerReference.class,
                                java.awt.Point.class);

        method.setAccessible(true);

        method.invoke(
                controller,
                reference,
                point);
    }

    private void setModel(
            QuickProfileModel model)
            throws Exception
    {
        final Field field =
                QuickProfileController.class
                        .getDeclaredField(
                                "model");

        field.setAccessible(true);

        field.set(
                controller,
                model);
    }

    private static PlayerIdentity identity(
            String name,
            Integer world,
            OnlineState onlineState,
            boolean nearby,
            String channelName,
            String channelRank,
            PlayerSource channelSource,
            AccountType accountType)
    {
        final EnumSet<PlayerSource> sources =
                EnumSet.noneOf(
                        PlayerSource.class);

        if (channelSource != null)
        {
            sources.add(
                    channelSource);
        }

        if (nearby)
        {
            sources.add(
                    PlayerSource.NEARBY);
        }

        return PlayerIdentity.builder()
                .canonicalName(
                        name)
                .accountType(
                        accountType)
                .world(
                        world)
                .onlineState(
                        onlineState)
                .channelName(
                        channelName)
                .channelRank(
                        channelRank)
                .channelSource(
                        channelSource)
                .sources(
                        sources)
                .build();
    }

    /*
     * PERFORMANCE
     */
    @Test
    public void performanceRefreshAndHistoryResolution()
            throws Exception
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final PlayerIdentity identity =
                identity(
                        "Santa",
                        302,
                        OnlineState.ONLINE,
                        false,
                        null,
                        null,
                        PlayerSource.FRIEND,
                        AccountType.NORMAL);

        Mockito.when(
                        playerDirectory.find(
                                "Santa"))
                .thenReturn(
                        Optional.of(
                                identity));

        setModel(
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .accountType(
                                AccountType.NORMAL)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .build());

        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            controller.refreshContext();

            checksum +=
                    controller.getModel()
                            .getWorld();

            checksum +=
                    controller.resolveHistoryContext(
                                    "Santa")
                            .getWorld();
        }

        long started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            controller.refreshContext();

            checksum +=
                    controller.getModel()
                            .getWorld();
        }

        final long refreshElapsed =
                System.nanoTime()
                        - started;

        started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            final QuickProfileController.ProfileContextSnapshot snapshot =
                    controller.resolveHistoryContext(
                            "Santa");

            checksum +=
                    snapshot.getWorld();
        }

        final long historyElapsed =
                System.nanoTime()
                        - started;

        Assert.assertTrue(
                checksum > 0L);

        final double refreshTotalMs =
                refreshElapsed
                        / 1_000_000.0;

        final double historyTotalMs =
                historyElapsed
                        / 1_000_000.0;

        System.out.printf(
                "[RuneTags][QuickProfileControllerTest] Performance= "
                        + "RefreshContextLive: %.3fms (%.6fms) | "
                        + "ResolveHistoryContext: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                refreshTotalMs,
                refreshTotalMs
                        / PERFORMANCE_ITERATIONS,
                historyTotalMs,
                historyTotalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }
}
