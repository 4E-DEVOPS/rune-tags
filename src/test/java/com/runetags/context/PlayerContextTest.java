package com.runetags.context;

import com.runetags.location.PlayerLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class PlayerContextTest
{
    /*
     * TESTS
     */

    @Test
    public void nullLocationBecomesUnknownLocation()
    {
        final PlayerContext context =
                new PlayerContext(
                        null,
                        Collections.emptyList());

        Assert.assertFalse(
                context.hasLocation());

        Assert.assertEquals(
                -1,
                context.getRegionId());

        Assert.assertNull(
                context.getLocationName());
    }

    @Test
    public void nullMetricsBecomeEmptyList()
    {
        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889),
                        null);

        Assert.assertNotNull(
                context.getMetrics());

        Assert.assertTrue(
                context.getMetrics()
                        .isEmpty());

        Assert.assertFalse(
                context.hasMetrics());
    }

    @Test
    public void preservesResolvedLocation()
    {
        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889),
                        Collections.emptyList());

        Assert.assertTrue(
                context.hasLocation());

        Assert.assertEquals(
                "Chambers of Xeric",
                context.getLocationName());

        Assert.assertEquals(
                12889,
                context.getRegionId());
    }

    @Test
    public void reportsMetricsWhenPresent()
    {
        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889),
                        Collections.singletonList(
                                new ProfileMetric(
                                        "CoX",
                                        "CHAMBERS_OF_XERIC")));

        Assert.assertTrue(
                context.hasMetrics());

        Assert.assertEquals(
                1,
                context.getMetrics()
                        .size());
    }

    @Test
    public void metricsListIsUnmodifiable()
    {
        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889),
                        Collections.singletonList(
                                new ProfileMetric(
                                        "CoX",
                                        "CHAMBERS_OF_XERIC")));

        try
        {
            context.getMetrics()
                    .add(
                            new ProfileMetric(
                                    "CM",
                                    "CHAMBERS_OF_XERIC_CHALLENGE_MODE"));

            Assert.fail(
                    "Expected metrics list to be unmodifiable");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }
    }

    @Test
    public void constructorDoesNotDefensivelyCopyMetrics()
    {
        final List<ProfileMetric> metrics =
                new ArrayList<>(
                        Collections.singletonList(
                                new ProfileMetric(
                                        "CoX",
                                        "CHAMBERS_OF_XERIC")));

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889),
                        metrics);

        metrics.add(
                new ProfileMetric(
                        "CM",
                        "CHAMBERS_OF_XERIC_CHALLENGE_MODE"));

        Assert.assertEquals(
                2,
                context.getMetrics()
                        .size());
    }

    @Test
    public void unknownProducesFullyUnknownContext()
    {
        final PlayerContext context =
                PlayerContext.unknown();

        Assert.assertFalse(
                context.hasLocation());

        Assert.assertFalse(
                context.hasMetrics());

        Assert.assertNull(
                context.getLocationName());

        Assert.assertEquals(
                -1,
                context.getRegionId());
    }

    @Test
    public void unknownRegionPreservesObservedRegion()
    {
        final PlayerContext context =
                PlayerContext.unknown(
                        12345);

        Assert.assertFalse(
                context.hasLocation());

        Assert.assertFalse(
                context.hasMetrics());

        Assert.assertNull(
                context.getLocationName());

        Assert.assertEquals(
                12345,
                context.getRegionId());
    }

    @Test
    public void multipleMetricsPreserveOrder()
    {
        final ProfileMetric cox =
                new ProfileMetric(
                        "CoX",
                        "CHAMBERS_OF_XERIC");

        final ProfileMetric cm =
                new ProfileMetric(
                        "CM",
                        "CHAMBERS_OF_XERIC_CHALLENGE_MODE");

        final PlayerContext context =
                new PlayerContext(
                        new PlayerLocation(
                                "Chambers of Xeric",
                                12889),
                        Arrays.asList(
                                cox,
                                cm));

        Assert.assertEquals(
                Arrays.asList(
                        cox,
                        cm),
                context.getMetrics());
    }
}