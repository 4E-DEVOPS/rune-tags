package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.hiscores.HiscoreEnrichmentState;
import com.runetags.player.AccountType;
import com.runetags.player.OnlineState;
import com.runetags.quickprofile.QuickProfileController;
import com.runetags.quickprofile.QuickProfileModel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class QuickProfileOverlayTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private Configurations config;
    private RuneLiteConfig runeLiteConfig;
    private QuickProfileController controller;
    private TooltipManager tooltipManager;

    private BufferedImage image;
    private Graphics2D graphics;

    private QuickProfileOverlay overlay;

    /*
     * TESTS
     */

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        config =
                Mockito.mock(
                        Configurations.class);

        runeLiteConfig =
                Mockito.mock(
                        RuneLiteConfig.class);

        controller =
                Mockito.mock(
                        QuickProfileController.class);

        tooltipManager =
                Mockito.mock(
                        TooltipManager.class);

        image =
                new BufferedImage(
                        800,
                        600,
                        BufferedImage.TYPE_INT_ARGB);

        graphics =
                image.createGraphics();

        Mockito.when(
                        client.getCanvasWidth())
                .thenReturn(
                        800);

        Mockito.when(
                        client.getCanvasHeight())
                .thenReturn(
                        600);

        Mockito.when(
                        client.getMouseCanvasPosition())
                .thenReturn(
                        new net.runelite.api.Point(
                                0,
                                0));

        Mockito.when(
                        config.showProfile())
                .thenReturn(
                        true);

        Mockito.when(
                        config.quickCardOpacity())
                .thenReturn(
                        100);

        Mockito.when(
                        runeLiteConfig.overlayBackgroundColor())
                .thenReturn(
                        new Color(
                                40,
                                40,
                                40));

        Mockito.when(
                        controller.isOpen())
                .thenReturn(
                        true);

        Mockito.when(
                        controller.getAnchorPoint())
                .thenReturn(
                        new java.awt.Point(
                                100,
                                100));

        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel());

        overlay =
                new QuickProfileOverlay(
                        client,
                        config,
                        runeLiteConfig,
                        controller,
                        tooltipManager);
    }

    @After
    public void tearDown()
    {
        graphics.dispose();
    }

    @Test
    public void overlayUsesExpectedRuneLitePlacement()
    {
        Assert.assertEquals(
                OverlayPosition.DYNAMIC,
                overlay.getPosition());

        Assert.assertEquals(
                OverlayLayer.ABOVE_WIDGETS,
                overlay.getLayer());

        Assert.assertEquals(
                Overlay.PRIORITY_HIGHEST,
                overlay.getPriority(),
                0.0f);
    }

    @Test
    public void disabledProfileClearsPublishedLayout()
    {
        Mockito.when(
                        config.showProfile())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        verifyClearedLayout();

        Mockito.verify(
                        controller,
                        Mockito.never())
                .getModel();
    }

    @Test
    public void closedProfileClearsPublishedLayout()
    {
        Mockito.when(
                        controller.isOpen())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        verifyClearedLayout();

        Mockito.verify(
                        controller,
                        Mockito.never())
                .getModel();
    }

    @Test
    public void missingModelDoesNotPublishLayout()
    {
        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verify(
                        controller,
                        Mockito.never())
                .updateLayoutBounds(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void missingAnchorDoesNotPublishLayout()
    {
        Mockito.when(
                        controller.getAnchorPoint())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verify(
                        controller,
                        Mockito.never())
                .updateLayoutBounds(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
    }

    @Test
    public void basicRenderPublishesCardCloseAndLookupBounds()
    {
        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNotNull(
                layout.card);

        Assert.assertNotNull(
                layout.close);

        Assert.assertNotNull(
                layout.lookup);

        Assert.assertNull(
                layout.target);

        Assert.assertNull(
                layout.tag);

        Assert.assertNull(
                layout.note);

        Assert.assertNull(
                layout.favorite);

        Assert.assertTrue(
                layout.card.contains(
                        layout.close));

        Assert.assertTrue(
                layout.card.contains(
                        layout.lookup));
    }

    @Test
    public void nearbyTargetableProfilePublishesTargetAndLookupButtons()
    {
        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        true);

        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel()
                                .toBuilder()
                                .nearby(
                                        true)
                                .build());

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNotNull(
                layout.target);

        Assert.assertNotNull(
                layout.lookup);

        Assert.assertEquals(
                layout.target.y,
                layout.lookup.y);

        Assert.assertTrue(
                layout.target.x
                        < layout.lookup.x);

        Assert.assertTrue(
                layout.target.width > 0);

        Assert.assertTrue(
                layout.lookup.width > 0);
    }

    @Test
    public void nonNearbyProfileDoesNotPublishTargetButton()
    {
        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        true);

        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel()
                                .toBuilder()
                                .nearby(
                                        false)
                                .build());

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNull(
                layout.target);

        Assert.assertNotNull(
                layout.lookup);
    }

    @Test
    public void visibleHeaderFeaturesPublishTheirControls()
    {
        Mockito.when(
                        config.showTags())
                .thenReturn(
                        true);

        Mockito.when(
                        config.showNotes())
                .thenReturn(
                        true);

        Mockito.when(
                        config.showFavorites())
                .thenReturn(
                        true);

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNotNull(
                layout.tag);

        Assert.assertNotNull(
                layout.note);

        Assert.assertNotNull(
                layout.favorite);

        Assert.assertTrue(
                layout.card.contains(
                        layout.tag));

        Assert.assertTrue(
                layout.card.contains(
                        layout.note));

        Assert.assertTrue(
                layout.card.contains(
                        layout.favorite));
    }

    @Test
    public void hiddenHeaderFeaturesDoNotPublishTheirControls()
    {
        Mockito.when(
                        config.showTags())
                .thenReturn(
                        false);

        Mockito.when(
                        config.showNotes())
                .thenReturn(
                        false);

        Mockito.when(
                        config.showFavorites())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNull(
                layout.tag);

        Assert.assertNull(
                layout.note);

        Assert.assertNull(
                layout.favorite);
    }

    @Test
    public void cardIsPositionedAtAnchorOffsetWhenCanvasAllows()
    {
        Mockito.when(
                        controller.getAnchorPoint())
                .thenReturn(
                        new java.awt.Point(
                                100,
                                100));

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertEquals(
                108,
                layout.card.x);

        Assert.assertEquals(
                108,
                layout.card.y);
    }

    @Test
    public void cardIsClampedAgainstLeftAndTopCanvasEdges()
    {
        Mockito.when(
                        controller.getAnchorPoint())
                .thenReturn(
                        new java.awt.Point(
                                -100,
                                -100));

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertEquals(
                4,
                layout.card.x);

        Assert.assertEquals(
                4,
                layout.card.y);
    }

    @Test
    public void cardIsClampedAgainstRightAndBottomCanvasEdges()
    {
        Mockito.when(
                        controller.getAnchorPoint())
                .thenReturn(
                        new java.awt.Point(
                                790,
                                590));

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertTrue(
                layout.card.x
                        + layout.card.width
                        <= 796);

        Assert.assertTrue(
                layout.card.y
                        + layout.card.height
                        <= 596);
    }

    @Test
    public void cardWidthRemainsWithinConfiguredRange()
    {
        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel()
                                .toBuilder()
                                .displayName(
                                        "Santa Clause With A Very Long Display Name That Should Force Width Growth")
                                .build());

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertTrue(
                layout.card.width >= 250);

        Assert.assertTrue(
                layout.card.width <= 420);
    }

    @Test
    public void normalProfileUsesMinimumCardWidthWhenContentIsShort()
    {
        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel()
                                .toBuilder()
                                .displayName(
                                        "Santa")
                                .build());

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertEquals(
                250,
                layout.card.width);
    }

    @Test
    public void noteLayoutMetricsArePublishedDuringVisibleRender()
    {
        overlay.render(
                graphics);

        Mockito.verify(
                        controller,
                        Mockito.times(
                                1))
                .updateNoteLayoutMetrics(
                        Mockito.any(),
                        Mockito.anyInt());
    }

    @Test
    public void disabledProfileDoesNotPublishNoteLayoutMetrics()
    {
        Mockito.when(
                        config.showProfile())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        Mockito.verify(
                        controller,
                        Mockito.never())
                .updateNoteLayoutMetrics(
                        Mockito.any(),
                        Mockito.anyInt());
    }

    @Test
    public void unresolvedProfileStillPublishesUsableCard()
    {
        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        QuickProfileModel.unresolved(
                                "Santa"));

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNotNull(
                layout.card);

        Assert.assertTrue(
                layout.card.width >= 250);

        Assert.assertTrue(
                layout.card.height >= 126);

        Assert.assertNotNull(
                layout.lookup);
    }

    @Test
    public void loadedUnresolvedProfileCanExposeLoadedStats()
    {
        Mockito.when(
                        config.showStats())
                .thenReturn(
                        true);

        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        QuickProfileModel.builder()
                                .displayName(
                                        "Santa")
                                .resolved(
                                        false)
                                .accountType(
                                        AccountType.NORMAL)
                                .combatLevel(
                                        126)
                                .totalLevel(
                                        2277)
                                .onlineState(
                                        OnlineState.UNKNOWN)
                                .enrichmentState(
                                        HiscoreEnrichmentState.LOADED)
                                .build());

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        Assert.assertNotNull(
                layout.card);

        Assert.assertTrue(
                layout.card.height >= 126);
    }

    @Test
    public void recordsContentIncreasesCardHeight()
    {
        Mockito.when(
                        config.showTags())
                .thenReturn(
                        true);

        Mockito.when(
                        config.showNotes())
                .thenReturn(
                        true);

        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel()
                                .toBuilder()
                                .tags(
                                        java.util.Arrays.asList(
                                                "PVM",
                                                "Friend",
                                                "Raids"))
                                .note(
                                        "Santa is a reliable player who regularly joins group activities.")
                                .build());

        overlay.render(
                graphics);

        final int contentHeight =
                publishedLayout()
                        .card
                        .height;

        Mockito.reset(
                controller);

        Mockito.when(
                        controller.isOpen())
                .thenReturn(
                        true);

        Mockito.when(
                        controller.getAnchorPoint())
                .thenReturn(
                        new java.awt.Point(
                                100,
                                100));

        Mockito.when(
                        controller.getModel())
                .thenReturn(
                        basicModel());

        Mockito.when(
                        config.showTags())
                .thenReturn(
                        false);

        Mockito.when(
                        config.showNotes())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        final int basicHeight =
                publishedLayout()
                        .card
                        .height;

        Assert.assertTrue(
                contentHeight > basicHeight);
    }

    @Test
    public void quickCardOpacityAffectsRenderedBackgroundAlpha()
    {
        Mockito.when(
                        config.quickCardOpacity())
                .thenReturn(
                        50);

        Mockito.when(
                        runeLiteConfig.overlayBackgroundColor())
                .thenReturn(
                        new Color(
                                60,
                                80,
                                100));

        overlay.render(
                graphics);

        final PublishedLayout layout =
                publishedLayout();

        final int sampleX =
                layout.card.x + 4;

        final int sampleY =
                layout.card.y + 4;

        final Color rendered =
                new Color(
                        image.getRGB(
                                sampleX,
                                sampleY),
                        true);

        Assert.assertTrue(
                rendered.getAlpha() > 0);

        Assert.assertTrue(
                rendered.getAlpha() < 255);
    }

    @Test
    public void performanceOverlayRender()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final PerformanceHarness harness =
                new PerformanceHarness();

        long checksum =
                warmUp(
                        harness);

        final long started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            harness.overlay.render(
                    harness.graphics);

            checksum +=
                    harness.publishedCardArea();
        }

        final long elapsed =
                System.nanoTime()
                        - started;

        Assert.assertTrue(
                checksum > 0L);

        final double totalMs =
                nanosToMilliseconds(
                        elapsed);

        System.out.printf(
                "[RuneTags][QuickProfileOverlayTest] Performance= "
                        + "OverlayRender: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                totalMs,
                totalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);

        harness.close();
    }

    /*
     * HELPERS
     */

    private static QuickProfileModel basicModel()
    {
        return QuickProfileModel.builder()
                .displayName(
                        "Santa")
                .resolved(
                        true)
                .accountType(
                        AccountType.NORMAL)
                .combatLevel(
                        126)
                .totalLevel(
                        2277)
                .world(
                        420)
                .onlineState(
                        OnlineState.ONLINE)
                .contextMetrics(
                        Collections.emptyList())
                .reportSummaries(
                        Collections.emptyList())
                .previousRsns(
                        Collections.emptyList())
                .tags(
                        Collections.emptyList())
                .favorite(
                        false)
                .nearby(
                        false)
                .enrichmentState(
                        HiscoreEnrichmentState.LOCAL)
                .build();
    }

    private void verifyClearedLayout()
    {
        Mockito.verify(
                        controller,
                        Mockito.times(
                                1))
                .updateLayoutBounds(
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull(),
                        Mockito.isNull());
    }

    @SuppressWarnings("unchecked")
    private PublishedLayout publishedLayout()
    {
        final ArgumentCaptor<Rectangle> cardCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> closeCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> tagCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> noteCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> favoriteCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> targetCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> lookupCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> clanCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Rectangle> reportCaptor =
                ArgumentCaptor.forClass(
                        Rectangle.class);

        final ArgumentCaptor<Map<String, Rectangle>> tagRemoveCaptor =
                ArgumentCaptor.forClass(
                        Map.class);

        Mockito.verify(
                        controller,
                        Mockito.atLeastOnce())
                .updateLayoutBounds(
                        cardCaptor.capture(),
                        closeCaptor.capture(),
                        tagCaptor.capture(),
                        noteCaptor.capture(),
                        favoriteCaptor.capture(),
                        targetCaptor.capture(),
                        lookupCaptor.capture(),
                        clanCaptor.capture(),
                        reportCaptor.capture(),
                        tagRemoveCaptor.capture());

        final int last =
                cardCaptor.getAllValues()
                        .size()
                        - 1;

        return new PublishedLayout(
                cardCaptor.getAllValues()
                        .get(
                                last),
                closeCaptor.getAllValues()
                        .get(
                                last),
                tagCaptor.getAllValues()
                        .get(
                                last),
                noteCaptor.getAllValues()
                        .get(
                                last),
                favoriteCaptor.getAllValues()
                        .get(
                                last),
                targetCaptor.getAllValues()
                        .get(
                                last),
                lookupCaptor.getAllValues()
                        .get(
                                last),
                clanCaptor.getAllValues()
                        .get(
                                last),
                reportCaptor.getAllValues()
                        .get(
                                last),
                tagRemoveCaptor.getAllValues()
                        .get(
                                last));
    }

    private static final class PublishedLayout
    {
        private final Rectangle card;
        private final Rectangle close;
        private final Rectangle tag;
        private final Rectangle note;
        private final Rectangle favorite;
        private final Rectangle target;
        private final Rectangle lookup;
        private final Rectangle clan;
        private final Rectangle report;
        private final Map<String, Rectangle> tagRemove;

        private PublishedLayout(
                Rectangle card,
                Rectangle close,
                Rectangle tag,
                Rectangle note,
                Rectangle favorite,
                Rectangle target,
                Rectangle lookup,
                Rectangle clan,
                Rectangle report,
                Map<String, Rectangle> tagRemove)
        {
            this.card =
                    card;

            this.close =
                    close;

            this.tag =
                    tag;

            this.note =
                    note;

            this.favorite =
                    favorite;

            this.target =
                    target;

            this.lookup =
                    lookup;

            this.clan =
                    clan;

            this.report =
                    report;

            this.tagRemove =
                    tagRemove;
        }
    }

    /*
     * PERFORMANCE
     */

    private static long warmUp(
            PerformanceHarness harness)
    {
        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            harness.overlay.render(
                    harness.graphics);

            checksum +=
                    harness.publishedCardArea();
        }

        return checksum;
    }

    private static double nanosToMilliseconds(
            long nanos)
    {
        return nanos
                / 1_000_000.0;
    }

    private static final class PerformanceHarness
    {
        private final Client client;
        private final Configurations config;
        private final RuneLiteConfig runeLiteConfig;
        private final QuickProfileController controller;
        private final TooltipManager tooltipManager;

        private final BufferedImage image;
        private final Graphics2D graphics;

        private final QuickProfileOverlay overlay;

        private Rectangle lastCardBounds;

        private PerformanceHarness()
        {
            client =
                    Mockito.mock(
                            Client.class);

            config =
                    Mockito.mock(
                            Configurations.class);

            runeLiteConfig =
                    Mockito.mock(
                            RuneLiteConfig.class);

            controller =
                    Mockito.mock(
                            QuickProfileController.class);

            tooltipManager =
                    Mockito.mock(
                            TooltipManager.class);

            image =
                    new BufferedImage(
                            800,
                            600,
                            BufferedImage.TYPE_INT_ARGB);

            graphics =
                    image.createGraphics();

            Mockito.when(
                            client.getCanvasWidth())
                    .thenReturn(
                            800);

            Mockito.when(
                            client.getCanvasHeight())
                    .thenReturn(
                            600);

            Mockito.when(
                            client.getMouseCanvasPosition())
                    .thenReturn(
                            new net.runelite.api.Point(
                                    0,
                                    0));

            Mockito.when(
                            config.showProfile())
                    .thenReturn(
                            true);

            Mockito.when(
                            config.quickCardOpacity())
                    .thenReturn(
                            100);

            Mockito.when(
                            config.showTags())
                    .thenReturn(
                            true);

            Mockito.when(
                            config.showNotes())
                    .thenReturn(
                            true);

            Mockito.when(
                            config.showFavorites())
                    .thenReturn(
                            true);

            Mockito.when(
                            config.showStats())
                    .thenReturn(
                            true);

            Mockito.when(
                            config.targetPlayerOption())
                    .thenReturn(
                            true);

            Mockito.when(
                            runeLiteConfig.overlayBackgroundColor())
                    .thenReturn(
                            new Color(
                                    40,
                                    40,
                                    40));

            Mockito.when(
                            controller.isOpen())
                    .thenReturn(
                            true);

            Mockito.when(
                            controller.getAnchorPoint())
                    .thenReturn(
                            new java.awt.Point(
                                    100,
                                    100));

            Mockito.when(
                            controller.getModel())
                    .thenReturn(
                            basicModel()
                                    .toBuilder()
                                    .nearby(
                                            true)
                                    .favorite(
                                            true)
                                    .tags(
                                            java.util.Arrays.asList(
                                                    "PVM",
                                                    "Raids",
                                                    "Friend"))
                                    .note(
                                            "Regular group player with persistent local profile information.")
                                    .build());

            Mockito.doAnswer(
                            invocation ->
                            {
                                lastCardBounds =
                                        invocation.getArgument(
                                                0);

                                return null;
                            })
                    .when(
                            controller)
                    .updateLayoutBounds(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any());

            overlay =
                    new QuickProfileOverlay(
                            client,
                            config,
                            runeLiteConfig,
                            controller,
                            tooltipManager);
        }

        private long publishedCardArea()
        {
            if (lastCardBounds == null)
            {
                return 0L;
            }

            return (long) lastCardBounds.width
                    * lastCardBounds.height;
        }

        private void close()
        {
            graphics.dispose();
        }
    }
}