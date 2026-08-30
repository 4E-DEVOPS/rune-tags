package com.runetags.config;

public enum ClickablePlayerMode
{
    ALL("All"),
    MENTIONS("Mentions + Tags"),
    TAGGED_ONLY("Tagged Only");

    private final String displayName;

    ClickablePlayerMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}