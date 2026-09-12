package com.runetags.records;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class PlayerTagCatalogTest
{
    /*
     * TESTS
     */

    @Test
    public void catalogContainsExpectedFixedTags()
    {
        Assert.assertEquals(
                38,
                PlayerTagCatalog.ALL.size());

        Assert.assertEquals(
                "Alt",
                PlayerTagCatalog.ALL.get(
                        0));

        Assert.assertEquals(
                "Verified",
                PlayerTagCatalog.ALL.get(
                        PlayerTagCatalog.ALL.size() - 1));

        Assert.assertTrue(
                PlayerTagCatalog.ALL.contains(
                        "BiS"));

        Assert.assertTrue(
                PlayerTagCatalog.ALL.contains(
                        "PvM"));

        Assert.assertTrue(
                PlayerTagCatalog.ALL.contains(
                        "PvP"));
    }

    @Test
    public void maximumTagsPerPlayerIsFive()
    {
        Assert.assertEquals(
                5,
                PlayerTagCatalog.MAX_TAGS_PER_PLAYER);
    }

    @Test
    public void canonicalRejectsNullBlankAndUnknownValues()
    {
        Assert.assertNull(
                PlayerTagCatalog.canonical(
                        null));

        Assert.assertNull(
                PlayerTagCatalog.canonical(
                        ""));

        Assert.assertNull(
                PlayerTagCatalog.canonical(
                        "   "));

        Assert.assertNull(
                PlayerTagCatalog.canonical(
                        "definitelyunknown123"));
    }

    @Test
    public void canonicalTrimsAndIgnoresCase()
    {
        Assert.assertEquals(
                "Trusted",
                PlayerTagCatalog.canonical(
                        "  trusted  "));

        Assert.assertEquals(
                "Scammer",
                PlayerTagCatalog.canonical(
                        "SCAMMER"));
    }

    @Test
    public void canonicalReturnsCatalogCapitalization()
    {
        Assert.assertEquals(
                "BiS",
                PlayerTagCatalog.canonical(
                        "bis"));

        Assert.assertEquals(
                "PvM",
                PlayerTagCatalog.canonical(
                        "pvm"));

        Assert.assertEquals(
                "PvP",
                PlayerTagCatalog.canonical(
                        "PVP"));

        Assert.assertEquals(
                "MVP",
                PlayerTagCatalog.canonical(
                        "mvp"));
    }

    @Test
    public void catalogContainsNoCaseInsensitiveDuplicates()
    {
        final Set<String> normalized =
                new HashSet<>();

        for (String tag : PlayerTagCatalog.ALL)
        {
            Assert.assertTrue(
                    "Duplicate tag: "
                            + tag,
                    normalized.add(
                            tag.toLowerCase(
                                    Locale.ROOT)));
        }
    }

    @Test
    public void catalogIsUnmodifiable()
    {
        try
        {
            PlayerTagCatalog.ALL.add(
                    "Test");

            Assert.fail("Expected PlayerTagCatalog.ALL to be unmodifiable.");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }
    }
}