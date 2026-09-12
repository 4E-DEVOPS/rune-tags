package com.runetags.history;

import com.runetags.mention.MatchReason;

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
     * Snapshot of contextual information when the mention occurred.
     *
     * These values remain historical and are not updated later.
     * (WORLD, CHANNELS, and LOCATIONS)
     */
    Integer world;
    String locationName;
    String channelName;

    Instant timestamp;
}