package com.runetags.context;

import com.runetags.context.PartyContextService.PartyContext;
import com.runetags.location.LocationIndex;
import com.runetags.mention.NameNormalizer;

import java.lang.reflect.Field;
import java.util.Map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.party.messages.LocationUpdate;
import net.runelite.client.plugins.party.messages.StatusUpdate;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PartyContextServiceTest
{
    private static final long MEMBER_ID =
            1001L;

    private PartyService partyService;
    private LocationIndex locationIndex;
    private NameNormalizer nameNormalizer;
    private PartyContextService service;

    @Before
    public void setUp()
    {
        partyService =
                Mockito.mock(
                        PartyService.class);

        locationIndex =
                Mockito.mock(
                        LocationIndex.class);

        nameNormalizer =
                new NameNormalizer();

        service =
                new PartyContextService(
                        partyService,
                        locationIndex,
                        nameNormalizer);
    }

    /*
     * TESTS
     */

    @Test
    public void nullLocationUpdateIsIgnored()
    {
        service.onLocationUpdate(
                null);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void nullPartyServiceMakesLocationUpdatesNoOp()
    {
        final PartyContextService noPartyService =
                new PartyContextService(
                        null,
                        locationIndex,
                        nameNormalizer);

        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        point);

        noPartyService.onLocationUpdate(
                event);

        Assert.assertNull(
                noPartyService.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void locationUpdateWithoutWorldPointIsIgnored()
    {
        final LocationUpdate event =
                Mockito.mock(
                        LocationUpdate.class);

        Mockito.when(
                        event.getMemberId())
                .thenReturn(
                        MEMBER_ID);

        Mockito.when(
                        event.getWorldPoint())
                .thenReturn(
                        null);

        service.onLocationUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void locationUpdateStoresRegionAndResolvedLocation()
    {
        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        final int regionId =
                point.getRegionID();

        Mockito.when(
                        locationIndex.findName(
                                regionId))
                .thenReturn(
                        "Chambers of Xeric");

        final PartyMember member =
                partyMember(
                        "Zezima");

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        member);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        point);

        service.onLocationUpdate(
                event);

        final PartyContext context =
                service.findByMemberId(
                        MEMBER_ID);

        Assert.assertNotNull(
                context);

        Assert.assertEquals(
                MEMBER_ID,
                context.getMemberId());

        Assert.assertEquals(
                "Zezima",
                context.getPlayerName());

        Assert.assertEquals(
                regionId,
                context.getRegionId());

        Assert.assertEquals(
                "Chambers of Xeric",
                context.getLocationName());

        Assert.assertTrue(
                context.getUpdatedAtMillis()
                        > 0L);
    }

    @Test
    public void locationUpdateCanStoreUnknownLocationName()
    {
        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        Mockito.when(
                        locationIndex.findName(
                                point.getRegionID()))
                .thenReturn(
                        null);

        final PartyMember member =
                partyMember(
                        "Zezima");

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        member);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        point);

        service.onLocationUpdate(
                event);

        final PartyContext context =
                service.findByMemberId(
                        MEMBER_ID);

        Assert.assertNotNull(
                context);

        Assert.assertNull(
                context.getLocationName());

        Assert.assertEquals(
                point.getRegionID(),
                context.getRegionId());
    }

    @Test
    public void nullLocationIndexStillStoresRegion()
    {
        final PartyContextService noIndexService =
                new PartyContextService(
                        partyService,
                        null,
                        nameNormalizer);

        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        final PartyMember member =
                partyMember(
                        "Zezima");

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        member);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        point);

        noIndexService.onLocationUpdate(
                event);

        final PartyContext context =
                noIndexService.findByMemberId(
                        MEMBER_ID);

        Assert.assertNotNull(
                context);

        Assert.assertEquals(
                point.getRegionID(),
                context.getRegionId());

        Assert.assertNull(
                context.getLocationName());
    }

    @Test
    public void locationBeforeStatusRetainsContextByMemberId()
    {
        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        null);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        point);

        service.onLocationUpdate(
                event);

        final PartyContext beforeStatus =
                service.findByMemberId(
                        MEMBER_ID);

        Assert.assertNotNull(
                beforeStatus);

        Assert.assertNull(
                beforeStatus.getPlayerName());

        Assert.assertNull(
                service.find(
                        "Zezima"));
    }

    @Test
    public void statusUpdateBindsEarlierAnonymousLocation()
    {
        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        null);

        final LocationUpdate locationEvent =
                locationUpdate(
                        MEMBER_ID,
                        point);

        service.onLocationUpdate(
                locationEvent);

        final PartyContext before =
                service.findByMemberId(
                        MEMBER_ID);

        Assert.assertNotNull(
                before);

        final long originalUpdatedAt =
                before.getUpdatedAtMillis();

        final StatusUpdate statusEvent =
                statusUpdate(
                        MEMBER_ID,
                        "Zezima");

        service.onStatusUpdate(
                statusEvent);

        final PartyContext byMember =
                service.findByMemberId(
                        MEMBER_ID);

        final PartyContext byName =
                service.find(
                        "Zezima");

        Assert.assertNotNull(
                byMember);

        Assert.assertSame(
                byMember,
                byName);

        Assert.assertEquals(
                "Zezima",
                byMember.getPlayerName());

        Assert.assertEquals(
                point.getRegionID(),
                byMember.getRegionId());

        Assert.assertEquals(
                originalUpdatedAt,
                byMember.getUpdatedAtMillis());
    }

    @Test
    public void statusWithoutPriorLocationDoesNotCreateContext()
    {
        final StatusUpdate event =
                statusUpdate(
                        MEMBER_ID,
                        "Zezima");

        service.onStatusUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNull(
                service.find(
                        "Zezima"));
    }

    @Test
    public void nullStatusUpdateIsIgnored()
    {
        service.onStatusUpdate(
                null);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void statusUpdateWithNullCharacterNameIsIgnored()
    {
        final StatusUpdate event =
                Mockito.mock(
                        StatusUpdate.class);

        Mockito.when(
                        event.getMemberId())
                .thenReturn(
                        MEMBER_ID);

        Mockito.when(
                        event.getCharacterName())
                .thenReturn(
                        null);

        service.onStatusUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void blankStatusNameRemovesExistingContext()
    {
        seedNamedContext(
                MEMBER_ID,
                "Zezima");

        final StatusUpdate event =
                statusUpdate(
                        MEMBER_ID,
                        "   ");

        service.onStatusUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNull(
                service.find(
                        "Zezima"));
    }

    @Test
    public void unknownStatusNameRemovesExistingContext()
    {
        seedNamedContext(
                MEMBER_ID,
                "Zezima");

        final StatusUpdate event =
                statusUpdate(
                        MEMBER_ID,
                        "<unknown>");

        service.onStatusUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNull(
                service.find(
                        "Zezima"));
    }

    @Test
    public void findRejectsNullName()
    {
        Assert.assertNull(
                service.find(
                        null));
    }

    @Test
    public void findRejectsBlankName()
    {
        Assert.assertNull(
                service.find(
                        "   "));
    }

    @Test
    public void findUsesNormalizedComparisonKey()
    {
        seedNamedContext(
                MEMBER_ID,
                "Santa Clause");

        Assert.assertNotNull(
                service.find(
                        "santa clause"));

        Assert.assertNotNull(
                service.find(
                        "  Santa Clause  "));
    }

    @Test
    public void newerLocationUpdatePreservesKnownNameWhenPartyMemberUnavailable()
    {
        seedNamedContext(
                MEMBER_ID,
                "Zezima");

        final WorldPoint secondPoint =
                worldPoint(
                        3300,
                        3300,
                        0);

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        null);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        secondPoint);

        service.onLocationUpdate(
                event);

        final PartyContext context =
                service.findByMemberId(
                        MEMBER_ID);

        Assert.assertNotNull(
                context);

        Assert.assertEquals(
                "Zezima",
                context.getPlayerName());

        Assert.assertEquals(
                secondPoint.getRegionID(),
                context.getRegionId());

        Assert.assertSame(
                context,
                service.find(
                        "Zezima"));
    }

    @Test
    public void nameRebindRemovesOldNameIndex()
    {
        seedNamedContext(
                MEMBER_ID,
                "Zezima");

        final StatusUpdate event =
                statusUpdate(
                        MEMBER_ID,
                        "Santa");

        service.onStatusUpdate(
                event);

        Assert.assertNull(
                service.find(
                        "Zezima"));

        final PartyContext rebound =
                service.find(
                        "Santa");

        Assert.assertNotNull(
                rebound);

        Assert.assertEquals(
                MEMBER_ID,
                rebound.getMemberId());
    }

    @Test
    public void removeMemberRemovesBothIndexes()
    {
        seedNamedContext(
                MEMBER_ID,
                "Zezima");

        Assert.assertNotNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNotNull(
                service.find(
                        "Zezima"));

        service.removeMember(
                MEMBER_ID);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNull(
                service.find(
                        "Zezima"));
    }

    @Test
    public void removingUnknownMemberIsSafe()
    {
        service.removeMember(
                MEMBER_ID);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void clearRemovesAllContextsAndNameIndexes()
    {
        seedNamedContext(
                MEMBER_ID,
                "Zezima");

        seedNamedContext(
                2002L,
                "Santa");

        service.clear();

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNull(
                service.findByMemberId(
                        2002L));

        Assert.assertNull(
                service.find(
                        "Zezima"));

        Assert.assertNull(
                service.find(
                        "Santa"));
    }

    @Test
    public void expiredContextIsRemovedByMemberLookup()
            throws Exception
    {
        final PartyContext expired =
                new PartyContext(
                        MEMBER_ID,
                        "Zezima",
                        12345,
                        "Chambers of Xeric",
                        System.currentTimeMillis()
                                - 60_001L);

        putContextDirectly(
                expired);

        putNameIndexDirectly(
                "Zezima",
                MEMBER_ID);

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));

        Assert.assertNull(
                service.find(
                        "Zezima"));
    }

    @Test
    public void expiredContextIsRemovedByNameLookup()
            throws Exception
    {
        final PartyContext expired =
                new PartyContext(
                        MEMBER_ID,
                        "Zezima",
                        12345,
                        "Chambers of Xeric",
                        System.currentTimeMillis()
                                - 60_001L);

        putContextDirectly(
                expired);

        putNameIndexDirectly(
                "Zezima",
                MEMBER_ID);

        Assert.assertNull(
                service.find(
                        "Zezima"));

        Assert.assertNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void newLocationUpdatePurgesOtherExpiredContexts()
            throws Exception
    {
        final PartyContext expired =
                new PartyContext(
                        2002L,
                        "Santa",
                        12345,
                        "Old Place",
                        System.currentTimeMillis()
                                - 60_001L);

        putContextDirectly(
                expired);

        putNameIndexDirectly(
                "Santa",
                2002L);

        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        final PartyMember member =
                partyMember(
                        "Zezima");

        Mockito.when(
                        partyService.getMemberById(
                                MEMBER_ID))
                .thenReturn(
                        member);

        final LocationUpdate event =
                locationUpdate(
                        MEMBER_ID,
                        point);

        service.onLocationUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        2002L));

        Assert.assertNull(
                service.find(
                        "Santa"));

        Assert.assertNotNull(
                service.findByMemberId(
                        MEMBER_ID));
    }

    @Test
    public void newStatusUpdatePurgesOtherExpiredContexts()
            throws Exception
    {
        final PartyContext expired =
                new PartyContext(
                        2002L,
                        "Santa",
                        12345,
                        "Old Place",
                        System.currentTimeMillis()
                                - 60_001L);

        putContextDirectly(
                expired);

        putNameIndexDirectly(
                "Santa",
                2002L);

        seedAnonymousContext(
                MEMBER_ID);

        final StatusUpdate event =
                statusUpdate(
                        MEMBER_ID,
                        "Zezima");

        service.onStatusUpdate(
                event);

        Assert.assertNull(
                service.findByMemberId(
                        2002L));

        Assert.assertNull(
                service.find(
                        "Santa"));

        Assert.assertNotNull(
                service.find(
                        "Zezima"));
    }

    /*
     * HELPERS
     */

    private void seedNamedContext(
            long memberId,
            String playerName)
    {
        final WorldPoint point =
                worldPoint(
                        3200 + (int) (memberId % 10),
                        3200 + (int) (memberId % 10),
                        0);

        final PartyMember member =
                partyMember(
                        playerName);

        Mockito.when(
                        partyService.getMemberById(
                                memberId))
                .thenReturn(
                        member);

        final LocationUpdate event =
                locationUpdate(
                        memberId,
                        point);

        service.onLocationUpdate(
                event);
    }

    private void seedAnonymousContext(
            long memberId)
    {
        final WorldPoint point =
                worldPoint(
                        3200,
                        3200,
                        0);

        Mockito.when(
                        partyService.getMemberById(
                                memberId))
                .thenReturn(
                        null);

        final LocationUpdate event =
                locationUpdate(
                        memberId,
                        point);

        service.onLocationUpdate(
                event);
    }

    private static PartyMember partyMember(
            String displayName)
    {
        final PartyMember member =
                Mockito.mock(
                        PartyMember.class);

        Mockito.when(
                        member.getDisplayName())
                .thenReturn(
                        displayName);

        return member;
    }

    private static LocationUpdate locationUpdate(
            long memberId,
            WorldPoint worldPoint)
    {
        final LocationUpdate event =
                Mockito.mock(
                        LocationUpdate.class);

        Mockito.when(
                        event.getMemberId())
                .thenReturn(
                        memberId);

        Mockito.when(
                        event.getWorldPoint())
                .thenReturn(
                        worldPoint);

        return event;
    }

    private static StatusUpdate statusUpdate(
            long memberId,
            String characterName)
    {
        final StatusUpdate event =
                Mockito.mock(
                        StatusUpdate.class);

        Mockito.when(
                        event.getMemberId())
                .thenReturn(
                        memberId);

        Mockito.when(
                        event.getCharacterName())
                .thenReturn(
                        characterName);

        return event;
    }

    private static WorldPoint worldPoint(
            int x,
            int y,
            int plane)
    {
        return new WorldPoint(
                x,
                y,
                plane);
    }

    @SuppressWarnings("unchecked")
    private void putContextDirectly(
            PartyContext context)
            throws Exception
    {
        final Field field =
                PartyContextService.class
                        .getDeclaredField(
                                "contextsByMemberId");

        field.setAccessible(
                true);

        final Map<Long, PartyContext> contexts =
                (Map<Long, PartyContext>) field.get(
                        service);

        contexts.put(
                context.getMemberId(),
                context);
    }

    @SuppressWarnings("unchecked")
    private void putNameIndexDirectly(
            String playerName,
            long memberId)
            throws Exception
    {
        final Field field =
                PartyContextService.class
                        .getDeclaredField(
                                "memberIdsByName");

        field.setAccessible(
                true);

        final Map<String, Long> names =
                (Map<String, Long>) field.get(
                        service);

        names.put(
                nameNormalizer.comparisonKey(
                        playerName),
                memberId);
    }
}