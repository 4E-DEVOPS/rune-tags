package com.runetags.player;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class PlayerIdentityTest
{
    /*
     * TESTS
     */

    @Test
    public void builderDefaultsAccountTypeToUnknown()
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .normalizedName(
                                "zezima")
                        .build();

        Assert.assertEquals(
                AccountType.UNKNOWN,
                identity.getAccountType());
    }

    @Test
    public void builderDefaultsSourcesToEmptySet()
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .normalizedName(
                                "zezima")
                        .build();

        Assert.assertNotNull(
                identity.getSources());

        Assert.assertTrue(
                identity.getSources()
                        .isEmpty());
    }

    @Test
    public void identityRetainsConfiguredFields()
    {
        final Player nearbyPlayer =
                Mockito.mock(
                        Player.class);

        final WorldPoint worldPoint =
                new WorldPoint(
                        3200,
                        3201,
                        0);

        final Set<PlayerSource> sources =
                Collections.unmodifiableSet(
                        EnumSet.of(
                                PlayerSource.CLAN,
                                PlayerSource.NEARBY));

        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa Clause")
                        .normalizedName(
                                "santa clause")
                        .accountType(
                                AccountType.IRONMAN)
                        .sources(
                                sources)
                        .combatLevel(
                                126)
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .channelName(
                                "RuneTags Clan")
                        .channelRank(
                                "General")
                        .channelSource(
                                PlayerSource.CLAN)
                        .nearbyPlayer(
                                nearbyPlayer)
                        .lastKnownWorldPoint(
                                worldPoint)
                        .build();

        Assert.assertEquals(
                "Santa Clause",
                identity.getCanonicalName());

        Assert.assertEquals(
                "santa clause",
                identity.getNormalizedName());

        Assert.assertEquals(
                AccountType.IRONMAN,
                identity.getAccountType());

        Assert.assertEquals(
                sources,
                identity.getSources());

        Assert.assertEquals(
                Integer.valueOf(
                        126),
                identity.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(
                        301),
                identity.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                identity.getOnlineState());

        Assert.assertEquals(
                "RuneTags Clan",
                identity.getChannelName());

        Assert.assertEquals(
                "General",
                identity.getChannelRank());

        Assert.assertEquals(
                PlayerSource.CLAN,
                identity.getChannelSource());

        Assert.assertSame(
                nearbyPlayer,
                identity.getNearbyPlayer());

        Assert.assertEquals(
                worldPoint,
                identity.getLastKnownWorldPoint());
    }

    @Test
    public void identityWithoutNearbyPlayerOrSourceIsNotNearby()
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .normalizedName(
                                "santa")
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.FRIEND))
                        .build();

        Assert.assertFalse(
                identity.isNearby());
    }

    @Test
    public void nearbySourceMakesIdentityNearby()
    {
        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .normalizedName(
                                "santa")
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.NEARBY))
                        .build();

        Assert.assertTrue(
                identity.isNearby());
    }

    @Test
    public void nearbyPlayerMakesIdentityNearbyWithoutNearbySource()
    {
        final Player nearbyPlayer =
                Mockito.mock(
                        Player.class);

        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Party Hat")
                        .normalizedName(
                                "party hat")
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.FRIEND))
                        .nearbyPlayer(
                                nearbyPlayer)
                        .build();

        Assert.assertTrue(
                identity.isNearby());
    }

    @Test
    public void nearbyPlayerAndNearbySourceRemainNearby()
    {
        final Player nearbyPlayer =
                Mockito.mock(
                        Player.class);

        final PlayerIdentity identity =
                PlayerIdentity.builder()
                        .canonicalName(
                                "FasT 07")
                        .normalizedName(
                                "fast 07")
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.NEARBY))
                        .nearbyPlayer(
                                nearbyPlayer)
                        .build();

        Assert.assertTrue(
                identity.isNearby());
    }

    @Test
    public void sourceSetContainsOnlyRequestedSource()
    {
        for (PlayerSource source
                : PlayerSource.values())
        {
            final Set<PlayerSource> sources =
                    PlayerIdentity.sourceSet(
                            source);

            Assert.assertEquals(
                    source.name(),
                    1,
                    sources.size());

            Assert.assertTrue(
                    source.name(),
                    sources.contains(
                            source));
        }
    }

    @Test
    public void sourceSetIsUnmodifiable()
    {
        final Set<PlayerSource> sources =
                PlayerIdentity.sourceSet(
                        PlayerSource.CLAN);

        try
        {
            sources.add(
                    PlayerSource.PARTY);

            Assert.fail(
                    "Expected PlayerIdentity.sourceSet() to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
        }

        Assert.assertEquals(
                1,
                sources.size());

        Assert.assertTrue(
                sources.contains(
                        PlayerSource.CLAN));
    }

    @Test
    public void toBuilderPreservesOriginalValues()
    {
        final WorldPoint worldPoint =
                new WorldPoint(
                        3210,
                        3220,
                        1);

        final PlayerIdentity original =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa Clause")
                        .normalizedName(
                                "santa clause")
                        .accountType(
                                AccountType.HARDCORE)
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.CLAN))
                        .combatLevel(
                                110)
                        .world(
                                302)
                        .onlineState(
                                OnlineState.ONLINE)
                        .channelName(
                                "RuneTags Clan")
                        .channelRank(
                                "Captain")
                        .channelSource(
                                PlayerSource.CLAN)
                        .lastKnownWorldPoint(
                                worldPoint)
                        .build();

        final PlayerIdentity copy =
                original.toBuilder()
                        .build();

        Assert.assertEquals(
                original,
                copy);

        Assert.assertEquals(
                original.hashCode(),
                copy.hashCode());
    }

    @Test
    public void toBuilderCanChangeCopyWithoutChangingOriginal()
    {
        final PlayerIdentity original =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Zezima")
                        .normalizedName(
                                "zezima")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.FRIEND))
                        .world(
                                301)
                        .onlineState(
                                OnlineState.ONLINE)
                        .build();

        final PlayerIdentity changed =
                original.toBuilder()
                        .accountType(
                                AccountType.IRONMAN)
                        .world(
                                302)
                        .build();

        Assert.assertEquals(
                AccountType.NORMAL,
                original.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(
                        301),
                original.getWorld());

        Assert.assertEquals(
                AccountType.IRONMAN,
                changed.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(
                        302),
                changed.getWorld());
    }

    @Test
    public void equalIdentitiesHaveEqualValueSemantics()
    {
        final PlayerIdentity first =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .normalizedName(
                                "santa")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.FRIEND))
                        .onlineState(
                                OnlineState.OFFLINE)
                        .build();

        final PlayerIdentity second =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .normalizedName(
                                "santa")
                        .accountType(
                                AccountType.NORMAL)
                        .sources(
                                PlayerIdentity.sourceSet(
                                        PlayerSource.FRIEND))
                        .onlineState(
                                OnlineState.OFFLINE)
                        .build();

        Assert.assertEquals(
                first,
                second);

        Assert.assertEquals(
                first.hashCode(),
                second.hashCode());
    }
}