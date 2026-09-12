package com.runetags.context;

import com.runetags.location.PlayerLocation;
import com.runetags.location.PlayerLocationService;

import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PlayerContextServiceTest
{
    private Client client;
    private PlayerLocationService playerLocationService;
    private ProfileMetricResolver resolver;
    private PlayerContextService service;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        playerLocationService =
                Mockito.mock(
                        PlayerLocationService.class);

        resolver =
                new ProfileMetricResolver();

        service =
                new PlayerContextService(
                        client,
                        playerLocationService,
                        resolver);
    }

    /*
     * TESTS
     */

    @Test
    public void startsUnknown()
    {
        final PlayerContext current =
                service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasMetrics());

        Assert.assertEquals(
                -1,
                current.getRegionId());
    }

    @Test
    public void clearRestoresUnknownContext()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        null);

        service.refresh();

        Assert.assertTrue(
                service.getCurrent()
                        .hasLocation());

        service.clear();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasMetrics());

        Assert.assertEquals(
                -1,
                current.getRegionId());
    }

    @Test
    public void nullLocationClearsCurrentContext()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        null);

        service.refresh();

        Assert.assertTrue(
                service.getCurrent()
                        .hasLocation());

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        null);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasMetrics());

        Assert.assertEquals(
                -1,
                current.getRegionId());
    }

    @Test
    public void locationWithoutRegionClearsCurrentContext()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        -1);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasMetrics());

        Assert.assertEquals(
                -1,
                current.getRegionId());

        Mockito.verify(
                        client,
                        Mockito.never())
                .getTopLevelWorldView();
    }

    @Test
    public void nullWorldViewFallsBackToLocationResolution()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        null);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertTrue(
                current.hasLocation());

        Assert.assertEquals(
                "Chambers of Xeric",
                current.getLocationName());

        Assert.assertEquals(
                12889,
                current.getRegionId());

        Assert.assertTrue(
                current.hasMetrics());

        Assert.assertEquals(
                2,
                current.getMetrics()
                        .size());

        Assert.assertEquals(
                "CoX",
                current.getMetrics()
                        .get(0)
                        .getLabel());

        Assert.assertEquals(
                "CM",
                current.getMetrics()
                        .get(1)
                        .getLabel());
    }

    @Test
    public void emptyNpcCollectionFallsBackToLocationResolution()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        final WorldView worldView =
                emptyWorldView();

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertTrue(
                current.hasLocation());

        Assert.assertEquals(
                "Chambers of Xeric",
                current.getLocationName());

        Assert.assertEquals(
                12889,
                current.getRegionId());

        Assert.assertTrue(
                current.hasMetrics());

        Assert.assertEquals(
                2,
                current.getMetrics()
                        .size());
    }

    @Test
    public void nullNpcIsIgnored()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        final WorldView worldView =
                worldView(
                        (NPC) null);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Chambers of Xeric",
                current.getLocationName());

        Assert.assertEquals(
                12889,
                current.getRegionId());

        Assert.assertEquals(
                2,
                current.getMetrics()
                        .size());
    }

    @Test
    public void unnamedNpcIsIgnored()
    {
        final NPC npc =
                Mockito.mock(
                        NPC.class);

        Mockito.when(
                        npc.getName())
                .thenReturn(
                        null);

        final WorldView worldView =
                worldView(
                        npc);

        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Chambers of Xeric",
                current.getLocationName());

        Assert.assertEquals(
                2,
                current.getMetrics()
                        .size());
    }

    @Test
    public void emptyNpcNameIsIgnored()
    {
        final NPC npc =
                npc(
                        "");

        final WorldView worldView =
                worldView(
                        npc);

        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Chambers of Xeric",
                current.getLocationName());

        Assert.assertEquals(
                2,
                current.getMetrics()
                        .size());
    }

    @Test
    public void ordinaryNpcDoesNotOverrideLocation()
    {
        final NPC man =
                npc(
                        "Man");

        final WorldView worldView =
                worldView(
                        man);

        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Chambers of Xeric",
                current.getLocationName());

        Assert.assertEquals(
                12889,
                current.getRegionId());

        Assert.assertEquals(
                2,
                current.getMetrics()
                        .size());
    }

    @Test
    public void encounterNpcOverridesCoarseLocation()
    {
        final NPC vorkath =
                npc(
                        "Vorkath");

        final WorldView worldView =
                worldView(
                        vorkath);

        final PlayerLocation location =
                new PlayerLocation(
                        "Ungael",
                        9023);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertTrue(
                current.hasLocation());

        Assert.assertEquals(
                "Ungael",
                current.getLocationName());

        Assert.assertEquals(
                9023,
                current.getRegionId());

        Assert.assertTrue(
                current.hasMetrics());

        Assert.assertEquals(
                1,
                current.getMetrics()
                        .size());

        Assert.assertEquals(
                "Vorkath KC",
                current.getMetrics()
                        .get(0)
                        .getLabel());

        Assert.assertEquals(
                "VORKATH",
                current.getMetrics()
                        .get(0)
                        .getHiscoreSkillName());
    }

    @Test
    public void firstMatchingNpcOverrideWins()
    {
        final NPC vorkath =
                npc(
                        "Vorkath");

        final NPC zulrah =
                npc(
                        "Zulrah");

        final WorldView worldView =
                worldView(
                        vorkath,
                        zulrah);

        final PlayerLocation location =
                new PlayerLocation(
                        "Ungael",
                        9023);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Ungael",
                current.getLocationName());

        Assert.assertEquals(
                1,
                current.getMetrics()
                        .size());

        Assert.assertEquals(
                "VORKATH",
                current.getMetrics()
                        .get(0)
                        .getHiscoreSkillName());
    }

    @Test
    public void scansPastNonMatchingNpcToLaterOverride()
    {
        final NPC man =
                npc(
                        "Man");

        final NPC vorkath =
                npc(
                        "Vorkath");

        final WorldView worldView =
                worldView(
                        man,
                        vorkath);

        final PlayerLocation location =
                new PlayerLocation(
                        "Ungael",
                        9023);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Ungael",
                current.getLocationName());

        Assert.assertEquals(
                1,
                current.getMetrics()
                        .size());

        Assert.assertEquals(
                "VORKATH",
                current.getMetrics()
                        .get(0)
                        .getHiscoreSkillName());
    }

    @Test
    public void npcOverrideCanResolveUnmappedLocation()
    {
        final NPC vorkath =
                npc(
                        "Vorkath");

        final WorldView worldView =
                worldView(
                        vorkath);

        final PlayerLocation location =
                new PlayerLocation(
                        "Definitely Unknown Place",
                        54321);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Ungael",
                current.getLocationName());

        Assert.assertEquals(
                54321,
                current.getRegionId());

        Assert.assertEquals(
                1,
                current.getMetrics()
                        .size());

        Assert.assertEquals(
                "VORKATH",
                current.getMetrics()
                        .get(0)
                        .getHiscoreSkillName());
    }

    @Test
    public void unmappedLocationWithoutEncounterPreservesRegion()
    {
        final WorldView worldView =
                emptyWorldView();

        final PlayerLocation location =
                new PlayerLocation(
                        "Definitely Unknown Place",
                        54321);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        location);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertTrue(
                current.hasLocation());

        Assert.assertEquals(
                "Definitely Unknown Place",
                current.getLocationName());

        Assert.assertEquals(
                54321,
                current.getRegionId());

        Assert.assertFalse(
                current.hasMetrics());
    }

    @Test
    public void refreshReplacesPreviousContext()
    {
        final PlayerLocation firstLocation =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        firstLocation);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        null);

        service.refresh();

        Assert.assertEquals(
                "Chambers of Xeric",
                service.getCurrent()
                        .getLocationName());

        final PlayerLocation secondLocation =
                new PlayerLocation(
                        "Definitely Unknown Place",
                        54321);

        Mockito.when(
                        playerLocationService.getCurrent())
                .thenReturn(
                        secondLocation);

        service.refresh();

        final PlayerContext current =
                service.getCurrent();

        Assert.assertEquals(
                "Definitely Unknown Place",
                current.getLocationName());

        Assert.assertEquals(
                54321,
                current.getRegionId());

        Assert.assertFalse(
                current.hasMetrics());
    }

    /*
     * HELPERS
     */

    private static NPC npc(
            String name)
    {
        final NPC npc =
                Mockito.mock(
                        NPC.class);

        Mockito.when(
                        npc.getName())
                .thenReturn(
                        name);

        return npc;
    }

    @SuppressWarnings("unchecked")
    private static WorldView emptyWorldView()
    {
        final WorldView worldView =
                Mockito.mock(
                        WorldView.class);

        final IndexedObjectSet<NPC> npcs =
                Mockito.mock(
                        IndexedObjectSet.class);

        Mockito.when(
                        npcs.iterator())
                .thenAnswer(
                        invocation ->
                                java.util.Collections
                                        .<NPC>emptyList()
                                        .iterator());

        Mockito.doReturn(
                        npcs)
                .when(
                        worldView)
                .npcs();

        return worldView;
    }

    @SuppressWarnings("unchecked")
    private static WorldView worldView(
            NPC... npcValues)
    {
        final WorldView worldView =
                Mockito.mock(
                        WorldView.class);

        final IndexedObjectSet<NPC> npcs =
                Mockito.mock(
                        IndexedObjectSet.class);

        Mockito.when(
                        npcs.iterator())
                .thenAnswer(
                        invocation ->
                                java.util.Arrays
                                        .asList(
                                                npcValues)
                                        .iterator());

        Mockito.doReturn(
                        npcs)
                .when(
                        worldView)
                .npcs();

        return worldView;
    }
}