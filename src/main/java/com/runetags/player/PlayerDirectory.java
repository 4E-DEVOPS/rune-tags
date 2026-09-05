package com.runetags.player;

import com.runetags.mention.NameNormalizer;
import com.runetags.model.OnlineState;
import com.runetags.model.PlayerAccountType;
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
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.WorldService;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

public class PlayerDirectory {
    private final Client client;
    private final PartyService partyService;
    private final WorldService worldService;
    private final NameNormalizer normalizer;

    private final Map<String, PlayerAccountType> observedAccountTypes =
            new LinkedHashMap<>();

    private final Map<String, PlayerAccountType> observedPermanentAccountTypes =
            new LinkedHashMap<>();

    private final Map<String, PlayerIdentity> identities =
            new LinkedHashMap<>();

    private final Map<Integer, EnumSet<WorldType>> worldTypesById =
            new LinkedHashMap<>();

    public PlayerDirectory(
            Client client,
            PartyService partyService,
            WorldService worldService,
            NameNormalizer normalizer)
    {
        this.client = client;
        this.partyService = partyService;
        this.worldService = worldService;
        this.normalizer = normalizer;
    }

    public void rebuild()
    {
        identities.clear();

        refreshWorldTypes();

        addFriends(null);
        addClan(null);
        addGuestClan(null);
        addFriendsChat(null);
        addParty(null);
        addNearbyPlayers(null);
    }

    /**
     * Refresh the current live identity for one player without rebuilding the
     * complete directory.
     *
     * The existing entry is removed first so sources which are no longer valid
     * cannot survive from an older snapshot. The player is then re-evaluated
     * against the same sources and merge rules used by a full rebuild.
     *
     * Passing a specific player here does not disturb any other identities in the
     * directory. Full-directory discovery remains available through rebuild().
     */
    public void refreshPlayer(String playerName)
    {
        if (isBlank(playerName))
        {
            return;
        }

        final String targetKey =
                normalizer.comparisonKey(
                        playerName);

        if (targetKey.isEmpty())
        {
            return;
        }

        /*
         * Start this player's live identity from a clean state.
         *
         * Durable account observations are stored separately and are therefore
         * preserved while Friends / Clan / Party / Nearby state is reconstructed.
         */
        identities.remove(targetKey);

        addFriends(targetKey);
        addClan(targetKey);
        addGuestClan(targetKey);
        addFriendsChat(targetKey);
        addParty(targetKey);
        addNearbyPlayers(targetKey);
    }

    public void observeAccountType(
            String rawPlayerName)
    {
        if (isBlank(rawPlayerName))
        {
            return;
        }

        final String plainName =
                Text.removeTags(rawPlayerName);

        final String key =
                normalizer.comparisonKey(
                        plainName);

        if (key.isEmpty())
        {
            return;
        }

        final boolean leaguesWorld =
                client.getWorldType() != null
                        && client.getWorldType().contains(
                        WorldType.SEASONAL);

        final PlayerAccountType observedType =
                PlayerAccountType.fromChatName(
                        rawPlayerName,
                        leaguesWorld);

        /*
         * A genuine native chat-name observation is authoritative for the
         * player's current displayed classification.
         *
         * This deliberately allows transitions such as:
         * HARDCORE -> IRONMAN
         * IRONMAN  -> NORMAL
         */
        if (observedType.isPermanentAccountType())
        {
            observedPermanentAccountTypes.put(key, observedType);
        }

        observedAccountTypes.put(key, observedType);

        /*
         * Update the current in-memory identity immediately as well.
         *
         * The observation cache ensures this value survives the next normal
         * PlayerDirectory.rebuild().
         */
        final PlayerIdentity existing = identities.get(key);

        if (existing != null)
        {
            identities.put(
                    key,
                    existing.toBuilder()
                            .accountType(observedType)
                            .build());
        }
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

    /**
     * Clear live/session-derived player state while preserving durable account
     * observations learned from native chat.
     *
     * RuneScape retains chat history across world hops and logout/login cycles,
     * so RuneTags should retain the corresponding account knowledge as well.
     *
     * Temporary world-mode classifications are deliberately discarded because
     * DEADMAN / LEAGUES must be re-established from the player's current world.
     */
    public void clearLiveState()
    {
        identities.clear();
        worldTypesById.clear();

        observedAccountTypes.entrySet()
                .removeIf(entry ->
                        entry.getValue() != null
                                && entry.getValue().isTemporary());
    }

    /**
     * Completely clear all PlayerDirectory state.
     *
     * Reserved for plugin shutdown or another true RuneTags runtime reset.
     */
    public void clear()
    {
        identities.clear();
        observedAccountTypes.clear();
        observedPermanentAccountTypes.clear();
        worldTypesById.clear();
    }

    /**
     * Refresh the RuneLite world-number -> WorldType mapping.
     *
     * This allows temporary account classifications such as Leagues and
     * Deadman to follow a player's actual world without relying solely on
     * another chat message being observed.
     */
    private void refreshWorldTypes()
    {
        worldTypesById.clear();

        /*
         * The client's own current world is always authoritative and does not
         * require the external world list.
         */
        if (client.getWorld() > 0
                && client.getWorldType() != null)
        {
            worldTypesById.put(
                    client.getWorld(),
                    EnumSet.copyOf(
                            client.getWorldType()));
        }

        if (worldService == null)
        {
            return;
        }

        final WorldResult worldResult =
                worldService.getWorlds();

        if (worldResult == null
                || worldResult.getWorlds() == null)
        {
            return;
        }

        for (World world : worldResult.getWorlds())
        {
            if (world == null
                    || world.getTypes() == null)
            {
                continue;
            }

            worldTypesById.put(
                    world.getId(),
                    WorldUtil.toWorldTypes(
                            world.getTypes()));
        }
    }

    /**
     * Resolve a temporary account classification from the player's world.
     *
     * DEADMAN is deliberately checked before SEASONAL so that a seasonal
     * Deadman world cannot accidentally be classified as Leagues if both
     * world flags are ever present.
     */
    private PlayerAccountType worldAccountType(
            Integer world)
    {
        if (world == null
                || world <= 0)
        {
            return PlayerAccountType.UNKNOWN;
        }

        final Set<WorldType> worldTypes =
                worldTypesById.get(
                        world);

        if (worldTypes == null
                || worldTypes.isEmpty())
        {
            return PlayerAccountType.UNKNOWN;
        }

        if (worldTypes.contains(
                WorldType.DEADMAN))
        {
            return PlayerAccountType.DEADMAN;
        }

        if (worldTypes.contains(
                WorldType.SEASONAL))
        {
            return PlayerAccountType.LEAGUES;
        }

        return PlayerAccountType.UNKNOWN;
    }

    private void addFriends(String targetKey) {
        final FriendContainer container = client.getFriendContainer();
        if (container == null || container.getMembers() == null) {
            return;
        }

        for (Friend friend : container.getMembers()) {
            if (friend == null
                    || isBlank(friend.getName())
                    || !shouldInclude(
                    friend.getName(),
                    targetKey)) {
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

    private void addClan(String targetKey) {
        final ClanSettings settings = client.getClanSettings();
        final ClanChannel channel = client.getClanChannel();

        if (settings != null && settings.getMembers() != null) {
            for (ClanMember member : settings.getMembers()) {
                if (member == null
                        || isBlank(member.getName())
                        || !shouldInclude(
                        member.getName(),
                        targetKey)) {
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
                if (member == null
                        || isBlank(member.getName())
                        || !shouldInclude(
                        member.getName(),
                        targetKey)) {
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

    private void addGuestClan(String targetKey) {
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
                if (member == null
                        || isBlank(member.getName())
                        || !shouldInclude(
                        member.getName(),
                        targetKey)) {
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
                if (member == null
                        || isBlank(member.getName())
                        || !shouldInclude(
                        member.getName(),
                        targetKey)) {
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

    private void addFriendsChat(String targetKey) {
        final FriendsChatManager manager = client.getFriendsChatManager();

        if (manager == null || manager.getMembers() == null) {
            return;
        }

        final String channelName =
                !isBlank(manager.getName())
                        ? manager.getName()
                        : "Friends Chat";

        for (FriendsChatMember member : manager.getMembers()) {
            if (member == null
                    || isBlank(member.getName())
                    || !shouldInclude(
                    member.getName(),
                    targetKey)) {
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

    private void addParty(String targetKey) {
        if (partyService == null || !partyService.isInParty()) {
            return;
        }

        for (PartyMember member : partyService.getMembers()) {
            if (member == null
                    || isBlank(member.getDisplayName())
                    || !shouldInclude(
                    member.getDisplayName(),
                    targetKey)) {
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

    private void addNearbyPlayers(String targetKey) {
        final WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            return;
        }

        for (Player player : worldView.players()) {
            if (player == null
                    || isBlank(player.getName())
                    || !shouldInclude(
                    player.getName(),
                    targetKey)) {
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

        final PlayerAccountType observedAccountType =
                observedAccountTypes.getOrDefault(
                        key,
                        PlayerAccountType.UNKNOWN);

        final PlayerAccountType permanentAccountType =
                observedPermanentAccountTypes.getOrDefault(
                        key,
                        PlayerAccountType.UNKNOWN);

        final PlayerAccountType temporaryWorldType =
                worldAccountType(
                        world);

        final PlayerAccountType mergedAccountType;

        /*
         * Native chat observations are authoritative for moderator status.
         *
         * A currently observed moderator crown always takes precedence over temporary
         * world modes and permanent account classifications.
         */
        if (observedAccountType.isModerator())
        {
            mergedAccountType =
                    observedAccountType;
        }
        /*
         * A known temporary world controls the temporary display classification.
         *
         * DEADMAN / LEAGUES are display states derived from the player's current
         * world rather than durable account modes.
         */
        else if (temporaryWorldType.isTemporary())
        {
            mergedAccountType =
                    temporaryWorldType;
        }
        /*
         * A known non-temporary world explicitly removes any stale temporary display
         * classification and restores the latest authoritative permanent account
         * mode when one is known.
         */
        else if (world != null
                && world > 0
                && worldTypesById.containsKey(world))
        {
            if (permanentAccountType.isKnown())
            {
                mergedAccountType =
                        permanentAccountType;
            }
            else if (!observedAccountType.isTemporary())
            {
                mergedAccountType =
                        observedAccountType;
            }
            else
            {
                mergedAccountType =
                        PlayerAccountType.UNKNOWN;
            }
        }
        /*
         * Without reliable world information, use the latest durable/native
         * observation. Do not allow a partially rebuilt identity to override it.
         */
        else if (!observedAccountType.isTemporary())
        {
            mergedAccountType =
                    observedAccountType;
        }
        else if (permanentAccountType.isKnown())
        {
            mergedAccountType =
                    permanentAccountType;
        }
        else
        {
            mergedAccountType =
                    PlayerAccountType.UNKNOWN;
        }

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
                        .accountType(mergedAccountType)
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

    private boolean shouldInclude(
            String playerName,
            String targetKey)
    {
        if (targetKey == null)
        {
            return true;
        }

        if (isBlank(playerName))
        {
            return false;
        }

        return targetKey.equals(
                normalizer.comparisonKey(
                        playerName));
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }
}
