package com.runetags.player;

import com.runetags.mention.NameNormalizer;

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

	private final Map<String, AccountType> observedAccountTypes = new LinkedHashMap<>();
	private final Map<String, AccountType> observedPermanentAccountTypes = new LinkedHashMap<>();
	private final Map<String, PlayerIdentity> identities = new LinkedHashMap<>();
	private final Map<Integer, EnumSet<WorldType>> worldTypesById = new LinkedHashMap<>();

	public PlayerDirectory(
			Client client,
			PartyService partyService,
			WorldService worldService,
			NameNormalizer normalizer) {
		this.client = client;
		this.partyService = partyService;
		this.worldService = worldService;
		this.normalizer = normalizer;
	}

	/*
	 * Rebuild the live directory from all authoritative sources.
	 */
	public void rebuild() {
		identities.clear();

		refreshWorldTypes();

		addFriends(null);
		addClan(null);
		addGuestClan(null);
		addFriendsChat(null);
		addParty(null);
		addNearbyPlayers(null);
	}

	/*
	 * Refresh one identity from authoritative sources after removing stale source contributions.
	 */
	public void refreshPlayer(String playerName) {
		if (isBlank(playerName)) {
			return;
		}

		final String targetKey = normalizer.comparisonKey(playerName);
		if (targetKey.isEmpty()) {
			return;
		}

		identities.remove(targetKey);

		addFriends(targetKey);
		addClan(targetKey);
		addGuestClan(targetKey);
		addFriendsChat(targetKey);
		addParty(targetKey);
		addNearbyPlayers(targetKey);
	}

	/*
	 * Apply one PlayerSpawned event directly to the Nearby contribution.
	 */
	public void addNearbyPlayer(Player player) {
		if (player == null || isBlank(player.getName())) {
			return;
		}

		mergeIdentity(
				player.getName(),
				PlayerSource.NEARBY,
				player.getCombatLevel(),
				client.getWorld(),
				OnlineState.ONLINE,
				null,
				null,
				player,
				player.getWorldLocation());
	}

	/*
	 * Remove one Nearby contribution and rebuild identities that retain other sources.
	 */
	public void removeNearbyPlayer(Player player) {
		if (player == null || isBlank(player.getName())) {
			return;
		}

		removeNearbyPlayer(player.getName());
	}

	private void removeNearbyPlayer(String playerName) {
		final String targetKey = normalizer.comparisonKey(playerName);
		if (targetKey.isEmpty()) {
			return;
		}

		final PlayerIdentity existing = identities.get(targetKey);
		if (existing == null || existing.getSources() == null || !existing.getSources().contains(PlayerSource.NEARBY)) {
			return;
		}

		if (existing.getSources().size() == 1) {
			identities.remove(targetKey);
			return;
		}

		/*
		 * Nearby can own combat, world, online, Player, and WorldPoint state. Rebuild
		 * the remaining aggregate instead of subtracting merged fields in place.
		 */
		identities.remove(targetKey);

		addFriends(targetKey);
		addClan(targetKey);
		addGuestClan(targetKey);
		addFriendsChat(targetKey);
		addParty(targetKey);
	}

	public void observeAccountType(String rawPlayerName) {
		if (isBlank(rawPlayerName)) {
			return;
		}

		final String plainName = Text.removeTags(rawPlayerName);
		final String key = normalizer.comparisonKey(plainName);
		if (key.isEmpty()) {
			return;
		}

		final boolean leaguesWorld = client.getWorldType() != null && client.getWorldType()
				.contains(WorldType.SEASONAL);

		final AccountType observedType = AccountType.fromChatName(rawPlayerName, leaguesWorld);

		// Chat markup is authoritative for the displayed account classification.
		if (observedType.isPermanentAccountType()) {
			observedPermanentAccountTypes.put(key, observedType);
		}

		observedAccountTypes.put(key, observedType);

		final PlayerIdentity existing = identities.get(key);
		if (existing != null) {
			identities.put(key, existing.toBuilder().accountType(observedType).build());
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
		players.sort((a, b) -> Integer.compare(b.getCanonicalName().length(), a.getCanonicalName().length()));
		return players;
	}

	/*
	 * Clear live state while preserving durable account observations from retained chat history.
	 * Temporary world classifications must be re-established from the current world.
	 */
	public void clearLiveState() {
		identities.clear();
		worldTypesById.clear();

		observedAccountTypes.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().isTemporary());
	}

	/*
	 * Clear all PlayerDirectory state for shutdown or a complete runtime reset.
	 */
	public void clear() {
		identities.clear();
		observedAccountTypes.clear();
		observedPermanentAccountTypes.clear();
		worldTypesById.clear();
	}

	/*
	 * Refresh the world-number to WorldType mapping used by temporary account classifications.
	 */
	private void refreshWorldTypes() {
		worldTypesById.clear();

		// The current world remains authoritative when the external world list is unavailable.
		if (client.getWorld() > 0 && client.getWorldType() != null) {
			worldTypesById.put(client.getWorld(), EnumSet.copyOf(client.getWorldType()));
		}

		if (worldService == null) {
			return;
		}

		final WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null || worldResult.getWorlds() == null) {
			return;
		}

		for (World world : worldResult.getWorlds()) {
			if (world == null || world.getTypes() == null) {
				continue;
			}

			worldTypesById.put(world.getId(), WorldUtil.toWorldTypes(world.getTypes()));
		}
	}

	/*
	 * Resolve temporary account classification from world flags.
	 * DEADMAN takes precedence over SEASONAL when both are present.
	 */
	private AccountType worldAccountType(Integer world) {
		if (world == null || world <= 0) {
			return AccountType.UNKNOWN;
		}

		final Set<WorldType> worldTypes = worldTypesById.get(world);
		if (worldTypes == null || worldTypes.isEmpty()) {
			return AccountType.UNKNOWN;
		}

		if (worldTypes.contains(WorldType.DEADMAN)) {
			return AccountType.DEADMAN;
		}

		if (worldTypes.contains(WorldType.SEASONAL)) {
			return AccountType.LEAGUES;
		}

		return AccountType.UNKNOWN;
	}

	private void addFriends(String targetKey) {
		final FriendContainer container = client.getFriendContainer();
		if (container == null || container.getMembers() == null) {
			return;
		}

		for (Friend friend : container.getMembers()) {
			if (friend == null || isBlank(friend.getName()) || !shouldInclude(friend.getName(), targetKey)) {
				continue;
			}

			final int world = friend.getWorld();
			mergeIdentity(friend.getName(), PlayerSource.FRIEND, null, world > 0
					? world
					: null, world > 0
					? OnlineState.ONLINE
					: OnlineState.OFFLINE, null, null, null, null);
		}
	}

	private void addClan(String targetKey) {
		final ClanSettings settings = client.getClanSettings();
		final ClanChannel channel = client.getClanChannel();
		if (settings != null && settings.getMembers() != null) {
			for (ClanMember member : settings.getMembers()) {
				if (member == null || isBlank(member.getName()) || !shouldInclude(member.getName(), targetKey)) {
					continue;
				}

				String rankName = null;
				if (member.getRank() != null) {
					final ClanTitle title = settings.titleForRank(member.getRank());
					rankName = title != null
							? title.getName()
							: member.getRank().toString();
				}

				mergeIdentity(member.getName(), PlayerSource.CLAN, null, null,
						OnlineState.UNKNOWN, settings.getName(), rankName, null, null);
			}
		}

		if (channel != null && channel.getMembers() != null) {
			for (ClanChannelMember member : channel.getMembers()) {
				if (member == null || isBlank(member.getName()) || !shouldInclude(member.getName(), targetKey)) {
					continue;
				}

				String rankName = null;
				if (member.getRank() != null) {
					if (settings != null) {
						final ClanTitle title = settings.titleForRank(member.getRank());
						rankName = title != null
								? title.getName()
								: member.getRank().toString();
					} else {
						rankName = member.getRank().toString();
					}
				}

				final int world = member.getWorld();
				mergeIdentity(member.getName(), PlayerSource.CLAN, null, world > 0
						? world
						: null, world > 0
						? OnlineState.ONLINE
						: OnlineState.UNKNOWN, !isBlank(channel.getName())
						? channel.getName()
						: null, rankName, null, null);
			}
		}
	}

	private void addGuestClan(String targetKey) {
		final ClanSettings settings = client.getGuestClanSettings();
		final ClanChannel channel = client.getGuestClanChannel();

		// Guest clan settings provide identity metadata without current world state.
		if (settings != null && settings.getMembers() != null) {
			for (ClanMember member : settings.getMembers()) {
				if (member == null || isBlank(member.getName()) || !shouldInclude(member.getName(), targetKey)) {
					continue;
				}

				String rankName = null;
				if (member.getRank() != null) {
					final ClanTitle title = settings.titleForRank(member.getRank());

					rankName = title != null
							? title.getName()
							: member.getRank().toString();
				}

				mergeIdentity(
						member.getName(), PlayerSource.GUEST_CLAN, null, null,
						OnlineState.UNKNOWN, settings.getName(), rankName, null, null);
			}
		}

		// The active guest clan channel provides current membership and world state.
		if (channel != null && channel.getMembers() != null) {
			for (ClanChannelMember member : channel.getMembers()) {
				if (member == null || isBlank(member.getName()) || !shouldInclude(member.getName(), targetKey)) {
					continue;
				}

				String rankName = null;
				if (member.getRank() != null) {
					if (settings != null) {
						final ClanTitle title = settings.titleForRank(member.getRank());
						rankName = title != null
								? title.getName()
								: member.getRank().toString();
					} else {
						rankName = member.getRank().toString();
					}
				}

				final int world = member.getWorld();
				mergeIdentity(member.getName(), PlayerSource.GUEST_CLAN, null, world > 0
						? world
						: null, world > 0
						? OnlineState.ONLINE
						: OnlineState.UNKNOWN, !isBlank(channel.getName())
						? channel.getName()
						: settings != null
						? settings.getName()
						: null, rankName, null, null);
			}
		}
	}

	private void addFriendsChat(String targetKey) {
		final FriendsChatManager manager = client.getFriendsChatManager();
		if (manager == null || manager.getMembers() == null) {
			return;
		}

		final String channelName = !isBlank(manager.getName())
				? manager.getName()
				: "Friends Chat";

		for (FriendsChatMember member : manager.getMembers()) {
			if (member == null || isBlank(member.getName()) || !shouldInclude(member.getName(), targetKey)) {
				continue;
			}

			final int world = member.getWorld();
			final String rankName = member.getRank() != null
					? member.getRank().toString()
					: null;

			mergeIdentity(member.getName(), PlayerSource.FRIENDS_CHAT, null, world > 0
					? world
					: null, world > 0
					? OnlineState.ONLINE
					: OnlineState.UNKNOWN, channelName, rankName, null, null);
		}
	}

	private String partyChannelName() {
		if (partyService == null || !partyService.isInParty()) {
			return "Party";
		}

		final String partyPassphrase = partyService.getPartyPassphrase();
		return !isBlank(partyPassphrase)
				? "Party: " + partyPassphrase
				: "Party";
	}

	private void addParty(String targetKey) {
		if (partyService == null || !partyService.isInParty()) {
			return;
		}

		for (PartyMember member : partyService.getMembers()) {
			if (member == null || !isUsablePartyName(member.getDisplayName()) || !shouldInclude(
					member.getDisplayName(),
					targetKey)) {
				continue;
			}

			mergeIdentity(member.getDisplayName(), PlayerSource.PARTY, null, null, member.isLoggedIn()
					? OnlineState.ONLINE
					: OnlineState.UNKNOWN, partyChannelName(), null, null, null);
		}
	}

	private void addNearbyPlayers(String targetKey) {
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null) {
			return;
		}

		for (Player player : worldView.players()) {
			if (player == null || isBlank(player.getName()) || !shouldInclude(player.getName(), targetKey)) {
				continue;
			}

			mergeIdentity(player.getName(), PlayerSource.NEARBY, player.getCombatLevel(), client.getWorld(),
					OnlineState.ONLINE, null, null, player, player.getWorldLocation());
		}
	}

	private static int channelPriority(PlayerSource source) {
		if (source == null) {
			return 0;
		}

		switch (source) {
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
			WorldPoint worldPoint) {
		final String canonicalName = normalizer.canonicalize(rawName);
		final String key = normalizer.comparisonKey(canonicalName);
		if (key.isEmpty()) {
			return;
		}

		final PlayerIdentity existing = identities.get(key);

		final AccountType observedAccountType = observedAccountTypes.getOrDefault(key, AccountType.UNKNOWN);

		final AccountType permanentAccountType = observedPermanentAccountTypes.getOrDefault(key, AccountType.UNKNOWN);

		final AccountType temporaryWorldType = worldAccountType(world);

		final AccountType mergedAccountType;

		/*
		 * Account classification precedence is moderator > temporary world > durable native observation.
		 */
		if (observedAccountType.isModerator()) {
			mergedAccountType = observedAccountType;
		} else if (temporaryWorldType.isTemporary()) {
			mergedAccountType = temporaryWorldType;
		} else if (world != null && world > 0 && worldTypesById.containsKey(world)) {
			if (permanentAccountType.isKnown()) {
				mergedAccountType = permanentAccountType;
			} else if (!observedAccountType.isTemporary()) {
				mergedAccountType = observedAccountType;
			} else {
				mergedAccountType = AccountType.UNKNOWN;
			}
		} else if (observedAccountType.isKnown() && !observedAccountType.isTemporary()) {
			mergedAccountType = observedAccountType;
		} else if (permanentAccountType.isKnown()) {
			mergedAccountType = permanentAccountType;
		} else {
			mergedAccountType = AccountType.UNKNOWN;
		}

		final Set<PlayerSource> sources = existing == null
				? EnumSet.of(source)
				: EnumSet.copyOf(existing.getSources());

		sources.add(source);

		// ONLINE overrides UNKNOWN or OFFLINE when any source has live-world state.
		OnlineState mergedState = onlineState;
		if (existing != null) {
			if (existing.getOnlineState() == OnlineState.ONLINE) {
				mergedState = OnlineState.ONLINE;
			} else if (mergedState == null || mergedState == OnlineState.UNKNOWN) {
				mergedState = existing.getOnlineState();
			}
		}

		if (mergedState == null) {
			mergedState = OnlineState.UNKNOWN;
		}

		/*
		 * Shared-channel priority is PARTY > CLAN > GUEST_CLAN > FRIENDS_CHAT.
		 * Equal priority may enrich metadata from an earlier representation.
		 */
		String mergedChannelName = existing != null
				? existing.getChannelName()
				: null;
		String mergedChannelRank = existing != null
				? existing.getChannelRank()
				: null;
		PlayerSource mergedChannelSource = existing != null
				? existing.getChannelSource()
				: null;

		final int incomingPriority = channelPriority(source);
		final int existingPriority = channelPriority(mergedChannelSource);
		if (incomingPriority > 0 && incomingPriority >= existingPriority) {
			mergedChannelSource = source;

			if (!isBlank(channelName)) {
				mergedChannelName = channelName;
			} else if (incomingPriority > existingPriority) {
				mergedChannelName = null;
			}

			if (!isBlank(channelRank)) {
				mergedChannelRank = channelRank;
			} else if (incomingPriority > existingPriority) {
				mergedChannelRank = null;
			}
		}

		identities.put(key, PlayerIdentity.builder()
			.canonicalName(existing != null
					? existing.getCanonicalName()
					: canonicalName)
			.normalizedName(key).accountType(mergedAccountType).sources(Collections.unmodifiableSet(sources))
				.combatLevel(combatLevel != null
					? combatLevel
					: existing == null
					? null
					: existing.getCombatLevel())
			.world(world != null
					? world
					: existing == null
					? null
					: existing.getWorld())
			.onlineState(mergedState)
			.channelName(mergedChannelName)
			.channelRank(mergedChannelRank)
			.channelSource(mergedChannelSource)
			.nearbyPlayer(nearbyPlayer != null
					? nearbyPlayer
					: existing == null
					? null
					: existing.getNearbyPlayer())
			.lastKnownWorldPoint(worldPoint != null
					? worldPoint
					: existing == null
					? null
					: existing.getLastKnownWorldPoint())
			.build());
	}

	private static boolean isUsablePartyName(String playerName) {
		return !isBlank(playerName) && !"<unknown>".equalsIgnoreCase(playerName.trim());
	}

	private boolean shouldInclude(String playerName, String targetKey) {
		if (targetKey == null) {
			return true;
		}

		if (isBlank(playerName)) {
			return false;
		}

		return targetKey.equals(normalizer.comparisonKey(playerName));
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
