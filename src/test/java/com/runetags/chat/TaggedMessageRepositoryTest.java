package com.runetags.chat;

import java.util.List;

import net.runelite.api.ChatMessageType;

import org.junit.Assert;
import org.junit.Test;

public class TaggedMessageRepositoryTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroCapacity()
    {
        new TaggedMessageRepository(
                0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeCapacity()
    {
        new TaggedMessageRepository(
                -1);
    }

    @Test
    public void storesAndRetrievesMessage()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        10);

        final TaggedMessage message =
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT);

        repository.add(
                message);

        Assert.assertEquals(
                1,
                repository.size());

        Assert.assertTrue(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertEquals(
                "Zezima",
                repository.get(
                                1)
                        .get()
                        .getOriginalMessage());
    }

    @Test
    public void missingMessageReturnsEmpty()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        10);

        repository.add(
                createMessage(
                        1,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertFalse(
                repository.get(
                                2)
                        .isPresent());
    }

    @Test
    public void nullMessageIsIgnored()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        10);

        repository.add(
                null);

        Assert.assertEquals(
                0,
                repository.size());

        Assert.assertTrue(
                repository.snapshot()
                        .isEmpty());
    }

    @Test
    public void trimsOldestMessageWithinSameType()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                2,
                repository.size());

        Assert.assertFalse(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                2)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                3)
                        .isPresent());
    }

    @Test
    public void trimmingContinuesAsMoreMessagesAreAdded()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        4,
                        "Santa Clause",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                2,
                repository.size());

        Assert.assertFalse(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertFalse(
                repository.get(
                                2)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                3)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                4)
                        .isPresent());
    }

    @Test
    public void capacityIsIndependentPerChatType()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        ChatMessageType.CLAN_CHAT));

        repository.add(
                createMessage(
                        4,
                        "Santa Clause",
                        ChatMessageType.CLAN_CHAT));

        Assert.assertEquals(
                4,
                repository.size());

        Assert.assertTrue(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                2)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                3)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                4)
                        .isPresent());
    }

    @Test
    public void evictionDoesNotRemoveMessagesFromOtherTypes()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.CLAN_CHAT));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        4,
                        "Santa Clause",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                3,
                repository.size());

        Assert.assertFalse(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                2)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                3)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                4)
                        .isPresent());
    }

    @Test
    public void snapshotPreservesGlobalChronologicalOrderAfterSelectiveEviction()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.CLAN_CHAT));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        4,
                        "Santa Clause",
                        ChatMessageType.PUBLICCHAT));

        final List<TaggedMessage> snapshot =
                repository.snapshot();

        Assert.assertEquals(
                3,
                snapshot.size());

        Assert.assertEquals(
                2,
                snapshot.get(
                                0)
                        .getId());

        Assert.assertEquals(
                3,
                snapshot.get(
                                1)
                        .getId());

        Assert.assertEquals(
                4,
                snapshot.get(
                                2)
                        .getId());
    }

    @Test
    public void nullTypeUsesUnknownRetentionBucket()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        null));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        null));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        4,
                        "Santa Clause",
                        null));

        Assert.assertEquals(
                3,
                repository.size());

        Assert.assertFalse(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                2)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                3)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                4)
                        .isPresent());
    }

    @Test
    public void unknownBucketIsIndependentFromExplicitChatTypes()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        1);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        null));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        3,
                        "Party Hat",
                        null));

        Assert.assertEquals(
                2,
                repository.size());

        Assert.assertFalse(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                2)
                        .isPresent());

        Assert.assertTrue(
                repository.get(
                                3)
                        .isPresent());
    }

    @Test
    public void retainedMessagePreservesOriginalContent()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa Clause",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        3,
                        "FasT 07",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                "Santa Clause",
                repository.get(
                                2)
                        .get()
                        .getOriginalMessage());

        Assert.assertEquals(
                "FasT 07",
                repository.get(
                                3)
                        .get()
                        .getOriginalMessage());
    }

    @Test
    public void clearRemovesAllMessages()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        2);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.CLAN_CHAT));

        repository.clear();

        Assert.assertEquals(
                0,
                repository.size());

        Assert.assertTrue(
                repository.snapshot()
                        .isEmpty());

        Assert.assertFalse(
                repository.get(
                                1)
                        .isPresent());

        Assert.assertFalse(
                repository.get(
                                2)
                        .isPresent());
    }

    @Test
    public void clearResetsRetentionCounts()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        1);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        repository.clear();

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                1,
                repository.size());

        Assert.assertTrue(
                repository.get(
                                2)
                        .isPresent());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotIsUnmodifiable()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        10);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        final List<TaggedMessage> snapshot =
                repository.snapshot();

        snapshot.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));
    }

    @Test
    public void snapshotIsDefensiveCopy()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        10);

        repository.add(
                createMessage(
                        1,
                        "Zezima",
                        ChatMessageType.PUBLICCHAT));

        final List<TaggedMessage> snapshot =
                repository.snapshot();

        repository.add(
                createMessage(
                        2,
                        "Santa",
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                1,
                snapshot.size());

        Assert.assertEquals(
                2,
                repository.size());
    }

    @Test
    public void performanceSample()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        warmUpPerformanceSamples();

        final long addNanos =
                measureAddPerformance();

        final long getNanos =
                measureGetPerformance();

        final long snapshotNanos =
                measureSnapshotPerformance();

        final long clearNanos =
                measureClearPerformance();

        System.out.printf(
                "[RuneTags][TaggedMessageRepositoryTest] Performance= "
                        + "Add: %.3fms (%.6fms) | "
                        + "Get: %.3fms (%.6fms) | "
                        + "Snapshot: %.3fms (%.6fms) | "
                        + "Clear: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                nanosToMilliseconds(
                        addNanos),
                nanosToMilliseconds(
                        addNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        getNanos),
                nanosToMilliseconds(
                        getNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        snapshotNanos),
                nanosToMilliseconds(
                        snapshotNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        clearNanos),
                nanosToMilliseconds(
                        clearNanos) / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    /*
     * HELPERS
     */

    private static TaggedMessage createMessage(
            int id,
            String originalMessage,
            ChatMessageType type)
    {
        return TaggedMessage.builder()
                .id(
                        id)
                .originalMessage(
                        originalMessage)
                .type(
                        type)
                .build();
    }

    /*
     * PERFORMANCE
     */

    private static void warmUpPerformanceSamples()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        100);

        for (int i = 0; i < PERFORMANCE_WARMUP_ITERATIONS; i++)
        {
            repository.add(
                    createMessage(
                            i,
                            "Zezima",
                            ChatMessageType.PUBLICCHAT));

            repository.get(
                    i);

            repository.snapshot();

            if ((i + 1) % 100 == 0)
            {
                repository.clear();
            }
        }
    }

    private static long measureAddPerformance()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        100);

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            repository.add(
                    createMessage(
                            i,
                            "Zezima",
                            ChatMessageType.PUBLICCHAT));
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertEquals(
                100,
                repository.size());

        return elapsed;
    }

    private static long measureGetPerformance()
    {
        final TaggedMessageRepository repository =
                createPerformanceRepository();

        int checksum =
                0;

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            if (repository.get(
                            99)
                    .isPresent())
            {
                checksum++;
            }
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertEquals(
                PERFORMANCE_ITERATIONS,
                checksum);

        return elapsed;
    }

    private static long measureSnapshotPerformance()
    {
        final TaggedMessageRepository repository =
                createPerformanceRepository();

        int checksum =
                0;

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            checksum +=
                    repository.snapshot()
                            .size();
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertEquals(
                PERFORMANCE_ITERATIONS * 100,
                checksum);

        return elapsed;
    }

    private static long measureClearPerformance()
    {
        long checksum =
                0;

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            final TaggedMessageRepository repository =
                    new TaggedMessageRepository(
                            1);

            repository.add(
                    createMessage(
                            i,
                            "Zezima",
                            ChatMessageType.PUBLICCHAT));

            repository.clear();

            checksum +=
                    repository.size();
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertEquals(
                0,
                checksum);

        return elapsed;
    }

    private static TaggedMessageRepository createPerformanceRepository()
    {
        final TaggedMessageRepository repository =
                new TaggedMessageRepository(
                        100);

        for (int i = 0; i < 100; i++)
        {
            repository.add(
                    createMessage(
                            i,
                            "Zezima",
                            ChatMessageType.PUBLICCHAT));
        }

        return repository;
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos / 1_000_000.0;
    }
}