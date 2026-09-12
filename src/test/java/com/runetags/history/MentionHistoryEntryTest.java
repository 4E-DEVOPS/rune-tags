package com.runetags.history;

import com.runetags.mention.MatchReason;

import java.time.Instant;

import net.runelite.api.ChatMessageType;

import org.junit.Assert;
import org.junit.Test;

public class MentionHistoryEntryTest
{
    /*
     * TESTS
     */

    @Test
    public void storesAllValues()
    {
        final Instant timestamp =
                Instant.ofEpochMilli(
                        1_700_000_000_000L);

        final MatchReason matchReason =
                anyMatchReason();

        final MentionHistoryEntry entry =
                new MentionHistoryEntry(
                        123L,
                        "Santa",
                        "Hello @Zezima",
                        ChatMessageType.PUBLICCHAT,
                        matchReason,
                        301,
                        "Lumbridge",
                        "Party Hat",
                        timestamp);

        Assert.assertEquals(
                123L,
                entry.getMessageId());

        Assert.assertEquals(
                "Santa",
                entry.getSender());

        Assert.assertEquals(
                "Hello @Zezima",
                entry.getMessage());

        Assert.assertEquals(
                ChatMessageType.PUBLICCHAT,
                entry.getChatType());

        Assert.assertEquals(
                matchReason,
                entry.getMatchReason());

        Assert.assertEquals(
                Integer.valueOf(
                        301),
                entry.getWorld());

        Assert.assertEquals(
                "Lumbridge",
                entry.getLocationName());

        Assert.assertEquals(
                "Party Hat",
                entry.getChannelName());

        Assert.assertEquals(
                timestamp,
                entry.getTimestamp());
    }

    @Test
    public void supportsNullableSnapshotFields()
    {
        final MentionHistoryEntry entry =
                new MentionHistoryEntry(
                        1L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        Assert.assertNull(
                entry.getSender());

        Assert.assertNull(
                entry.getMessage());

        Assert.assertNull(
                entry.getChatType());

        Assert.assertNull(
                entry.getMatchReason());

        Assert.assertNull(
                entry.getWorld());

        Assert.assertNull(
                entry.getLocationName());

        Assert.assertNull(
                entry.getChannelName());

        Assert.assertNull(
                entry.getTimestamp());
    }

    @Test
    public void equalEntriesHaveValueEquality()
    {
        final Instant timestamp =
                Instant.ofEpochMilli(
                        1_700_000_000_000L);

        final MatchReason matchReason =
                anyMatchReason();

        final MentionHistoryEntry first =
                new MentionHistoryEntry(
                        12L,
                        "Santa",
                        "Message",
                        ChatMessageType.PUBLICCHAT,
                        matchReason,
                        301,
                        "Lumbridge",
                        "Party Hat",
                        timestamp);

        final MentionHistoryEntry second =
                new MentionHistoryEntry(
                        12L,
                        "Santa",
                        "Message",
                        ChatMessageType.PUBLICCHAT,
                        matchReason,
                        301,
                        "Lumbridge",
                        "Party Hat",
                        timestamp);

        Assert.assertEquals(
                first,
                second);

        Assert.assertEquals(
                first.hashCode(),
                second.hashCode());
    }

    @Test
    public void differingEntriesAreNotEqual()
    {
        final MatchReason matchReason =
                anyMatchReason();

        final MentionHistoryEntry first =
                new MentionHistoryEntry(
                        1L,
                        "Santa",
                        "Message",
                        ChatMessageType.PUBLICCHAT,
                        matchReason,
                        null,
                        null,
                        null,
                        Instant.EPOCH);

        final MentionHistoryEntry second =
                new MentionHistoryEntry(
                        2L,
                        "Santa",
                        "Message",
                        ChatMessageType.PUBLICCHAT,
                        matchReason,
                        null,
                        null,
                        null,
                        Instant.EPOCH);

        Assert.assertNotEquals(
                first,
                second);
    }

    /*
     * HELPERS
     */

    private static MatchReason anyMatchReason()
    {
        final MatchReason[] values =
                MatchReason.values();

        Assert.assertTrue(
                values.length > 0);

        return values[0];
    }
}