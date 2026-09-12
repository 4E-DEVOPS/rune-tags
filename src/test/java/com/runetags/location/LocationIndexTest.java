package com.runetags.location;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class LocationIndexTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void indexLoadsBundledLocations()
    {
        final LocationIndex index =
                new LocationIndex(
                        new Gson());

        Assert.assertTrue(
                index.size() > 0);
    }

    @Test
    public void knownRegionResolvesExpectedLocation()
    {
        final LocationIndex index =
                new LocationIndex(
                        new Gson());

        Assert.assertEquals(
                "Al Kharid",
                index.findName(
                        regionId(
                                51,
                                49)));
    }

    @Test
    public void knownInstancedRegionResolvesExpectedLocation()
    {
        final LocationIndex index =
                new LocationIndex(
                        new Gson());

        Assert.assertEquals(
                "Ancient Prison",
                index.findName(
                        regionId(
                                21,
                                149)));
    }

    @Test
    public void alternateRegionForSameLocationResolvesExpectedLocation()
    {
        final LocationIndex index =
                new LocationIndex(
                        new Gson());

        Assert.assertEquals(
                "Abandoned Mine",
                index.findName(
                        regionId(
                                53,
                                150)));
    }

    @Test
    public void unknownRegionReturnsNull()
    {
        final LocationIndex index =
                new LocationIndex(
                        new Gson());

        Assert.assertNull(
                index.findName(
                        regionId(
                                255,
                                255)));
    }

    @Test
    public void sizeIsStableAcrossInstances()
    {
        final LocationIndex first =
                new LocationIndex(
                        new Gson());

        final LocationIndex second =
                new LocationIndex(
                        new Gson());

        Assert.assertEquals(
                first.size(),
                second.size());

        Assert.assertTrue(
                first.size() > 0);
    }

    @Test
    public void locationLookupPerformance()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final LocationIndex index =
                new LocationIndex(
                        new Gson());

        final int knownRegion =
                regionId(
                        51,
                        49);

        final int unknownRegion =
                regionId(
                        255,
                        255);

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            consume(
                    index.findName(
                            knownRegion));

            consume(
                    index.findName(
                            unknownRegion));
        }

        final long knownStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    index.findName(
                            knownRegion));
        }

        final long knownElapsed =
                System.nanoTime()
                        - knownStart;

        final long unknownStart =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            consume(
                    index.findName(
                            unknownRegion));
        }

        final long unknownElapsed =
                System.nanoTime()
                        - unknownStart;

        final double knownTotalMs =
                knownElapsed
                        / 1_000_000.0;

        final double unknownTotalMs =
                unknownElapsed
                        / 1_000_000.0;

        final double knownAverageMs =
                knownTotalMs
                        / PERFORMANCE_ITERATIONS;

        final double unknownAverageMs =
                unknownTotalMs
                        / PERFORMANCE_ITERATIONS;

        System.out.printf(
                "[RuneTags][LocationIndexTest] Performance= "
                        + "KnownLookup: %.3fms (%.6fms) | "
                        + "UnknownLookup: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                knownTotalMs,
                knownAverageMs,
                unknownTotalMs,
                unknownAverageMs,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * HELPERS
     */

    private static int regionId(
            int regionX,
            int regionY)
    {
        return (regionX << 8)
                | regionY;
    }

    /*
     * PERFORMANCE
     */

    private static volatile Object performanceSink;

    private static void consume(
            Object value)
    {
        performanceSink =
                value;
    }
}