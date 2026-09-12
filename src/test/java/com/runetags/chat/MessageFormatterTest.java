package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.MatchReason;
import com.runetags.mention.NameNormalizer;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Color;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.util.ColorUtil;

import org.junit.Assert;
import org.junit.Test;

public class MessageFormatterTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 10_000;
    private static final int PERFORMANCE_ITERATIONS = 100_000;

    /*
     * TESTS
     */

    @Test
    public void nullMessageReturnsRawMessage()
    {
        final TestConfigurations config =
                new TestConfigurations();

        final MessageFormatter formatter =
                formatter(
                        config);

        Assert.assertEquals(
                "Zezima is here",
                formatter.format(
                        null,
                        "Zezima is here",
                        "Santa"));
    }

    @Test
    public void nullRawMessageReturnsNull()
    {
        final TestConfigurations config =
                new TestConfigurations();

        final MessageFormatter formatter =
                formatter(
                        config);

        Assert.assertNull(
                formatter.format(
                        message(
                                "Zezima is here",
                                Collections.emptyList(),
                                LocalMentionMatch.none()),
                        null,
                        "Santa"));
    }

    @Test
    public void semanticMismatchReturnsRawMessageUnchanged()
    {
        final TestConfigurations config =
                new TestConfigurations();

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "<col=ff0000>Zezima</col> is here";

        final TaggedMessage message =
                message(
                        "Santa is here",
                        Collections.singletonList(
                                reference(
                                        "Santa",
                                        0,
                                        5,
                                        ReferenceType.MENTION,
                                        true)),
                        LocalMentionMatch.none());

        Assert.assertEquals(
                raw,
                formatter.format(
                        message,
                        raw,
                        "Party Hat"));
    }

    @Test
    public void messageWithoutReferencesOrLocalMatchRemainsUnchanged()
    {
        final TestConfigurations config =
                new TestConfigurations();

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Zezima is here";

        Assert.assertEquals(
                raw,
                formatter.format(
                        message(
                                raw,
                                Collections.emptyList(),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void otherPlayerReferenceUsesOtherMentionColor()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello Zezima";

        final String expected =
                "Hello "
                        + ColorUtil.colorTag(
                        config.otherMentionColor())
                        + "Zezima"
                        + normalColorTag();

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Zezima",
                                                6,
                                                12,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void localPlayerReferenceUsesSelfMentionColor()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello Santa";

        final String expected =
                "Hello "
                        + ColorUtil.colorTag(
                        config.selfMentionColor())
                        + "Santa"
                        + normalColorTag();

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Santa",
                                                6,
                                                11,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void disablingOtherColorStillAllowsUnderline()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionOthers = false;
        config.underlineMentions = true;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello Zezima";

        Assert.assertEquals(
                "Hello <u>Zezima</u>",
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Zezima",
                                                6,
                                                12,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void disablingSelfColorStillAllowsUnderline()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionSelf = false;
        config.underlineMentions = true;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello Santa";

        Assert.assertEquals(
                "Hello <u>Santa</u>",
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Santa",
                                                6,
                                                11,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void unresolvedTagDoesNotReceiveNativeUnderline()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionOthers = false;
        config.underlineMentions = true;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello @Zezima";

        Assert.assertEquals(
                raw,
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "@Zezima",
                                                6,
                                                13,
                                                ReferenceType.TAG,
                                                false)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void resolvedTagReceivesNativeUnderline()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionOthers = false;
        config.underlineMentions = true;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello @Zezima";

        Assert.assertEquals(
                "Hello <u>@Zezima</u>",
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "@Zezima",
                                                6,
                                                13,
                                                ReferenceType.TAG,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void shadowCanBeAppliedWithoutColorOrUnderline()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionOthers = false;
        config.underlineMentions = false;
        config.shadowMentions = true;
        config.shadowMentionColor =
                new Color(
                        0x12,
                        0x34,
                        0x56);

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello Zezima";

        Assert.assertEquals(
                "Hello <shad=123456>Zezima</shad>",
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Zezima",
                                                6,
                                                12,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void existingColorIsRestoredAfterReference()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "<col=00ff00>Hello Zezima</col>";

        final String expected =
                "<col=00ff00>Hello "
                        + ColorUtil.colorTag(
                        config.otherMentionColor())
                        + "Zezima"
                        + "<col=00ff00></col>";

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                "Hello Zezima",
                                Collections.singletonList(
                                        reference(
                                                "Zezima",
                                                6,
                                                12,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void wholeMessageLocalMatchUsesSelfMentionColor()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionWholeMessage = true;
        config.mentionSelf = true;
        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Santa is here";

        Assert.assertEquals(
                ColorUtil.colorTag(
                        config.selfMentionColor())
                        + raw
                        + ColorUtil.CLOSING_COLOR_TAG,
                formatter.format(
                        message(
                                raw,
                                Collections.emptyList(),
                                new LocalMentionMatch(
                                        true,
                                        MatchReason.ACCOUNT_NAME,
                                        "Santa")),
                        raw,
                        "Santa"));
    }

    @Test
    public void wholeMessageColorRestoresAfterOtherPlayerReference()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionWholeMessage = true;
        config.mentionSelf = true;
        config.mentionOthers = true;
        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Santa saw Zezima";

        final String selfColor =
                ColorUtil.colorTag(
                        config.selfMentionColor());

        final String otherColor =
                ColorUtil.colorTag(
                        config.otherMentionColor());

        final String expected =
                selfColor
                        + "Santa saw "
                        + otherColor
                        + "Zezima"
                        + selfColor
                        + ColorUtil.CLOSING_COLOR_TAG;

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Zezima",
                                                10,
                                                16,
                                                ReferenceType.MENTION,
                                                true)),
                                new LocalMentionMatch(
                                        true,
                                        MatchReason.ACCOUNT_NAME,
                                        "Santa")),
                        raw,
                        "Santa"));
    }

    @Test
    public void normalizedLocalTokenIsStyledWhenWholeMessageIsDisabled()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionWholeMessage = false;
        config.mentionSelf = true;
        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "hello santa-clause";

        final String expected =
                "hello "
                        + ColorUtil.colorTag(
                        config.selfMentionColor())
                        + "santa-clause"
                        + normalColorTag();

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                raw,
                                Collections.emptyList(),
                                new LocalMentionMatch(
                                        true,
                                        MatchReason.NORMALIZED_ACCOUNT_NAME,
                                        "santa-clause")),
                        raw,
                        "Santa Clause"));
    }

    @Test
    public void uniqueHighlightMatchesAreCaseInsensitiveAndRespectBoundaries()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionWholeMessage = false;
        config.mentionSelf = true;
        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Santa saw SANTA but SantaClause stayed";

        final String selfColor =
                ColorUtil.colorTag(
                        config.selfMentionColor());

        final String expected =
                selfColor
                        + "Santa"
                        + normalColorTag()
                        + " saw "
                        + selfColor
                        + "SANTA"
                        + normalColorTag()
                        + " but SantaClause stayed";

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                raw,
                                Collections.emptyList(),
                                new LocalMentionMatch(
                                        true,
                                        MatchReason.UNIQUE_HIGHLIGHT,
                                        "santa")),
                        raw,
                        "Party Hat"));
    }

    @Test
    public void localMessageSpanDoesNotDuplicateExistingReferenceSpan()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.mentionWholeMessage = false;
        config.mentionSelf = true;
        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Santa is here";

        final String expected =
                ColorUtil.colorTag(
                        config.selfMentionColor())
                        + "Santa"
                        + normalColorTag()
                        + " is here";

        Assert.assertEquals(
                expected,
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Santa",
                                                0,
                                                5,
                                                ReferenceType.MENTION,
                                                true)),
                                new LocalMentionMatch(
                                        true,
                                        MatchReason.NORMALIZED_ACCOUNT_NAME,
                                        "Santa")),
                        raw,
                        "Santa"));
    }

    @Test
    public void invalidReferenceOffsetsAreIgnored()
    {
        final TestConfigurations config =
                new TestConfigurations();

        config.underlineMentions = false;
        config.shadowMentions = false;

        final MessageFormatter formatter =
                formatter(
                        config);

        final String raw =
                "Hello Zezima";

        Assert.assertEquals(
                raw,
                formatter.format(
                        message(
                                raw,
                                Collections.singletonList(
                                        reference(
                                                "Zezima",
                                                50,
                                                60,
                                                ReferenceType.MENTION,
                                                true)),
                                LocalMentionMatch.none()),
                        raw,
                        "Santa"));
    }

    @Test
    public void performanceSample()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final TestConfigurations config =
                new TestConfigurations();

        config.underlineMentions = true;
        config.shadowMentions = true;
        config.mentionSelf = true;
        config.mentionOthers = true;
        config.mentionWholeMessage = true;

        final MessageFormatter formatter =
                formatter(
                        config);

        final TaggedMessage plainMessage =
                message(
                        "Zezima met Santa",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        final TaggedMessage otherReferenceMessage =
                message(
                        "Zezima met Santa",
                        Collections.singletonList(
                                reference(
                                        "Zezima",
                                        0,
                                        6,
                                        ReferenceType.MENTION,
                                        true)),
                        LocalMentionMatch.none());

        final TaggedMessage mixedReferencesMessage =
                message(
                        "Zezima met Santa Clause",
                        Arrays.asList(
                                reference(
                                        "Zezima",
                                        0,
                                        6,
                                        ReferenceType.MENTION,
                                        true),
                                reference(
                                        "Santa Clause",
                                        11,
                                        23,
                                        ReferenceType.MENTION,
                                        true)),
                        new LocalMentionMatch(
                                true,
                                MatchReason.ACCOUNT_NAME,
                                "Santa Clause"));

        warmUp(
                formatter,
                plainMessage,
                otherReferenceMessage,
                mixedReferencesMessage);

        final long plainStart =
                System.nanoTime();

        String plainResult = null;

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            plainResult =
                    formatter.format(
                            plainMessage,
                            "Zezima met Santa",
                            "Party Hat");
        }

        final long plainNanos =
                System.nanoTime()
                        - plainStart;

        final long otherReferenceStart =
                System.nanoTime();

        String otherReferenceResult = null;

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            otherReferenceResult =
                    formatter.format(
                            otherReferenceMessage,
                            "Zezima met Santa",
                            "Party Hat");
        }

        final long otherReferenceNanos =
                System.nanoTime()
                        - otherReferenceStart;

        final long mixedReferencesStart =
                System.nanoTime();

        String mixedReferencesResult = null;

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            mixedReferencesResult =
                    formatter.format(
                            mixedReferencesMessage,
                            "Zezima met Santa Clause",
                            "Santa Clause");
        }

        final long mixedReferencesNanos =
                System.nanoTime()
                        - mixedReferencesStart;

        Assert.assertNotNull(
                plainResult);

        Assert.assertNotNull(
                otherReferenceResult);

        Assert.assertNotNull(
                mixedReferencesResult);

        printPerformance(
                plainNanos,
                otherReferenceNanos,
                mixedReferencesNanos);
    }

    /*
     * HELPERS
     */

    private static MessageFormatter formatter(
            TestConfigurations config)
    {
        return new MessageFormatter(
                config,
                new LocalMentionMatcher(
                        config,
                        new NameNormalizer()));
    }

    private static TaggedMessage message(
            String originalMessage,
            java.util.List<PlayerReference> references,
            LocalMentionMatch localMentionMatch)
    {
        return TaggedMessage.builder()
                .id(
                        1L)
                .type(
                        ChatMessageType.PUBLICCHAT)
                .originalSender(
                        "Party Hat")
                .canonicalSender(
                        "Party Hat")
                .originalMessage(
                        originalMessage)
                .timestamp(
                        Instant.EPOCH)
                .references(
                        references)
                .localMentionMatch(
                        localMentionMatch)
                .build();
    }

    private static PlayerReference reference(
            String rawText,
            int startOffset,
            int endOffset,
            ReferenceType type,
            boolean locallyResolved)
    {
        return PlayerReference.builder()
                .rawText(
                        rawText)
                .normalizedToken(
                        rawText)
                .lookupName(
                        rawText.replaceFirst(
                                "^@",
                                ""))
                .startOffset(
                        startOffset)
                .endOffset(
                        endOffset)
                .type(
                        type)
                .locallyResolved(
                        locallyResolved)
                .identity(
                        null)
                .chatType(
                        ChatMessageType.PUBLICCHAT)
                .build();
    }

    private static String normalColorTag()
    {
        return "<col"
                + ChatColorType.NORMAL
                + ">";
    }

    /*
     * PERFORMANCE
     */

    private static void warmUp(
            MessageFormatter formatter,
            TaggedMessage plainMessage,
            TaggedMessage otherReferenceMessage,
            TaggedMessage mixedReferencesMessage)
    {
        String result = null;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            result =
                    formatter.format(
                            plainMessage,
                            "Zezima met Santa",
                            "Party Hat");

            result =
                    formatter.format(
                            otherReferenceMessage,
                            "Zezima met Santa",
                            "Party Hat");

            result =
                    formatter.format(
                            mixedReferencesMessage,
                            "Zezima met Santa Clause",
                            "Santa Clause");
        }

        Assert.assertNotNull(
                result);
    }

    private static void printPerformance(
            long plainNanos,
            long otherReferenceNanos,
            long mixedReferencesNanos)
    {
        final double plainTotalMs =
                nanosToMilliseconds(
                        plainNanos);

        final double otherReferenceTotalMs =
                nanosToMilliseconds(
                        otherReferenceNanos);

        final double mixedReferencesTotalMs =
                nanosToMilliseconds(
                        mixedReferencesNanos);

        System.out.printf(
                "[RuneTags][MessageFormatterTest] Performance= "
                        + "Plain: %.3fms (%.6fms) | "
                        + "OtherReference: %.3fms (%.6fms) | "
                        + "MixedReferences: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                plainTotalMs,
                plainTotalMs
                        / PERFORMANCE_ITERATIONS,
                otherReferenceTotalMs,
                otherReferenceTotalMs
                        / PERFORMANCE_ITERATIONS,
                mixedReferencesTotalMs,
                mixedReferencesTotalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos
                / 1_000_000.0;
    }

    private static final class TestConfigurations
            implements Configurations
    {
        private boolean underlineMentions = true;
        private boolean shadowMentions = true;
        private Color shadowMentionColor =
                new Color(
                        255,
                        200,
                        0,
                        255);

        private boolean mentionSelf = true;
        private Color selfMentionColor =
                Color.RED;

        private boolean mentionOthers = true;
        private Color otherMentionColor =
                Color.WHITE;

        private boolean mentionWholeMessage = true;

        @Override
        public boolean underlineMentions()
        {
            return underlineMentions;
        }

        @Override
        public boolean shadowMentions()
        {
            return shadowMentions;
        }

        @Override
        public Color shadowMentionColor()
        {
            return shadowMentionColor;
        }

        @Override
        public boolean mentionSelf()
        {
            return mentionSelf;
        }

        @Override
        public Color selfMentionColor()
        {
            return selfMentionColor;
        }

        @Override
        public boolean mentionOthers()
        {
            return mentionOthers;
        }

        @Override
        public Color otherMentionColor()
        {
            return otherMentionColor;
        }

        @Override
        public boolean mentionWholeMessage()
        {
            return mentionWholeMessage;
        }

        @Override
        public String uniqueMentions()
        {
            return "";
        }
    }
}