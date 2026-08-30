package com.runetags.model;

import lombok.Builder;
import lombok.Value;

import net.runelite.api.ChatMessageType;

@Value
@Builder(toBuilder = true)
public class PlayerReference
{
    String rawText;
    String normalizedToken;
    String lookupName;

    int startOffset;
    int endOffset;

    ReferenceType type;
    boolean locallyResolved;
    PlayerIdentity identity;

    ChatMessageType chatType;
}
