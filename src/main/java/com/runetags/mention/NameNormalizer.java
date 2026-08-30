package com.runetags.mention;

import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

public class NameNormalizer
{
    private static final Pattern SEPARATORS = Pattern.compile("[ _-]+");
    private static final Pattern NON_NAME_CHARS = Pattern.compile("[^A-Za-z0-9 _-]");

    public NormalizedPlayerName normalize(String raw)
    {
        final String canonical = canonicalize(raw);
        final String lowercase = canonical.toLowerCase(Locale.ROOT);
        final String comparison = SEPARATORS.matcher(lowercase).replaceAll("");
        final String tagged = SEPARATORS.matcher(lowercase).replaceAll("_");

        return new NormalizedPlayerName(canonical, comparison, tagged);
    }

    public String comparisonKey(String raw)
    {
        return normalize(raw).getComparisonKey();
    }

    public String taggedToken(String raw)
    {
        return normalize(raw).getTaggedToken();
    }

    public String canonicalize(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        String clean = Text.removeTags(raw).trim();

        /*
         * RuneScape/RuneLite social lists may represent spaces in player names
         * using a non-breaking space rather than a normal ASCII space.
         *
         * Normalize those before filtering characters rather than incorrectly collapsing them.
         *
         */
        clean = clean
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');

        clean = NON_NAME_CHARS.matcher(clean).replaceAll("");
        clean = clean.replace('_', ' ').replace('-', ' ');
        clean = clean.replaceAll("\\s+", " ").trim();

        return clean;
    }
}
