package com.runetags.context;

import com.runetags.location.InstanceIndex;
import com.runetags.location.RegionIndex;
import com.runetags.mention.NameNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyData;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Tracks recent RuneLite Party location context by stable member ID and normalized player name.
 */
public class PartyContextService {
	private static final long CONTEXT_TTL_MILLIS = 60_000L;
	private static final long INSTANCE_CONTEXT_TTL_MILLIS = 45_000L;

	private final PartyService partyService;
	private final RegionIndex regionIndex;
	private final InstanceIndex instanceIndex;
	private final NameNormalizer nameNormalizer;

	/*
	 * Member IDs own incoming Party context; normalized player names provide a secondary lookup index.
	 * Location updates may exist without a name until a later status update binds the member.
	 */
	private final Map<Long, PartyContext> contextsByMemberId = new LinkedHashMap<>();
	private final Map<String, Long> memberIdsByName = new LinkedHashMap<>();

	public PartyContextService(PartyService partyService, RegionIndex regionIndex, InstanceIndex instanceIndex, NameNormalizer nameNormalizer) {
		this.partyService = partyService;
		this.regionIndex = regionIndex;
		this.instanceIndex = instanceIndex;
		this.nameNormalizer = nameNormalizer;
	}

	/**
	 * RuneLite 1.13.4 sends partial PartyData updates instead of the former
	 * LocationUpdate/StatusUpdate messages. A name and a coordinate can arrive
	 * independently; keep the existing location when only the name changes.
	 */
	public void onPartyData(PartyData event) {
		if (event == null || partyService == null) {
			return;
		}

		removeExpired();

		final long memberId = event.getMemberId();
		final PartyMember member = partyService.getMemberById(memberId);
		if (member == null) {
			return;
		}

		final PartyContext previous = contextsByMemberId.get(memberId);
		final String characterNameUpdate = event.getCharacterName();
		if (characterNameUpdate != null && canonicalPartyName(characterNameUpdate) == null) {
			// A blank character-name update means the member logged out.
			remove(memberId);
			return;
		}

		final String playerName = characterNameUpdate != null
				? canonicalPartyName(characterNameUpdate)
				: previous != null && previous.getPlayerName() != null
						? previous.getPlayerName()
						: canonicalPartyName(member.getDisplayName());

		final Integer packedCoord = event.getCoord();
		if (packedCoord != null) {
			final WorldPoint worldPoint = WorldPoint.fromCoord(packedCoord);
			final int regionId = worldPoint.getRegionID();
			final String locationName = regionIndex != null
					? regionIndex.findName(regionId)
					: null;
			final boolean previousInstance = hasFreshInstance(previous);
			putContext(new PartyContext(memberId, playerName, regionId, locationName, System.currentTimeMillis(), previousInstance, previousInstance ? previous.getInstanceId() : -1, previousInstance ? previous.getInstanceLocationName() : null, previousInstance ? previous.getInstanceUpdatedAtMillis() : 0L));
		} else if (characterNameUpdate != null && previous != null) {
			// Do not extend location freshness for a name-only update.
			final boolean previousInstance = hasFreshInstance(previous);
			putContext(new PartyContext(memberId, playerName, previous.getRegionId(), previous.getLocationName(), previous.getUpdatedAtMillis(), previousInstance, previousInstance ? previous.getInstanceId() : -1, previousInstance ? previous.getInstanceLocationName() : null, previousInstance ? previous.getInstanceUpdatedAtMillis() : 0L));
		}
	}

	/**
	 * Stores RuneTags-specific instance context alongside RuneLite's normal Party region data.
	 */
	public void onPartyContextUpdate(PartyContextUpdate event) {
		if (event == null || partyService == null) {
			return;
		}

		removeExpired();

		final long memberId = event.getMemberId();
		final PartyMember member = partyService.getMemberById(memberId);
		if (member == null) {
			return;
		}

		final PartyContext previous = contextsByMemberId.get(memberId);
		if (!event.isInstance() && previous == null) {
			return;
		}

		final String playerName = previous != null && previous.getPlayerName() != null
				? previous.getPlayerName()
				: canonicalPartyName(member.getDisplayName());
		final boolean instance = event.isInstance();
		final int instanceId = instance
				? event.getInstanceId()
				: -1;
		final String instanceLocationName = instance
				? resolveInstanceLocation(instanceId, event.getInstanceLocationName())
				: null;

		final long now = System.currentTimeMillis();
		final long updatedAtMillis = instance || previous == null
				? now
				: previous.getUpdatedAtMillis();
		putContext(new PartyContext(memberId, playerName, previous != null ? previous.getRegionId() : -1, previous != null ? previous.getLocationName() : null, updatedAtMillis, instance, instanceId, instanceLocationName, instance ? now : 0L));
	}

	public PartyContext find(String playerName) {
		if (playerName == null || playerName.trim().isEmpty()) {
			return null;
		}

		final String key = nameNormalizer.comparisonKey(playerName);
		if (key.isEmpty()) {
			return null;
		}

		final Long memberId = memberIdsByName.get(key);
		if (memberId == null) {
			return null;
		}

		final PartyContext context = contextsByMemberId.get(memberId);
		if (isExpired(context)) {
			remove(memberId);
			return null;
		}

		return clearExpiredInstanceContext(context);
	}

	public PartyContext findByMemberId(long memberId) {
		final PartyContext context = contextsByMemberId.get(memberId);
		if (isExpired(context)) {
			remove(memberId);
			return null;
		}

		return clearExpiredInstanceContext(context);
	}

	public void removeMember(long memberId) {
		remove(memberId);
	}

	private void putContext(PartyContext context) {
		if (context == null) {
			return;
		}

		final long memberId = context.getMemberId();
		final PartyContext previous = contextsByMemberId.put(memberId, context);
		if (previous != null) {
			final String previousKey = comparisonKeyOrEmpty(previous.getPlayerName());
			if (!previousKey.isEmpty()) {
				memberIdsByName.remove(previousKey, memberId);
			}
		}
		final String key = comparisonKeyOrEmpty(context.getPlayerName());
		if (!key.isEmpty()) {
			memberIdsByName.put(key, memberId);
		}
	}

	private String resolveInstanceLocation(int instanceId, String sharedLocationName) {
		if (instanceId >= 0 && instanceIndex != null) {
			final String indexedLocationName = instanceIndex.findName(instanceId);
			if (indexedLocationName != null && !indexedLocationName.trim().isEmpty()) {
				return indexedLocationName;
			}
		}

		return sharedLocationName == null || sharedLocationName.trim().isEmpty()
				? null
				: sharedLocationName;
	}

	private String canonicalPartyName(String rawName) {
		if (rawName == null || rawName.trim().isEmpty() || "<unknown>".equalsIgnoreCase(rawName.trim())) {
			return null;
		}

		final String canonical = nameNormalizer.canonicalize(rawName);
		return canonical.isEmpty()
				? null
				: canonical;
	}

	private String comparisonKeyOrEmpty(String playerName) {
		return playerName == null || playerName.trim().isEmpty()
				? ""
				: nameNormalizer.comparisonKey(playerName);
	}

	private PartyContext clearExpiredInstanceContext(PartyContext context) {
		if (context == null || !context.isInstance() || hasFreshInstance(context)) {
			return context;
		}

		final PartyContext cleared = new PartyContext(context.getMemberId(), context.getPlayerName(), context.getRegionId(), context.getLocationName(), context.getUpdatedAtMillis(), false, -1, null, 0L);
		putContext(cleared);
		return cleared;
	}

	private static boolean hasFreshInstance(PartyContext context) {
		return context != null && context.isInstance() && context.getInstanceUpdatedAtMillis() > 0L && System.currentTimeMillis() - context.getInstanceUpdatedAtMillis() <= INSTANCE_CONTEXT_TTL_MILLIS;
	}

	private static boolean isExpired(PartyContext context) {
		return context == null || System.currentTimeMillis() - context.getUpdatedAtMillis() > CONTEXT_TTL_MILLIS;
	}

	private void remove(long memberId) {
		final PartyContext removed = contextsByMemberId.remove(memberId);
		if (removed == null) {
			return;
		}

		final String key = comparisonKeyOrEmpty(removed.getPlayerName());
		if (!key.isEmpty()) {
			memberIdsByName.remove(key, memberId);
		}
	}

	private void removeExpired() {
		final List<Long> expired = new ArrayList<>();
		for (Map.Entry<Long, PartyContext> entry : contextsByMemberId.entrySet()) {
			if (isExpired(entry.getValue())) {
				expired.add(entry.getKey());
			}
		}

		for (Long memberId : expired) {
			remove(memberId);
		}
	}

	public void clear() {
		contextsByMemberId.clear();
		memberIdsByName.clear();
	}

	/**
	 * RuneTags-specific Party message that supplements RuneLite's built-in coordinate data.
	 */
	public static class PartyContextUpdate extends PartyMemberMessage {
		private boolean instance;
		private int instanceId = -1;
		private String instanceLocationName;

		public PartyContextUpdate() {
		}

		public PartyContextUpdate(boolean instance, int instanceId, String instanceLocationName) {
			this.instance = instance;
			this.instanceId = instance
					? instanceId
					: -1;
			this.instanceLocationName = instance
					? instanceLocationName
					: null;
		}

		public boolean isInstance() {
			return instance;
		}

		public int getInstanceId() {
			return instanceId;
		}

		public String getInstanceLocationName() {
			return instanceLocationName;
		}
	}

	@lombok.Value
	public static class PartyContext {
		long memberId;
		String playerName;
		int regionId;
		String locationName;
		long updatedAtMillis;
		boolean instance;
		int instanceId;
		String instanceLocationName;
		long instanceUpdatedAtMillis;

		public PartyContext(long memberId, String playerName, int regionId, String locationName, long updatedAtMillis) {
			this(memberId, playerName, regionId, locationName, updatedAtMillis, false, -1, null, 0L);
		}

		public PartyContext(long memberId, String playerName, int regionId, String locationName, long updatedAtMillis, boolean instance, int instanceId, String instanceLocationName) {
			this(memberId, playerName, regionId, locationName, updatedAtMillis, instance, instanceId, instanceLocationName, instance
					? updatedAtMillis
					: 0L);
		}

		public PartyContext(long memberId, String playerName, int regionId, String locationName, long updatedAtMillis, boolean instance, int instanceId, String instanceLocationName, long instanceUpdatedAtMillis) {
			this.memberId = memberId;
			this.playerName = playerName;
			this.regionId = regionId;
			this.locationName = locationName;
			this.updatedAtMillis = updatedAtMillis;
			this.instance = instance;
			this.instanceId = instance
					? instanceId
					: -1;
			this.instanceLocationName = instance
					? instanceLocationName
					: null;
			this.instanceUpdatedAtMillis = instance
					? instanceUpdatedAtMillis
					: 0L;
		}
	}
}
