package com.runetags.mention;

import lombok.Value;

@Value
public class LocalMentionMatch
{
    boolean matchesLocalPlayer;
    MatchReason reason;
    String matchedToken;

    public static LocalMentionMatch none()
    {
        return new LocalMentionMatch(false, MatchReason.NONE, null);
    }
}
