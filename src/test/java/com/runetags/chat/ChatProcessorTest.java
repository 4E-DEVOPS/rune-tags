package com.runetags.chat;

import com.runetags.mention.KnownPlayerMentionParser;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.MatchReason;
import com.runetags.mention.TagParser;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.runelite.api.ChatMessageType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ChatProcessorTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    private TagParser tagParser;
    private KnownPlayerMentionParser knownPlayerMentionParser;
    private LocalMentionMatcher localMentionMatcher;
    private ChatProcessor processor;

    @Before
    public void setUp()
    {
        tagParser =
                Mockito.mock(
                        TagParser.class);

        knownPlayerMentionParser =
                Mockito.mock(
                        KnownPlayerMentionParser.class);

        localMentionMatcher =
                Mockito.mock(
                        LocalMentionMatcher.class);

        processor =
                new ChatProcessor(
                        tagParser,
                        knownPlayerMentionParser,
                        localMentionMatcher);
    }

    /*
     * TESTS
     */

    @Test
    public void preservesMessageMetadata()
    {
        final String message =
                "Zezima met Santa";

        stubNoReferences(
                message,
                "Party Hat");

        final TaggedMessage taggedMessage =
                processor.process(
                        42L,
                        ChatMessageType.PUBLICCHAT,
                        "FasT 07",
                        message,
                        "Party Hat");

        Assert.assertEquals(
                42L,
                taggedMessage.getId());

        Assert.assertEquals(
                ChatMessageType.PUBLICCHAT,
                taggedMessage.getType());

        Assert.assertEquals(
                "FasT 07",
                taggedMessage.getOriginalSender());

        Assert.assertEquals(
                "FasT 07",
                taggedMessage.getCanonicalSender());

        Assert.assertEquals(
                message,
                taggedMessage.getOriginalMessage());

        Assert.assertNotNull(
                taggedMessage.getTimestamp());

        Assert.assertTrue(
                taggedMessage.getReferences()
                        .isEmpty());
    }

    @Test
    public void knownParserReceivesExplicitTagsAsReservedReferences()
    {
        final String message =
                "@Santa met Zezima";

        final List<PlayerReference> tags =
                Collections.singletonList(
                        reference(
                                "@Santa",
                                "Santa",
                                0,
                                6,
                                ReferenceType.TAG));

        Mockito.when(
                        tagParser.parse(
                                message))
                .thenReturn(
                        tags);

        Mockito.when(
                        knownPlayerMentionParser.parse(
                                message,
                                tags))
                .thenReturn(
                        Collections.emptyList());

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.eq(
                                        "Party Hat")))
                .thenReturn(
                        LocalMentionMatch.none());

        Mockito.when(
                        localMentionMatcher.matchMessage(
                                message,
                                "Party Hat"))
                .thenReturn(
                        LocalMentionMatch.none());

        processor.process(
                1L,
                ChatMessageType.PUBLICCHAT,
                "FasT 07",
                message,
                "Party Hat");

        Mockito.verify(
                        knownPlayerMentionParser)
                .parse(
                        message,
                        tags);
    }

    @Test
    public void combinesAndSortsTagAndKnownReferencesByStartOffset()
    {
        final String message =
                "Zezima met @Santa";

        final PlayerReference tag =
                reference(
                        "@Santa",
                        "Santa",
                        11,
                        17,
                        ReferenceType.TAG);

        final PlayerReference knownMention =
                reference(
                        "Zezima",
                        "Zezima",
                        0,
                        6,
                        ReferenceType.MENTION);

        final List<PlayerReference> tags =
                Collections.singletonList(
                        tag);

        Mockito.when(
                        tagParser.parse(
                                message))
                .thenReturn(
                        tags);

        Mockito.when(
                        knownPlayerMentionParser.parse(
                                message,
                                tags))
                .thenReturn(
                        Collections.singletonList(
                                knownMention));

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.eq(
                                        "Party Hat")))
                .thenReturn(
                        LocalMentionMatch.none());

        Mockito.when(
                        localMentionMatcher.matchMessage(
                                message,
                                "Party Hat"))
                .thenReturn(
                        LocalMentionMatch.none());

        final TaggedMessage taggedMessage =
                processor.process(
                        2L,
                        ChatMessageType.PUBLICCHAT,
                        "FasT 07",
                        message,
                        "Party Hat");

        final List<PlayerReference> references =
                taggedMessage.getReferences();

        Assert.assertEquals(
                2,
                references.size());

        Assert.assertEquals(
                "Zezima",
                references.get(
                                0)
                        .getRawText());

        Assert.assertEquals(
                "@Santa",
                references.get(
                                1)
                        .getRawText());

        Assert.assertEquals(
                0,
                references.get(
                                0)
                        .getStartOffset());

        Assert.assertEquals(
                11,
                references.get(
                                1)
                        .getStartOffset());
    }

    @Test
    public void attachesOriginatingChatTypeToEveryReference()
    {
        final String message =
                "Zezima met @Santa";

        final PlayerReference tag =
                reference(
                        "@Santa",
                        "Santa",
                        11,
                        17,
                        ReferenceType.TAG);

        final PlayerReference knownMention =
                reference(
                        "Zezima",
                        "Zezima",
                        0,
                        6,
                        ReferenceType.MENTION);

        final List<PlayerReference> tags =
                Collections.singletonList(
                        tag);

        Mockito.when(
                        tagParser.parse(
                                message))
                .thenReturn(
                        tags);

        Mockito.when(
                        knownPlayerMentionParser.parse(
                                message,
                                tags))
                .thenReturn(
                        Collections.singletonList(
                                knownMention));

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.eq(
                                        "Party Hat")))
                .thenReturn(
                        LocalMentionMatch.none());

        Mockito.when(
                        localMentionMatcher.matchMessage(
                                message,
                                "Party Hat"))
                .thenReturn(
                        LocalMentionMatch.none());

        final TaggedMessage taggedMessage =
                processor.process(
                        3L,
                        ChatMessageType.CLAN_CHAT,
                        "FasT 07",
                        message,
                        "Party Hat");

        Assert.assertEquals(
                2,
                taggedMessage.getReferences()
                        .size());

        for (PlayerReference reference :
                taggedMessage.getReferences())
        {
            Assert.assertEquals(
                    ChatMessageType.CLAN_CHAT,
                    reference.getChatType());
        }
    }

    @Test
    public void structuredLocalReferenceTakesPriorityOverMessageFallback()
    {
        final String message =
                "@Zezima met Santa";

        final PlayerReference localReference =
                reference(
                        "@Zezima",
                        "Zezima",
                        0,
                        7,
                        ReferenceType.TAG);

        final List<PlayerReference> tags =
                Collections.singletonList(
                        localReference);

        final LocalMentionMatch structuredMatch =
                new LocalMentionMatch(
                        true,
                        MatchReason.ACCOUNT_NAME,
                        "@Zezima");

        Mockito.when(
                        tagParser.parse(
                                message))
                .thenReturn(
                        tags);

        Mockito.when(
                        knownPlayerMentionParser.parse(
                                message,
                                tags))
                .thenReturn(
                        Collections.emptyList());

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.eq(
                                        "Zezima")))
                .thenReturn(
                        structuredMatch);

        final TaggedMessage taggedMessage =
                processor.process(
                        4L,
                        ChatMessageType.PUBLICCHAT,
                        "FasT 07",
                        message,
                        "Zezima");

        Assert.assertEquals(
                structuredMatch,
                taggedMessage.getLocalMentionMatch());

        Assert.assertEquals(
                MatchReason.ACCOUNT_NAME,
                taggedMessage.getLocalMentionMatch()
                        .getReason());

        Mockito.verify(
                        localMentionMatcher,
                        Mockito.never())
                .matchMessage(
                        Mockito.anyString(),
                        Mockito.anyString());
    }

    @Test
    public void stopsAtFirstStructuredLocalReferenceMatch()
    {
        final String message =
                "Santa met Zezima";

        final PlayerReference santa =
                reference(
                        "Santa",
                        "Santa",
                        0,
                        5,
                        ReferenceType.MENTION);

        final PlayerReference zezima =
                reference(
                        "Zezima",
                        "Zezima",
                        10,
                        16,
                        ReferenceType.MENTION);

        final List<PlayerReference> knownReferences =
                Arrays.asList(
                        santa,
                        zezima);

        Mockito.when(
                        tagParser.parse(
                                message))
                .thenReturn(
                        Collections.emptyList());

        Mockito.when(
                        knownPlayerMentionParser.parse(
                                message,
                                Collections.emptyList()))
                .thenReturn(
                        knownReferences);

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.argThat(
                                        reference ->
                                                reference != null
                                                        && "Santa".equals(
                                                        reference.getRawText())),
                                Mockito.eq(
                                        "Santa")))
                .thenReturn(
                        new LocalMentionMatch(
                                true,
                                MatchReason.ACCOUNT_NAME,
                                "Santa"));

        final TaggedMessage taggedMessage =
                processor.process(
                        5L,
                        ChatMessageType.PUBLICCHAT,
                        "Party Hat",
                        message,
                        "Santa");

        Assert.assertTrue(
                taggedMessage.getLocalMentionMatch()
                        .isMatchesLocalPlayer());

        Assert.assertEquals(
                "Santa",
                taggedMessage.getLocalMentionMatch()
                        .getMatchedToken());

        Mockito.verify(
                        localMentionMatcher,
                        Mockito.times(
                                1))
                .match(
                        Mockito.any(
                                PlayerReference.class),
                        Mockito.eq(
                                "Santa"));

        Mockito.verify(
                        localMentionMatcher,
                        Mockito.never())
                .matchMessage(
                        Mockito.anyString(),
                        Mockito.anyString());
    }

    @Test
    public void fallsBackToMessageLevelLocalMatch()
    {
        final String message =
                "hello Zezima";

        final LocalMentionMatch messageMatch =
                new LocalMentionMatch(
                        true,
                        MatchReason.NORMALIZED_ACCOUNT_NAME,
                        "Zezima");

        stubNoReferences(
                message,
                "Zezima");

        Mockito.when(
                        localMentionMatcher.matchMessage(
                                message,
                                "Zezima"))
                .thenReturn(
                        messageMatch);

        final TaggedMessage taggedMessage =
                processor.process(
                        6L,
                        ChatMessageType.PUBLICCHAT,
                        "Santa",
                        message,
                        "Zezima");

        Assert.assertEquals(
                messageMatch,
                taggedMessage.getLocalMentionMatch());

        Assert.assertEquals(
                MatchReason.NORMALIZED_ACCOUNT_NAME,
                taggedMessage.getLocalMentionMatch()
                        .getReason());
    }

    @Test
    public void noStructuredOrMessageMatchRemainsNone()
    {
        final String message =
                "Santa met Party Hat";

        stubNoReferences(
                message,
                "Zezima");

        final TaggedMessage taggedMessage =
                processor.process(
                        7L,
                        ChatMessageType.PUBLICCHAT,
                        "FasT 07",
                        message,
                        "Zezima");

        Assert.assertFalse(
                taggedMessage.getLocalMentionMatch()
                        .isMatchesLocalPlayer());

        Assert.assertEquals(
                MatchReason.NONE,
                taggedMessage.getLocalMentionMatch()
                        .getReason());

        Assert.assertNull(
                taggedMessage.getLocalMentionMatch()
                        .getMatchedToken());
    }

    /*
     * HELPERS
     */

    private void stubNoReferences(
            String message,
            String localPlayerName)
    {
        final List<PlayerReference> tags =
                Collections.emptyList();

        Mockito.when(
                        tagParser.parse(
                                message))
                .thenReturn(
                        tags);

        Mockito.when(
                        knownPlayerMentionParser.parse(
                                message,
                                tags))
                .thenReturn(
                        Collections.emptyList());

        Mockito.when(
                        localMentionMatcher.matchMessage(
                                message,
                                localPlayerName))
                .thenReturn(
                        LocalMentionMatch.none());
    }

    private static PlayerReference reference(
            String rawText,
            String lookupName,
            int startOffset,
            int endOffset,
            ReferenceType type)
    {
        return PlayerReference.builder()
                .rawText(
                        rawText)
                .normalizedToken(
                        rawText.replaceFirst(
                                "^@",
                                ""))
                .lookupName(
                        lookupName)
                .startOffset(
                        startOffset)
                .endOffset(
                        endOffset)
                .type(
                        type)
                .build();
    }

    private static void assertSameSemanticResult(
            TaggedMessage expected,
            TaggedMessage actual)
    {
        Assert.assertEquals(
                expected.getId(),
                actual.getId());

        Assert.assertEquals(
                expected.getType(),
                actual.getType());

        Assert.assertEquals(
                expected.getOriginalSender(),
                actual.getOriginalSender());

        Assert.assertEquals(
                expected.getCanonicalSender(),
                actual.getCanonicalSender());

        Assert.assertEquals(
                expected.getOriginalMessage(),
                actual.getOriginalMessage());

        Assert.assertEquals(
                expected.getReferences(),
                actual.getReferences());

        Assert.assertEquals(
                expected.getLocalMentionMatch(),
                actual.getLocalMentionMatch());
    }

    /*
     * PERFORMANCE
     */

    private static ChatProcessor createPerformanceProcessor()
    {
        final PlayerReference tag =
                reference(
                        "@Santa",
                        "Santa",
                        11,
                        17,
                        ReferenceType.TAG);

        final PlayerReference knownMention =
                reference(
                        "Zezima",
                        "Zezima",
                        0,
                        6,
                        ReferenceType.MENTION);

        final TagParser performanceTagParser =
                new TagParser(
                        null,
                        null)
                {
                    @Override
                    public List<PlayerReference> parse(
                            String message)
                    {
                        if ("Zezima met @Santa".equals(
                                message))
                        {
                            return Collections.singletonList(
                                    tag);
                        }

                        return Collections.emptyList();
                    }
                };

        final KnownPlayerMentionParser performanceKnownParser =
                new KnownPlayerMentionParser(
                        null,
                        null)
                {
                    @Override
                    public List<PlayerReference> parse(
                            String message,
                            List<PlayerReference> reservedReferences)
                    {
                        if ("Zezima met @Santa".equals(
                                message))
                        {
                            return Collections.singletonList(
                                    knownMention);
                        }

                        return Collections.emptyList();
                    }
                };

        final LocalMentionMatcher performanceLocalMatcher =
                new LocalMentionMatcher(
                        null,
                        null)
                {
                    @Override
                    public LocalMentionMatch match(
                            PlayerReference reference,
                            String localPlayerName)
                    {
                        return LocalMentionMatch.none();
                    }

                    @Override
                    public LocalMentionMatch matchMessage(
                            String message,
                            String localPlayerName)
                    {
                        return LocalMentionMatch.none();
                    }
                };

        return new ChatProcessor(
                performanceTagParser,
                performanceKnownParser,
                performanceLocalMatcher);
    }

    private static void warmUpPerformanceSamples(
            ChatProcessor processor)
    {
        for (int i = 0; i < PERFORMANCE_WARMUP_ITERATIONS; i++)
        {
            processor.process(
                    i,
                    ChatMessageType.PUBLICCHAT,
                    "FasT 07",
                    "hello Party Hat",
                    "Zezima");

            processor.process(
                    i,
                    ChatMessageType.PUBLICCHAT,
                    "FasT 07",
                    "Zezima met @Santa",
                    "Party Hat");
        }
    }

    private static long measurePerformance(
            ChatProcessor processor,
            String message,
            String localPlayerName)
    {
        long checksum =
                0L;

        final long start =
                System.nanoTime();

        for (int i = 0; i < PERFORMANCE_ITERATIONS; i++)
        {
            final TaggedMessage taggedMessage =
                    processor.process(
                            i,
                            ChatMessageType.PUBLICCHAT,
                            "FasT 07",
                            message,
                            localPlayerName);

            checksum +=
                    taggedMessage.getReferences()
                            .size();

            checksum +=
                    taggedMessage.getOriginalMessage()
                            .length();
        }

        final long elapsed =
                System.nanoTime()
                        - start;

        Assert.assertTrue(
                checksum > 0L);

        return elapsed;
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos / 1_000_000.0;
    }

    @Test
    public void performanceSample()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final ChatProcessor performanceProcessor =
                createPerformanceProcessor();

        warmUpPerformanceSamples(
                performanceProcessor);

        final long noReferencesNanos =
                measurePerformance(
                        performanceProcessor,
                        "hello Party Hat",
                        "Zezima");

        final long mixedReferencesNanos =
                measurePerformance(
                        performanceProcessor,
                        "Zezima met @Santa",
                        "Party Hat");

        System.out.printf(
                "[RuneTags][ChatProcessorTest] Performance= "
                        + "NoReferences: %.3fms (%.6fms) | "
                        + "MixedReferences: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                nanosToMilliseconds(
                        noReferencesNanos),
                nanosToMilliseconds(
                        noReferencesNanos) / PERFORMANCE_ITERATIONS,
                nanosToMilliseconds(
                        mixedReferencesNanos),
                nanosToMilliseconds(
                        mixedReferencesNanos) / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }
}