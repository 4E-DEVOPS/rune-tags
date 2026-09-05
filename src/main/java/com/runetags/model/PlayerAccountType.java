package com.runetags.model;

import net.runelite.api.IconID;

public enum PlayerAccountType
{
    UNKNOWN(
            "Unknown",
            null),

    NORMAL(
            "Normal",
            "normal.png"),

    IRONMAN(
            "Ironman",
            "ironman.png"),

    HARDCORE(
            "Hardcore Ironman",
            "hardcore.png"),

    ULTIMATE(
            "Ultimate Ironman",
            "ultimate.png"),

    GROUP_IRONMAN(
            "Group Ironman",
            "group_ironman.png"),

    GROUP_HARDCORE(
            "Hardcore Group Ironman",
            "group_hardcore.png"),

    GROUP_UNRANKED(
            "Unranked Group Ironman",
            "group_unranked.png"),

    DEADMAN(
            "Deadman",
            "deadman.png"),

    LEAGUES(
            "Leagues",
            "leagues.png"),

    PLAYER_MODERATOR(
            "Player Moderator",
            "player_moderator.png"),

    JAGEX_MODERATOR(
            "Jagex Moderator",
            "jagex_moderator.png");

    private final String displayName;
    private final String iconFileName;

    PlayerAccountType(
            String displayName,
            String iconFileName)
    {
        this.displayName = displayName;
        this.iconFileName = iconFileName;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getIconFileName()
    {
        return iconFileName;
    }

    public boolean isKnown()
    {
        return this != UNKNOWN;
    }

    public boolean isTemporary()
    {
        return this == DEADMAN
                || this == LEAGUES;
    }

    public boolean isPermanentAccountType()
    {
        switch (this)
        {
            case NORMAL:
            case IRONMAN:
            case HARDCORE:
            case ULTIMATE:
            case GROUP_IRONMAN:
            case GROUP_HARDCORE:
            case GROUP_UNRANKED:
                return true;

            case UNKNOWN:
            case DEADMAN:
            case LEAGUES:
            case PLAYER_MODERATOR:
            case JAGEX_MODERATOR:
            default:
                return false;
        }
    }

    public boolean isModerator()
    {
        return this == JAGEX_MODERATOR
                || this == PLAYER_MODERATOR;
    }

    /**
     * Display precedence for competing account classifications.
     *
     * Moderator crowns override temporary game-mode icons.
     * Leagues overrides the permanent account-mode icon while applicable.
     * Permanent account types share the same priority because they represent
     * mutually exclusive account modes rather than stronger/weaker badges.
     */
    public int getPriority()
    {
        switch (this)
        {
            case JAGEX_MODERATOR:
                return 100;

            case PLAYER_MODERATOR:
                return 90;

            case DEADMAN:
            case LEAGUES:
                return 80;

            case GROUP_HARDCORE:
            case GROUP_UNRANKED:
            case GROUP_IRONMAN:
            case HARDCORE:
            case ULTIMATE:
            case IRONMAN:
            case NORMAL:
                return 50;

            case UNKNOWN:
            default:
                return 0;
        }
    }

    /**
     * Prefer the stronger of two account classifications.
     *
     * Equal priority preserves the first value. This prevents one permanent
     * account-mode classification from arbitrarily replacing another merely
     * because it was observed later.
     */
    public static PlayerAccountType prefer(
            PlayerAccountType first,
            PlayerAccountType second)
    {
        final PlayerAccountType left =
                first != null
                        ? first
                        : UNKNOWN;

        final PlayerAccountType right =
                second != null
                        ? second
                        : UNKNOWN;

        return right.getPriority() > left.getPriority()
                ? right
                : left;
    }


    /**
     * Resolve an account type from Jagex's native chat-name markup.
     *
     * RuneScape supplies the official account-mode icons as <img=X> tags
     * inside player-name strings.
     */
    public static PlayerAccountType fromChatName(
            String rawName,
            boolean leaguesWorld)
    {
        if (rawName == null
                || rawName.isEmpty())
        {
            return UNKNOWN;
        }

        /*
         * Display precedence:
         *
         * Jagex Moderator
         * Player Moderator
         * Leagues
         * Permanent account type
         */

        if (rawName.contains(
                IconID.JAGEX_MODERATOR.toString()))
        {
            return JAGEX_MODERATOR;
        }

        if (rawName.contains(
                IconID.PLAYER_MODERATOR.toString()))
        {
            return PLAYER_MODERATOR;
        }

        /*
         * Leagues is a temporary display classification.
         *
         * Never classify somebody as LEAGUES unless the current world is
         * actually a seasonal/Leagues world.
         */
        if (leaguesWorld
                && rawName.contains(
                IconID.LEAGUE.toString()))
        {
            return LEAGUES;
        }

        if (rawName.contains(
                IconID.HARDCORE_GROUP_IRONMAN.toString()))
        {
            return GROUP_HARDCORE;
        }

        if (rawName.contains(
                IconID.UNRANKED_GROUP_IRONMAN.toString()))
        {
            return GROUP_UNRANKED;
        }

        if (rawName.contains(
                IconID.GROUP_IRONMAN.toString()))
        {
            return GROUP_IRONMAN;
        }

        if (rawName.contains(
                IconID.HARDCORE_IRONMAN.toString()))
        {
            return HARDCORE;
        }

        if (rawName.contains(
                IconID.ULTIMATE_IRONMAN.toString()))
        {
            return ULTIMATE;
        }

        if (rawName.contains(
                IconID.IRONMAN.toString()))
        {
            return IRONMAN;
        }

        return NORMAL;
    }
}