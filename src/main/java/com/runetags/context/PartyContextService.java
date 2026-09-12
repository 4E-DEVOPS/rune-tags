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
import net.runelite.client.plugins.party.messages.LocationUpdate;
import net.runelite.client.plugins.party.messages.StatusUpdate;

public class PartyContextService
{
    /*
     * Treat Party context as stale after 60 seconds without a location update.
     */
    private static final long CONTEXT_TTL_MILLIS =
            60_000L;

    private final PartyService partyService;
    private final LocationIndex locationIndex;
    private final NameNormalizer nameNormalizer;

    /*
     * RuneLite Party member IDs are authoritative for incoming Party updates.
     * Player names are maintained as a secondary Quick Profile lookup index.
     *
     * LocationUpdate may arrive before the member's display name is available, so
     * context is retained by member ID until a later StatusUpdate binds the name.
     */
    private final Map<Long, PartyContext> contextsByMemberId =
            new LinkedHashMap<>();

    private final Map<String, Long> memberIdsByName =
            new LinkedHashMap<>();

    public PartyContextService(
            PartyService partyService,
            LocationIndex locationIndex,
            NameNormalizer nameNormalizer)
    {
        this.partyService = partyService;
        this.locationIndex = locationIndex;
        this.nameNormalizer = nameNormalizer;
    }

    public void onLocationUpdate(LocationUpdate event)
    {
        if (event == null || partyService == null)
        {
            return;
        }

        removeExpired();

        final WorldPoint worldPoint =
                event.getWorldPoint();

        if (worldPoint == null)
        {
            return;
        }

        final long memberId =
                event.getMemberId();

        final PartyMember member =
                partyService.getMemberById(
                        memberId);

        final PartyContext previous =
                contextsByMemberId.get(
                        memberId);

        final String memberName =
                canonicalPartyName(
                        member != null
                                ? member.getDisplayName()
                                : null);

        final String playerName =
                memberName != null
                        ? memberName
                        : previous != null
                        ? previous.getPlayerName()
                        : null;

        final int regionId =
                worldPoint.getRegionID();

        final String locationName =
                locationIndex != null
                        ? locationIndex.findName(regionId)
                        : null;

        putContext(
                new PartyContext(
                        memberId,
                        playerName,
                        regionId,
                        locationName,
                        System.currentTimeMillis()));
    }

    /**
     * Bind RuneLite's stable Party member ID to the character name from a StatusUpdate.
     *
     * Reading the name directly from the event avoids dependence on EventBus
     * subscriber ordering and allows an earlier unnamed LocationUpdate to be bound.
     */
    public void onStatusUpdate(StatusUpdate event)
    {
        if (event == null
                || event.getCharacterName() == null)
        {
            return;
        }

        removeExpired();

        final long memberId =
                event.getMemberId();

        final String playerName =
                canonicalPartyName(
                        event.getCharacterName());

        /*
         * RuneLite sends an empty character name when the member is no longer logged
         * into a character, so their previous location must not remain live.
         */
        if (playerName == null)
        {
            remove(memberId);
            return;
        }

        final PartyContext previous =
                contextsByMemberId.get(
                        memberId);

        if (previous == null)
        {
            return;
        }

        putContext(
                new PartyContext(
                        memberId,
                        playerName,
                        previous.getRegionId(),
                        previous.getLocationName(),
                        previous.getUpdatedAtMillis()));
    }

    public PartyContext find(String playerName)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            return null;
        }

        final String key =
                nameNormalizer.comparisonKey(
                        playerName);

        if (key.isEmpty())
        {
            return null;
        }

        final Long memberId =
                memberIdsByName.get(key);

        if (memberId == null)
        {
            return null;
        }

        final PartyContext context =
                contextsByMemberId.get(memberId);

        if (isExpired(context))
        {
            remove(memberId);
            return null;
        }

        return context;
    }

    public PartyContext findByMemberId(long memberId)
    {
        final PartyContext context =
                contextsByMemberId.get(memberId);

        if (isExpired(context))
        {
            remove(memberId);
            return null;
        }

        return context;
    }

    public void removeMember(long memberId)
    {
        remove(memberId);
    }

    private void putContext(
            PartyContext context)
    {
        if (context == null)
        {
            return;
        }

        final long memberId =
                context.getMemberId();

        final PartyContext previous =
                contextsByMemberId.put(
                        memberId,
                        context);

        if (previous != null)
        {
            final String previousKey =
                    comparisonKeyOrEmpty(
                            previous.getPlayerName());

            if (!previousKey.isEmpty())
            {
                memberIdsByName.remove(
                        previousKey,
                        memberId);
            }
        }

        final String key =
                comparisonKeyOrEmpty(
                        context.getPlayerName());

        if (!key.isEmpty())
        {
            memberIdsByName.put(
                    key,
                    memberId);
        }
    }

    private String canonicalPartyName(
            String rawName)
    {
        if (rawName == null
                || rawName.trim().isEmpty()
                || "<unknown>".equalsIgnoreCase(
                rawName.trim()))
        {
            return null;
        }

        final String canonical =
                nameNormalizer.canonicalize(
                        rawName);

        return canonical.isEmpty()
                ? null
                : canonical;
    }

    private String comparisonKeyOrEmpty(
            String playerName)
    {
        return playerName == null
                || playerName.trim().isEmpty()
                ? ""
                : nameNormalizer.comparisonKey(
                playerName);
    }

    private static boolean isExpired(PartyContext context)
    {
        return context == null
                || System.currentTimeMillis()
                - context.getUpdatedAtMillis()
                > CONTEXT_TTL_MILLIS;
    }

    private void remove(long memberId)
    {
        final PartyContext removed =
                contextsByMemberId.remove(memberId);

        if (removed == null)
        {
            return;
        }

        final String key =
                comparisonKeyOrEmpty(
                        removed.getPlayerName());

        if (!key.isEmpty())
        {
            memberIdsByName.remove(
                    key,
                    memberId);
        }
    }

    private void removeExpired()
    {
        final List<Long> expired =
                new ArrayList<>();

        for (Map.Entry<Long, PartyContext> entry
                : contextsByMemberId.entrySet())
        {
            if (isExpired(entry.getValue()))
            {
                expired.add(entry.getKey());
            }
        }

        for (Long memberId : expired)
        {
            remove(memberId);
        }
    }

    public void clear()
    {
        contextsByMemberId.clear();
        memberIdsByName.clear();
    }

    @lombok.Value
    public static class PartyContext
    {
        long memberId;
        String playerName;
        int regionId;
        String locationName;
        long updatedAtMillis;
    }
}
