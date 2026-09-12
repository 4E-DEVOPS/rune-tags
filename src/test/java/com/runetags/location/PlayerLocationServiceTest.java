package com.runetags.location;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class PlayerLocationServiceTest
{
    /*
     * TESTS
     */

    @Test
    public void initialLocationIsUnknown()
    {
        final TestHarness harness =
                createHarness();

        final PlayerLocation current =
                harness.service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasRegion());

        Assert.assertNull(
                current.getLocationName());

        Assert.assertEquals(
                -1,
                current.getRegionId());
    }

    @Test
    public void clearRestoresUnknownLocation()
    {
        final TestHarness harness =
                createHarness();

        final PlayerLocation existing =
                new PlayerLocation(
                        "Al Kharid",
                        12345);

        setCurrent(
                harness.service,
                existing);

        harness.service.clear();

        final PlayerLocation current =
                harness.service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasRegion());

        Assert.assertEquals(
                -1,
                current.getRegionId());
    }

    @Test
    public void refreshClearsWhenLocalPlayerIsNull()
    {
        final TestHarness harness =
                createHarness();

        setCurrent(
                harness.service,
                new PlayerLocation(
                        "Al Kharid",
                        12345));

        Mockito.when(
                        harness.client.getLocalPlayer())
                .thenReturn(
                        null);

        harness.service.refresh();

        final PlayerLocation current =
                harness.service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasRegion());
    }

    @Test
    public void refreshClearsWhenWorldLocationIsNull()
    {
        final TestHarness harness =
                createHarness();

        final Player player =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        harness.client.getLocalPlayer())
                .thenReturn(
                        player);

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        null);

        setCurrent(
                harness.service,
                new PlayerLocation(
                        "Al Kharid",
                        12345));

        harness.service.refresh();

        final PlayerLocation current =
                harness.service.getCurrent();

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertFalse(
                current.hasRegion());
    }

    @Test
    public void refreshUsesPlayersRegionId()
    {
        final TestHarness harness =
                createHarness();

        final Player player =
                Mockito.mock(
                        Player.class);

        final WorldPoint point =
                new WorldPoint(
                        3200,
                        3200,
                        0);

        final int expectedRegionId =
                point.getRegionID();

        Mockito.when(
                        harness.client.getLocalPlayer())
                .thenReturn(
                        player);

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        point);

        Mockito.when(
                        harness.locationIndex.findName(
                                expectedRegionId))
                .thenReturn(
                        "Al Kharid");

        harness.service.refresh();

        Assert.assertEquals(
                expectedRegionId,
                harness.service
                        .getCurrent()
                        .getRegionId());
    }

    @Test
    public void refreshResolvesLocationThroughIndex()
    {
        final TestHarness harness =
                createHarness();

        final Player player =
                Mockito.mock(
                        Player.class);

        final WorldPoint point =
                new WorldPoint(
                        3200,
                        3200,
                        0);

        final int regionId =
                point.getRegionID();

        Mockito.when(
                        harness.client.getLocalPlayer())
                .thenReturn(
                        player);

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        point);

        Mockito.when(
                        harness.locationIndex.findName(
                                regionId))
                .thenReturn(
                        "Al Kharid");

        harness.service.refresh();

        final PlayerLocation current =
                harness.service.getCurrent();

        Assert.assertEquals(
                "Al Kharid",
                current.getLocationName());

        Assert.assertTrue(
                current.hasLocation());

        Mockito.verify(
                        harness.locationIndex,
                        Mockito.times(
                                1))
                .findName(
                        regionId);
    }

    @Test
    public void refreshPreservesRegionWhenLocationIsUnknown()
    {
        final TestHarness harness =
                createHarness();

        final Player player =
                Mockito.mock(
                        Player.class);

        final WorldPoint point =
                new WorldPoint(
                        3200,
                        3200,
                        0);

        final int regionId =
                point.getRegionID();

        Mockito.when(
                        harness.client.getLocalPlayer())
                .thenReturn(
                        player);

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        point);

        Mockito.when(
                        harness.locationIndex.findName(
                                regionId))
                .thenReturn(
                        null);

        harness.service.refresh();

        final PlayerLocation current =
                harness.service.getCurrent();

        Assert.assertNull(
                current.getLocationName());

        Assert.assertFalse(
                current.hasLocation());

        Assert.assertTrue(
                current.hasRegion());

        Assert.assertEquals(
                regionId,
                current.getRegionId());
    }

    @Test
    public void refreshReplacesPreviousSnapshot()
    {
        final TestHarness harness =
                createHarness();

        final Player player =
                Mockito.mock(
                        Player.class);

        final WorldPoint firstPoint =
                new WorldPoint(
                        3200,
                        3200,
                        0);

        final WorldPoint secondPoint =
                new WorldPoint(
                        3300,
                        3300,
                        0);

        Mockito.when(
                        harness.client.getLocalPlayer())
                .thenReturn(
                        player);

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        firstPoint);

        Mockito.when(
                        harness.locationIndex.findName(
                                firstPoint.getRegionID()))
                .thenReturn(
                        "Al Kharid");

        harness.service.refresh();

        final PlayerLocation first =
                harness.service.getCurrent();

        Mockito.when(
                        player.getWorldLocation())
                .thenReturn(
                        secondPoint);

        Mockito.when(
                        harness.locationIndex.findName(
                                secondPoint.getRegionID()))
                .thenReturn(
                        "Ape Atoll");

        harness.service.refresh();

        final PlayerLocation second =
                harness.service.getCurrent();

        Assert.assertNotSame(
                first,
                second);

        Assert.assertEquals(
                "Ape Atoll",
                second.getLocationName());

        Assert.assertEquals(
                secondPoint.getRegionID(),
                second.getRegionId());
    }

    @Test
    public void getCurrentReturnsCurrentSnapshot()
    {
        final TestHarness harness =
                createHarness();

        final PlayerLocation expected =
                new PlayerLocation(
                        "Al Kharid",
                        12345);

        setCurrent(
                harness.service,
                expected);

        Assert.assertSame(
                expected,
                harness.service.getCurrent());
    }

    /*
     * HELPERS
     */

    private static TestHarness createHarness()
    {
        final Client client =
                Mockito.mock(
                        Client.class);

        final LocationIndex locationIndex =
                Mockito.mock(
                        LocationIndex.class);

        return new TestHarness(
                client,
                locationIndex,
                new PlayerLocationService(
                        client,
                        locationIndex));
    }

    private static void setCurrent(
            PlayerLocationService service,
            PlayerLocation location)
    {
        try
        {
            final java.lang.reflect.Field field =
                    PlayerLocationService.class
                            .getDeclaredField(
                                    "current");

            field.setAccessible(
                    true);

            field.set(
                    service,
                    location);
        }
        catch (ReflectiveOperationException ex)
        {
            throw new AssertionError(
                    ex);
        }
    }

    private static final class TestHarness
    {
        private final Client client;
        private final LocationIndex locationIndex;
        private final PlayerLocationService service;

        private TestHarness(
                Client client,
                LocationIndex locationIndex,
                PlayerLocationService service)
        {
            this.client =
                    client;

            this.locationIndex =
                    locationIndex;

            this.service =
                    service;
        }
    }
}