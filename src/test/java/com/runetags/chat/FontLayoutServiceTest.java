package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.config.MentionFont;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class FontLayoutServiceTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private TestConfigurations config;
    private TaggedMessageRepository repository;

    private TestFont nativeFont;
    private TestFont boldFont;
    private TestFont verdanaFont;

    private RecordingReferenceLayoutService referenceLayoutService;
    private FontLayoutService service;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        config =
                new TestConfigurations();

        repository =
                new TaggedMessageRepository(
                        100);

        nativeFont =
                new TestFont(
                        5);

        boldFont =
                new TestFont(
                        6);

        verdanaFont =
                new TestFont(
                        7);

        referenceLayoutService =
                new RecordingReferenceLayoutService(
                        nativeFont,
                        boldFont,
                        verdanaFont);

        service =
                new FontLayoutService(
                        client,
                        config,
                        repository,
                        referenceLayoutService);
    }

    /*
     * TESTS
     */

    @Test
    public void nullEventIsIgnored()
    {
        service.onScriptPreFired(
                null);

        Assert.assertFalse(
                service.onPostClientTick());

        Assert.assertEquals(
                0,
                referenceLayoutService.syncCalls);
    }

    @Test
    public void unsupportedScriptIsIgnored()
    {
        service.onScriptPreFired(
                new ScriptPreFired(
                        999));

        Assert.assertFalse(
                service.onPostClientTick());

        Assert.assertEquals(
                0,
                referenceLayoutService.syncCalls);

        Assert.assertEquals(
                0,
                referenceLayoutService.favoriteSenderDirtyCalls);

        Mockito.verifyNoInteractions(
                client);
    }

    @Test
    public void markFontsDirtyPerformsExactlyOnePostTickSynchronization()
    {
        service.markFontsDirty();

        Assert.assertTrue(
                service.onPostClientTick());

        Assert.assertFalse(
                service.onPostClientTick());

        Assert.assertEquals(
                1,
                referenceLayoutService.syncCalls);
    }

    @Test
    public void supportedConstructionScriptsMarkDirtyAndCoalesce()
    {
        Mockito.when(
                        client.getObjectStack())
                .thenReturn(
                        new Object[0]);

        Mockito.when(
                        client.getObjectStackSize())
                .thenReturn(
                        0);

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        service.onScriptPreFired(
                new ScriptPreFired(
                        4483));

        Assert.assertTrue(
                service.onPostClientTick());

        Assert.assertFalse(
                service.onPostClientTick());

        Assert.assertEquals(
                1,
                referenceLayoutService.syncCalls);
    }

    @Test
    public void normalFontDoesNotChangeNativeHeight()
    {
        config.fontMentions =
                MentionFont.NORMAL;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                14,
                construction.intStack[5]);

        Assert.assertEquals(
                0,
                referenceLayoutService.measureCalls);
    }

    @Test
    public void messageWithoutMentionTreatmentDoesNotChangeNativeHeight()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                ordinaryMessage(
                        "Hello Santa",
                        "Zezima"));

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                14,
                construction.intStack[5]);

        Assert.assertEquals(
                0,
                referenceLayoutService.measureCalls);
    }

    @Test
    public void boldFontCompensationUsesNativeAndCustomLineCounts()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        referenceLayoutService.setLineCounts(
                1,
                2);

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                28,
                construction.intStack[5]);

        Assert.assertEquals(
                2,
                referenceLayoutService.measureCalls);

        Assert.assertEquals(
                Arrays.asList(
                        62,
                        62),
                referenceLayoutService.availableWidths);
    }

    @Test
    public void compensationUsesCeilingWhenNativeMessageAlreadyWraps()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        referenceLayoutService.setLineCounts(
                2,
                3);

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                21,
                construction.intStack[5]);
    }

    @Test
    public void equalLineCountsPreserveNativeHeight()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        referenceLayoutService.setLineCounts(
                2,
                2);

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                14,
                construction.intStack[5]);
    }

    @Test
    public void missingPrefixPreventsCompensation()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        Mockito.when(
                        client.getObjectStack())
                .thenReturn(
                        new Object[]
                                {
                                        "Hello Santa"
                                });

        Mockito.when(
                        client.getObjectStackSize())
                .thenReturn(
                        1);

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                14,
                construction.intStack[5]);

        Assert.assertEquals(
                0,
                referenceLayoutService.measureCalls);
    }

    @Test
    public void invalidConstructionIntStackPreventsCompensation()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        Mockito.when(
                        client.getObjectStack())
                .thenReturn(
                        new Object[]
                                {
                                        "Zezima:",
                                        "Hello Santa"
                                });

        Mockito.when(
                        client.getObjectStackSize())
                .thenReturn(
                        2);

        final int[] intStack =
                new int[10];

        Mockito.when(
                        client.getIntStack())
                .thenReturn(
                        intStack);

        Mockito.when(
                        client.getIntStackSize())
                .thenReturn(
                        10);

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                0,
                referenceLayoutService.measureCalls);
    }

    @Test
    public void clanConstructionIncludesNativeDecorationWidth()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        referenceLayoutService.setLineCounts(
                1,
                1);

        final ConstructionState construction =
                configure4483Construction(
                        "Hello Santa",
                        "[Clan]",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        4483));

        Assert.assertEquals(
                14,
                construction.intStack[8]);

        Assert.assertEquals(
                Arrays.asList(
                        115,
                        115),
                referenceLayoutService.availableWidths);
    }

    @Test
    public void semanticBodyMarkupStillMatchesStoredMessage()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        referenceLayoutService.setLineCounts(
                1,
                2);

        final ConstructionState construction =
                configure203Construction(
                        "<col=ff0000>Hello Santa</col>",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                28,
                construction.intStack[5]);

        Assert.assertEquals(
                2,
                referenceLayoutService.measureCalls);
    }

    @Test
    public void nonBreakingSpacesInPrefixAreNormalizedForMeasurement()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Santa Clause"));

        referenceLayoutService.setLineCounts(
                1,
                1);

        configure203Construction(
                "Hello Santa",
                "Santa\u00A0Clause:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertTrue(
                nativeFont.measuredTexts.contains(
                        "Santa Clause:"));
    }

    @Test
    public void fontProbeRestoresOriginalFontAndCachesResolvedFonts()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                treatedMessage(
                        "Hello Santa",
                        "Zezima"));

        referenceLayoutService.setLineCounts(
                1,
                2);

        final ConstructionState construction =
                configure203Construction(
                        "Hello Santa",
                        "Zezima:");

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                777,
                construction.probe.currentFontId.get());

        Assert.assertEquals(
                Arrays.asList(
                        FontID.PLAIN_12,
                        777,
                        FontID.BOLD_12,
                        777),
                construction.probe.assignedFontIds);

        construction.intStack[5] =
                14;

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                777,
                construction.probe.currentFontId.get());

        Assert.assertEquals(
                4,
                construction.probe.assignedFontIds
                        .size());
    }

    @Test
    public void performanceSample()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final PerformanceHarness ordinary =
                PerformanceHarness.create(
                        false);

        final PerformanceHarness mention =
                PerformanceHarness.create(
                        true);

        final long warmupChecksum =
                warmUp(
                        ordinary,
                        mention);

        Assert.assertTrue(
                warmupChecksum > 0L);

        long ordinaryChecksum =
                0L;

        final long ordinaryStarted =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            ordinaryChecksum +=
                    ordinary.runConstruction();
        }

        final long ordinaryNanos =
                System.nanoTime()
                        - ordinaryStarted;

        long mentionChecksum =
                0L;

        final long mentionStarted =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            mentionChecksum +=
                    mention.runConstruction();
        }

        final long mentionNanos =
                System.nanoTime()
                        - mentionStarted;

        Assert.assertTrue(
                ordinaryChecksum > 0L);

        Assert.assertTrue(
                mentionChecksum > ordinaryChecksum);

        printPerformance(
                ordinaryNanos,
                mentionNanos);
    }

    @Test
    public void supportedConstructionScriptsMarkFavoriteSenderRowsDirty()
    {
        Mockito.when(
                        client.getObjectStack())
                .thenReturn(
                        new Object[0]);

        Mockito.when(
                        client.getObjectStackSize())
                .thenReturn(
                        0);

        service.onScriptPreFired(
                new ScriptPreFired(
                        203));

        Assert.assertEquals(
                1,
                referenceLayoutService.favoriteSenderDirtyCalls);

        service.onScriptPreFired(
                new ScriptPreFired(
                        4483));

        Assert.assertEquals(
                2,
                referenceLayoutService.favoriteSenderDirtyCalls);
    }

    /*
     * HELPERS
     */

    private ConstructionState configure203Construction(
            String rawBody,
            String rawPrefix)
    {
        final Object[] objectStack =
                new Object[]
                        {
                                rawPrefix,
                                rawBody
                        };

        Mockito.when(
                        client.getObjectStack())
                .thenReturn(
                        objectStack);

        Mockito.when(
                        client.getObjectStackSize())
                .thenReturn(
                        objectStack.length);

        final int[] intStack =
                new int[11];

        /*
         * Relative positions for the trailing eleven-value payload:
         *
         * size - 10 = line widget id = 1
         * size - 8  = right boundary = 3
         * size - 6  = vertical value = 5
         */
        intStack[1] =
                7001;

        intStack[3] =
                100;

        intStack[5] =
                14;

        Mockito.when(
                        client.getIntStack())
                .thenReturn(
                        intStack);

        Mockito.when(
                        client.getIntStackSize())
                .thenReturn(
                        intStack.length);

        final Widget lineWidget =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        lineWidget.getOriginalX())
                .thenReturn(
                        0);

        Mockito.when(
                        client.getWidget(
                                7001))
                .thenReturn(
                        lineWidget);

        final ProbeState probe =
                createProbe(
                        777);

        Mockito.when(
                        client.getWidget(
                                WidgetInfo.CHATBOX_INPUT))
                .thenReturn(
                        probe.widget);

        return new ConstructionState(
                intStack,
                probe);
    }

    private ConstructionState configure4483Construction(
            String rawBody,
            String channel,
            String sender)
    {
        final Object[] objectStack =
                new Object[]
                        {
                                channel,
                                sender,
                                rawBody
                        };

        Mockito.when(
                        client.getObjectStack())
                .thenReturn(
                        objectStack);

        Mockito.when(
                        client.getObjectStackSize())
                .thenReturn(
                        objectStack.length);

        final int[] intStack =
                new int[14];

        /*
         * 4483 has three leading values before the common eleven-value payload.
         *
         * Its native 13 x 13 decoration contributes 12px horizontally.
         */
        intStack[1] =
                13;

        intStack[2] =
                13;

        intStack[4] =
                7002;

        intStack[6] =
                200;

        intStack[8] =
                14;

        Mockito.when(
                        client.getIntStack())
                .thenReturn(
                        intStack);

        Mockito.when(
                        client.getIntStackSize())
                .thenReturn(
                        intStack.length);

        final Widget lineWidget =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        lineWidget.getOriginalX())
                .thenReturn(
                        0);

        Mockito.when(
                        client.getWidget(
                                7002))
                .thenReturn(
                        lineWidget);

        final ProbeState probe =
                createProbe(
                        777);

        Mockito.when(
                        client.getWidget(
                                WidgetInfo.CHATBOX_INPUT))
                .thenReturn(
                        probe.widget);

        return new ConstructionState(
                intStack,
                probe);
    }

    private ProbeState createProbe(
            int originalFontId)
    {
        final Widget probe =
                Mockito.mock(
                        Widget.class);

        final AtomicInteger currentFontId =
                new AtomicInteger(
                        originalFontId);

        final List<Integer> assignedFontIds =
                new ArrayList<>();

        Mockito.when(
                        probe.getFontId())
                .thenAnswer(
                        ignored ->
                                currentFontId.get());

        Mockito.when(
                        probe.setFontId(
                                Mockito.anyInt()))
                .thenAnswer(
                        invocation ->
                        {
                            final int fontId =
                                    invocation.getArgument(
                                            0);

                            currentFontId.set(
                                    fontId);

                            assignedFontIds.add(
                                    fontId);

                            return probe;
                        });

        Mockito.when(
                        probe.getFont())
                .thenAnswer(
                        ignored ->
                        {
                            final int fontId =
                                    currentFontId.get();

                            if (fontId == FontID.PLAIN_12)
                            {
                                return nativeFont;
                            }

                            if (fontId == FontID.BOLD_12)
                            {
                                return boldFont;
                            }

                            if (fontId == FontID.VERDANA_13_BOLD)
                            {
                                return verdanaFont;
                            }

                            return null;
                        });

        return new ProbeState(
                probe,
                currentFontId,
                assignedFontIds);
    }

    private static TaggedMessage treatedMessage(
            String body,
            String sender)
    {
        final int start =
                body.indexOf(
                        "Santa");

        final int safeStart =
                start >= 0
                        ? start
                        : 0;

        final int safeEnd =
                start >= 0
                        ? start + 5
                        : Math.min(
                        body.length(),
                        5);

        return TaggedMessage.builder()
                .id(
                        1L)
                .type(
                        ChatMessageType.PUBLICCHAT)
                .originalSender(
                        sender)
                .canonicalSender(
                        sender)
                .originalMessage(
                        body)
                .timestamp(
                        Instant.EPOCH)
                .references(
                        Collections.singletonList(
                                PlayerReference.builder()
                                        .rawText(
                                                "Santa")
                                        .normalizedToken(
                                                "Santa")
                                        .lookupName(
                                                "Santa")
                                        .startOffset(
                                                safeStart)
                                        .endOffset(
                                                safeEnd)
                                        .type(
                                                ReferenceType.MENTION)
                                        .locallyResolved(
                                                true)
                                        .identity(
                                                null)
                                        .chatType(
                                                ChatMessageType.PUBLICCHAT)
                                        .build()))
                .localMentionMatch(
                        LocalMentionMatch.none())
                .build();
    }

    private static TaggedMessage ordinaryMessage(
            String body,
            String sender)
    {
        return TaggedMessage.builder()
                .id(
                        1L)
                .type(
                        ChatMessageType.PUBLICCHAT)
                .originalSender(
                        sender)
                .canonicalSender(
                        sender)
                .originalMessage(
                        body)
                .timestamp(
                        Instant.EPOCH)
                .references(
                        Collections.emptyList())
                .localMentionMatch(
                        LocalMentionMatch.none())
                .build();
    }

    private static final class ConstructionState
    {
        private final int[] intStack;
        private final ProbeState probe;

        private ConstructionState(
                int[] intStack,
                ProbeState probe)
        {
            this.intStack =
                    intStack;

            this.probe =
                    probe;
        }
    }

    private static final class ProbeState
    {
        private final Widget widget;
        private final AtomicInteger currentFontId;
        private final List<Integer> assignedFontIds;

        private ProbeState(
                Widget widget,
                AtomicInteger currentFontId,
                List<Integer> assignedFontIds)
        {
            this.widget =
                    widget;

            this.currentFontId =
                    currentFontId;

            this.assignedFontIds =
                    assignedFontIds;
        }
    }

    private static final class TestConfigurations
            implements Configurations
    {
        private MentionFont fontMentions =
                MentionFont.NORMAL;

        @Override
        public MentionFont fontMentions()
        {
            return fontMentions;
        }
    }

    private static final class TestFont
            implements FontTypeFace
    {
        private final int characterWidth;

        private final List<String> measuredTexts =
                new ArrayList<>();

        private TestFont(
                int characterWidth)
        {
            this.characterWidth =
                    characterWidth;
        }

        @Override
        public int getTextWidth(
                String text)
        {
            measuredTexts.add(
                    text);

            return text == null
                    ? 0
                    : text.length()
                    * characterWidth;
        }

        @Override
        public int getBaseline()
        {
            return 12;
        }

        @Override
        public void drawWidgetText(
                String text,
                int x,
                int y,
                int width,
                int height,
                int rgb,
                int shadowRgb,
                int alpha,
                int xTextAlignment,
                int yTextAlignment,
                int lineHeight)
        {
        }
    }

    private static final class RecordingReferenceLayoutService
            extends ReferenceLayoutService
    {
        private final FontTypeFace nativeFont;
        private final FontTypeFace boldFont;
        private final FontTypeFace verdanaFont;

        private int nativeLines =
                1;

        private int selectedLines =
                1;

        private int measureCalls;
        private int syncCalls;
        private int favoriteSenderDirtyCalls;

        private final List<Integer> availableWidths =
                new ArrayList<>();

        private RecordingReferenceLayoutService(
                FontTypeFace nativeFont,
                FontTypeFace boldFont,
                FontTypeFace verdanaFont)
        {
            super(
                    null,
                    new Configurations()
                    {
                    },
                    new TaggedMessageRepository(
                            1),
                    null);

            this.nativeFont =
                    nativeFont;

            this.boldFont =
                    boldFont;

            this.verdanaFont =
                    verdanaFont;
        }

        private void setLineCounts(
                int nativeLines,
                int selectedLines)
        {
            this.nativeLines =
                    nativeLines;

            this.selectedLines =
                    selectedLines;

            measureCalls =
                    0;

            availableWidths.clear();
        }

        @Override
        public int measureWrappedLineCount(
                String rawWidgetText,
                FontTypeFace font,
                int availableWidth)
        {
            measureCalls++;

            availableWidths.add(
                    availableWidth);

            if (font == nativeFont)
            {
                return nativeLines;
            }

            if (font == boldFont
                    || font == verdanaFont)
            {
                return selectedLines;
            }

            return 1;
        }

        @Override
        public void syncMentionFonts()
        {
            syncCalls++;
        }

        @Override
        public void markFavoriteSenderRowsDirty()
        {
            favoriteSenderDirtyCalls++;
        }
    }

    /*
     * PERFORMANCE
     */

    private static long warmUp(
            PerformanceHarness ordinary,
            PerformanceHarness mention)
    {
        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            checksum +=
                    ordinary.runConstruction();

            checksum +=
                    mention.runConstruction();
        }

        return checksum;
    }

    private static void printPerformance(
            long ordinaryNanos,
            long mentionNanos)
    {
        final double ordinaryTotalMs =
                nanosToMilliseconds(
                        ordinaryNanos);

        final double mentionTotalMs =
                nanosToMilliseconds(
                        mentionNanos);

        System.out.printf(
                "[RuneTags][FontLayoutServiceTest] Performance= "
                        + "Ordinary203: %.3fms (%.6fms) | "
                        + "Mention203: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                ordinaryTotalMs,
                ordinaryTotalMs
                        / PERFORMANCE_ITERATIONS,
                mentionTotalMs,
                mentionTotalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos
                / 1_000_000.0;
    }

    private static final class PerformanceHarness
    {
        private final FontLayoutService service;
        private final int[] intStack;

        private PerformanceHarness(
                FontLayoutService service,
                int[] intStack)
        {
            this.service =
                    service;

            this.intStack =
                    intStack;
        }

        private static PerformanceHarness create(
                boolean mentionTreatment)
        {
            final Client client =
                    Mockito.mock(
                            Client.class);

            final PerformanceConfigurations config =
                    new PerformanceConfigurations();

            final TaggedMessageRepository repository =
                    new TaggedMessageRepository(
                            10);

            final PerformanceFont nativeFont =
                    new PerformanceFont(
                            5);

            final PerformanceFont boldFont =
                    new PerformanceFont(
                            6);

            final PerformanceReferenceLayoutService referenceLayoutService =
                    new PerformanceReferenceLayoutService(
                            nativeFont,
                            boldFont);

            final String body =
                    "Hello Santa";

            final String sender =
                    "Zezima";

            final TaggedMessage message;

            if (mentionTreatment)
            {
                message =
                        TaggedMessage.builder()
                                .id(
                                        1L)
                                .type(
                                        ChatMessageType.PUBLICCHAT)
                                .originalSender(
                                        sender)
                                .canonicalSender(
                                        sender)
                                .originalMessage(
                                        body)
                                .timestamp(
                                        Instant.EPOCH)
                                .references(
                                        Collections.singletonList(
                                                PlayerReference.builder()
                                                        .rawText(
                                                                "Santa")
                                                        .normalizedToken(
                                                                "Santa")
                                                        .lookupName(
                                                                "Santa")
                                                        .startOffset(
                                                                6)
                                                        .endOffset(
                                                                11)
                                                        .type(
                                                                ReferenceType.MENTION)
                                                        .locallyResolved(
                                                                true)
                                                        .identity(
                                                                null)
                                                        .chatType(
                                                                ChatMessageType.PUBLICCHAT)
                                                        .build()))
                                .localMentionMatch(
                                        LocalMentionMatch.none())
                                .build();
            }
            else
            {
                message =
                        TaggedMessage.builder()
                                .id(
                                        1L)
                                .type(
                                        ChatMessageType.PUBLICCHAT)
                                .originalSender(
                                        sender)
                                .canonicalSender(
                                        sender)
                                .originalMessage(
                                        body)
                                .timestamp(
                                        Instant.EPOCH)
                                .references(
                                        Collections.emptyList())
                                .localMentionMatch(
                                        LocalMentionMatch.none())
                                .build();
            }

            repository.add(
                    message);

            final Object[] objectStack =
                    new Object[]
                            {
                                    "Zezima:",
                                    body
                            };

            Mockito.when(
                            client.getObjectStack())
                    .thenReturn(
                            objectStack);

            Mockito.when(
                            client.getObjectStackSize())
                    .thenReturn(
                            objectStack.length);

            final int[] intStack =
                    new int[11];

            intStack[1] =
                    8001;

            intStack[3] =
                    100;

            intStack[5] =
                    14;

            Mockito.when(
                            client.getIntStack())
                    .thenReturn(
                            intStack);

            Mockito.when(
                            client.getIntStackSize())
                    .thenReturn(
                            intStack.length);

            final Widget lineWidget =
                    Mockito.mock(
                            Widget.class);

            Mockito.when(
                            lineWidget.getOriginalX())
                    .thenReturn(
                            0);

            Mockito.when(
                            client.getWidget(
                                    8001))
                    .thenReturn(
                            lineWidget);

            final Widget probe =
                    Mockito.mock(
                            Widget.class);

            final AtomicInteger currentFontId =
                    new AtomicInteger(
                            777);

            Mockito.when(
                            probe.getFontId())
                    .thenAnswer(
                            ignored ->
                                    currentFontId.get());

            Mockito.when(
                            probe.setFontId(
                                    Mockito.anyInt()))
                    .thenAnswer(
                            invocation ->
                            {
                                currentFontId.set(
                                        invocation.getArgument(
                                                0));

                                return probe;
                            });

            Mockito.when(
                            probe.getFont())
                    .thenAnswer(
                            ignored ->
                            {
                                if (currentFontId.get()
                                        == FontID.PLAIN_12)
                                {
                                    return nativeFont;
                                }

                                if (currentFontId.get()
                                        == FontID.BOLD_12)
                                {
                                    return boldFont;
                                }

                                return null;
                            });

            Mockito.when(
                            client.getWidget(
                                    WidgetInfo.CHATBOX_INPUT))
                    .thenReturn(
                            probe);

            final FontLayoutService service =
                    new FontLayoutService(
                            client,
                            config,
                            repository,
                            referenceLayoutService);

            /*
             * Resolve and cache the fonts before the measured steady-state path.
             */
            if (mentionTreatment)
            {
                service.onScriptPreFired(
                        new ScriptPreFired(
                                203));

                intStack[5] =
                        14;
            }

            return new PerformanceHarness(
                    service,
                    intStack);
        }

        private int runConstruction()
        {
            /*
             * Each real native construction receives a fresh vertical input.
             *
             * Reset it here so repeated benchmark iterations do not feed the
             * previously injected value back in as RuneScape's native value.
             */
            intStack[5] =
                    14;

            service.onScriptPreFired(
                    new ScriptPreFired(
                            203));

            return intStack[5];
        }
    }

    private static final class PerformanceConfigurations
            implements Configurations
    {
        @Override
        public MentionFont fontMentions()
        {
            return MentionFont.BOLD;
        }
    }

    private static final class PerformanceReferenceLayoutService
            extends ReferenceLayoutService
    {
        private final FontTypeFace nativeFont;
        private final FontTypeFace selectedFont;

        private PerformanceReferenceLayoutService(
                FontTypeFace nativeFont,
                FontTypeFace selectedFont)
        {
            super(
                    null,
                    new Configurations()
                    {
                    },
                    new TaggedMessageRepository(
                            1),
                    null);

            this.nativeFont =
                    nativeFont;

            this.selectedFont =
                    selectedFont;
        }

        @Override
        public int measureWrappedLineCount(
                String rawWidgetText,
                FontTypeFace font,
                int availableWidth)
        {
            if (font == nativeFont)
            {
                return 1;
            }

            if (font == selectedFont)
            {
                return 2;
            }

            return 1;
        }

        @Override
        public void syncMentionFonts()
        {
        }
    }

    private static final class PerformanceFont
            implements FontTypeFace
    {
        private final int characterWidth;

        private PerformanceFont(
                int characterWidth)
        {
            this.characterWidth =
                    characterWidth;
        }

        @Override
        public int getTextWidth(
                String text)
        {
            return text == null
                    ? 0
                    : text.length()
                    * characterWidth;
        }

        @Override
        public int getBaseline()
        {
            return 12;
        }

        @Override
        public void drawWidgetText(
                String text,
                int x,
                int y,
                int width,
                int height,
                int rgb,
                int shadowRgb,
                int alpha,
                int xTextAlignment,
                int yTextAlignment,
                int lineHeight)
        {
        }
    }
}