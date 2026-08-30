package com.runetags.config;

public enum MinimapIndicatorMode
{
    OFF("Off", 0),
    NORMAL("Normal", 4),
    MEDIUM("Medium", 6),
    LARGE("Large", 8);

    private final String displayName;
    private final int diameter;

    MinimapIndicatorMode(
            String displayName,
            int diameter)
    {
        this.displayName = displayName;
        this.diameter = diameter;
    }

    public int getDiameter()
    {
        return diameter;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}