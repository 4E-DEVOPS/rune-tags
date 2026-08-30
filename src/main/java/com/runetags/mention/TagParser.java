package com.runetags.mention;

import com.runetags.model.PlayerIdentity;
import com.runetags.model.PlayerReference;
import com.runetags.model.ReferenceType;
import com.runetags.player.PlayerDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagParser
{
    private static final Pattern TAG_PATTERN = Pattern.compile("@([A-Za-z0-9_-]+)");

    private final NameNormalizer nameNormalizer;
    private final PlayerDirectory playerDirectory;

    public TagParser(NameNormalizer nameNormalizer, PlayerDirectory playerDirectory)
    {
        this.nameNormalizer = nameNormalizer;
        this.playerDirectory = playerDirectory;
    }

    public List<PlayerReference> parse(String message)
    {
        final List<PlayerReference> references = new ArrayList<>();

        if (message == null || message.isEmpty())
        {
            return references;
        }

        final Matcher matcher = TAG_PATTERN.matcher(message);

        while (matcher.find())
        {
            final String token =
                    matcher.group(1);

            final String canonicalLookupName =
                    nameNormalizer.canonicalize(token);

            final Optional<PlayerIdentity> identity =
                    playerDirectory.find(canonicalLookupName);

            final String resolvedLookupName =
                    identity
                            .map(PlayerIdentity::getCanonicalName)
                            .orElse(canonicalLookupName);

            references.add(
                    PlayerReference.builder()
                            .rawText(matcher.group(0))
                            .normalizedToken(
                                    nameNormalizer.taggedToken(token))
                            .lookupName(resolvedLookupName)
                            .startOffset(matcher.start())
                            .endOffset(matcher.end())
                            .type(ReferenceType.TAG)
                            .locallyResolved(identity.isPresent())
                            .identity(identity.orElse(null))
                            .build());
        }

        return references;
    }
}
