package com.runetags.chat;

import com.runetags.mention.KnownPlayerMentionParser;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.TagParser;
import com.runetags.reference.PlayerReference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.runelite.api.ChatMessageType;

/**
 * Processes semantic chat messages into structured RuneTags message state.
 */
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
         * Parse explicit @tags first so their spans are reserved before
         * ordinary known-player matching.
         */
        final List<PlayerReference> tags =
                tagParser.parse(
                        message);

        references.addAll(
                tags);

        final List<PlayerReference> knownReferences =
                knownPlayerMentionParser.parse(
                        message,
                        tags);

        references.addAll(
                knownReferences);

        /*
         * Attach the originating chat channel to every reference before
         * final ordering and local-player matching.
         */
        for (int index = 0;
             index < references.size();
             index++)
        {
            references.set(
                    index,
                    references.get(
                                    index)
                            .toBuilder()
                            .chatType(
                                    type)
                            .build());
        }

        references.sort(
                Comparator.comparingInt(
                        PlayerReference::getStartOffset));

        LocalMentionMatch bestMatch =
                LocalMentionMatch.none();

        /*
         * Structured references take priority because they provide the most
         * specific semantic match.
         */
        for (PlayerReference reference : references)
        {
            final LocalMentionMatch candidate =
                    localMentionMatcher.match(
                            reference,
                            localPlayerName);

            if (candidate.isMatchesLocalPlayer())
            {
                bestMatch =
                        candidate;

                break;
            }
        }

        /*
         * If no structured reference matched the local player, check the
         * complete message for account-name variants and unique highlights.
         */
        if (!bestMatch.isMatchesLocalPlayer())
        {
            final LocalMentionMatch messageMatch =
                    localMentionMatcher.matchMessage(
                            message,
                            localPlayerName);

            if (messageMatch.isMatchesLocalPlayer())
            {
                bestMatch =
                        messageMatch;
            }
        }

        return TaggedMessage.builder()
                .id(
                        id)
                .type(
                        type)
                .originalSender(
                        sender)
                .canonicalSender(
                        sender)
                .originalMessage(
                        message)
                .timestamp(
                        Instant.now())
                .references(
                        references)
                .localMentionMatch(
                        bestMatch)
                .build();
    }
}