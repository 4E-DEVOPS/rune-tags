package com.runetags.config;

public enum MentionFont
{
    NORMAL("Normal"),
    BOLD("Bold");

    private final String displayName;

    MentionFont(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
