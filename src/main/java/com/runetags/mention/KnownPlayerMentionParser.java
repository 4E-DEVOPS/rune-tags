package com.runetags.mention;

import com.runetags.player.PlayerIdentity;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;
import com.runetags.player.PlayerDirectory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KnownPlayerMentionParser
{
    private final PlayerDirectory playerDirectory;
    private final NameNormalizer normalizer;

    public KnownPlayerMentionParser(
            PlayerDirectory playerDirectory,
            NameNormalizer normalizer)
    {
        this.playerDirectory = playerDirectory;
        this.normalizer = normalizer;
    }

    public List<PlayerReference> parse(
            String message,
            List<PlayerReference> reservedReferences)
    {
        final List<PlayerReference> matches =
                new ArrayList<>();

        if (message == null || message.isEmpty())
        {
            return matches;
        }

        final String lowered =
                message.toLowerCase(Locale.ROOT);

        /*
         * Preserve longest-name-first identity ordering so more specific player names
         * retain overlap priority.
         */
        final List<PlayerIdentity> identities =
                playerDirectory.allSortedLongestNameFirst();

        /*
         * Build normalized message spans once per message and index them by comparison
         * key. Original offsets are retained so normalized fallback matches can still
         * produce exact PlayerReference spans.
         */
        final Map<String, List<NormalizedMessageSpan>> normalizedSpanIndex =
                buildNormalizedSpanIndex(message);

        for (PlayerIdentity identity : identities)
        {
            final String canonical =
                    identity.getCanonicalName();

            if (canonical == null || canonical.isEmpty())
            {
                continue;
            }

            findVariant(
                    message,
                    lowered,
                    canonical,
                    identity,
                    matches,
                    reservedReferences);

            findVariant(
                    message,
                    lowered,
                    canonical.replace(' ', '_'),
                    identity,
                    matches,
                    reservedReferences);

            findVariant(
                    message,
                    lowered,
                    canonical.replace(' ', '-'),
                    identity,
                    matches,
                    reservedReferences);

            /*
             * Fall back to normalized matching when a RuneScape name's source
             * representation differs in separator usage.
             *
             * For example, "Santaclause" and "Santa clause" share the same comparison key.
             */
            findNormalizedVariant(
                    message,
                    identity,
                    normalizedSpanIndex,
                    matches,
                    reservedReferences);
        }

        matches.sort(
                Comparator.comparingInt(
                        PlayerReference::getStartOffset));

        return removeOverlaps(matches);
    }

    private void findVariant(
            String original,
            String lowered,
            String candidate,
            PlayerIdentity identity,
            List<PlayerReference> output,
            List<PlayerReference> reserved)
    {
        if (candidate == null || candidate.isEmpty())
        {
            return;
        }

        final String needle =
                candidate.toLowerCase(Locale.ROOT);

        int from = 0;

        while (from <= lowered.length() - needle.length())
        {
            final int start =
                    lowered.indexOf(needle, from);

            if (start < 0)
            {
                return;
            }

            final int end =
                    start + needle.length();

            if (hasBoundaries(lowered, start, end)
                    && !isInsideExplicitTag(lowered, start)
                    && !overlapsAny(start, end, reserved)
                    && !overlapsAny(start, end, output))
            {
                addReference(
                        original,
                        start,
                        end,
                        identity,
                        output);
            }

            from = start + 1;
        }
    }

    private void findNormalizedVariant(
            String original,
            PlayerIdentity identity,
            Map<String, List<NormalizedMessageSpan>> normalizedSpanIndex,
            List<PlayerReference> output,
            List<PlayerReference> reserved)
    {
        String targetKey =
                identity.getNormalizedName();

        if (targetKey == null
                || targetKey.isEmpty())
        {
            targetKey =
                    normalizer.comparisonKey(
                            identity.getCanonicalName());
        }

        if (targetKey.isEmpty())
        {
            return;
        }

        final List<NormalizedMessageSpan> candidates =
                normalizedSpanIndex.get(targetKey);

        if (candidates == null || candidates.isEmpty())
        {
            return;
        }

        /*
         * Preserve candidate discovery order so longest-name-first identity ordering
         * continues to determine overlap priority.
         */
        for (NormalizedMessageSpan candidate : candidates)
        {
            final int start = candidate.start;
            final int end = candidate.end;

            if (!hasBoundaries(original, start, end)
                    || overlapsAny(start, end, reserved)
                    || overlapsAny(start, end, output))
            {
                continue;
            }

            addReference(
                    original,
                    start,
                    end,
                    identity,
                    output);
        }
    }

    private Map<String, List<NormalizedMessageSpan>> buildNormalizedSpanIndex(
            String original)
    {
        final Map<String, List<NormalizedMessageSpan>> index =
                new HashMap<>();

        for (int start = 0; start < original.length(); start++)
        {
            if (!isNameChar(original.charAt(start)))
            {
                continue;
            }

            if (start > 0
                    && isNameChar(original.charAt(start - 1)))
            {
                continue;
            }

            if (isInsideExplicitTag(original, start))
            {
                continue;
            }

            for (int end = start + 1;
                 end <= original.length();
                 end++)
            {
                final char last =
                        original.charAt(end - 1);

                if (!isNameChar(last) && last != ' ')
                {
                    break;
                }

                if (end < original.length()
                        && isNameChar(original.charAt(end)))
                {
                    continue;
                }

                final String candidate =
                        original.substring(start, end);

                final String candidateKey =
                        normalizer.comparisonKey(candidate);

                if (candidateKey.isEmpty())
                {
                    continue;
                }

                index.computeIfAbsent(
                                candidateKey,
                                ignored -> new ArrayList<>())
                        .add(new NormalizedMessageSpan(start, end));
            }
        }

        return index;
    }

    private void addReference(
            String original,
            int start,
            int end,
            PlayerIdentity identity,
            List<PlayerReference> output)
    {
        output.add(
                PlayerReference.builder()
                        .rawText(original.substring(start, end))
                        .normalizedToken(
                                normalizer.taggedToken(
                                        identity.getCanonicalName()))
                        .lookupName(
                                identity.getCanonicalName())
                        .startOffset(start)
                        .endOffset(end)
                        .type(ReferenceType.MENTION)
                        .locallyResolved(true)
                        .identity(identity)
                        .build());
    }

    private static boolean hasBoundaries(
            String text,
            int start,
            int end)
    {
        return (start == 0
                || !isNameChar(text.charAt(start - 1)))
                && (end == text.length()
                || !isNameChar(text.charAt(end)));
    }

    private static boolean isInsideExplicitTag(
            String text,
            int start)
    {
        return start > 0
                && text.charAt(start - 1) == '@';
    }

    private static boolean isNameChar(char c)
    {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '-';
    }

    private static boolean overlapsAny(
            int start,
            int end,
            List<PlayerReference> references)
    {
        if (references == null)
        {
            return false;
        }

        for (PlayerReference reference : references)
        {
            if (start < reference.getEndOffset()
                    && end > reference.getStartOffset())
            {
                return true;
            }
        }

        return false;
    }

    private static List<PlayerReference> removeOverlaps(
            List<PlayerReference> references)
    {
        final List<PlayerReference> result =
                new ArrayList<>();

        for (PlayerReference reference : references)
        {
            if (!overlapsAny(
                    reference.getStartOffset(),
                    reference.getEndOffset(),
                    result))
            {
                result.add(reference);
            }
        }

        return result;
    }

    private static final class NormalizedMessageSpan
    {
        private final int start;
        private final int end;

        private NormalizedMessageSpan(
                int start,
                int end)
        {
            this.start = start;
            this.end = end;
        }
    }
}
