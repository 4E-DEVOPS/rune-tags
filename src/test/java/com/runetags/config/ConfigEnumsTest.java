package com.runetags.config;

import org.junit.Assert;
import org.junit.Test;

public class ConfigEnumsTest
{
    /*
     * TESTS
     */

    @Test
    public void chatInteractionModeControlsLeftClick()
    {
        Assert.assertTrue(
                ChatInteractionMode.LEFT_CLICK.allowsLeftClick());

        Assert.assertFalse(
                ChatInteractionMode.RIGHT_CLICK.allowsLeftClick());

        Assert.assertTrue(
                ChatInteractionMode.BOTH.allowsLeftClick());
    }

    @Test
    public void chatInteractionModeControlsRightClick()
    {
        Assert.assertFalse(
                ChatInteractionMode.LEFT_CLICK.allowsRightClick());

        Assert.assertTrue(
                ChatInteractionMode.RIGHT_CLICK.allowsRightClick());

        Assert.assertTrue(
                ChatInteractionMode.BOTH.allowsRightClick());
    }

    @Test
    public void chatInteractionModeDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "Left-Click",
                ChatInteractionMode.LEFT_CLICK.toString());

        Assert.assertEquals(
                "Right-Click",
                ChatInteractionMode.RIGHT_CLICK.toString());

        Assert.assertEquals(
                "Both",
                ChatInteractionMode.BOTH.toString());
    }

    @Test
    public void clickablePlayerModeDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "All",
                ClickablePlayerMode.ALL.toString());

        Assert.assertEquals(
                "Both",
                ClickablePlayerMode.MENTIONS.toString());

        Assert.assertEquals(
                "Tagged",
                ClickablePlayerMode.TAGGED_ONLY.toString());
    }

    @Test
    public void hideOthersOffIsDisabled()
    {
        Assert.assertFalse(
                HideOthersMode.OFF.isEnabled());

        Assert.assertFalse(
                HideOthersMode.OFF.isTimed());

        Assert.assertFalse(
                HideOthersMode.OFF.isPersistent());

        Assert.assertEquals(
                0,
                HideOthersMode.OFF.getDurationSeconds());
    }

    @Test
    public void hideOthersTimedModesExposeExactDurations()
    {
        assertTimedHideMode(
                HideOthersMode.TEN,
                10);

        assertTimedHideMode(
                HideOthersMode.FIFTEEN,
                15);

        assertTimedHideMode(
                HideOthersMode.THIRTY,
                30);

        assertTimedHideMode(
                HideOthersMode.SIXTY,
                60);
    }

    @Test
    public void hideOthersOnIsPersistent()
    {
        Assert.assertTrue(
                HideOthersMode.ON.isEnabled());

        Assert.assertFalse(
                HideOthersMode.ON.isTimed());

        Assert.assertTrue(
                HideOthersMode.ON.isPersistent());

        Assert.assertEquals(
                -1,
                HideOthersMode.ON.getDurationSeconds());
    }

    @Test
    public void hideOthersDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "Off",
                HideOthersMode.OFF.toString());

        Assert.assertEquals(
                "10 Seconds",
                HideOthersMode.TEN.toString());

        Assert.assertEquals(
                "15 Seconds",
                HideOthersMode.FIFTEEN.toString());

        Assert.assertEquals(
                "30 Seconds",
                HideOthersMode.THIRTY.toString());

        Assert.assertEquals(
                "60 Seconds",
                HideOthersMode.SIXTY.toString());

        Assert.assertEquals(
                "On",
                HideOthersMode.ON.toString());
    }

    @Test
    public void lookupProviderDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "HiScores",
                LookupProvider.HISCORES.toString());

        Assert.assertEquals(
                "Wise Old Man",
                LookupProvider.WISE_OLD_MAN.toString());

        Assert.assertEquals(
                "RuneProfile",
                LookupProvider.RUNE_PROFILE.toString());
    }

    @Test
    public void mentionFontDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "Normal",
                MentionFont.NORMAL.toString());

        Assert.assertEquals(
                "Bold",
                MentionFont.BOLD.toString());

        Assert.assertEquals(
                "Verdana",
                MentionFont.VERDANA.toString());
    }

    @Test
    public void minimapIndicatorDiametersAreStable()
    {
        Assert.assertEquals(
                0,
                MinimapIndicatorMode.OFF.getDiameter());

        Assert.assertEquals(
                4,
                MinimapIndicatorMode.NORMAL.getDiameter());

        Assert.assertEquals(
                6,
                MinimapIndicatorMode.MEDIUM.getDiameter());

        Assert.assertEquals(
                8,
                MinimapIndicatorMode.LARGE.getDiameter());
    }

    @Test
    public void minimapIndicatorDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "Off",
                MinimapIndicatorMode.OFF.toString());

        Assert.assertEquals(
                "Normal",
                MinimapIndicatorMode.NORMAL.toString());

        Assert.assertEquals(
                "Medium",
                MinimapIndicatorMode.MEDIUM.toString());

        Assert.assertEquals(
                "Large",
                MinimapIndicatorMode.LARGE.toString());
    }

    @Test
    public void targetModeControlsOutlineVisibility()
    {
        Assert.assertFalse(
                TargetMode.OFF.showsOutline());

        Assert.assertTrue(
                TargetMode.OUTLINE.showsOutline());

        Assert.assertFalse(
                TargetMode.TILE.showsOutline());

        Assert.assertTrue(
                TargetMode.BOTH.showsOutline());
    }

    @Test
    public void targetModeControlsTileVisibility()
    {
        Assert.assertFalse(
                TargetMode.OFF.showsTile());

        Assert.assertFalse(
                TargetMode.OUTLINE.showsTile());

        Assert.assertTrue(
                TargetMode.TILE.showsTile());

        Assert.assertTrue(
                TargetMode.BOTH.showsTile());
    }

    @Test
    public void targetModeDisplayNamesAreStable()
    {
        Assert.assertEquals(
                "Off",
                TargetMode.OFF.toString());

        Assert.assertEquals(
                "Outline",
                TargetMode.OUTLINE.toString());

        Assert.assertEquals(
                "Tile",
                TargetMode.TILE.toString());

        Assert.assertEquals(
                "Both",
                TargetMode.BOTH.toString());
    }

    /*
     * HELPERS
     */

    private static void assertTimedHideMode(
            HideOthersMode mode,
            int expectedDurationSeconds)
    {
        Assert.assertTrue(
                mode.isEnabled());

        Assert.assertTrue(
                mode.isTimed());

        Assert.assertFalse(
                mode.isPersistent());

        Assert.assertEquals(
                expectedDurationSeconds,
                mode.getDurationSeconds());
    }
}