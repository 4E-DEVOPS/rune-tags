package com.runetags.quickprofile;

import com.runetags.context.ProfileMetricValue;
import com.runetags.hiscores.HiscoreEnrichmentState;
import com.runetags.player.OnlineState;
import com.runetags.player.AccountType;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;
import com.runetags.reports.ReportSummary;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;

import net.runelite.api.ChatMessageType;

@Value
@Builder(toBuilder = true)
public class QuickProfileModel
{
    String displayName;
    boolean resolved;

    @Builder.Default
    AccountType accountType = AccountType.UNKNOWN;

    Integer combatLevel;
    Integer totalLevel;
    Double efficientHoursPlayed;
    Double efficientHoursBossed;
    Integer world;
    OnlineState onlineState;

    String locationName;

    String channelName;
    String channelRank;
    PlayerSource channelSource;

    ChatMessageType originatingChatType;

    @Builder.Default
    List<ProfileMetricValue> contextMetrics =
            Collections.emptyList();

    @Builder.Default
    List<ReportSummary> reportSummaries =
            Collections.emptyList();

    @Builder.Default
    List<String> previousRsns =
            Collections.emptyList();

    @Builder.Default
    List<String> tags =
            Collections.emptyList();

    boolean favorite;

    String note;

    boolean nearby;
    PlayerIdentity identity;

    @Builder.Default
    HiscoreEnrichmentState enrichmentState = HiscoreEnrichmentState.LOCAL;

    public static QuickProfileModel unresolved(String name)
    {
        return QuickProfileModel.builder()
                .displayName(name)
                .resolved(false)
                .onlineState(OnlineState.UNKNOWN)
                .nearby(false)
                .enrichmentState(HiscoreEnrichmentState.LOCAL)
                .build();
    }
}
