package com.runetags.chat;

import com.runetags.mention.KnownPlayerMentionParser;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.TagParser;
import com.runetags.model.LocalMentionMatch;
import com.runetags.model.PlayerReference;
import com.runetags.model.TaggedMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.runelite.api.ChatMessageType;

public class ChatProcessor
{
    private final TagParser tagParser;
    private final KnownPlayerMentionParser knownPlayerMentionParser;
    private final LocalMentionMatcher localMentionMatcher;

    public ChatProcessor(
            TagParser tagParser,
            KnownPlayerMentionParser knownPlayerMentionParser,
            LocalMentionMatcher localMentionMatcher)
    {
        this.tagParser = tagParser;
        this.knownPlayerMentionParser =
                knownPlayerMentionParser;
        this.localMentionMatcher =
                localMentionMatcher;
    }

    public TaggedMessage process(
            long id,
            ChatMessageType type,
            String sender,
            String message,
            String localPlayerName)
    {
        final List<PlayerReference> references =
                new ArrayList<>();

        /*
         * Explicit @tags are parsed first and reserve their message spans.
         */
        final List<PlayerReference> tags =
                tagParser.parse(message);

        references.addAll(tags);

        /*
         * Ordinary mentions are only recognized when the identity exists in
         * the local PlayerDirectory.
         */
        references.addAll(
                knownPlayerMentionParser.parse(
                        message,
                        tags));

        /*
         * Attach the originating message channel to every reference.
         */
        for (int index = 0;
             index < references.size();
             index++)
        {
            references.set(
                    index,
                    references.get(index)
                            .toBuilder()
                            .chatType(type)
                            .build());
        }

        references.sort(
                Comparator.comparingInt(
                        PlayerReference::getStartOffset));

        LocalMentionMatch bestMatch =
                LocalMentionMatch.none();

        /*
         * Structured references take priority because they give us the most
         * specific semantic information.
         */
        for (PlayerReference reference : references)
        {
            final LocalMentionMatch candidate =
                    localMentionMatcher.match(
                            reference,
                            localPlayerName);

            if (candidate.isMatchesLocalPlayer())
            {
                bestMatch = candidate;
                break;
            }
        }

        /*
         * If no structured reference matched the local player, scan the
         * ordinary message text for normalized account-name variants and
         * Unique Highlights.
         */
        if (!bestMatch.isMatchesLocalPlayer())
        {
            final LocalMentionMatch messageMatch =
                    localMentionMatcher.matchMessage(
                            message,
                            localPlayerName);

            if (messageMatch.isMatchesLocalPlayer())
            {
                bestMatch = messageMatch;
            }
        }

        return TaggedMessage.builder()
                .id(id)
                .type(type)
                .originalSender(sender)
                .canonicalSender(sender)
                .originalMessage(message)
                .timestamp(Instant.now())
                .references(references)
                .localMentionMatch(bestMatch)
                .build();
    }
}