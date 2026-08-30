package com.runetags.player;

import com.runetags.mention.NameNormalizer;
import com.runetags.model.OnlineState;
import com.runetags.model.PlayerIdentity;
import com.runetags.model.PlayerSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;

public class PlayerDirectory {
    private final Client client;
    private final PartyService partyService;
    private final NameNormalizer normalizer;
    private final Map<String, PlayerIdentity> identities = new LinkedHashMap<>();

    public PlayerDirectory(Client client, PartyService partyService, NameNormalizer normalizer) {
        this.client = client;
        this.partyService = partyService;
        this.normalizer = normalizer;
    }

    public void rebuild() {
        identities.clear();
        addFriends();
        addClan();
        addGuestClan();
        addFriendsChat();
        addParty();
        addNearbyPlayers();
    }

    public Optional<PlayerIdentity> find(String name) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(identities.get(normalizer.comparisonKey(name)));
    }

    public Collection<PlayerIdentity> all() {
        return Collections.unmodifiableCollection(identities.values());
    }

    public List<PlayerIdentity> allSortedLongestNameFirst() {
        final List<PlayerIdentity> players = new ArrayList<>(identities.values());
        players.sort((a, b) -> Integer.compare(
                b.getCanonicalName().length(), a.getCanonicalName().length()));
        return players;
    }

    public void clear() {
        identities.clear();
    }

    private void addFriends() {
        final FriendContainer container = client.getFriendContainer();
        if (container == null || container.getMembers() == null) {
            return;
        }

        for (Friend friend : container.getMembers()) {
            if (friend == null || isBlank(friend.getName())) {
                continue;
            }

            final int world = friend.getWorld();
            mergeIdentity(friend.getName(), PlayerSource.FRIEND,
                    null,
                    world > 0 ? world : null,
                    world > 0 ? OnlineState.ONLINE : OnlineState.OFFLINE,
                    null, null, null, null);
        }
    }

    private void addClan() {
        final ClanSettings settings = client.getClanSettings();
        final ClanChannel channel = client.getClanChannel();

        if (settings != null && settings.getMembers() != null) {
            for (ClanMember member : settings.getMembers()) {
                if (member == null || isBlank(member.getName())) {
                    continue;
                }

                String rankName = null;
                if (member.getRank() != null) {
                    final ClanTitle title = settings.titleForRank(member.getRank());
                    rankName = title != null ? title.getName() : member.getRank().toString();
                }

                mergeIdentity(member.getName(), PlayerSource.CLAN,
                        null, null, OnlineState.UNKNOWN,
                        settings.getName(), rankName, null, null);
            }
        }

        if (channel != null && channel.getMembers() != null) {
            for (ClanChannelMember member : channel.getMembers()) {
                if (member == null || isBlank(member.getName())) {
                    continue;
                }

                String rankName = null;
                if (member.getRank() != null) {
                    if (settings != null) {
                        final ClanTitle title = settings.titleForRank(member.getRank());
                        rankName = title != null ? title.getName() : member.getRank().toString();
                    } else {
                        rankName = member.getRank().toString();
                    }
                }

                final int world = member.getWorld();
                mergeIdentity(member.getName(), PlayerSource.CLAN,
                        null,
                        world > 0 ? world : null,
                        world > 0 ? OnlineState.ONLINE : OnlineState.UNKNOWN,
                        !isBlank(channel.getName()) ? channel.getName() : null,
                        rankName,
                        null,
                        null);
            }
        }
    }

    private void addGuestClan() {
        final ClanSettings settings =
                client.getGuestClanSettings();

        final ClanChannel channel =
                client.getGuestClanChannel();

        /*
         * Guest clan settings can provide identity/clan metadata even where
         * current-world information is unavailable.
         */
        if (settings != null && settings.getMembers() != null) {
            for (ClanMember member : settings.getMembers()) {
                if (member == null || isBlank(member.getName())) {
                    continue;
                }

                String rankName = null;

                if (member.getRank() != null) {
                    final ClanTitle title =
                            settings.titleForRank(member.getRank());

                    rankName =
                            title != null
                                    ? title.getName()
                                    : member.getRank().toString();
                }

                mergeIdentity(
                        member.getName(),
                        PlayerSource.GUEST_CLAN,
                        null,
                        null,
                        OnlineState.UNKNOWN,
                        settings.getName(),
                        rankName,
                        null,
                        null);
            }
        }

        /*
         * The active guest clan channel is the authoritative source for currently
         * visible guest-clan members and their worlds.
         */
        if (channel != null && channel.getMembers() != null) {
            for (ClanChannelMember member : channel.getMembers()) {
                if (member == null || isBlank(member.getName())) {
                    continue;
                }

                String rankName = null;

                if (member.getRank() != null) {
                    if (settings != null) {
                        final ClanTitle title =
                                settings.titleForRank(member.getRank());

                        rankName =
                                title != null
                                        ? title.getName()
                                        : member.getRank().toString();
                    } else {
                        rankName =
                                member.getRank().toString();
                    }
                }

                final int world =
                        member.getWorld();

                mergeIdentity(
                        member.getName(),
                        PlayerSource.GUEST_CLAN,
                        null,
                        world > 0 ? world : null,
                        world > 0
                                ? OnlineState.ONLINE
                                : OnlineState.UNKNOWN,
                        !isBlank(channel.getName())
                                ? channel.getName()
                                : settings != null
                                ? settings.getName()
                                : null,
                        rankName,
                        null,
                        null);
            }
        }
    }

    private void addFriendsChat() {
        final FriendsChatManager manager = client.getFriendsChatManager();

        if (manager == null || manager.getMembers() == null) {
            return;
        }

        final String channelName =
                !isBlank(manager.getName())
                        ? manager.getName()
                        : "Friends Chat";

        for (FriendsChatMember member : manager.getMembers()) {
            if (member == null || isBlank(member.getName())) {
                continue;
            }

            final int world = member.getWorld();

            final String rankName =
                    member.getRank() != null
                            ? member.getRank().toString()
                            : null;

            mergeIdentity(
                    member.getName(),
                    PlayerSource.FRIENDS_CHAT,
                    null,
                    world > 0 ? world : null,
                    world > 0
                            ? OnlineState.ONLINE
                            : OnlineState.UNKNOWN,
                    channelName,
                    rankName,
                    null,
                    null);
        }
    }

    private String partyChannelName()
    {
        if (partyService == null || !partyService.isInParty())
        {
            return "Party";
        }

        final String partyPassphrase =
                partyService.getPartyPassphrase();

        return !isBlank(partyPassphrase)
                ? "Party: " + partyPassphrase
                : "Party";
    }

    private void addParty() {
        if (partyService == null || !partyService.isInParty()) {
            return;
        }

        for (PartyMember member : partyService.getMembers()) {
            if (member == null || isBlank(member.getDisplayName())) {
                continue;
            }

            mergeIdentity(
                    member.getDisplayName(),
                    PlayerSource.PARTY,
                    null,
                    null,
                    member.isLoggedIn()
                            ? OnlineState.ONLINE
                            : OnlineState.UNKNOWN,
                    partyChannelName(),
                    null,
                    null,
                    null);
        }
    }

    private void addNearbyPlayers() {
        final WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            return;
        }

        for (Player player : worldView.players()) {
            if (player == null || isBlank(player.getName())) {
                continue;
            }

            mergeIdentity(player.getName(), PlayerSource.NEARBY,
                    player.getCombatLevel(), client.getWorld(), OnlineState.ONLINE,
                    null, null, player, player.getWorldLocation());
        }
    }

    private static int channelPriority(PlayerSource source)
    {
        if (source == null)
        {
            return 0;
        }

        switch (source)
        {
            case PARTY:
                return 4;

            case CLAN:
                return 3;

            case GUEST_CLAN:
                return 2;

            case FRIENDS_CHAT:
                return 1;

            default:
                return 0;
        }
    }

    private void mergeIdentity(
            String rawName,
            PlayerSource source,
            Integer combatLevel,
            Integer world,
            OnlineState onlineState,
            String channelName,
            String channelRank,
            Player nearbyPlayer,
            WorldPoint worldPoint)
    {
        final String canonicalName = normalizer.canonicalize(rawName);
        final String key = normalizer.comparisonKey(canonicalName);

        if (key.isEmpty())
        {
            return;
        }

        final PlayerIdentity existing = identities.get(key);

        final Set<PlayerSource> sources = existing == null
                ? EnumSet.of(source)
                : EnumSet.copyOf(existing.getSources());

        sources.add(source);

        /*
         * Preserve the strongest known online state.
         *
         * ONLINE wins over UNKNOWN/OFFLINE because another directory source may
         * have authoritative live-world information for the same player.
         */
        OnlineState mergedState = onlineState;

        if (existing != null)
        {
            if (existing.getOnlineState() == OnlineState.ONLINE)
            {
                mergedState = OnlineState.ONLINE;
            }
            else if (mergedState == null
                    || mergedState == OnlineState.UNKNOWN)
            {
                mergedState = existing.getOnlineState();
            }
        }

        if (mergedState == null)
        {
            mergedState = OnlineState.UNKNOWN;
        }

        /*
         * Select the highest-priority shared channel:
         *
         * PARTY > CLAN > GUEST_CLAN > FRIENDS_CHAT
         *
         * Equal priority is allowed so a later/live representation of the same
         * channel can enrich metadata supplied by its earlier settings pass.
         */
        String mergedChannelName =
                existing != null
                        ? existing.getChannelName()
                        : null;

        String mergedChannelRank =
                existing != null
                        ? existing.getChannelRank()
                        : null;

        PlayerSource mergedChannelSource =
                existing != null
                        ? existing.getChannelSource()
                        : null;

        final int incomingPriority =
                channelPriority(source);

        final int existingPriority =
                channelPriority(mergedChannelSource);

        if (incomingPriority > 0
                && incomingPriority >= existingPriority)
        {
            mergedChannelSource = source;

            if (!isBlank(channelName))
            {
                mergedChannelName = channelName;
            }
            else if (incomingPriority > existingPriority)
            {
                mergedChannelName = null;
            }

            if (!isBlank(channelRank))
            {
                mergedChannelRank = channelRank;
            }
            else if (incomingPriority > existingPriority)
            {
                mergedChannelRank = null;
            }
        }

        identities.put(
                key,
                PlayerIdentity.builder()
                        .canonicalName(
                                existing != null
                                        ? existing.getCanonicalName()
                                        : canonicalName)
                        .normalizedName(key)
                        .sources(Collections.unmodifiableSet(sources))
                        .combatLevel(
                                combatLevel != null
                                        ? combatLevel
                                        : existing == null
                                        ? null
                                        : existing.getCombatLevel())
                        .world(
                                world != null
                                        ? world
                                        : existing == null
                                        ? null
                                        : existing.getWorld())
                        .onlineState(mergedState)
                        .channelName(mergedChannelName)
                        .channelRank(mergedChannelRank)
                        .channelSource(mergedChannelSource)
                        .nearbyPlayer(
                                nearbyPlayer != null
                                        ? nearbyPlayer
                                        : existing == null
                                        ? null
                                        : existing.getNearbyPlayer())
                        .lastKnownWorldPoint(
                                worldPoint != null
                                        ? worldPoint
                                        : existing == null
                                        ? null
                                        : existing.getLastKnownWorldPoint())
                        .build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
