package com.runetags.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;

import net.runelite.api.ChatMessageType;

@Value
@Builder
public class TaggedMessage
{
    long id;
    ChatMessageType type;

    String originalSender;
    String canonicalSender;
    String originalMessage;

    Instant timestamp;

    @Builder.Default
    List<PlayerReference> references = Collections.emptyList();

    @Builder.Default
    LocalMentionMatch localMentionMatch = LocalMentionMatch.none();
}
