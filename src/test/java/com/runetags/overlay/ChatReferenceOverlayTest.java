package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ReferenceHitbox;
import com.runetags.chat.ReferenceLayoutService;
import com.runetags.chat.ReferenceLayoutService.LayoutResult;
import com.runetags.chat.ReferenceLayoutService.LocalHighlight;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.MatchReason;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ChatReferenceOverlayTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private Configurations config;
    private ReferenceLayoutService layoutService;
    private ChatHitboxRegistry registry;
    private LocalMentionMatcher localMentionMatcher;

    private BufferedImage image;
    private Graphics2D graphics;

    private Widget chatbox;

    private ChatReferenceOverlay overlay;

    /*
     * TESTS
     */

    @Before
    public void setUp()
            throws Exception
    {
        client =
                Mockito.mock(
                        Client.class);

        config =
                Mockito.mock(
                        Configurations.class);

        layoutService =
                Mockito.mock(
                        ReferenceLayoutService.class);

        registry =
                new ChatHitboxRegistry();

        localMentionMatcher =
                Mockito.mock(
                        LocalMentionMatcher.class);

        image =
                new BufferedImage(
                        400,
                        300,
                        BufferedImage.TYPE_INT_ARGB);

        graphics =
                image.createGraphics();

        chatbox =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        chatbox.isHidden())
                .thenReturn(
                        false);

        Mockito.when(
                        chatbox.getBounds())
                .thenReturn(
                        new Rectangle(
                                20,
                                100,
                                300,
                                120));

        Mockito.when(
                        client.getWidget(
                                WidgetInfo.CHATBOX_MESSAGE_LINES))
                .thenReturn(
                        chatbox);

        Mockito.when(
                        client.getWidget(
                                WidgetInfo.PRIVATE_CHAT_MESSAGE))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidgetRoots())
                .thenReturn(
                        new Widget[0]);

        Mockito.when(
                        client.getCanvasWidth())
                .thenReturn(
                        400);

        Mockito.when(
                        client.getCanvasHeight())
                .thenReturn(
                        300);

        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        false);

        Mockito.when(
                        config.underlineMentions())
                .thenReturn(
                        false);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.YELLOW);

        Mockito.when(
                        config.otherBackgroundColor())
                .thenReturn(
                        Color.CYAN);

        Mockito.when(
                        config.otherMentionColor())
                .thenReturn(
                        Color.MAGENTA);

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.nullable(
                                        String.class)))
                .thenReturn(
                        LocalMentionMatch.none());

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.emptyList()));

        overlay =
                new ChatReferenceOverlay(
                        layoutService,
                        registry,
                        client,
                        config,
                        localMentionMatcher);
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
    public void renderAlwaysRequestsSharedLayout()
    {
        overlay.render(
                graphics);

        Mockito.verify(
                        layoutService,
                        Mockito.times(
                                1))
                .layout();
    }

    @Test
    public void emptyLayoutClearsRegistry()
    {
        registry.replace(
                Collections.singletonList(
                        hitbox(
                                99L,
                                new Rectangle(
                                        30,
                                        110,
                                        20,
                                        12),
                                ReferenceType.MENTION,
                                true,
                                ReferenceLayoutService.Surface.CHATBOX)));

        overlay.render(
                graphics);

        Assert.assertTrue(
                registry.snapshot().isEmpty());
    }

    @Test
    public void chatboxReferenceInsideVisibleChatboxIsRegistered()
            throws Exception
    {
        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                40,
                                120,
                                50,
                                14),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        final List<ReferenceHitbox> visible =
                registry.snapshot();

        Assert.assertEquals(
                1,
                visible.size());

        Assert.assertEquals(
                new Rectangle(
                        40,
                        120,
                        50,
                        14),
                visible.get(
                                0)
                        .getBounds());

        Assert.assertEquals(
                ReferenceLayoutService.Surface.CHATBOX,
                visible.get(
                                0)
                        .getSurface());
    }

    @Test
    public void chatboxReferenceIsClippedToVisibleChatbox()
            throws Exception
    {
        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                10,
                                110,
                                40,
                                20),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        final List<ReferenceHitbox> visible =
                registry.snapshot();

        Assert.assertEquals(
                1,
                visible.size());

        Assert.assertEquals(
                new Rectangle(
                        20,
                        110,
                        30,
                        20),
                visible.get(
                                0)
                        .getBounds());
    }

    @Test
    public void chatboxReferenceOutsideVisibleChatboxIsRemoved()
            throws Exception
    {
        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                30,
                                40,
                                50,
                                14),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertTrue(
                registry.snapshot().isEmpty());
    }

    @Test
    public void hiddenChatboxRemovesChatboxReferences()
            throws Exception
    {
        Mockito.when(
                        chatbox.isHidden())
                .thenReturn(
                        true);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                40,
                                120,
                                50,
                                14),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertTrue(
                registry.snapshot().isEmpty());
    }

    @Test
    public void missingChatboxRemovesChatboxReferences()
            throws Exception
    {
        Mockito.when(
                        client.getWidget(
                                WidgetInfo.CHATBOX_MESSAGE_LINES))
                .thenReturn(
                        null);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                40,
                                120,
                                50,
                                14),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertTrue(
                registry.snapshot().isEmpty());
    }

    @Test
    public void splitPrivateReferenceIsNotClippedAgainstChatbox()
            throws Exception
    {
        final Rectangle splitBounds =
                new Rectangle(
                        40,
                        40,
                        80,
                        14);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        splitBounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.SPLIT_PRIVATE);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        final List<ReferenceHitbox> visible =
                registry.snapshot();

        Assert.assertEquals(
                1,
                visible.size());

        Assert.assertEquals(
                splitBounds,
                visible.get(
                                0)
                        .getBounds());
    }

    @Test
    public void splitPrivateReferenceSurvivesWithoutVisibleChatbox()
            throws Exception
    {
        Mockito.when(
                        chatbox.isHidden())
                .thenReturn(
                        true);

        final Rectangle splitBounds =
                new Rectangle(
                        40,
                        40,
                        80,
                        14);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        splitBounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.SPLIT_PRIVATE);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                1,
                registry.snapshot().size());

        Assert.assertEquals(
                splitBounds,
                registry.snapshot()
                        .get(
                                0)
                        .getBounds());
    }

    @Test
    public void nullAndEmptyHitboxesAreIgnored()
            throws Exception
    {
        final ReferenceHitbox nullBounds =
                new ReferenceHitbox(
                        1L,
                        null,
                        reference(
                                ReferenceType.MENTION,
                                true),
                        ReferenceLayoutService.Surface.CHATBOX);

        final ReferenceHitbox emptyBounds =
                hitbox(
                        2L,
                        new Rectangle(
                                40,
                                120,
                                0,
                                14),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Arrays.asList(
                                        null,
                                        nullBounds,
                                        emptyBounds),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertTrue(
                registry.snapshot().isEmpty());
    }

    @Test
    public void blockingWidgetRemovesFullyCoveredReference()
            throws Exception
    {
        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                50,
                                120,
                                40,
                                14),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        final Widget blocker =
                blockingWidget(
                        new Rectangle(
                                40,
                                110,
                                80,
                                40));

        Mockito.when(
                        client.getWidgetRoots())
                .thenReturn(
                        new Widget[]
                                {
                                        blocker
                                });

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertTrue(
                registry.snapshot().isEmpty());
    }

    @Test
    public void blockingWidgetFragmentsPartiallyCoveredReference()
            throws Exception
    {
        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                40,
                                120,
                                60,
                                20),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        final Widget blocker =
                blockingWidget(
                        new Rectangle(
                                65,
                                115,
                                10,
                                30));

        Mockito.when(
                        client.getWidgetRoots())
                .thenReturn(
                        new Widget[]
                                {
                                        blocker
                                });

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        final List<ReferenceHitbox> visible =
                registry.snapshot();

        Assert.assertEquals(
                2,
                visible.size());

        Assert.assertTrue(
                containsBounds(
                        visible,
                        new Rectangle(
                                40,
                                120,
                                25,
                                20)));

        Assert.assertTrue(
                containsBounds(
                        visible,
                        new Rectangle(
                                75,
                                120,
                                25,
                                20)));
    }

    @Test
    public void blockerOutsideInteractionRegionDoesNotAffectReference()
            throws Exception
    {
        final Rectangle sourceBounds =
                new Rectangle(
                        40,
                        120,
                        60,
                        20);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        sourceBounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        final Widget blocker =
                blockingWidget(
                        new Rectangle(
                                300,
                                20,
                                50,
                                50));

        Mockito.when(
                        client.getWidgetRoots())
                .thenReturn(
                        new Widget[]
                                {
                                        blocker
                                });

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                1,
                registry.snapshot().size());

        Assert.assertEquals(
                sourceBounds,
                registry.snapshot()
                        .get(
                                0)
                        .getBounds());
    }

    @Test
    public void chatboxNoClickThroughWidgetDoesNotBlockOwnPresentation()
            throws Exception
    {
        Mockito.when(
                        chatbox.getNoClickThrough())
                .thenReturn(
                        true);

        Mockito.when(
                        client.getWidgetRoots())
                .thenReturn(
                        new Widget[]
                                {
                                        chatbox
                                });

        final Rectangle sourceBounds =
                new Rectangle(
                        40,
                        120,
                        60,
                        20);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        sourceBounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                1,
                registry.snapshot().size());

        Assert.assertEquals(
                sourceBounds,
                registry.snapshot()
                        .get(
                                0)
                        .getBounds());
    }

    @Test
    public void senderReferenceDoesNotDrawMentionBackground()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.otherBackgroundColor())
                .thenReturn(
                        Color.RED);

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        30,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.SENDER,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        55,
                        135));

        Mockito.verifyNoInteractions(
                localMentionMatcher);
    }

    @Test
    public void otherMentionUsesOtherBackgroundColor()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.otherBackgroundColor())
                .thenReturn(
                        Color.RED);

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.nullable(
                                        String.class)))
                .thenReturn(
                        LocalMentionMatch.none());

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        30,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                Color.RED.getRGB(),
                image.getRGB(
                        55,
                        135));
    }

    @Test
    public void selfMentionUsesSelfBackgroundColor()
            throws Exception
    {
        final Player localPlayer =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        localPlayer.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        client.getLocalPlayer())
                .thenReturn(
                        localPlayer);

        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.YELLOW);

        Mockito.when(
                        localMentionMatcher.match(
                                Mockito.any(
                                        PlayerReference.class),
                                Mockito.eq(
                                        "Santa")))
                .thenReturn(
                        new LocalMentionMatch(
                                true,
                                MatchReason.ACCOUNT_NAME,
                                "Santa"));

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        30,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                Color.YELLOW.getRGB(),
                image.getRGB(
                        55,
                        135));
    }

    @Test
    public void transparentReferenceBackgroundDoesNotPaint()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.otherBackgroundColor())
                .thenReturn(
                        new Color(
                                255,
                                0,
                                0,
                                0));

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        30,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        55,
                        135));
    }

    @Test
    public void unresolvedTagDrawsDottedUnderline()
            throws Exception
    {
        Mockito.when(
                        config.underlineMentions())
                .thenReturn(
                        true);

        Mockito.when(
                        config.otherMentionColor())
                .thenReturn(
                        Color.MAGENTA);

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        12,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.TAG,
                        false,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                Color.MAGENTA.getRGB(),
                image.getRGB(
                        50,
                        140));

        Assert.assertEquals(
                Color.MAGENTA.getRGB(),
                image.getRGB(
                        53,
                        140));

        Assert.assertEquals(
                0,
                image.getRGB(
                        51,
                        140));
    }

    @Test
    public void resolvedTagDoesNotDrawDottedUnderline()
            throws Exception
    {
        Mockito.when(
                        config.underlineMentions())
                .thenReturn(
                        true);

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        12,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.TAG,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        50,
                        140));
    }

    @Test
    public void ordinaryMentionDoesNotDrawDottedUnderline()
            throws Exception
    {
        Mockito.when(
                        config.underlineMentions())
                .thenReturn(
                        true);

        final Rectangle bounds =
                new Rectangle(
                        50,
                        130,
                        12,
                        12);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        bounds,
                        ReferenceType.MENTION,
                        false,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        50,
                        140));
    }

    @Test
    public void chatboxLocalHighlightUsesSelfBackground()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.GREEN);

        final LocalHighlight highlight =
                localHighlight(
                        1L,
                        new Rectangle(
                                60,
                                140,
                                40,
                                12),
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.singletonList(
                                        highlight)));

        overlay.render(
                graphics);

        Assert.assertEquals(
                Color.GREEN.getRGB(),
                image.getRGB(
                        65,
                        145));
    }

    @Test
    public void chatboxLocalHighlightOutsideVisibleChatboxIsNotPainted()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.GREEN);

        final LocalHighlight highlight =
                localHighlight(
                        1L,
                        new Rectangle(
                                60,
                                40,
                                40,
                                12),
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.singletonList(
                                        highlight)));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        65,
                        45));
    }

    @Test
    public void splitPrivateLocalHighlightDoesNotRequireChatboxVisibility()
            throws Exception
    {
        Mockito.when(
                        chatbox.isHidden())
                .thenReturn(
                        true);

        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.GREEN);

        final LocalHighlight highlight =
                localHighlight(
                        1L,
                        new Rectangle(
                                60,
                                40,
                                40,
                                12),
                        ReferenceLayoutService.Surface.SPLIT_PRIVATE);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.singletonList(
                                        highlight)));

        overlay.render(
                graphics);

        Assert.assertEquals(
                Color.GREEN.getRGB(),
                image.getRGB(
                        65,
                        45));
    }

    @Test
    public void transparentLocalHighlightDoesNotPaint()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        new Color(
                                0,
                                255,
                                0,
                                0));

        final LocalHighlight highlight =
                localHighlight(
                        1L,
                        new Rectangle(
                                60,
                                140,
                                40,
                                12),
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.singletonList(
                                        highlight)));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        65,
                        145));
    }

    @Test
    public void highlightBackgroundDisabledDoesNotPaintLocalHighlight()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        false);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.GREEN);

        final LocalHighlight highlight =
                localHighlight(
                        1L,
                        new Rectangle(
                                60,
                                140,
                                40,
                                12),
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.singletonList(
                                        highlight)));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        65,
                        145));
    }

    @Test
    public void blockingWidgetPreventsLocalHighlightPainting()
            throws Exception
    {
        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.selfBackgroundColor())
                .thenReturn(
                        Color.GREEN);

        final Rectangle highlightBounds =
                new Rectangle(
                        60,
                        140,
                        40,
                        12);

        final LocalHighlight highlight =
                localHighlight(
                        1L,
                        highlightBounds,
                        ReferenceLayoutService.Surface.CHATBOX);

        final Widget blocker =
                blockingWidget(
                        highlightBounds);

        Mockito.when(
                        client.getWidgetRoots())
                .thenReturn(
                        new Widget[]
                                {
                                        blocker
                                });

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.emptyList(),
                                Collections.singletonList(
                                        highlight)));

        overlay.render(
                graphics);

        Assert.assertEquals(
                0,
                image.getRGB(
                        65,
                        145));
    }

    @Test
    public void renderRestoresOriginalGraphicsColor()
            throws Exception
    {
        final Color originalColor =
                new Color(
                        20,
                        40,
                        60);

        graphics.setColor(
                originalColor);

        Mockito.when(
                        config.highlightBackground())
                .thenReturn(
                        true);

        Mockito.when(
                        config.otherBackgroundColor())
                .thenReturn(
                        Color.RED);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                50,
                                130,
                                30,
                                12),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        Assert.assertEquals(
                originalColor,
                graphics.getColor());
    }

    @Test
    public void renderRestoresOriginalGraphicsClip()
            throws Exception
    {
        final Rectangle originalClip =
                new Rectangle(
                        10,
                        20,
                        250,
                        200);

        graphics.setClip(
                originalClip);

        final ReferenceHitbox source =
                hitbox(
                        1L,
                        new Rectangle(
                                50,
                                130,
                                30,
                                12),
                        ReferenceType.MENTION,
                        true,
                        ReferenceLayoutService.Surface.CHATBOX);

        Mockito.when(
                        layoutService.layout())
                .thenReturn(
                        layoutResult(
                                Collections.singletonList(
                                        source),
                                Collections.emptyList()));

        overlay.render(
                graphics);

        final Shape restoredClip =
                graphics.getClip();

        Assert.assertNotNull(
                restoredClip);

        Assert.assertEquals(
                originalClip,
                restoredClip.getBounds());
    }

    @Test
    public void performanceOverlayRender()
            throws Exception
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
                    harness.registry.snapshot()
                            .size();
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
                "[RuneTags][ChatReferenceOverlayTest] Performance= "
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

    private static LayoutResult layoutResult(
            List<ReferenceHitbox> hitboxes,
            List<LocalHighlight> localHighlights)
            throws Exception
    {
        final Constructor<LayoutResult> constructor =
                LayoutResult.class.getDeclaredConstructor(
                        List.class,
                        List.class);

        constructor.setAccessible(
                true);

        return constructor.newInstance(
                hitboxes,
                localHighlights);
    }

    private static LocalHighlight localHighlight(
            long messageId,
            Rectangle bounds,
            ReferenceLayoutService.Surface surface)
            throws Exception
    {
        final Constructor<LocalHighlight> constructor =
                LocalHighlight.class.getDeclaredConstructor(
                        long.class,
                        Rectangle.class,
                        ReferenceLayoutService.Surface.class);

        constructor.setAccessible(
                true);

        return constructor.newInstance(
                messageId,
                bounds,
                surface);
    }

    private static ReferenceHitbox hitbox(
            long messageId,
            Rectangle bounds,
            ReferenceType type,
            boolean locallyResolved,
            ReferenceLayoutService.Surface surface)
    {
        return new ReferenceHitbox(
                messageId,
                bounds,
                reference(
                        type,
                        locallyResolved),
                surface);
    }

    private static PlayerReference reference(
            ReferenceType type,
            boolean locallyResolved)
    {
        return PlayerReference.builder()
                .rawText(
                        type == ReferenceType.TAG
                                ? "@Santa"
                                : "Santa")
                .normalizedToken(
                        "Santa")
                .lookupName(
                        "Santa")
                .startOffset(
                        0)
                .endOffset(
                        5)
                .type(
                        type)
                .locallyResolved(
                        locallyResolved)
                .identity(
                        null)
                .chatType(
                        null)
                .build();
    }

    private static Widget blockingWidget(
            Rectangle bounds)
    {
        final Widget widget =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        widget.isHidden())
                .thenReturn(
                        false);

        Mockito.when(
                        widget.getBounds())
                .thenReturn(
                        bounds);

        Mockito.when(
                        widget.getNoClickThrough())
                .thenReturn(
                        true);

        Mockito.when(
                        widget.getParent())
                .thenReturn(
                        null);

        Mockito.when(
                        widget.getChildren())
                .thenReturn(
                        null);

        Mockito.when(
                        widget.getStaticChildren())
                .thenReturn(
                        null);

        Mockito.when(
                        widget.getNestedChildren())
                .thenReturn(
                        null);

        return widget;
    }

    private static boolean containsBounds(
            List<ReferenceHitbox> hitboxes,
            Rectangle bounds)
    {
        for (ReferenceHitbox hitbox : hitboxes)
        {
            if (hitbox != null
                    && bounds.equals(
                    hitbox.getBounds()))
            {
                return true;
            }
        }

        return false;
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
                    harness.registry.snapshot()
                            .size();
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
        private final ReferenceLayoutService layoutService;
        private final ChatHitboxRegistry registry;
        private final LocalMentionMatcher localMentionMatcher;

        private final BufferedImage image;
        private final Graphics2D graphics;

        private final ChatReferenceOverlay overlay;

        private PerformanceHarness()
                throws Exception
        {
            client =
                    Mockito.mock(
                            Client.class);

            config =
                    Mockito.mock(
                            Configurations.class);

            layoutService =
                    Mockito.mock(
                            ReferenceLayoutService.class);

            registry =
                    new ChatHitboxRegistry();

            localMentionMatcher =
                    Mockito.mock(
                            LocalMentionMatcher.class);

            image =
                    new BufferedImage(
                            400,
                            300,
                            BufferedImage.TYPE_INT_ARGB);

            graphics =
                    image.createGraphics();

            final Widget chatbox =
                    Mockito.mock(
                            Widget.class);

            Mockito.when(
                            chatbox.isHidden())
                    .thenReturn(
                            false);

            Mockito.when(
                            chatbox.getBounds())
                    .thenReturn(
                            new Rectangle(
                                    20,
                                    100,
                                    300,
                                    120));

            Mockito.when(
                            client.getWidget(
                                    WidgetInfo.CHATBOX_MESSAGE_LINES))
                    .thenReturn(
                            chatbox);

            Mockito.when(
                            client.getWidget(
                                    WidgetInfo.PRIVATE_CHAT_MESSAGE))
                    .thenReturn(
                            null);

            Mockito.when(
                            client.getWidgetRoots())
                    .thenReturn(
                            new Widget[0]);

            Mockito.when(
                            client.getCanvasWidth())
                    .thenReturn(
                            400);

            Mockito.when(
                            client.getCanvasHeight())
                    .thenReturn(
                            300);

            Mockito.when(
                            config.highlightBackground())
                    .thenReturn(
                            false);

            Mockito.when(
                            config.underlineMentions())
                    .thenReturn(
                            false);

            final List<ReferenceHitbox> hitboxes =
                    Arrays.asList(
                            hitbox(
                                    1L,
                                    new Rectangle(
                                            40,
                                            120,
                                            45,
                                            14),
                                    ReferenceType.MENTION,
                                    true,
                                    ReferenceLayoutService.Surface.CHATBOX),
                            hitbox(
                                    2L,
                                    new Rectangle(
                                            90,
                                            120,
                                            50,
                                            14),
                                    ReferenceType.TAG,
                                    false,
                                    ReferenceLayoutService.Surface.CHATBOX),
                            hitbox(
                                    3L,
                                    new Rectangle(
                                            40,
                                            50,
                                            55,
                                            14),
                                    ReferenceType.MENTION,
                                    true,
                                    ReferenceLayoutService.Surface.SPLIT_PRIVATE));

            final LayoutResult result =
                    layoutResult(
                            hitboxes,
                            Collections.emptyList());

            Mockito.when(
                            layoutService.layout())
                    .thenReturn(
                            result);

            overlay =
                    new ChatReferenceOverlay(
                            layoutService,
                            registry,
                            client,
                            config,
                            localMentionMatcher);
        }

        private void close()
        {
            graphics.dispose();
        }
    }
}