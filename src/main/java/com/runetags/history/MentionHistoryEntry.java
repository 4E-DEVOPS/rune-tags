package com.runetags.history;

import com.runetags.model.MatchReason;

import java.time.Instant;

import lombok.Value;

import net.runelite.api.ChatMessageType;

@Value
public class MentionHistoryEntry
{
    long messageId;

    String sender;
    String message;

    ChatMessageType chatType;
    MatchReason matchReason;

    /*
     * Snapshot of contextual information at the moment the mention occurred.
     *
     * These values are historical. They are NOT updated later if the sender
     * changes worlds, channels, or locations.
     */
    Integer world;
    String locationName;
    String channelName;

    Instant timestamp;
}