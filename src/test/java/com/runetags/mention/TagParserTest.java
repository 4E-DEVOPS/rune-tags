package com.runetags.mention;

import com.runetags.model.PlayerReference;
import com.runetags.player.PlayerDirectory;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class TagParserTest
{
    @Test
    public void whitespaceTerminatesExplicitTag()
    {
        NameNormalizer normalizer = new NameNormalizer();
        PlayerDirectory directory = new PlayerDirectory(null, null, normalizer);
        TagParser parser = new TagParser(normalizer, directory);

        List<PlayerReference> refs = parser.parse("hey @party hat");
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals("@party", refs.get(0).getRawText());
        Assert.assertEquals("party", refs.get(0).getLookupName().toLowerCase());
    }

    @Test
    public void underscoreRemainsInsideTag()
    {
        NameNormalizer normalizer = new NameNormalizer();
        PlayerDirectory directory = new PlayerDirectory(null, null, normalizer);
        TagParser parser = new TagParser(normalizer, directory);

        List<PlayerReference> refs = parser.parse("hey @santa_clause");
        Assert.assertEquals(1, refs.size());
        Assert.assertEquals("santa_clause", refs.get(0).getNormalizedToken());
    }
}
