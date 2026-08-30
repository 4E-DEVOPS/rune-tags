package com.runetags.mention;

import com.runetags.model.PlayerIdentity;
import com.runetags.model.PlayerReference;
import com.runetags.model.ReferenceType;
import com.runetags.player.PlayerDirectory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

        for (PlayerIdentity identity
                : playerDirectory.allSortedLongestNameFirst())
        {
            final String canonical =
                    identity.getCanonicalName();

            if (canonical == null || canonical.isEmpty())
            {
                continue;
            }

            /*
             * Fast-path literal forms.
             */
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
             * Fallback for RuneScape names whose source representation may
             * omit separators.
             *
             * Example:
             *
             * Directory: Gainscronic
             * Message:   Gains cronic
             *
             * Both normalize to the same comparison key.
             */
            findNormalizedVariant(
                    message,
                    identity,
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
            List<PlayerReference> output,
            List<PlayerReference> reserved)
    {
        final String targetKey =
                normalizer.comparisonKey(
                        identity.getCanonicalName());

        if (targetKey.isEmpty())
        {
            return;
        }

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

                if (candidateKey.length() > targetKey.length())
                {
                    break;
                }

                if (!candidateKey.equals(targetKey))
                {
                    continue;
                }

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

                break;
            }
        }
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
}