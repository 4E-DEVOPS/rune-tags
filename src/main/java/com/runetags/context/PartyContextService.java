package com.runetags.context;

import com.runetags.location.LocationIndex;
import com.runetags.mention.NameNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyData;

/**
 * Tracks recent RuneLite Party location context by stable member ID and normalized player name.
 */
public class PartyContextService {
	private static final long CONTEXT_TTL_MILLIS = 60_000L;

	private final PartyService partyService;
	private final LocationIndex locationIndex;
	private final NameNormalizer nameNormalizer;

	/*
	 * Member IDs own incoming Party context; normalized player names provide a secondary lookup index.
	 * Location updates may exist without a name until a later status update binds the member.
	 */
	private final Map<Long, PartyContext> contextsByMemberId = new LinkedHashMap<>();
	private final Map<String, Long> memberIdsByName = new LinkedHashMap<>();

	public PartyContextService(PartyService partyService, LocationIndex locationIndex, NameNormalizer nameNormalizer) {
		this.partyService = partyService;
		this.locationIndex = locationIndex;
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
			final String locationName = locationIndex != null
					? locationIndex.findName(regionId)
					: null;
			putContext(new PartyContext(memberId, playerName, regionId, locationName, System.currentTimeMillis()));
		} else if (characterNameUpdate != null && previous != null) {
			// Do not extend location freshness for a name-only update.
			putContext(new PartyContext(
					memberId, playerName, previous.getRegionId(), previous.getLocationName(), previous.getUpdatedAtMillis()));
		}
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

		return context;
	}

	public PartyContext findByMemberId(long memberId) {
		final PartyContext context = contextsByMemberId.get(memberId);
		if (isExpired(context)) {
			remove(memberId);
			return null;
		}

		return context;
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

	private static boolean isExpired(PartyContext context) {
		return context == null
				|| System.currentTimeMillis() - context.getUpdatedAtMillis() > CONTEXT_TTL_MILLIS;
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

	@lombok.Value
	public static class PartyContext {
		long memberId;
		String playerName;
		int regionId;
		String locationName;
		long updatedAtMillis;
	}
}
