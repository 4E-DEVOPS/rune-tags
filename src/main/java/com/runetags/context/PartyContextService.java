package com.runetags.context;

import com.runetags.mention.NameNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.party.messages.LocationUpdate;

public class PartyContextService
{
    /*
     * Party location updates are normally instantaneous.
     * Treat a context as stale if no update has been received for 60 seconds.
     */
    private static final long CONTEXT_TTL_MILLIS =
            60_000L;

    private final PartyService partyService;
    private final LocationIndex locationIndex;
    private final NameNormalizer nameNormalizer;

    /*
     * Authoritative Party context storage.
     *
     * RuneLite Party member IDs are the stable identity for incoming Party
     * messages. Player names are maintained as a secondary lookup index for
     * Quick Profiles.
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

        final PartyMember member =
                partyService.getMemberById(
                        event.getMemberId());

        if (member == null
                || member.getDisplayName() == null
                || member.getDisplayName().trim().isEmpty())
        {
            return;
        }

        final int regionId =
                worldPoint.getRegionID();

        final String locationName =
                locationIndex != null
                        ? locationIndex.findName(regionId)
                        : null;

        final String key =
                nameNormalizer.comparisonKey(
                        member.getDisplayName());

        if (key.isEmpty())
        {
            return;
        }

        final long memberId =
                event.getMemberId();

        final PartyContext previous =
                contextsByMemberId.get(memberId);

        /*
         * A Party member's display name can theoretically change during the
         * lifetime of the cache. Remove the old name index before replacing it.
         */
        if (previous != null)
        {
            final String previousKey =
                    nameNormalizer.comparisonKey(
                            previous.getPlayerName());

            if (!previousKey.isEmpty()
                    && !previousKey.equals(key))
            {
                memberIdsByName.remove(
                        previousKey,
                        memberId);
            }
        }

        final PartyContext context =
                new PartyContext(
                        memberId,
                        member.getDisplayName(),
                        regionId,
                        locationName,
                        System.currentTimeMillis());

        contextsByMemberId.put(
                memberId,
                context);

        memberIdsByName.put(
                key,
                memberId);
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
                nameNormalizer.comparisonKey(
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