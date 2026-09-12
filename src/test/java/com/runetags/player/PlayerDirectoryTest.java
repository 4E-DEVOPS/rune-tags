package com.runetags.player;

import com.runetags.mention.NameNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.IconID;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.game.WorldService;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PlayerDirectoryTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private PartyService partyService;
    private WorldService worldService;
    private NameNormalizer normalizer;
    private PlayerDirectory playerDirectory;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        partyService =
                Mockito.mock(
                        PartyService.class);

        worldService =
                Mockito.mock(
                        WorldService.class);

        normalizer =
                new NameNormalizer();

        playerDirectory =
                new PlayerDirectory(
                        client,
                        partyService,
                        worldService,
                        normalizer);

        Mockito.when(
                        client.getWorld())
                .thenReturn(
                        301);
    }

    /*
     * TESTS
     */

    @Test
    public void nullBlankAndUnknownNamesAreNotFound()
    {
        Assert.assertFalse(
                playerDirectory.find(
                                null)
                        .isPresent());

        Assert.assertFalse(
                playerDirectory.find(
                                "")
                        .isPresent());

        Assert.assertFalse(
                playerDirectory.find(
                                "   ")
                        .isPresent());

        Assert.assertFalse(
                playerDirectory.find(
                                "definitelyunknown123")
                        .isPresent());
    }

    @Test
    public void nearbyPlayerIsAddedToDirectory()
    {
        final Player zezima =
                player(
                        "Zezima",
                        126);

        playerDirectory.addNearbyPlayer(
                zezima);

        Assert.assertEquals(
                1,
                playerDirectory.all()
                        .size());

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Zezima")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                "Zezima",
                identity.getCanonicalName());

        Assert.assertEquals(
                normalizer.comparisonKey(
                        "Zezima"),
                identity.getNormalizedName());

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

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.NEARBY));

        Assert.assertSame(
                zezima,
                identity.getNearbyPlayer());
    }

    @Test
    public void findUsesNormalizedSeparatorAndCaseIdentity()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        Assert.assertTrue(
                playerDirectory.find(
                                "FasT 07")
                        .isPresent());

        Assert.assertTrue(
                playerDirectory.find(
                                "fast_07")
                        .isPresent());

        Assert.assertTrue(
                playerDirectory.find(
                                "FAST-07")
                        .isPresent());

        Assert.assertTrue(
                playerDirectory.find(
                                "fast 07")
                        .isPresent());
    }

    @Test
    public void normalizedDuplicateDoesNotCreateSecondIdentity()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "santa_clause",
                        101));

        Assert.assertEquals(
                1,
                playerDirectory.all()
                        .size());

        Assert.assertTrue(
                playerDirectory.find(
                                "Santa-Clause")
                        .isPresent());
    }

    @Test
    public void normalizedDuplicatePreservesFirstCanonicalName()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "santa_clause",
                        101));

        final PlayerIdentity identity =
                playerDirectory.find(
                                "santa clause")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                "Santa Clause",
                identity.getCanonicalName());
    }

    @Test
    public void repeatedNearbyMergeRefreshesLiveFields()
    {
        final Player first =
                player(
                        "Party Hat",
                        90);

        final Player second =
                player(
                        "Party_Hat",
                        91);

        playerDirectory.addNearbyPlayer(
                first);

        playerDirectory.addNearbyPlayer(
                second);

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                1,
                playerDirectory.all()
                        .size());

        Assert.assertEquals(
                "Party Hat",
                identity.getCanonicalName());

        Assert.assertEquals(
                Integer.valueOf(
                        91),
                identity.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(
                        301),
                identity.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                identity.getOnlineState());

        Assert.assertSame(
                second,
                identity.getNearbyPlayer());

        Assert.assertEquals(
                1,
                identity.getSources()
                        .size());

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.NEARBY));
    }

    @Test
    public void allSortedLongestNameFirstOrdersByCanonicalLength()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "Zezima",
                        126));

        playerDirectory.addNearbyPlayer(
                player(
                        "Party Hat",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        final List<PlayerIdentity> sorted =
                playerDirectory.allSortedLongestNameFirst();

        Assert.assertEquals(
                4,
                sorted.size());

        Assert.assertEquals(
                "Santa Clause",
                sorted.get(0)
                        .getCanonicalName());

        Assert.assertEquals(
                "Party Hat",
                sorted.get(1)
                        .getCanonicalName());

        Assert.assertEquals(
                "Zezima",
                sorted.get(2)
                        .getCanonicalName());

        Assert.assertEquals(
                "Santa",
                sorted.get(3)
                        .getCanonicalName());
    }

    @Test
    public void sortedDirectoryDoesNotModifyUnderlyingInsertionOrder()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        final List<PlayerIdentity> sorted =
                playerDirectory.allSortedLongestNameFirst();

        Assert.assertEquals(
                "Santa Clause",
                sorted.get(0)
                        .getCanonicalName());

        final List<PlayerIdentity> insertionOrder =
                new ArrayList<>(
                        playerDirectory.all());

        Assert.assertEquals(
                "Santa",
                insertionOrder.get(0)
                        .getCanonicalName());

        Assert.assertEquals(
                "Santa Clause",
                insertionOrder.get(1)
                        .getCanonicalName());
    }

    @Test
    public void allReturnsUnmodifiableCollection()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Zezima",
                        126));

        final Collection<PlayerIdentity> identities =
                playerDirectory.all();

        try
        {
            identities.clear();

            Assert.fail(
                    "Expected PlayerDirectory.all() to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
        }

        Assert.assertTrue(
                playerDirectory.find(
                                "Zezima")
                        .isPresent());
    }

    @Test
    public void removeNearbyPlayerRemovesNearbyOnlyIdentity()
    {
        final Player santa =
                player(
                        "Santa",
                        100);

        playerDirectory.addNearbyPlayer(
                santa);

        Assert.assertTrue(
                playerDirectory.find(
                                "Santa")
                        .isPresent());

        playerDirectory.removeNearbyPlayer(
                santa);

        Assert.assertFalse(
                playerDirectory.find(
                                "Santa")
                        .isPresent());

        Assert.assertEquals(
                0,
                playerDirectory.all()
                        .size());
    }

    @Test
    public void removeNearbyPlayerUsesNormalizedIdentity()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        final Player alternateRepresentation =
                player(
                        "santa_clause",
                        100);

        playerDirectory.removeNearbyPlayer(
                alternateRepresentation);

        Assert.assertFalse(
                playerDirectory.find(
                                "Santa Clause")
                        .isPresent());
    }

    @Test
    public void nullAndBlankNearbyPlayersAreIgnored()
    {
        playerDirectory.addNearbyPlayer(
                null);

        playerDirectory.addNearbyPlayer(
                player(
                        "",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "   ",
                        100));

        Assert.assertTrue(
                playerDirectory.all()
                        .isEmpty());
    }

    @Test
    public void clearLiveStateRemovesCurrentIdentities()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Zezima",
                        126));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        Assert.assertEquals(
                2,
                playerDirectory.all()
                        .size());

        playerDirectory.clearLiveState();

        Assert.assertTrue(
                playerDirectory.all()
                        .isEmpty());

        Assert.assertFalse(
                playerDirectory.find(
                                "Zezima")
                        .isPresent());

        Assert.assertFalse(
                playerDirectory.find(
                                "Santa Clause")
                        .isPresent());
    }

    @Test
    public void clearRemovesCurrentIdentities()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        playerDirectory.addNearbyPlayer(
                player(
                        "Party Hat",
                        100));

        Assert.assertEquals(
                2,
                playerDirectory.all()
                        .size());

        playerDirectory.clear();

        Assert.assertTrue(
                playerDirectory.all()
                        .isEmpty());

        Assert.assertFalse(
                playerDirectory.find(
                                "FasT 07")
                        .isPresent());

        Assert.assertFalse(
                playerDirectory.find(
                                "Party Hat")
                        .isPresent());
    }

    @Test
    public void friendAndNearbySourcesMergeIntoSingleIdentity()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend santa =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        santa.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        santa.getWorld())
                .thenReturn(
                        0);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        santa
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        Assert.assertEquals(
                1,
                playerDirectory.all()
                        .size());

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                2,
                identity.getSources()
                        .size());

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.FRIEND));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.NEARBY));
    }

    @Test
    public void onlineNearbyStateOverridesOfflineFriendState()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend partyHat =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        partyHat.getName())
                .thenReturn(
                        "Party Hat");

        Mockito.when(
                        partyHat.getWorld())
                .thenReturn(
                        0);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        partyHat
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        playerDirectory.rebuild();

        final PlayerIdentity friendOnly =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                OnlineState.OFFLINE,
                friendOnly.getOnlineState());

        playerDirectory.addNearbyPlayer(
                player(
                        "Party Hat",
                        100));

        final PlayerIdentity merged =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                OnlineState.ONLINE,
                merged.getOnlineState());

        Assert.assertTrue(
                merged.getSources()
                        .contains(
                                PlayerSource.FRIEND));

        Assert.assertTrue(
                merged.getSources()
                        .contains(
                                PlayerSource.NEARBY));
    }

    @Test
    public void permanentAccountObservationUpdatesExistingIdentityImmediately()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Zezima",
                        126));

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "Zezima");

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Zezima")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                AccountType.IRONMAN,
                identity.getAccountType());
    }

    @Test
    public void permanentAccountObservationSurvivesClearLiveState()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "FasT 07");

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.clearLiveState();

        Assert.assertFalse(
                playerDirectory.find(
                                "FasT 07")
                        .isPresent());

        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());
    }

    @Test
    public void temporaryAccountObservationIsDiscardedByClearLiveState()
    {
        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.of(
                                WorldType.SEASONAL));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        playerDirectory.observeAccountType(
                IconID.LEAGUE.toString()
                        + "Santa");

        Assert.assertEquals(
                AccountType.LEAGUES,
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.clearLiveState();

        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.noneOf(
                                WorldType.class));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        Assert.assertEquals(
                AccountType.UNKNOWN,
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());
    }

    @Test
    public void permanentAccountTypeIsRestoredAfterTemporaryObservationIsCleared()
    {
        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.noneOf(
                                WorldType.class));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "Santa Clause");

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.of(
                                WorldType.SEASONAL));

        playerDirectory.observeAccountType(
                IconID.LEAGUE.toString()
                        + "Santa Clause");

        Assert.assertEquals(
                AccountType.LEAGUES,
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.clearLiveState();

        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.noneOf(
                                WorldType.class));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        100));

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());
    }

    @Test
    public void clearRemovesPermanentAccountObservation()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Party Hat",
                        100));

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "Party Hat");

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.clear();

        playerDirectory.addNearbyPlayer(
                player(
                        "Party Hat",
                        100));

        Assert.assertEquals(
                AccountType.UNKNOWN,
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());
    }

    @Test
    public void newerNativePermanentObservationReplacesOlderPermanentObservation()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Zezima",
                        126));

        playerDirectory.observeAccountType(
                IconID.HARDCORE_IRONMAN.toString()
                        + "Zezima");

        Assert.assertEquals(
                AccountType.HARDCORE,
                playerDirectory.find(
                                "Zezima")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "Zezima");

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "Zezima")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.observeAccountType(
                "Zezima");

        Assert.assertEquals(
                AccountType.NORMAL,
                playerDirectory.find(
                                "Zezima")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());
    }

    @Test
    public void moderatorObservationSurvivesSubsequentNearbyMerge()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        playerDirectory.observeAccountType(
                IconID.PLAYER_MODERATOR.toString()
                        + "FasT 07");

        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.addNearbyPlayer(
                player(
                        "fast_07",
                        125));

        final PlayerIdentity identity =
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                identity.getAccountType());

        Assert.assertEquals(
                Integer.valueOf(
                        125),
                identity.getCombatLevel());
    }

    @Test
    public void partyChannelWinsOverClanGuestClanAndFriendsChat()
    {
        final FriendsChatManager friendsChatManager =
                Mockito.mock(
                        FriendsChatManager.class);

        final FriendsChatMember friendsChatMember =
                Mockito.mock(
                        FriendsChatMember.class);

        Mockito.when(
                        friendsChatManager.getName())
                .thenReturn(
                        "Friends Chat");

        Mockito.when(
                        friendsChatMember.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        friendsChatMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendsChatManager.getMembers())
                .thenReturn(
                        new FriendsChatMember[]
                                {
                                        friendsChatMember
                                });

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        friendsChatManager);

        final ClanChannel guestClanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember guestClanMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        guestClanChannel.getName())
                .thenReturn(
                        "Guest Clan");

        Mockito.when(
                        guestClanMember.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        guestClanMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        guestClanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                guestClanMember));

        Mockito.when(
                        client.getGuestClanChannel())
                .thenReturn(
                        guestClanChannel);

        final ClanChannel clanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember clanMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        clanChannel.getName())
                .thenReturn(
                        "RuneTags Clan");

        Mockito.when(
                        clanMember.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        clanMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        clanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                clanMember));

        Mockito.when(
                        client.getClanChannel())
                .thenReturn(
                        clanChannel);

        final PartyMember partyMember =
                new PartyMember(
                        1L);

        partyMember.setDisplayName(
                "Santa");

        partyMember.setLoggedIn(
                true);

        Mockito.when(
                        partyService.isInParty())
                .thenReturn(
                        true);

        Mockito.when(
                        partyService.getPartyPassphrase())
                .thenReturn(
                        "runetags-test");

        Mockito.when(
                        partyService.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                partyMember));

        playerDirectory.rebuild();

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                4,
                identity.getSources()
                        .size());

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.FRIENDS_CHAT));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.GUEST_CLAN));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.CLAN));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.PARTY));

        Assert.assertEquals(
                PlayerSource.PARTY,
                identity.getChannelSource());

        Assert.assertEquals(
                "Party: runetags-test",
                identity.getChannelName());

        Assert.assertEquals(
                OnlineState.ONLINE,
                identity.getOnlineState());
    }

    @Test
    public void clanChannelWinsOverGuestClanAndFriendsChat()
    {
        final FriendsChatManager friendsChatManager =
                Mockito.mock(
                        FriendsChatManager.class);

        final FriendsChatMember friendsChatMember =
                Mockito.mock(
                        FriendsChatMember.class);

        Mockito.when(
                        friendsChatManager.getName())
                .thenReturn(
                        "Friends Chat");

        Mockito.when(
                        friendsChatMember.getName())
                .thenReturn(
                        "Santa Clause");

        Mockito.when(
                        friendsChatMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendsChatManager.getMembers())
                .thenReturn(
                        new FriendsChatMember[]
                                {
                                        friendsChatMember
                                });

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        friendsChatManager);

        final ClanChannel guestClanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember guestClanMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        guestClanChannel.getName())
                .thenReturn(
                        "Guest Clan");

        Mockito.when(
                        guestClanMember.getName())
                .thenReturn(
                        "Santa Clause");

        Mockito.when(
                        guestClanMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        guestClanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                guestClanMember));

        Mockito.when(
                        client.getGuestClanChannel())
                .thenReturn(
                        guestClanChannel);

        final ClanChannel clanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember clanMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        clanChannel.getName())
                .thenReturn(
                        "RuneTags Clan");

        Mockito.when(
                        clanMember.getName())
                .thenReturn(
                        "Santa Clause");

        Mockito.when(
                        clanMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        clanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                clanMember));

        Mockito.when(
                        client.getClanChannel())
                .thenReturn(
                        clanChannel);

        playerDirectory.rebuild();

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.FRIENDS_CHAT));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.GUEST_CLAN));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.CLAN));

        Assert.assertFalse(
                identity.getSources()
                        .contains(
                                PlayerSource.PARTY));

        Assert.assertEquals(
                PlayerSource.CLAN,
                identity.getChannelSource());

        Assert.assertEquals(
                "RuneTags Clan",
                identity.getChannelName());
    }

    @Test
    public void guestClanWinsOverFriendsChat()
    {
        final FriendsChatManager friendsChatManager =
                Mockito.mock(
                        FriendsChatManager.class);

        final FriendsChatMember friendsChatMember =
                Mockito.mock(
                        FriendsChatMember.class);

        Mockito.when(
                        friendsChatManager.getName())
                .thenReturn(
                        "Friends Chat");

        Mockito.when(
                        friendsChatMember.getName())
                .thenReturn(
                        "Party Hat");

        Mockito.when(
                        friendsChatMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendsChatManager.getMembers())
                .thenReturn(
                        new FriendsChatMember[]
                                {
                                        friendsChatMember
                                });

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        friendsChatManager);

        final ClanChannel guestClanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember guestClanMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        guestClanChannel.getName())
                .thenReturn(
                        "Guest Clan");

        Mockito.when(
                        guestClanMember.getName())
                .thenReturn(
                        "Party Hat");

        Mockito.when(
                        guestClanMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        guestClanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                guestClanMember));

        Mockito.when(
                        client.getGuestClanChannel())
                .thenReturn(
                        guestClanChannel);

        playerDirectory.rebuild();

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.FRIENDS_CHAT));

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.GUEST_CLAN));

        Assert.assertEquals(
                PlayerSource.GUEST_CLAN,
                identity.getChannelSource());

        Assert.assertEquals(
                "Guest Clan",
                identity.getChannelName());
    }

    @Test
    public void friendsChatIsUsedWhenNoHigherPriorityChannelExists()
    {
        final FriendsChatManager friendsChatManager =
                Mockito.mock(
                        FriendsChatManager.class);

        final FriendsChatMember friendsChatMember =
                Mockito.mock(
                        FriendsChatMember.class);

        Mockito.when(
                        friendsChatManager.getName())
                .thenReturn(
                        "Friends Chat");

        Mockito.when(
                        friendsChatMember.getName())
                .thenReturn(
                        "FasT 07");

        Mockito.when(
                        friendsChatMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendsChatManager.getMembers())
                .thenReturn(
                        new FriendsChatMember[]
                                {
                                        friendsChatMember
                                });

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        friendsChatManager);

        playerDirectory.rebuild();

        final PlayerIdentity identity =
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                PlayerSource.FRIENDS_CHAT,
                identity.getChannelSource());

        Assert.assertEquals(
                "Friends Chat",
                identity.getChannelName());

        Assert.assertEquals(
                OnlineState.ONLINE,
                identity.getOnlineState());
    }

    @Test
    public void liveClanChannelEnrichesSamePriorityClanSettingsIdentity()
    {
        final ClanSettings clanSettings =
                Mockito.mock(
                        ClanSettings.class);

        final ClanMember settingsMember =
                Mockito.mock(
                        ClanMember.class);

        Mockito.when(
                        clanSettings.getName())
                .thenReturn(
                        "Settings Clan");

        Mockito.when(
                        settingsMember.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        clanSettings.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                settingsMember));

        Mockito.when(
                        client.getClanSettings())
                .thenReturn(
                        clanSettings);

        final ClanChannel clanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember liveMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        clanChannel.getName())
                .thenReturn(
                        "Live Clan");

        Mockito.when(
                        liveMember.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        liveMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        clanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                liveMember));

        Mockito.when(
                        client.getClanChannel())
                .thenReturn(
                        clanChannel);

        playerDirectory.rebuild();

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Zezima")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                1,
                identity.getSources()
                        .size());

        Assert.assertTrue(
                identity.getSources()
                        .contains(
                                PlayerSource.CLAN));

        Assert.assertEquals(
                PlayerSource.CLAN,
                identity.getChannelSource());

        Assert.assertEquals(
                "Live Clan",
                identity.getChannelName());

        Assert.assertEquals(
                Integer.valueOf(
                        301),
                identity.getWorld());

        Assert.assertEquals(
                OnlineState.ONLINE,
                identity.getOnlineState());
    }

    @Test
    public void partyWithoutPassphraseUsesGenericPartyChannelName()
    {
        final PartyMember partyMember =
                new PartyMember(
                        1L);

        partyMember.setDisplayName(
                "Santa");

        partyMember.setLoggedIn(
                true);

        Mockito.when(
                        partyService.isInParty())
                .thenReturn(
                        true);

        Mockito.when(
                        partyService.getPartyPassphrase())
                .thenReturn(
                        null);

        Mockito.when(
                        partyService.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                partyMember));

        playerDirectory.rebuild();

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                PlayerSource.PARTY,
                identity.getChannelSource());

        Assert.assertEquals(
                "Party",
                identity.getChannelName());
    }

    @Test
    public void refreshPlayerOnlyRebuildsRequestedIdentity()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend santa =
                Mockito.mock(
                        Friend.class);

        final Friend partyHat =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        santa.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        santa.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        partyHat.getName())
                .thenReturn(
                        "Party Hat");

        Mockito.when(
                        partyHat.getWorld())
                .thenReturn(
                        302);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        santa,
                                        partyHat
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        playerDirectory.rebuild();

        final PlayerIdentity partyHatBefore =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                2,
                playerDirectory.all()
                        .size());

        Mockito.when(
                        santa.getWorld())
                .thenReturn(
                        303);

        playerDirectory.refreshPlayer(
                "Santa");

        Assert.assertEquals(
                2,
                playerDirectory.all()
                        .size());

        final PlayerIdentity refreshedSanta =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        final PlayerIdentity partyHatAfter =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                Integer.valueOf(
                        303),
                refreshedSanta.getWorld());

        Assert.assertSame(
                partyHatBefore,
                partyHatAfter);

        Assert.assertEquals(
                Integer.valueOf(
                        302),
                partyHatAfter.getWorld());
    }

    @Test
    public void refreshPlayerRemovesStaleSource()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend santa =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        santa.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        santa.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        santa
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        final FriendsChatManager friendsChatManager =
                Mockito.mock(
                        FriendsChatManager.class);

        final FriendsChatMember friendsChatMember =
                Mockito.mock(
                        FriendsChatMember.class);

        Mockito.when(
                        friendsChatManager.getName())
                .thenReturn(
                        "Friends Chat");

        Mockito.when(
                        friendsChatMember.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        friendsChatMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendsChatManager.getMembers())
                .thenReturn(
                        new FriendsChatMember[]
                                {
                                        friendsChatMember
                                });

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        friendsChatManager);

        playerDirectory.rebuild();

        final PlayerIdentity before =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertTrue(
                before.getSources()
                        .contains(
                                PlayerSource.FRIEND));

        Assert.assertTrue(
                before.getSources()
                        .contains(
                                PlayerSource.FRIENDS_CHAT));

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        null);

        playerDirectory.refreshPlayer(
                "Santa");

        final PlayerIdentity after =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                1,
                after.getSources()
                        .size());

        Assert.assertTrue(
                after.getSources()
                        .contains(
                                PlayerSource.FRIEND));

        Assert.assertFalse(
                after.getSources()
                        .contains(
                                PlayerSource.FRIENDS_CHAT));

        Assert.assertNull(
                after.getChannelSource());

        Assert.assertNull(
                after.getChannelName());
    }

    @Test
    public void removeNearbyPlayerPreservesOtherSources()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend partyHat =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        partyHat.getName())
                .thenReturn(
                        "Party Hat");

        Mockito.when(
                        partyHat.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        partyHat
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        playerDirectory.rebuild();

        final Player nearbyPlayer =
                player(
                        "Party Hat",
                        100);

        playerDirectory.addNearbyPlayer(
                nearbyPlayer);

        final PlayerIdentity merged =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertTrue(
                merged.getSources()
                        .contains(
                                PlayerSource.FRIEND));

        Assert.assertTrue(
                merged.getSources()
                        .contains(
                                PlayerSource.NEARBY));

        Assert.assertTrue(
                merged.isNearby());

        playerDirectory.removeNearbyPlayer(
                nearbyPlayer);

        final PlayerIdentity afterRemoval =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                1,
                afterRemoval.getSources()
                        .size());

        Assert.assertTrue(
                afterRemoval.getSources()
                        .contains(
                                PlayerSource.FRIEND));

        Assert.assertFalse(
                afterRemoval.getSources()
                        .contains(
                                PlayerSource.NEARBY));

        Assert.assertFalse(
                afterRemoval.isNearby());
    }

    @Test
    public void refreshPlayerPreservesDurableAccountObservation()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend fast07 =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        fast07.getName())
                .thenReturn(
                        "FasT 07");

        Mockito.when(
                        fast07.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        fast07
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        playerDirectory.rebuild();

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "FasT 07");

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        playerDirectory.refreshPlayer(
                "fast_07");

        final PlayerIdentity refreshed =
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                AccountType.IRONMAN,
                refreshed.getAccountType());

        Assert.assertTrue(
                refreshed.getSources()
                        .contains(
                                PlayerSource.FRIEND));
    }

    @Test
    public void blankRefreshDoesNotDisturbDirectory()
    {
        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa Clause",
                        110));

        final PlayerIdentity santaBefore =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        final PlayerIdentity santaClauseBefore =
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new);

        playerDirectory.refreshPlayer(
                null);

        playerDirectory.refreshPlayer(
                "");

        playerDirectory.refreshPlayer(
                "   ");

        Assert.assertEquals(
                2,
                playerDirectory.all()
                        .size());

        Assert.assertSame(
                santaBefore,
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new));

        Assert.assertSame(
                santaClauseBefore,
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new));
    }

    @Test
    public void refreshPlayerReplacesStaleChannelWithCurrentHigherPrioritySource()
    {
        final FriendsChatManager friendsChatManager =
                Mockito.mock(
                        FriendsChatManager.class);

        final FriendsChatMember friendsChatMember =
                Mockito.mock(
                        FriendsChatMember.class);

        Mockito.when(
                        friendsChatManager.getName())
                .thenReturn(
                        "Friends Chat");

        Mockito.when(
                        friendsChatMember.getName())
                .thenReturn(
                        "Santa Clause");

        Mockito.when(
                        friendsChatMember.getWorld())
                .thenReturn(
                        301);

        Mockito.when(
                        friendsChatManager.getMembers())
                .thenReturn(
                        new FriendsChatMember[]
                                {
                                        friendsChatMember
                                });

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        friendsChatManager);

        playerDirectory.rebuild();

        final PlayerIdentity before =
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                PlayerSource.FRIENDS_CHAT,
                before.getChannelSource());

        Assert.assertEquals(
                "Friends Chat",
                before.getChannelName());

        final ClanChannel guestClanChannel =
                Mockito.mock(
                        ClanChannel.class);

        final ClanChannelMember guestClanMember =
                Mockito.mock(
                        ClanChannelMember.class);

        Mockito.when(
                        guestClanChannel.getName())
                .thenReturn(
                        "Guest Clan");

        Mockito.when(
                        guestClanMember.getName())
                .thenReturn(
                        "Santa Clause");

        Mockito.when(
                        guestClanMember.getWorld())
                .thenReturn(
                        302);

        Mockito.when(
                        guestClanChannel.getMembers())
                .thenReturn(
                        Collections.singletonList(
                                guestClanMember));

        Mockito.when(
                        client.getGuestClanChannel())
                .thenReturn(
                        guestClanChannel);

        Mockito.when(
                        client.getFriendsChatManager())
                .thenReturn(
                        null);

        playerDirectory.refreshPlayer(
                "Santa Clause");

        final PlayerIdentity after =
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertFalse(
                after.getSources()
                        .contains(
                                PlayerSource.FRIENDS_CHAT));

        Assert.assertTrue(
                after.getSources()
                        .contains(
                                PlayerSource.GUEST_CLAN));

        Assert.assertEquals(
                PlayerSource.GUEST_CLAN,
                after.getChannelSource());

        Assert.assertEquals(
                "Guest Clan",
                after.getChannelName());

        Assert.assertEquals(
                Integer.valueOf(
                        302),
                after.getWorld());
    }

    @Test
    public void nearbyPlayerRetainsLastKnownWorldPoint()
    {
        final WorldPoint worldPoint =
                new WorldPoint(
                        3200,
                        3201,
                        0);

        final Player santa =
                player(
                        "Santa",
                        100,
                        worldPoint);

        playerDirectory.addNearbyPlayer(
                santa);

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                worldPoint,
                identity.getLastKnownWorldPoint());

        Assert.assertSame(
                santa,
                identity.getNearbyPlayer());
    }

    @Test
    public void repeatedNearbyMergeRefreshesLastKnownWorldPoint()
    {
        final WorldPoint firstPoint =
                new WorldPoint(
                        3200,
                        3200,
                        0);

        final WorldPoint secondPoint =
                new WorldPoint(
                        3210,
                        3220,
                        1);

        playerDirectory.addNearbyPlayer(
                player(
                        "Party Hat",
                        100,
                        firstPoint));

        final Player second =
                player(
                        "party_hat",
                        101,
                        secondPoint);

        playerDirectory.addNearbyPlayer(
                second);

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Party Hat")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                secondPoint,
                identity.getLastKnownWorldPoint());

        Assert.assertSame(
                second,
                identity.getNearbyPlayer());
    }

    @Test
    public void removingNearbyContributionClearsNearbyOwnedFields()
    {
        final FriendContainer friendContainer =
                Mockito.mock(
                        FriendContainer.class);

        final Friend santa =
                Mockito.mock(
                        Friend.class);

        Mockito.when(
                        santa.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        santa.getWorld())
                .thenReturn(
                        302);

        Mockito.when(
                        friendContainer.getMembers())
                .thenReturn(
                        new Friend[]
                                {
                                        santa
                                });

        Mockito.when(
                        client.getFriendContainer())
                .thenReturn(
                        friendContainer);

        playerDirectory.rebuild();

        final WorldPoint worldPoint =
                new WorldPoint(
                        3200,
                        3200,
                        0);

        final Player nearby =
                player(
                        "Santa",
                        100,
                        worldPoint);

        playerDirectory.addNearbyPlayer(
                nearby);

        final PlayerIdentity merged =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                Integer.valueOf(
                        100),
                merged.getCombatLevel());

        Assert.assertSame(
                nearby,
                merged.getNearbyPlayer());

        Assert.assertEquals(
                worldPoint,
                merged.getLastKnownWorldPoint());

        playerDirectory.removeNearbyPlayer(
                nearby);

        final PlayerIdentity afterRemoval =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                Collections.singleton(
                        PlayerSource.FRIEND),
                afterRemoval.getSources());

        Assert.assertNull(
                afterRemoval.getCombatLevel());

        Assert.assertNull(
                afterRemoval.getNearbyPlayer());

        Assert.assertNull(
                afterRemoval.getLastKnownWorldPoint());

        Assert.assertEquals(
                Integer.valueOf(
                        302),
                afterRemoval.getWorld());
    }

    @Test
    public void seasonalCurrentWorldClassifiesNearbyPlayerAsLeagues()
    {
        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.of(
                                WorldType.SEASONAL));

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                AccountType.LEAGUES,
                identity.getAccountType());
    }

    @Test
    public void deadmanCurrentWorldClassifiesNearbyPlayerAsDeadman()
    {
        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.of(
                                WorldType.DEADMAN));

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                AccountType.DEADMAN,
                identity.getAccountType());
    }

    @Test
    public void deadmanTakesPriorityWhenWorldIsAlsoSeasonal()
    {
        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.of(
                                WorldType.DEADMAN,
                                WorldType.SEASONAL));

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "Santa",
                        100));

        final PlayerIdentity identity =
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new);

        Assert.assertEquals(
                AccountType.DEADMAN,
                identity.getAccountType());
    }

    @Test
    public void regularWorldRestoresDurablePermanentAccountType()
    {
        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.noneOf(
                                WorldType.class));

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        playerDirectory.observeAccountType(
                IconID.IRONMAN.toString()
                        + "FasT 07");

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.of(
                                WorldType.SEASONAL));

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        Assert.assertEquals(
                AccountType.LEAGUES,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());

        Mockito.when(
                        client.getWorldType())
                .thenReturn(
                        EnumSet.noneOf(
                                WorldType.class));

        playerDirectory.rebuild();

        playerDirectory.addNearbyPlayer(
                player(
                        "FasT 07",
                        126));

        Assert.assertEquals(
                AccountType.IRONMAN,
                playerDirectory.find(
                                "FasT 07")
                        .orElseThrow(
                                AssertionError::new)
                        .getAccountType());
    }

    @Test
    public void refreshPlayerScansOnlyRequestedNearbyIdentity()
    {
        final Player santa =
                player(
                        "Santa",
                        100);

        final Player santaClause =
                player(
                        "Santa Clause",
                        110);

        final WorldView worldView =
                worldView(
                        santa,
                        santaClause);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        playerDirectory.rebuild();

        final PlayerIdentity santaClauseBefore =
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new);

        playerDirectory.refreshPlayer(
                "Santa");

        Assert.assertEquals(
                2,
                playerDirectory.all()
                        .size());

        Assert.assertSame(
                santaClauseBefore,
                playerDirectory.find(
                                "Santa Clause")
                        .orElseThrow(
                                AssertionError::new));

        Assert.assertTrue(
                playerDirectory.find(
                                "Santa")
                        .orElseThrow(
                                AssertionError::new)
                        .getSources()
                        .contains(
                                PlayerSource.NEARBY));
    }

    /*
     * HELPERS
     */

    private static Player player(
            String name,
            int combatLevel)
    {
        return player(
                name,
                combatLevel,
                null);
    }

    private static Player player(
            String name,
            int combatLevel,
            WorldPoint worldPoint)
    {
        final Player player =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        player.getName())
                .thenReturn(
                        name);

        Mockito.when(
                        player.getCombatLevel())
                .thenReturn(
                        combatLevel);

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        worldPoint);

        return player;
    }

    private static WorldView worldView(
            Player... players)
    {
        final WorldView worldView =
                Mockito.mock(
                        WorldView.class);

        @SuppressWarnings("unchecked")
        final IndexedObjectSet<Player> playerSet =
                Mockito.mock(
                        IndexedObjectSet.class);

        final List<Player> playerList =
                players == null
                        ? Collections.emptyList()
                        : java.util.Arrays.asList(
                        players);

        Mockito.when(
                        playerSet.iterator())
                .thenAnswer(
                        invocation ->
                                playerList.iterator());

        Mockito.doReturn(
                        playerSet)
                .when(
                        worldView)
                .players();

        return worldView;
    }

    /*
     * PERFORMANCE
     */

    private static long warmUp(
            PerformanceHarness harness)
    {
        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            harness.directory.rebuild();

            checksum +=
                    harness.directory.all()
                            .size();

            harness.directory.refreshPlayer(
                    "Santa");

            checksum +=
                    harness.directory.find(
                                    "Santa")
                            .isPresent()
                            ? 1L
                            : 0L;
        }

        return checksum;
    }

    @Test
    public void performanceDirectoryRebuildAndRefresh()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final PerformanceHarness harness =
                new PerformanceHarness();

        long checksum =
                warmUp(
                        harness);

        long started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            harness.directory.rebuild();

            checksum +=
                    harness.directory.all()
                            .size();
        }

        final long rebuildElapsed =
                System.nanoTime()
                        - started;

        started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            harness.directory.refreshPlayer(
                    "Santa");

            checksum +=
                    harness.directory.find(
                                    "Santa")
                            .isPresent()
                            ? 1L
                            : 0L;
        }

        final long refreshElapsed =
                System.nanoTime()
                        - started;

        Assert.assertTrue(
                checksum > 0L);

        final double rebuildTotalMs =
                nanosToMilliseconds(
                        rebuildElapsed);

        final double refreshTotalMs =
                nanosToMilliseconds(
                        refreshElapsed);

        System.out.printf(
                "[RuneTags][PlayerDirectoryTest] Performance= "
                        + "RebuildNearby4: %.3fms (%.6fms) | "
                        + "RefreshNearby1: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                rebuildTotalMs,
                rebuildTotalMs
                        / PERFORMANCE_ITERATIONS,
                refreshTotalMs,
                refreshTotalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);

        harness.close();
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos
                / 1_000_000.0;
    }

    private static final class PerformanceHarness
    {
        private final Client client;
        private final PartyService partyService;
        private final WorldService worldService;
        private final PlayerDirectory directory;

        private PerformanceHarness()
        {
            client =
                    Mockito.mock(
                            Client.class);

            partyService =
                    Mockito.mock(
                            PartyService.class);

            worldService =
                    Mockito.mock(
                            WorldService.class);

            Mockito.when(
                            client.getWorld())
                    .thenReturn(
                            301);

            Mockito.when(
                            client.getWorldType())
                    .thenReturn(
                            EnumSet.noneOf(
                                    WorldType.class));

            final Player zezima =
                    player(
                            "Zezima",
                            126);

            final Player fast07 =
                    player(
                            "FasT 07",
                            126);

            final Player santa =
                    player(
                            "Santa",
                            100);

            final Player santaClause =
                    player(
                            "Santa Clause",
                            110);

            final WorldView worldView =
                    worldView(
                            zezima,
                            fast07,
                            santa,
                            santaClause);

            Mockito.when(
                            client.getTopLevelWorldView())
                    .thenReturn(
                            worldView);

            directory =
                    new PlayerDirectory(
                            client,
                            partyService,
                            worldService,
                            new NameNormalizer());
        }

        private void close()
        {
            directory.clear();
        }
    }
}