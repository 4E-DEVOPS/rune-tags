package com.runetags.quickprofile;

import com.runetags.context.ContextMetricValue;
import com.runetags.model.OnlineState;
import com.runetags.model.PlayerIdentity;
import com.runetags.model.PlayerSource;

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

    Integer combatLevel;
    Integer totalLevel;
    Integer world;
    OnlineState onlineState;

    String locationName;

    String channelName;
    String channelRank;
    PlayerSource channelSource;

    ChatMessageType originatingChatType;

    @Builder.Default
    List<ContextMetricValue> contextMetrics =
            Collections.emptyList();

    boolean nearby;
    PlayerIdentity identity;

    @Builder.Default
    ProfileEnrichmentState enrichmentState = ProfileEnrichmentState.LOCAL;

    public static QuickProfileModel unresolved(String name)
    {
        return QuickProfileModel.builder()
                .displayName(name)
                .resolved(false)
                .onlineState(OnlineState.UNKNOWN)
                .nearby(false)
                .enrichmentState(ProfileEnrichmentState.LOCAL)
                .build();
    }
}
