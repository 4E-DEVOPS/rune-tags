package com.runetags.records;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public class LocalPlayerRecordTest
{
    /*
     * TESTS
     */

    @Test
    public void builderUsesSafeDefaults()
    {
        final LocalPlayerRecord record =
                LocalPlayerRecord.builder()
                        .currentRsn(
                                "Santa")
                        .build();

        Assert.assertEquals(
                "Santa",
                record.getCurrentRsn());

        Assert.assertEquals(
                Collections.emptyList(),
                record.getPreviousRsns());

        Assert.assertFalse(
                record.isFavorite());

        Assert.assertNull(
                record.getNote());

        Assert.assertEquals(
                Collections.emptyList(),
                record.getTags());
    }

    @Test
    public void builderAcceptsExplicitValues()
    {
        final LocalPlayerRecord record =
                LocalPlayerRecord.builder()
                        .currentRsn(
                                "Santa Clause")
                        .previousRsns(
                                Arrays.asList(
                                        "Santa",
                                        "FasT 07"))
                        .favorite(true)
                        .note(
                                "Trusted player")
                        .tags(
                                Arrays.asList(
                                        "Trusted",
                                        "Raider"))
                        .build();

        Assert.assertEquals(
                "Santa Clause",
                record.getCurrentRsn());

        Assert.assertEquals(
                Arrays.asList(
                        "Santa",
                        "FasT 07"),
                record.getPreviousRsns());

        Assert.assertTrue(
                record.isFavorite());

        Assert.assertEquals(
                "Trusted player",
                record.getNote());

        Assert.assertEquals(
                Arrays.asList(
                        "Trusted",
                        "Raider"),
                record.getTags());
    }

    @Test
    public void toBuilderPreservesUnchangedMetadata()
    {
        final LocalPlayerRecord original =
                LocalPlayerRecord.builder()
                        .currentRsn(
                                "Party Hat")
                        .previousRsns(
                                Collections.singletonList(
                                        "Zezima"))
                        .favorite(true)
                        .note(
                                "Original note")
                        .tags(
                                Collections.singletonList(
                                        "MVP"))
                        .build();

        final LocalPlayerRecord copy =
                original.toBuilder()
                        .build();

        Assert.assertEquals(
                original,
                copy);
    }

    @Test
    public void toBuilderCanChangeOneFieldWithoutClearingOthers()
    {
        final LocalPlayerRecord original =
                LocalPlayerRecord.builder()
                        .currentRsn(
                                "FasT 07")
                        .previousRsns(
                                Collections.singletonList(
                                        "Santa"))
                        .favorite(true)
                        .note(
                                "Original note")
                        .tags(
                                Collections.singletonList(
                                        "Trusted"))
                        .build();

        final LocalPlayerRecord updated =
                original.toBuilder()
                        .note(
                                "Updated note")
                        .build();

        Assert.assertEquals(
                "FasT 07",
                updated.getCurrentRsn());

        Assert.assertEquals(
                Collections.singletonList(
                        "Santa"),
                updated.getPreviousRsns());

        Assert.assertTrue(
                updated.isFavorite());

        Assert.assertEquals(
                "Updated note",
                updated.getNote());

        Assert.assertEquals(
                Collections.singletonList(
                        "Trusted"),
                updated.getTags());
    }

    @Test
    public void equalRecordsHaveEqualValueSemantics()
    {
        final LocalPlayerRecord first =
                LocalPlayerRecord.builder()
                        .currentRsn(
                                "Zezima")
                        .favorite(true)
                        .note(
                                "Note")
                        .tags(
                                Collections.singletonList(
                                        "Elite"))
                        .build();

        final LocalPlayerRecord second =
                LocalPlayerRecord.builder()
                        .currentRsn(
                                "Zezima")
                        .favorite(true)
                        .note(
                                "Note")
                        .tags(
                                Collections.singletonList(
                                        "Elite"))
                        .build();

        Assert.assertEquals(
                first,
                second);

        Assert.assertEquals(
                first.hashCode(),
                second.hashCode());
    }
}