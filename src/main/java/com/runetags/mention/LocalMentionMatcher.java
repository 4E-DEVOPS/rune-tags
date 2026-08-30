package com.runetags.mention;

import com.runetags.RuneTagsConfig;
import com.runetags.model.LocalMentionMatch;
import com.runetags.model.MatchReason;
import com.runetags.model.PlayerReference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalMentionMatcher
{
    private final RuneTagsConfig config;
    private final NameNormalizer normalizer;

    public LocalMentionMatcher(
            RuneTagsConfig config,
            NameNormalizer normalizer)
    {
        this.config = config;
        this.normalizer = normalizer;
    }

    /**
     * Match a structured player reference against the local player or one of
     * the user's Unique Highlights.
     *
     * Unique Highlights are notification triggers only. They never remap the
     * PlayerReference identity.
     */
    public LocalMentionMatch match(
            PlayerReference reference,
            String localPlayerName)
    {
        if (reference == null
                || localPlayerName == null
                || localPlayerName.isEmpty())
        {
            return LocalMentionMatch.none();
        }

        final String referenceText =
                reference.getRawText().replaceFirst("^@", "");

        final String referenceKey =
                normalizer.comparisonKey(referenceText);

        final String localKey =
                normalizer.comparisonKey(localPlayerName);

        /*
         * A structured player reference that resolves to the local account
         * takes precedence over Unique Highlights.
         */
        if (!referenceKey.isEmpty()
                && referenceKey.equals(localKey))
        {
            return new LocalMentionMatch(
                    true,
                    MatchReason.ACCOUNT_NAME,
                    reference.getRawText());
        }

        /*
         * This only changes notification/highlight behavior. It does NOT
         * associate the reference with the local player's account.
         */
        if (uniqueHighlightKeys().contains(referenceKey))
        {
            return new LocalMentionMatch(
                    true,
                    MatchReason.UNIQUE_HIGHLIGHT,
                    reference.getRawText());
        }

        return LocalMentionMatch.none();
    }

    /**
     * Scan ordinary message text for a local-account variant or configured
     * Unique Highlight.
     *
     * Unlike the old boolean method, this returns the actual reason so
     * notifications/history/logging know why the message matched.
     */
    public LocalMentionMatch matchMessage(
            String message,
            String localPlayerName)
    {
        if (message == null || message.isEmpty())
        {
            return LocalMentionMatch.none();
        }

        final String lowered =
                message.toLowerCase(Locale.ROOT);

        /*
         * Local account-name variants have priority over Unique Highlights.
         */
        if (localPlayerName != null
                && !localPlayerName.isEmpty())
        {
            final String canonical =
                    normalizer.canonicalize(localPlayerName)
                            .toLowerCase(Locale.ROOT);

            final String tagged =
                    normalizer.taggedToken(localPlayerName)
                            .toLowerCase(Locale.ROOT);

            final String hyphenated =
                    canonical.replace(' ', '-');

            if (containsWholePhrase(lowered, canonical))
            {
                return new LocalMentionMatch(
                        true,
                        MatchReason.NORMALIZED_ACCOUNT_NAME,
                        canonical);
            }

            if (containsWholePhrase(lowered, tagged))
            {
                return new LocalMentionMatch(
                        true,
                        MatchReason.NORMALIZED_ACCOUNT_NAME,
                        tagged);
            }

            if (containsWholePhrase(lowered, hyphenated))
            {
                return new LocalMentionMatch(
                        true,
                        MatchReason.NORMALIZED_ACCOUNT_NAME,
                        hyphenated);
            }
        }

        /*
         * Unique Highlights are evaluated separately so they retain their
         * proper semantic reason.
         */
        for (String trigger : configuredHighlights())
        {
            final String loweredTrigger =
                    trigger.toLowerCase(Locale.ROOT);

            if (containsWholePhrase(lowered, loweredTrigger))
            {
                return new LocalMentionMatch(
                        true,
                        MatchReason.UNIQUE_HIGHLIGHT,
                        trigger);
            }
        }

        return LocalMentionMatch.none();
    }

    private Set<String> uniqueHighlightKeys()
    {
        return configuredHighlights().stream()
                .map(normalizer::comparisonKey)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<String> configuredHighlights()
    {
        final String configured =
                config.uniqueMentions();

        if (configured == null
                || configured.trim().isEmpty())
        {
            return new HashSet<>();
        }

        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static boolean containsWholePhrase(
            String message,
            String phrase)
    {
        if (phrase == null || phrase.isEmpty())
        {
            return false;
        }

        int from = 0;

        while (from <= message.length() - phrase.length())
        {
            final int index =
                    message.indexOf(phrase, from);

            if (index < 0)
            {
                return false;
            }

            final boolean leftBoundary =
                    index == 0
                            || !isNameChar(message.charAt(index - 1));

            final int right =
                    index + phrase.length();

            final boolean rightBoundary =
                    right == message.length()
                            || !isNameChar(message.charAt(right));

            if (leftBoundary && rightBoundary)
            {
                return true;
            }

            from = index + 1;
        }

        return false;
    }

    private static boolean isNameChar(char c)
    {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '-';
    }
}