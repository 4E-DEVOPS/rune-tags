package com.runetags.config;

public enum HideOthersMode
{
    OFF("Off", 0),
    TEN("10 Seconds", 10),
    FIFTEEN("15 Seconds", 15),
    THIRTY("30 Seconds", 30),
    SIXTY("60 Seconds", 60),
    ON("On", -1);

    private final String displayName;
    private final int durationSeconds;

    HideOthersMode(
            String displayName,
            int durationSeconds)
    {
        this.displayName = displayName;
        this.durationSeconds = durationSeconds;
    }

    public boolean isEnabled()
    {
        return this != OFF;
    }

    public boolean isTimed()
    {
        return durationSeconds > 0;
    }

    public boolean isPersistent()
    {
        return this == ON;
    }

    public int getDurationSeconds()
    {
        return durationSeconds;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}