package com.runetags.location;

import org.junit.Assert;
import org.junit.Test;

public class PlayerLocationTest
{
    /*
     * TESTS
     */

    @Test
    public void unknownHasNoLocation()
    {
        final PlayerLocation location =
                PlayerLocation.unknown();

        Assert.assertFalse(
                location.hasLocation());
    }

    @Test
    public void unknownHasNoRegion()
    {
        final PlayerLocation location =
                PlayerLocation.unknown();

        Assert.assertFalse(
                location.hasRegion());
    }

    @Test
    public void unknownUsesNullLocationName()
    {
        final PlayerLocation location =
                PlayerLocation.unknown();

        Assert.assertNull(
                location.getLocationName());
    }

    @Test
    public void unknownUsesNegativeRegionId()
    {
        final PlayerLocation location =
                PlayerLocation.unknown();

        Assert.assertEquals(
                -1,
                location.getRegionId());
    }

    @Test
    public void populatedLocationHasLocation()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Al Kharid",
                        12345);

        Assert.assertTrue(
                location.hasLocation());
    }

    @Test
    public void nullLocationNameHasNoLocation()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        null,
                        12345);

        Assert.assertFalse(
                location.hasLocation());
    }

    @Test
    public void emptyLocationNameHasNoLocation()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "",
                        12345);

        Assert.assertFalse(
                location.hasLocation());
    }

    @Test
    public void whitespaceLocationNameHasNoLocation()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "   ",
                        12345);

        Assert.assertFalse(
                location.hasLocation());
    }

    @Test
    public void zeroRegionIsValid()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        null,
                        0);

        Assert.assertTrue(
                location.hasRegion());
    }

    @Test
    public void positiveRegionIsValid()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        null,
                        12345);

        Assert.assertTrue(
                location.hasRegion());
    }

    @Test
    public void negativeRegionIsInvalid()
    {
        final PlayerLocation location =
                new PlayerLocation(
                        "Al Kharid",
                        -1);

        Assert.assertFalse(
                location.hasRegion());
    }

    @Test
    public void locationAndRegionStateAreIndependent()
    {
        final PlayerLocation namedWithoutRegion =
                new PlayerLocation(
                        "Al Kharid",
                        -1);

        final PlayerLocation regionWithoutName =
                new PlayerLocation(
                        null,
                        12345);

        Assert.assertTrue(
                namedWithoutRegion.hasLocation());

        Assert.assertFalse(
                namedWithoutRegion.hasRegion());

        Assert.assertFalse(
                regionWithoutName.hasLocation());

        Assert.assertTrue(
                regionWithoutName.hasRegion());
    }
}