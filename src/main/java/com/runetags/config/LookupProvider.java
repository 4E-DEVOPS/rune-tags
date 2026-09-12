package com.runetags.config;

public enum LookupProvider
{
    HISCORES("HiScores"),
    WISE_OLD_MAN("Wise Old Man"),
    RUNE_PROFILE("RuneProfile");

    private final String displayName;

    LookupProvider(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}