package com.runetags.history;

import com.runetags.mention.MatchReason;

import java.time.Instant;

import lombok.Value;

import net.runelite.api.ChatMessageType;

/**
 * Immutable mention-history entry containing message and captured context.
 */
@Value
public class MentionHistoryEntry {
	long messageId;

	String sender;
	String message;

	ChatMessageType chatType;
	MatchReason matchReason;

	/*
	 * Context captured when the mention occurred. These values remain historical snapshots.
	 */
	Integer world;
	String locationName;
	String channelName;

	Instant timestamp;
}
