package com.runetags.mention;

import org.junit.Assert;
import org.junit.Test;

public class NameNormalizerTest
{
    private final NameNormalizer normalizer = new NameNormalizer();

    @Test
    public void normalizesSeparators()
    {
        Assert.assertEquals("santaclause", normalizer.comparisonKey("Santa Clause"));
        Assert.assertEquals("santaclause", normalizer.comparisonKey("santa_clause"));
        Assert.assertEquals("santaclause", normalizer.comparisonKey("santa-clause"));
        Assert.assertEquals("santa_clause", normalizer.taggedToken("Santa Clause"));
    }

    @Test
    public void stripsFormatting()
    {
        Assert.assertEquals("santaclause",
            normalizer.comparisonKey("<col=ff0000>Santa Clause</col>"));
    }
}
