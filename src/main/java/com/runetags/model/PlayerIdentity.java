package com.runetags.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

@Value
@Builder(toBuilder = true)
public class PlayerIdentity
{
    String canonicalName;
    String normalizedName;

    @Builder.Default
    Set<PlayerSource> sources = Collections.emptySet();

    Integer combatLevel;
    Integer world;
    OnlineState onlineState;

    String channelName;
    String channelRank;
    PlayerSource channelSource;

    Player nearbyPlayer;
    WorldPoint lastKnownWorldPoint;

    public boolean isNearby()
    {
        return nearbyPlayer != null
                || sources.contains(PlayerSource.NEARBY);
    }

    public static Set<PlayerSource> sourceSet(PlayerSource source)
    {
        return Collections.unmodifiableSet(
                EnumSet.of(source));
    }
}