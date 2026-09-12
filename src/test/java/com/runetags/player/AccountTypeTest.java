package com.runetags.player;

import net.runelite.api.IconID;

import org.junit.Assert;
import org.junit.Test;

public class AccountTypeTest
{
    /*
     * TESTS
     */

    @Test
    public void unknownIsNotKnown()
    {
        Assert.assertFalse(
                AccountType.UNKNOWN.isKnown());
    }

    @Test
    public void everyNonUnknownTypeIsKnown()
    {
        for (AccountType type
                : AccountType.values())
        {
            if (type
                    == AccountType.UNKNOWN)
            {
                continue;
            }

            Assert.assertTrue(
                    type.name(),
                    type.isKnown());
        }
    }

    @Test
    public void permanentAccountTypesAreClassifiedCorrectly()
    {
        Assert.assertTrue(
                AccountType.NORMAL
                        .isPermanentAccountType());

        Assert.assertTrue(
                AccountType.IRONMAN
                        .isPermanentAccountType());

        Assert.assertTrue(
                AccountType.HARDCORE
                        .isPermanentAccountType());

        Assert.assertTrue(
                AccountType.ULTIMATE
                        .isPermanentAccountType());

        Assert.assertTrue(
                AccountType.GROUP_IRONMAN
                        .isPermanentAccountType());

        Assert.assertTrue(
                AccountType.GROUP_HARDCORE
                        .isPermanentAccountType());

        Assert.assertTrue(
                AccountType.GROUP_UNRANKED
                        .isPermanentAccountType());
    }

    @Test
    public void nonPermanentTypesAreRejected()
    {
        Assert.assertFalse(
                AccountType.UNKNOWN
                        .isPermanentAccountType());

        Assert.assertFalse(
                AccountType.DEADMAN
                        .isPermanentAccountType());

        Assert.assertFalse(
                AccountType.LEAGUES
                        .isPermanentAccountType());

        Assert.assertFalse(
                AccountType.PLAYER_MODERATOR
                        .isPermanentAccountType());

        Assert.assertFalse(
                AccountType.JAGEX_MODERATOR
                        .isPermanentAccountType());
    }

    @Test
    public void onlyDeadmanAndLeaguesAreTemporary()
    {
        for (AccountType type
                : AccountType.values())
        {
            final boolean expected =
                    type == AccountType.DEADMAN
                            || type == AccountType.LEAGUES;

            Assert.assertEquals(
                    type.name(),
                    expected,
                    type.isTemporary());
        }
    }

    @Test
    public void onlyModeratorTypesAreModerators()
    {
        for (AccountType type
                : AccountType.values())
        {
            final boolean expected =
                    type == AccountType.PLAYER_MODERATOR
                            || type == AccountType.JAGEX_MODERATOR;

            Assert.assertEquals(
                    type.name(),
                    expected,
                    type.isModerator());
        }
    }

    @Test
    public void permanentTypeBeatsUnknown()
    {
        Assert.assertEquals(
                AccountType.IRONMAN,
                AccountType.prefer(
                        AccountType.UNKNOWN,
                        AccountType.IRONMAN));

        Assert.assertEquals(
                AccountType.IRONMAN,
                AccountType.prefer(
                        AccountType.IRONMAN,
                        AccountType.UNKNOWN));
    }

    @Test
    public void temporaryTypeBeatsPermanentType()
    {
        Assert.assertEquals(
                AccountType.LEAGUES,
                AccountType.prefer(
                        AccountType.IRONMAN,
                        AccountType.LEAGUES));

        Assert.assertEquals(
                AccountType.LEAGUES,
                AccountType.prefer(
                        AccountType.LEAGUES,
                        AccountType.IRONMAN));
    }

    @Test
    public void moderatorTypeBeatsPermanentType()
    {
        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                AccountType.prefer(
                        AccountType.NORMAL,
                        AccountType.PLAYER_MODERATOR));

        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                AccountType.prefer(
                        AccountType.PLAYER_MODERATOR,
                        AccountType.NORMAL));
    }

    @Test
    public void moderatorTypeBeatsTemporaryType()
    {
        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                AccountType.prefer(
                        AccountType.LEAGUES,
                        AccountType.PLAYER_MODERATOR));

        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                AccountType.prefer(
                        AccountType.PLAYER_MODERATOR,
                        AccountType.LEAGUES));
    }

    @Test
    public void jagexModeratorBeatsPermanentType()
    {
        Assert.assertEquals(
                AccountType.JAGEX_MODERATOR,
                AccountType.prefer(
                        AccountType.HARDCORE,
                        AccountType.JAGEX_MODERATOR));

        Assert.assertEquals(
                AccountType.JAGEX_MODERATOR,
                AccountType.prefer(
                        AccountType.JAGEX_MODERATOR,
                        AccountType.HARDCORE));
    }

    @Test
    public void sameTypeRemainsUnchanged()
    {
        for (AccountType type
                : AccountType.values())
        {
            Assert.assertEquals(
                    type.name(),
                    type,
                    AccountType.prefer(
                            type,
                            type));
        }
    }

    @Test
    public void unknownAgainstUnknownRemainsUnknown()
    {
        Assert.assertEquals(
                AccountType.UNKNOWN,
                AccountType.prefer(
                        AccountType.UNKNOWN,
                        AccountType.UNKNOWN));
    }

    @Test
    public void displayNamesMatchExpectedAccountTypes()
    {
        Assert.assertEquals(
                "Unknown",
                AccountType.UNKNOWN
                        .getDisplayName());

        Assert.assertEquals(
                "Normal",
                AccountType.NORMAL
                        .getDisplayName());

        Assert.assertEquals(
                "Ironman",
                AccountType.IRONMAN
                        .getDisplayName());

        Assert.assertEquals(
                "Hardcore Ironman",
                AccountType.HARDCORE
                        .getDisplayName());

        Assert.assertEquals(
                "Ultimate Ironman",
                AccountType.ULTIMATE
                        .getDisplayName());

        Assert.assertEquals(
                "Group Ironman",
                AccountType.GROUP_IRONMAN
                        .getDisplayName());

        Assert.assertEquals(
                "Hardcore Group Ironman",
                AccountType.GROUP_HARDCORE
                        .getDisplayName());

        Assert.assertEquals(
                "Unranked Group Ironman",
                AccountType.GROUP_UNRANKED
                        .getDisplayName());

        Assert.assertEquals(
                "Deadman",
                AccountType.DEADMAN
                        .getDisplayName());

        Assert.assertEquals(
                "Leagues",
                AccountType.LEAGUES
                        .getDisplayName());

        Assert.assertEquals(
                "Player Moderator",
                AccountType.PLAYER_MODERATOR
                        .getDisplayName());

        Assert.assertEquals(
                "Jagex Moderator",
                AccountType.JAGEX_MODERATOR
                        .getDisplayName());
    }

    @Test
    public void iconFileNamesMatchExpectedResources()
    {
        Assert.assertNull(
                AccountType.UNKNOWN
                        .getIconFileName());

        Assert.assertEquals(
                "normal.png",
                AccountType.NORMAL
                        .getIconFileName());

        Assert.assertEquals(
                "ironman.png",
                AccountType.IRONMAN
                        .getIconFileName());

        Assert.assertEquals(
                "hardcore.png",
                AccountType.HARDCORE
                        .getIconFileName());

        Assert.assertEquals(
                "ultimate.png",
                AccountType.ULTIMATE
                        .getIconFileName());

        Assert.assertEquals(
                "group_ironman.png",
                AccountType.GROUP_IRONMAN
                        .getIconFileName());

        Assert.assertEquals(
                "group_hardcore.png",
                AccountType.GROUP_HARDCORE
                        .getIconFileName());

        Assert.assertEquals(
                "group_unranked.png",
                AccountType.GROUP_UNRANKED
                        .getIconFileName());

        Assert.assertEquals(
                "deadman.png",
                AccountType.DEADMAN
                        .getIconFileName());

        Assert.assertEquals(
                "leagues.png",
                AccountType.LEAGUES
                        .getIconFileName());

        Assert.assertEquals(
                "player_moderator.png",
                AccountType.PLAYER_MODERATOR
                        .getIconFileName());

        Assert.assertEquals(
                "jagex_moderator.png",
                AccountType.JAGEX_MODERATOR
                        .getIconFileName());
    }

    @Test
    public void prioritiesFollowDisplayPrecedence()
    {
        Assert.assertTrue(
                AccountType.NORMAL.getPriority()
                        > AccountType.UNKNOWN.getPriority());

        Assert.assertEquals(
                AccountType.NORMAL.getPriority(),
                AccountType.IRONMAN.getPriority());

        Assert.assertEquals(
                AccountType.NORMAL.getPriority(),
                AccountType.HARDCORE.getPriority());

        Assert.assertEquals(
                AccountType.NORMAL.getPriority(),
                AccountType.ULTIMATE.getPriority());

        Assert.assertEquals(
                AccountType.NORMAL.getPriority(),
                AccountType.GROUP_IRONMAN.getPriority());

        Assert.assertEquals(
                AccountType.NORMAL.getPriority(),
                AccountType.GROUP_HARDCORE.getPriority());

        Assert.assertEquals(
                AccountType.NORMAL.getPriority(),
                AccountType.GROUP_UNRANKED.getPriority());

        Assert.assertTrue(
                AccountType.DEADMAN.getPriority()
                        > AccountType.NORMAL.getPriority());

        Assert.assertEquals(
                AccountType.DEADMAN.getPriority(),
                AccountType.LEAGUES.getPriority());

        Assert.assertTrue(
                AccountType.PLAYER_MODERATOR.getPriority()
                        > AccountType.LEAGUES.getPriority());

        Assert.assertTrue(
                AccountType.JAGEX_MODERATOR.getPriority()
                        > AccountType.PLAYER_MODERATOR.getPriority());
    }

    @Test
    public void preferTreatsNullAsUnknown()
    {
        Assert.assertEquals(
                AccountType.UNKNOWN,
                AccountType.prefer(
                        null,
                        null));

        Assert.assertEquals(
                AccountType.IRONMAN,
                AccountType.prefer(
                        null,
                        AccountType.IRONMAN));

        Assert.assertEquals(
                AccountType.IRONMAN,
                AccountType.prefer(
                        AccountType.IRONMAN,
                        null));
    }

    @Test
    public void equalPriorityPreservesFirstClassification()
    {
        Assert.assertEquals(
                AccountType.IRONMAN,
                AccountType.prefer(
                        AccountType.IRONMAN,
                        AccountType.HARDCORE));

        Assert.assertEquals(
                AccountType.HARDCORE,
                AccountType.prefer(
                        AccountType.HARDCORE,
                        AccountType.IRONMAN));

        Assert.assertEquals(
                AccountType.DEADMAN,
                AccountType.prefer(
                        AccountType.DEADMAN,
                        AccountType.LEAGUES));

        Assert.assertEquals(
                AccountType.LEAGUES,
                AccountType.prefer(
                        AccountType.LEAGUES,
                        AccountType.DEADMAN));
    }

    @Test
    public void nullAndEmptyChatNamesResolveUnknown()
    {
        Assert.assertEquals(
                AccountType.UNKNOWN,
                AccountType.fromChatName(
                        null,
                        false));

        Assert.assertEquals(
                AccountType.UNKNOWN,
                AccountType.fromChatName(
                        "",
                        false));
    }

    @Test
    public void ordinaryChatNameResolvesNormal()
    {
        Assert.assertEquals(
                AccountType.NORMAL,
                AccountType.fromChatName(
                        "Zezima",
                        false));
    }

    @Test
    public void nativePermanentIconsResolveExpectedAccountTypes()
    {
        Assert.assertEquals(
                AccountType.IRONMAN,
                AccountType.fromChatName(
                        IconID.IRONMAN.toString()
                                + "Zezima",
                        false));

        Assert.assertEquals(
                AccountType.HARDCORE,
                AccountType.fromChatName(
                        IconID.HARDCORE_IRONMAN.toString()
                                + "FasT 07",
                        false));

        Assert.assertEquals(
                AccountType.ULTIMATE,
                AccountType.fromChatName(
                        IconID.ULTIMATE_IRONMAN.toString()
                                + "Santa",
                        false));

        Assert.assertEquals(
                AccountType.GROUP_IRONMAN,
                AccountType.fromChatName(
                        IconID.GROUP_IRONMAN.toString()
                                + "Santa Clause",
                        false));

        Assert.assertEquals(
                AccountType.GROUP_HARDCORE,
                AccountType.fromChatName(
                        IconID.HARDCORE_GROUP_IRONMAN.toString()
                                + "Party Hat",
                        false));

        Assert.assertEquals(
                AccountType.GROUP_UNRANKED,
                AccountType.fromChatName(
                        IconID.UNRANKED_GROUP_IRONMAN.toString()
                                + "Zezima",
                        false));
    }

    @Test
    public void moderatorIconsResolveExpectedAccountTypes()
    {
        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                AccountType.fromChatName(
                        IconID.PLAYER_MODERATOR.toString()
                                + "Zezima",
                        false));

        Assert.assertEquals(
                AccountType.JAGEX_MODERATOR,
                AccountType.fromChatName(
                        IconID.JAGEX_MODERATOR.toString()
                                + "Zezima",
                        false));
    }

    @Test
    public void leaguesIconRequiresSeasonalWorld()
    {
        final String rawName =
                IconID.LEAGUE.toString()
                        + "Santa";

        Assert.assertEquals(
                AccountType.NORMAL,
                AccountType.fromChatName(
                        rawName,
                        false));

        Assert.assertEquals(
                AccountType.LEAGUES,
                AccountType.fromChatName(
                        rawName,
                        true));
    }

    @Test
    public void moderatorIconOverridesLeaguesAndPermanentIcons()
    {
        final String rawName =
                IconID.PLAYER_MODERATOR.toString()
                        + IconID.LEAGUE.toString()
                        + IconID.IRONMAN.toString()
                        + "Santa";

        Assert.assertEquals(
                AccountType.PLAYER_MODERATOR,
                AccountType.fromChatName(
                        rawName,
                        true));
    }

    @Test
    public void jagexModeratorOverridesPlayerModerator()
    {
        final String rawName =
                IconID.PLAYER_MODERATOR.toString()
                        + IconID.JAGEX_MODERATOR.toString()
                        + "Santa";

        Assert.assertEquals(
                AccountType.JAGEX_MODERATOR,
                AccountType.fromChatName(
                        rawName,
                        false));
    }

    @Test
    public void leaguesOverridesPermanentIconOnSeasonalWorld()
    {
        final String rawName =
                IconID.LEAGUE.toString()
                        + IconID.HARDCORE_IRONMAN.toString()
                        + "Santa";

        Assert.assertEquals(
                AccountType.LEAGUES,
                AccountType.fromChatName(
                        rawName,
                        true));
    }
}