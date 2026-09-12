package com.runetags.config;

public enum ChatInteractionMode
{
    LEFT_CLICK("Left-Click"),
    RIGHT_CLICK("Right-Click"),
    BOTH("Both");

    private final String displayName;

    ChatInteractionMode(String displayName)
    {
        this.displayName = displayName;
    }

    public boolean allowsLeftClick()
    {
        return this == LEFT_CLICK || this == BOTH;
    }

    public boolean allowsRightClick()
    {
        return this == RIGHT_CLICK || this == BOTH;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}