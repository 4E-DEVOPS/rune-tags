package com.runetags.quickprofile;

import com.runetags.context.ProfileMetricValue;
import com.runetags.hiscores.HiscoreEnrichmentState;
import com.runetags.player.OnlineState;
import com.runetags.player.AccountType;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;
import com.runetags.reports.ReportSummary;

import java.util.Arrays;
import java.util.Collections;

import net.runelite.api.ChatMessageType;

import org.junit.Assert;
import org.junit.Test;

public class QuickProfileModelTest
{

    /*
     * TESTS
     */

    @Test
    public void unresolvedProfileUsesSafeDefaults()
    {
        final QuickProfileModel model =
                QuickProfileModel.unresolved(
                        "Zezima");

        Assert.assertEquals(
                "Zezima",
                model.getDisplayName());

        Assert.assertFalse(
                model.isResolved());

        Assert.assertEquals(
                AccountType.UNKNOWN,
                model.getAccountType());

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                model.getOnlineState());

        Assert.assertFalse(
                model.isNearby());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOCAL,
                model.getEnrichmentState());

        Assert.assertNotNull(
                model.getContextMetrics());

        Assert.assertTrue(
                model.getContextMetrics()
                        .isEmpty());

        Assert.assertNotNull(
                model.getReportSummaries());

        Assert.assertTrue(
                model.getReportSummaries()
                        .isEmpty());

        Assert.assertNotNull(
                model.getPreviousRsns());

        Assert.assertTrue(
                model.getPreviousRsns()
                        .isEmpty());

        Assert.assertNotNull(
                model.getTags());

        Assert.assertTrue(
                model.getTags()
                        .isEmpty());
    }

    @Test
    public void builderUsesDefaultAccountType()
    {
        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .build();

        Assert.assertEquals(
                AccountType.UNKNOWN,
                model.getAccountType());
    }

    @Test
    public void builderUsesDefaultEnrichmentState()
    {
        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .build();

        Assert.assertEquals(
                HiscoreEnrichmentState.LOCAL,
                model.getEnrichmentState());
    }

    @Test
    public void builderUsesEmptyCollectionDefaults()
    {
        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .build();

        Assert.assertNotNull(
                model.getContextMetrics());

        Assert.assertTrue(
                model.getContextMetrics()
                        .isEmpty());

        Assert.assertNotNull(
                model.getReportSummaries());

        Assert.assertTrue(
                model.getReportSummaries()
                        .isEmpty());

        Assert.assertNotNull(
                model.getPreviousRsns());

        Assert.assertTrue(
                model.getPreviousRsns()
                        .isEmpty());

        Assert.assertNotNull(
                model.getTags());

        Assert.assertTrue(
                model.getTags()
                        .isEmpty());
    }

    @Test
    public void toBuilderPreservesUnchangedProfileData()
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Party Hat")
                        .accountType(
                                AccountType.NORMAL)
                        .build();

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
                                "Test Clan")
                        .channelRank(
                                "General")
                        .channelSource(
                                PlayerSource.CLAN)
                        .previousRsns(
                                Arrays.asList(
                                        "Santa",
                                        "Santa Clause"))
                        .tags(
                                Arrays.asList(
                                        "friend",
                                        "trusted"))
                        .favorite(true)
                        .note(
                                "Known player")
                        .nearby(true)
                        .identity(identity)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADED)
                        .build();

        final QuickProfileModel refreshed =
                original.toBuilder()
                        .world(
                                302)
                        .locationName(
                                "Falador")
                        .nearby(false)
                        .build();

        Assert.assertEquals(
                "Party Hat",
                refreshed.getDisplayName());

        Assert.assertTrue(
                refreshed.isResolved());

        Assert.assertEquals(
                AccountType.NORMAL,
                refreshed.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(100),
                refreshed.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2000),
                refreshed.getTotalLevel());

        Assert.assertEquals(
                Double.valueOf(500.0),
                refreshed.getEfficientHoursPlayed());

        Assert.assertEquals(
                Double.valueOf(250.0),
                refreshed.getEfficientHoursBossed());

        Assert.assertEquals(
                Integer.valueOf(302),
                refreshed.getWorld());

        Assert.assertEquals(
                "Falador",
                refreshed.getLocationName());

        Assert.assertFalse(
                refreshed.isNearby());

        Assert.assertEquals(
                "Test Clan",
                refreshed.getChannelName());

        Assert.assertEquals(
                "General",
                refreshed.getChannelRank());

        Assert.assertEquals(
                PlayerSource.CLAN,
                refreshed.getChannelSource());

        Assert.assertEquals(
                Arrays.asList(
                        "Santa",
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

        Assert.assertSame(
                identity,
                refreshed.getIdentity());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                refreshed.getEnrichmentState());
    }

    @Test
    public void toBuilderCanClearLiveStateWithoutClearingEnrichment()
    {
        final QuickProfileModel original =
                QuickProfileModel.builder()
                        .displayName(
                                "FasT 07")
                        .resolved(true)
                        .combatLevel(
                                126)
                        .totalLevel(
                                2277)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .locationName(
                                "Theatre of Blood")
                        .nearby(true)
                        .enrichmentState(
                                HiscoreEnrichmentState.LOADED)
                        .build();

        final QuickProfileModel refreshed =
                original.toBuilder()
                        .world(null)
                        .onlineState(
                                OnlineState.UNKNOWN)
                        .locationName(
                                "Location: Unknown")
                        .nearby(false)
                        .identity(null)
                        .build();

        Assert.assertNull(
                refreshed.getWorld());

        Assert.assertEquals(
                OnlineState.UNKNOWN,
                refreshed.getOnlineState());

        Assert.assertEquals(
                "Location: Unknown",
                refreshed.getLocationName());

        Assert.assertFalse(
                refreshed.isNearby());

        Assert.assertNull(
                refreshed.getIdentity());

        /*
         * HiScore-derived values are intentionally independent
         * of live directory presence.
         */
        Assert.assertEquals(
                Integer.valueOf(126),
                refreshed.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2277),
                refreshed.getTotalLevel());

        Assert.assertEquals(
                HiscoreEnrichmentState.LOADED,
                refreshed.getEnrichmentState());
    }

    @Test
    public void builderAcceptsExplicitCollectionValues()
    {
        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa Clause")
                        .resolved(true)
                        .previousRsns(
                                Collections.singletonList(
                                        "Santa"))
                        .tags(
                                Collections.singletonList(
                                        "favorite"))
                        .build();

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                model.getPreviousRsns());

        Assert.assertEquals(
                Collections.singletonList(
                        "favorite"),
                model.getTags());
    }

    @Test
    public void unresolvedProfileDoesNotInventLiveIdentity()
    {
        final QuickProfileModel model =
                QuickProfileModel.unresolved(
                        "definitelyunknown123");

        Assert.assertNull(
                model.getIdentity());

        Assert.assertNull(
                model.getWorld());

        Assert.assertNull(
                model.getLocationName());

        Assert.assertNull(
                model.getChannelName());

        Assert.assertNull(
                model.getChannelRank());

        Assert.assertNull(
                model.getChannelSource());

        Assert.assertNull(
                model.getCombatLevel());

        Assert.assertNull(
                model.getTotalLevel());
    }

    @Test
    public void unresolvedProfilePreservesRequestedDisplayName()
    {
        final QuickProfileModel model =
                QuickProfileModel.unresolved(
                        "definitelyunknown123");

        Assert.assertEquals(
                "definitelyunknown123",
                model.getDisplayName());

        Assert.assertFalse(
                model.isResolved());
    }

    @Test
    public void builderLeavesOnlineStateUnsetUnlessProvided()
    {
        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .build();

        Assert.assertNull(
                model.getOnlineState());
    }

    @Test
    public void builderPreservesOriginatingChatType()
    {
        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Zezima")
                        .resolved(true)
                        .originatingChatType(
                                ChatMessageType.CLAN_CHAT)
                        .build();

        Assert.assertEquals(
                ChatMessageType.CLAN_CHAT,
                model.getOriginatingChatType());
    }

    @Test
    public void builderAcceptsContextMetrics()
    {
        final ProfileMetricValue metric =
                new ProfileMetricValue(
                        "Vorkath KC",
                        1250);

        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .resolved(true)
                        .contextMetrics(
                                Collections.singletonList(
                                        metric))
                        .build();

        Assert.assertEquals(
                Collections.singletonList(
                        metric),
                model.getContextMetrics());
    }

    @Test
    public void builderAcceptsReportSummaries()
    {
        final ReportSummary reportSummary =
                ReportSummary.builder()
                        .source(
                                "RuneWatch")
                        .caseCount(
                                1)
                        .rsn(
                                "Santa Clause")
                        .caseId(
                                "abc123")
                        .reason(
                                "Scamming")
                        .evidenceRating(
                                "Confirmed")
                        .build();

        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa Clause")
                        .resolved(true)
                        .reportSummaries(
                                Collections.singletonList(
                                        reportSummary))
                        .build();

        Assert.assertEquals(
                Collections.singletonList(
                        reportSummary),
                model.getReportSummaries());
    }

    @Test
    public void toBuilderPreservesOriginatingChatTypeAndCollections()
    {
        final ProfileMetricValue metric =
                new ProfileMetricValue(
                        "Zulrah KC",
                        850);

        final ReportSummary reportSummary =
                ReportSummary.builder()
                        .source(
                                "RuneWatch")
                        .caseCount(
                                1)
                        .rsn(
                                "Party Hat")
                        .caseId(
                                "case_1")
                        .build();

        final QuickProfileModel original =
                QuickProfileModel.builder()
                        .displayName(
                                "Party Hat")
                        .resolved(true)
                        .originatingChatType(
                                ChatMessageType.PUBLICCHAT)
                        .contextMetrics(
                                Collections.singletonList(
                                        metric))
                        .reportSummaries(
                                Collections.singletonList(
                                        reportSummary))
                        .previousRsns(
                                Collections.singletonList(
                                        "Party_Hat"))
                        .tags(
                                Collections.singletonList(
                                        "friend"))
                        .build();

        final QuickProfileModel copy =
                original.toBuilder()
                        .build();

        Assert.assertEquals(
                ChatMessageType.PUBLICCHAT,
                copy.getOriginatingChatType());

        Assert.assertEquals(
                Collections.singletonList(
                        metric),
                copy.getContextMetrics());

        Assert.assertEquals(
                Collections.singletonList(
                        reportSummary),
                copy.getReportSummaries());

        Assert.assertEquals(
                Collections.singletonList(
                        "Party_Hat"),
                copy.getPreviousRsns());

        Assert.assertEquals(
                Collections.singletonList(
                        "friend"),
                copy.getTags());
    }

    @Test
    public void equalModelsHaveEqualValueSemantics()
    {
        final QuickProfileModel first =
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
                        .onlineState(
                                OnlineState.ONLINE)
                        .originatingChatType(
                                ChatMessageType.PUBLICCHAT)
                        .build();

        final QuickProfileModel second =
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
                        .onlineState(
                                OnlineState.ONLINE)
                        .originatingChatType(
                                ChatMessageType.PUBLICCHAT)
                        .build();

        Assert.assertEquals(
                first,
                second);

        Assert.assertEquals(
                first.hashCode(),
                second.hashCode());
    }
}