package com.runetags.history;

import com.runetags.Configurations;
import com.runetags.mention.MatchReason;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Deque;
import java.util.List;

import net.runelite.api.ChatMessageType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class MentionHistoryServiceTest
{
    private Configurations config;
    private MentionHistoryService historyService;

    @Before
    public void setUp()
            throws Exception
    {
        config =
                Mockito.mock(
                        Configurations.class);

        Mockito.when(
                        config.maximumHistory())
                .thenReturn(
                        100);

        historyService =
                new MentionHistoryService(
                        config);

        entries().clear();
    }

    /*
     * TESTS
     */

    @Test
    public void emptyServiceHasEmptySnapshot()
            throws Exception
    {
        Assert.assertEquals(
                0,
                historyService.size());

        Assert.assertTrue(
                historyService.snapshot()
                        .isEmpty());
    }

    @Test
    public void sizeReflectsInMemoryEntries()
            throws Exception
    {
        entries().add(
                entry(
                        1L,
                        "Santa"));

        entries().add(
                entry(
                        2L,
                        "Zezima"));

        Assert.assertEquals(
                2,
                historyService.size());
    }

    @Test
    public void snapshotPreservesDequeOrder()
            throws Exception
    {
        final MentionHistoryEntry newest =
                entry(
                        2L,
                        "Zezima");

        final MentionHistoryEntry oldest =
                entry(
                        1L,
                        "Santa");

        entries().addLast(
                newest);

        entries().addLast(
                oldest);

        final List<MentionHistoryEntry> snapshot =
                historyService.snapshot();

        Assert.assertEquals(
                2,
                snapshot.size());

        Assert.assertSame(
                newest,
                snapshot.get(
                        0));

        Assert.assertSame(
                oldest,
                snapshot.get(
                        1));
    }

    @Test
    public void snapshotIsDefensiveCopy()
            throws Exception
    {
        entries().add(
                entry(
                        1L,
                        "Santa"));

        final List<MentionHistoryEntry> snapshot =
                historyService.snapshot();

        entries().clear();

        Assert.assertEquals(
                1,
                snapshot.size());

        Assert.assertEquals(
                0,
                historyService.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotIsUnmodifiable()
            throws Exception
    {
        entries().add(
                entry(
                        1L,
                        "Santa"));

        historyService.snapshot()
                .add(
                        entry(
                                2L,
                                "Zezima"));
    }

    @Test
    public void trimRetainsConfiguredMaximum()
            throws Exception
    {
        Mockito.when(
                        config.maximumHistory())
                .thenReturn(
                        2);

        entries().addLast(
                entry(
                        3L,
                        "Santa"));

        entries().addLast(
                entry(
                        2L,
                        "Zezima"));

        entries().addLast(
                entry(
                        1L,
                        "Party Hat"));

        invokeTrim();

        Assert.assertEquals(
                2,
                historyService.size());

        Assert.assertEquals(
                3L,
                historyService.snapshot()
                        .get(
                                0)
                        .getMessageId());

        Assert.assertEquals(
                2L,
                historyService.snapshot()
                        .get(
                                1)
                        .getMessageId());
    }

    @Test
    public void trimTreatsZeroMaximumAsOne()
            throws Exception
    {
        Mockito.when(
                        config.maximumHistory())
                .thenReturn(
                        0);

        entries().addLast(
                entry(
                        3L,
                        "Santa"));

        entries().addLast(
                entry(
                        2L,
                        "Zezima"));

        invokeTrim();

        Assert.assertEquals(
                1,
                historyService.size());

        Assert.assertEquals(
                3L,
                historyService.snapshot()
                        .get(
                                0)
                        .getMessageId());
    }

    @Test
    public void trimTreatsNegativeMaximumAsOne()
            throws Exception
    {
        Mockito.when(
                        config.maximumHistory())
                .thenReturn(
                        -10);

        entries().addLast(
                entry(
                        3L,
                        "Santa"));

        entries().addLast(
                entry(
                        2L,
                        "Zezima"));

        invokeTrim();

        Assert.assertEquals(
                1,
                historyService.size());
    }

    @Test
    public void persistedRoundTripPreservesFields()
            throws Exception
    {
        final Instant timestamp =
                Instant.ofEpochMilli(
                        1_700_000_000_123L);

        final MentionHistoryEntry original =
                new MentionHistoryEntry(
                        123L,
                        "Santa",
                        "Hello Zezima",
                        ChatMessageType.PUBLICCHAT,
                        null,
                        301,
                        "Lumbridge",
                        "Party Hat",
                        timestamp);

        final Object persisted =
                toPersisted(
                        original);

        final MentionHistoryEntry restored =
                fromPersisted(
                        persisted);

        Assert.assertEquals(
                original,
                restored);
    }

    @Test
    public void persistedRepresentationStoresEnumNames()
            throws Exception
    {
        final MatchReason reason =
                anyMatchReason();

        final MentionHistoryEntry original =
                new MentionHistoryEntry(
                        123L,
                        "Santa",
                        "Message",
                        ChatMessageType.PUBLICCHAT,
                        reason,
                        301,
                        "Lumbridge",
                        "Party Hat",
                        Instant.EPOCH);

        final Object persisted =
                toPersisted(
                        original);

        Assert.assertEquals(
                ChatMessageType.PUBLICCHAT.name(),
                getPersistedField(
                        persisted,
                        "chatType"));

        Assert.assertEquals(
                reason.name(),
                getPersistedField(
                        persisted,
                        "matchReason"));
    }

    @Test
    public void persistedRepresentationSupportsNullEnums()
            throws Exception
    {
        final MentionHistoryEntry original =
                new MentionHistoryEntry(
                        1L,
                        "Santa",
                        "Message",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.EPOCH);

        final Object persisted =
                toPersisted(
                        original);

        Assert.assertNull(
                getPersistedField(
                        persisted,
                        "chatType"));

        Assert.assertNull(
                getPersistedField(
                        persisted,
                        "matchReason"));

        final MentionHistoryEntry restored =
                fromPersisted(
                        persisted);

        Assert.assertNull(
                restored.getChatType());

        Assert.assertNull(
                restored.getMatchReason());
    }

    @Test
    public void persistedTimestampUsesEpochMilliseconds()
            throws Exception
    {
        final Instant timestamp =
                Instant.ofEpochMilli(
                        1_700_000_123_456L);

        final Object persisted =
                toPersisted(
                        new MentionHistoryEntry(
                                1L,
                                "Santa",
                                "Message",
                                null,
                                null,
                                null,
                                null,
                                null,
                                timestamp));

        Assert.assertEquals(
                timestamp.toEpochMilli(),
                ((Number) getPersistedField(
                        persisted,
                        "timestampMillis"))
                        .longValue());
    }

    @Test
    public void nullTimestampGetsUsablePersistedTimestamp()
            throws Exception
    {
        final long before =
                System.currentTimeMillis();

        final Object persisted =
                toPersisted(
                        new MentionHistoryEntry(
                                1L,
                                "Santa",
                                "Message",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));

        final long after =
                System.currentTimeMillis();

        final long timestamp =
                ((Number) getPersistedField(
                        persisted,
                        "timestampMillis"))
                        .longValue();

        Assert.assertTrue(
                timestamp >= before);

        Assert.assertTrue(
                timestamp <= after);
    }

    @Test
    public void nullPersistedEntryIsRejected()
            throws Exception
    {
        Assert.assertNull(
                fromPersisted(
                        null));
    }

    @Test
    public void invalidPersistedChatTypeIsRejected()
            throws Exception
    {
        final Object persisted =
                toPersisted(
                        entry(
                                1L,
                                "Santa"));

        setPersistedField(
                persisted,
                "chatType",
                "NOT_A_CHAT_TYPE");

        Assert.assertNull(
                fromPersisted(
                        persisted));
    }

    @Test
    public void invalidPersistedMatchReasonIsRejected()
            throws Exception
    {
        final Object persisted =
                toPersisted(
                        new MentionHistoryEntry(
                                1L,
                                "Santa",
                                "Message",
                                ChatMessageType.PUBLICCHAT,
                                anyMatchReason(),
                                null,
                                null,
                                null,
                                Instant.EPOCH));

        setPersistedField(
                persisted,
                "matchReason",
                "NOT_A_MATCH_REASON");

        Assert.assertNull(
                fromPersisted(
                        persisted));
    }

    /*
     * HELPERS
     */

    private static MentionHistoryEntry entry(
            long messageId,
            String sender)
    {
        return new MentionHistoryEntry(
                messageId,
                sender,
                "Message",
                ChatMessageType.PUBLICCHAT,
                null,
                301,
                "Lumbridge",
                "Party Hat",
                Instant.EPOCH);
    }

    private static MatchReason anyMatchReason()
    {
        final MatchReason[] values =
                MatchReason.values();

        Assert.assertTrue(
                values.length > 0);

        return values[0];
    }

    @SuppressWarnings("unchecked")
    private Deque<MentionHistoryEntry> entries()
            throws Exception
    {
        final Field field =
                MentionHistoryService.class.getDeclaredField(
                        "entries");

        field.setAccessible(
                true);

        return (Deque<MentionHistoryEntry>) field.get(
                historyService);
    }

    private void invokeTrim()
            throws Exception
    {
        final Method method =
                MentionHistoryService.class.getDeclaredMethod(
                        "trim");

        method.setAccessible(
                true);

        method.invoke(
                historyService);
    }

    private static Object toPersisted(
            MentionHistoryEntry entry)
            throws Exception
    {
        final Method method =
                MentionHistoryService.class.getDeclaredMethod(
                        "toPersisted",
                        MentionHistoryEntry.class);

        method.setAccessible(
                true);

        return method.invoke(
                null,
                entry);
    }

    private static MentionHistoryEntry fromPersisted(
            Object persisted)
            throws Exception
    {
        final Class<?> persistedType =
                persistedClass();

        final Method method =
                MentionHistoryService.class.getDeclaredMethod(
                        "fromPersisted",
                        persistedType);

        method.setAccessible(
                true);

        return (MentionHistoryEntry) method.invoke(
                null,
                persisted);
    }

    private static Class<?> persistedClass()
    {
        for (Class<?> nested :
                MentionHistoryService.class.getDeclaredClasses())
        {
            if ("PersistedEntry".equals(
                    nested.getSimpleName()))
            {
                return nested;
            }
        }

        throw new AssertionError(
                "PersistedEntry class not found");
    }

    private static Object getPersistedField(
            Object persisted,
            String fieldName)
            throws Exception
    {
        final Field field =
                persistedClass().getDeclaredField(
                        fieldName);

        field.setAccessible(
                true);

        return field.get(
                persisted);
    }

    private static void setPersistedField(
            Object persisted,
            String fieldName,
            Object value)
            throws Exception
    {
        final Field field =
                persistedClass().getDeclaredField(
                        fieldName);

        field.setAccessible(
                true);

        field.set(
                persisted,
                value);
    }
}