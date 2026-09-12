package com.runetags.context;

import com.runetags.location.PlayerLocation;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProfileMetricResolverTest
{
    private ProfileMetricResolver resolver;

    @Before
    public void setUp()
    {
        resolver =
                new ProfileMetricResolver();
    }

    /*
     * TESTS
     */

    @Test
    public void nullLocationResolvesUnknownContext()
    {
        final PlayerContext context =
                resolver.resolveLocation(
                        null);

        Assert.assertNotNull(
                context);

        Assert.assertFalse(
                context.hasLocation());

        Assert.assertFalse(
                context.hasMetrics());

        Assert.assertEquals(
                -1,
                context.getRegionId());
    }

    @Test
    public void knownLocationResolvesCatalogMetrics()
    {
        final PlayerContext context =
                resolver.resolveLocation(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889));

        Assert.assertTrue(
                context.hasLocation());

        Assert.assertEquals(
                "Chambers of Xeric",
                context.getLocationName());

        Assert.assertEquals(
                12889,
                context.getRegionId());

        Assert.assertEquals(
                2,
                context.getMetrics()
                        .size());

        assertMetric(
                context.getMetrics(),
                0,
                "CoX",
                "CHAMBERS_OF_XERIC");

        assertMetric(
                context.getMetrics(),
                1,
                "CM",
                "CHAMBERS_OF_XERIC_CHALLENGE_MODE");
    }

    @Test
    public void knownLocationLookupIsCaseInsensitive()
    {
        final PlayerContext context =
                resolver.resolveLocation(
                        new PlayerLocation(
                                "chambers of xeric",
                                12889));

        Assert.assertEquals(
                2,
                context.getMetrics()
                        .size());
    }

    @Test
    public void unmappedLocationPreservesLocationWithoutMetrics()
    {
        final PlayerContext context =
                resolver.resolveLocation(
                        new PlayerLocation(
                                "Definitely Unknown Place",
                                54321));

        Assert.assertTrue(
                context.hasLocation());

        Assert.assertEquals(
                "Definitely Unknown Place",
                context.getLocationName());

        Assert.assertEquals(
                54321,
                context.getRegionId());

        Assert.assertFalse(
                context.hasMetrics());
    }

    @Test
    public void locationWithoutNamePreservesRegionWithoutMetrics()
    {
        final PlayerContext context =
                resolver.resolveLocation(
                        new PlayerLocation(
                                null,
                                54321));

        Assert.assertFalse(
                context.hasLocation());

        Assert.assertEquals(
                54321,
                context.getRegionId());

        Assert.assertFalse(
                context.hasMetrics());
    }

    @Test
    public void nullNpcNameDoesNotProduceOverride()
    {
        Assert.assertNull(
                resolver.resolveNpc(
                        null,
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889)));
    }

    @Test
    public void unknownNpcDoesNotProduceOverride()
    {
        Assert.assertNull(
                resolver.resolveNpc(
                        "Definitely Unknown NPC",
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889)));
    }

    @Test
    public void knownNpcProducesOverrideContext()
    {
        final PlayerContext context =
                resolver.resolveNpc(
                        "Vorkath",
                        new PlayerLocation(
                                "Ungael",
                                9023));

        Assert.assertNotNull(
                context);

        Assert.assertTrue(
                context.hasLocation());

        Assert.assertTrue(
                context.hasMetrics());

        Assert.assertEquals(
                9023,
                context.getRegionId());

        assertMetric(
                context.getMetrics(),
                0,
                "Vorkath KC",
                "VORKATH");
    }

    @Test
    public void npcLookupIsCaseInsensitive()
    {
        final PlayerContext context =
                resolver.resolveNpc(
                        "vorkath",
                        new PlayerLocation(
                                "Ungael",
                                9023));

        Assert.assertNotNull(
                context);

        Assert.assertEquals(
                1,
                context.getMetrics()
                        .size());
    }

    @Test
    public void npcOverridePreservesObservedRegion()
    {
        final PlayerContext context =
                resolver.resolveNpc(
                        "Vorkath",
                        new PlayerLocation(
                                null,
                                45678));

        Assert.assertNotNull(
                context);

        Assert.assertEquals(
                45678,
                context.getRegionId());
    }

    @Test
    public void npcOverrideWithoutLocationUsesUnknownRegion()
    {
        final PlayerContext context =
                resolver.resolveNpc(
                        "Vorkath",
                        null);

        Assert.assertNotNull(
                context);

        Assert.assertEquals(
                -1,
                context.getRegionId());

        Assert.assertTrue(
                context.hasLocation());

        Assert.assertTrue(
                context.hasMetrics());
    }

    @Test
    public void unknownPreservesProvidedLocationAndClearsMetrics()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Chambers of Xeric",
                        12889);

        final PlayerContext context =
                resolver.unknown(
                        location);

        Assert.assertSame(
                location,
                context.getLocation());

        Assert.assertEquals(
                "Chambers of Xeric",
                context.getLocationName());

        Assert.assertEquals(
                12889,
                context.getRegionId());

        Assert.assertFalse(
                context.hasMetrics());
    }

    @Test
    public void unknownWithNullLocationProducesUnknownLocation()
    {
        final PlayerContext context =
                resolver.unknown(
                        null);

        Assert.assertFalse(
                context.hasLocation());

        Assert.assertFalse(
                context.hasMetrics());

        Assert.assertEquals(
                -1,
                context.getRegionId());
    }

    /*
     * HELPERS
     */

    private static void assertMetric(
            List<ProfileMetric> metrics,
            int index,
            String expectedLabel,
            String expectedHiscoreSkillName)
    {
        final ProfileMetric metric =
                metrics.get(
                        index);

        Assert.assertEquals(
                expectedLabel,
                metric.getLabel());

        Assert.assertEquals(
                expectedHiscoreSkillName,
                metric.getHiscoreSkillName());
    }
}