package com.runetags.config;

public enum TargetMode
{
    OFF("Off"),
    OUTLINE("Outline"),
    TILE("Tile"),
    BOTH("Both");

    private final String displayName;

    TargetMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }

    public boolean showsOutline()
    {
        return this == OUTLINE || this == BOTH;
    }

    public boolean showsTile()
    {
        return this == TILE || this == BOTH;
    }
}