package com.runetags.config;

public enum ClickablePlayerMode
{
    ALL("All"),
    MENTIONS("Both"),
    TAGGED_ONLY("Tagged");

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