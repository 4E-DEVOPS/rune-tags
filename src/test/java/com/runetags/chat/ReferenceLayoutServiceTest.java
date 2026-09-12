package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.config.ClickablePlayerMode;
import com.runetags.config.MentionFont;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.MatchReason;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Color;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ReferenceLayoutServiceTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private TestConfigurations config;
    private TaggedMessageRepository repository;
    private ReferenceLayoutService service;
    private FixedWidthFont font;

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

        service =
                new ReferenceLayoutService(
                        client,
                        config,
                        repository,
                        null);

        font =
                new FixedWidthFont(
                        5);
    }

    /*
     * TESTS
     */

    @Test
    public void emptyRepositorySkipsPhysicalSurfaceLookup()
    {
        final ReferenceLayoutService.LayoutResult result =
                service.layout();

        Assert.assertTrue(
                result.getHitboxes()
                        .isEmpty());

        Assert.assertTrue(
                result.getLocalHighlights()
                        .isEmpty());

        Mockito.verifyNoInteractions(
                client);
    }

    @Test
    public void findRenderedMessageWidgetRejectsNullArguments()
    {
        final TaggedMessage message =
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        final Set<Widget> usedWidgets =
                identityWidgetSet();

        Assert.assertNull(
                service.findRenderedMessageWidget(
                        null,
                        Collections.emptyList(),
                        usedWidgets));

        Assert.assertNull(
                service.findRenderedMessageWidget(
                        message,
                        null,
                        usedWidgets));

        Assert.assertNull(
                service.findRenderedMessageWidget(
                        message,
                        Collections.emptyList(),
                        null));
    }

    @Test
    public void findRenderedMessageWidgetMatchesExactSemanticBody()
    {
        final Widget widget =
                textWidget(
                        "<col=ff0000>Hello Zezima</col>",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final TaggedMessage message =
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        Assert.assertSame(
                widget,
                service.findRenderedMessageWidget(
                        message,
                        Collections.singletonList(
                                widget),
                        identityWidgetSet()));
    }

    @Test
    public void findRenderedMessageWidgetSkipsAlreadyUsedWidget()
    {
        final Widget first =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final Widget second =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                40,
                                100,
                                14),
                        font);

        final Set<Widget> usedWidgets =
                identityWidgetSet();

        usedWidgets.add(
                first);

        final TaggedMessage message =
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        Assert.assertSame(
                second,
                service.findRenderedMessageWidget(
                        message,
                        Arrays.asList(
                                first,
                                second),
                        usedWidgets));
    }

    @Test
    public void privateMessageAllowsCaseOnlyBodyFallback()
    {
        final Widget widget =
                textWidget(
                        "hello santa",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final TaggedMessage message =
                message(
                        1L,
                        ChatMessageType.PRIVATECHAT,
                        "",
                        "Hello Santa",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        Assert.assertSame(
                widget,
                service.findRenderedMessageWidget(
                        message,
                        Collections.singletonList(
                                widget),
                        identityWidgetSet()));
    }

    @Test
    public void publicMessageDoesNotUseCaseOnlyBodyFallback()
    {
        final Widget widget =
                textWidget(
                        "hello santa",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final TaggedMessage message =
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Santa",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        Assert.assertNull(
                service.findRenderedMessageWidget(
                        message,
                        Collections.singletonList(
                                widget),
                        identityWidgetSet()));
    }

    @Test
    public void duplicateBodiesPreferCandidateWithMatchingSenderRow()
    {
        final Widget firstBody =
                textWidget(
                        "Hello",
                        new Rectangle(
                                100,
                                10,
                                80,
                                14),
                        font);

        final Widget secondBody =
                textWidget(
                        "Hello",
                        new Rectangle(
                                100,
                                30,
                                80,
                                14),
                        font);

        final Widget sender =
                textWidget(
                        "Santa:",
                        new Rectangle(
                                20,
                                30,
                                50,
                                14),
                        font);

        final TaggedMessage message =
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "Santa",
                        "Hello",
                        Collections.emptyList(),
                        LocalMentionMatch.none());

        Assert.assertSame(
                secondBody,
                service.findRenderedMessageWidget(
                        message,
                        Arrays.asList(
                                firstBody,
                                secondBody,
                                sender),
                        identityWidgetSet()));
    }

    @Test
    public void measureWrappedLineCountReturnsOneForInvalidInputs()
    {
        Assert.assertEquals(
                1,
                service.measureWrappedLineCount(
                        null,
                        font,
                        100));

        Assert.assertEquals(
                1,
                service.measureWrappedLineCount(
                        "",
                        font,
                        100));

        Assert.assertEquals(
                1,
                service.measureWrappedLineCount(
                        "Zezima",
                        null,
                        100));

        Assert.assertEquals(
                1,
                service.measureWrappedLineCount(
                        "Zezima",
                        font,
                        0));
    }

    @Test
    public void measureWrappedLineCountKeepsTextOnOneLineWhenItFits()
    {
        Assert.assertEquals(
                1,
                service.measureWrappedLineCount(
                        "Zezima Santa",
                        font,
                        60));
    }

    @Test
    public void measureWrappedLineCountWrapsAtWhitespace()
    {
        Assert.assertEquals(
                2,
                service.measureWrappedLineCount(
                        "Zezima Santa",
                        font,
                        35));
    }

    @Test
    public void measureWrappedLineCountHardWrapsLongToken()
    {
        Assert.assertEquals(
                2,
                service.measureWrappedLineCount(
                        "Zezima",
                        font,
                        15));
    }

    @Test
    public void measureWrappedLineCountHonorsExplicitLineBreak()
    {
        Assert.assertEquals(
                2,
                service.measureWrappedLineCount(
                        "Zezima\nSanta",
                        font,
                        100));
    }

    @Test
    public void layoutSemanticSpanRejectsInvalidInput()
    {
        Assert.assertTrue(
                service.layoutSemanticSpan(
                                null,
                                0,
                                1)
                        .isEmpty());

        final Widget widget =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        Assert.assertTrue(
                service.layoutSemanticSpan(
                                widget,
                                -1,
                                5)
                        .isEmpty());

        Assert.assertTrue(
                service.layoutSemanticSpan(
                                widget,
                                6,
                                6)
                        .isEmpty());

        Assert.assertTrue(
                service.layoutSemanticSpan(
                                widget,
                                6,
                                50)
                        .isEmpty());
    }

    @Test
    public void layoutSemanticSpanMapsSingleLineLeftAlignedText()
    {
        final Widget widget =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final List<Rectangle> rectangles =
                service.layoutSemanticSpan(
                        widget,
                        6,
                        12);

        Assert.assertEquals(
                1,
                rectangles.size());

        Assert.assertEquals(
                new Rectangle(
                        40,
                        20,
                        30,
                        14),
                rectangles.get(
                        0));
    }

    @Test
    public void layoutSemanticSpanRespectsCenterAndRightAlignment()
    {
        final Widget center =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        Mockito.when(
                        center.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.CENTER);

        final Widget right =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        Mockito.when(
                        right.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.RIGHT);

        final List<Rectangle> centered =
                service.layoutSemanticSpan(
                        center,
                        6,
                        12);

        final List<Rectangle> rightAligned =
                service.layoutSemanticSpan(
                        right,
                        6,
                        12);

        Assert.assertEquals(
                new Rectangle(
                        60,
                        20,
                        30,
                        14),
                centered.get(
                        0));

        Assert.assertEquals(
                new Rectangle(
                        80,
                        20,
                        30,
                        14),
                rightAligned.get(
                        0));
    }

    @Test
    public void layoutSemanticSpanSplitsAcrossWrappedLines()
    {
        final Widget widget =
                textWidget(
                        "Zezima Santa",
                        new Rectangle(
                                10,
                                20,
                                35,
                                28),
                        font);

        final List<Rectangle> rectangles =
                service.layoutSemanticSpan(
                        widget,
                        0,
                        12);

        Assert.assertEquals(
                2,
                rectangles.size());

        Assert.assertEquals(
                new Rectangle(
                        10,
                        20,
                        30,
                        14),
                rectangles.get(
                        0));

        Assert.assertEquals(
                new Rectangle(
                        10,
                        34,
                        25,
                        14),
                rectangles.get(
                        1));
    }

    @Test
    public void chatboxGeometryWaitsForRepeatedStableBodyX()
    {
        final AtomicReference<Rectangle> bodyBounds =
                new AtomicReference<>(
                        new Rectangle(
                                122,
                                20,
                                335,
                                14));

        final Widget body =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        body.getText())
                .thenReturn(
                        "Test @Santa");

        Mockito.when(
                        body.getBounds())
                .thenAnswer(
                        ignored ->
                                new Rectangle(
                                        bodyBounds.get()));

        Mockito.when(
                        body.getFont())
                .thenReturn(
                        font);

        Mockito.when(
                        body.getFontId())
                .thenReturn(
                        FontID.PLAIN_12);

        Mockito.when(
                        body.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.LEFT);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                600,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Test @Santa",
                        Collections.singletonList(
                                reference(
                                        "@Santa",
                                        5,
                                        11,
                                        ReferenceType.TAG,
                                        false)),
                        LocalMentionMatch.none()));

        /*
         * First observation:
         *
         * RuneScape has exposed the body, but its initial coordinates have not
         * yet been confirmed by a second layout pass.
         */
        ReferenceLayoutService.LayoutResult result =
                service.layout();

        Assert.assertTrue(
                result.getHitboxes()
                        .isEmpty());

        /*
         * Second observation:
         *
         * The body moved before stabilizing, so the original coordinates must
         * never be published.
         */
        bodyBounds.set(
                new Rectangle(
                        180,
                        20,
                        335,
                        14));

        result =
                service.layout();

        Assert.assertTrue(
                result.getHitboxes()
                        .isEmpty());

        /*
         * Third observation:
         *
         * The same physical geometry has now been observed twice consecutively.
         * RuneTags may publish the reference geometry.
         */
        result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        final ReferenceHitbox stableHitbox =
                result.getHitboxes()
                        .get(
                                0);

        Assert.assertEquals(
                ReferenceType.TAG,
                stableHitbox.getReference()
                        .getType());

        Assert.assertEquals(
                205,
                stableHitbox.getBounds()
                        .x);

        Assert.assertEquals(
                20,
                stableHitbox.getBounds()
                        .y);

        /*
         * Once the horizontal position is initialized, ordinary vertical chat
         * movement remains live immediately.
         */
        bodyBounds.set(
                new Rectangle(
                        180,
                        21,
                        335,
                        14));

        result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        Assert.assertEquals(
                205,
                result.getHitboxes()
                        .get(
                                0)
                        .getBounds()
                        .x);

        Assert.assertEquals(
                21,
                result.getHitboxes()
                        .get(
                                0)
                        .getBounds()
                        .y);

        /*
         * A genuinely new horizontal position must stabilize independently.
         */
        bodyBounds.set(
                new Rectangle(
                        181,
                        21,
                        335,
                        14));

        result =
                service.layout();

        Assert.assertTrue(
                result.getHitboxes()
                        .isEmpty());

        result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        Assert.assertEquals(
                206,
                result.getHitboxes()
                        .get(
                                0)
                        .getBounds()
                        .x);
    }

    @Test
    public void layoutProducesChatboxReferenceHitbox()
    {
        final PlayerReference reference =
                reference(
                        "Zezima",
                        6,
                        12,
                        ReferenceType.MENTION,
                        true);

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.singletonList(
                                reference),
                        LocalMentionMatch.none()));

        final Widget body =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        final ReferenceLayoutService.LayoutResult result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        final ReferenceHitbox hitbox =
                result.getHitboxes()
                        .get(
                                0);

        Assert.assertEquals(
                1L,
                hitbox.getMessageId());

        Assert.assertSame(
                reference,
                hitbox.getReference());

        Assert.assertEquals(
                ReferenceLayoutService.Surface.CHATBOX,
                hitbox.getSurface());

        Assert.assertEquals(
                new Rectangle(
                        40,
                        20,
                        30,
                        14),
                hitbox.getBounds());
    }

    @Test
    public void layoutCullsFullyOffscreenChatboxRows()
    {
        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.singletonList(
                                reference(
                                        "Zezima",
                                        6,
                                        12,
                                        ReferenceType.MENTION,
                                        true)),
                        LocalMentionMatch.none()));

        final Widget body =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                150,
                                100,
                                14),
                        font);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        Mockito.verify(
                        body,
                        Mockito.never())
                .getFont();

        Mockito.verify(
                        body,
                        Mockito.never())
                .getFontId();
    }

    @Test
    public void splitPrivateRejectsNonPrivateMessages()
    {
        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.singletonList(
                                reference(
                                        "Zezima",
                                        6,
                                        12,
                                        ReferenceType.MENTION,
                                        true)),
                        LocalMentionMatch.none()));

        final Widget body =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final Widget splitPrivate =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        splitPrivate);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        null);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());
    }

    @Test
    public void privateMessageCanLayoutOnBothPhysicalSurfaces()
    {
        final PlayerReference reference =
                reference(
                        "Zezima",
                        6,
                        12,
                        ReferenceType.MENTION,
                        true);

        repository.add(
                message(
                        1L,
                        ChatMessageType.PRIVATECHAT,
                        "",
                        "Hello Zezima",
                        Collections.singletonList(
                                reference),
                        LocalMentionMatch.none()));

        final Widget splitBody =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        final Widget chatboxBody =
                textWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                40,
                                100,
                                14),
                        font);

        final Widget splitPrivate =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        splitBody);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        chatboxBody);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        splitPrivate);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        final List<ReferenceHitbox> firstPass =
                service.layout()
                        .getHitboxes();

        Assert.assertEquals(
                1,
                firstPass.size());

        Assert.assertEquals(
                ReferenceLayoutService.Surface.SPLIT_PRIVATE,
                firstPass.get(
                                0)
                        .getSurface());

        final List<ReferenceHitbox> hitboxes =
                service.layout()
                        .getHitboxes();

        Assert.assertEquals(
                2,
                hitboxes.size());

        Assert.assertEquals(
                ReferenceLayoutService.Surface.SPLIT_PRIVATE,
                hitboxes.get(
                                0)
                        .getSurface());

        Assert.assertEquals(
                ReferenceLayoutService.Surface.CHATBOX,
                hitboxes.get(
                                1)
                        .getSurface());
    }

    @Test
    public void normalizedLocalMatchProducesLocalHighlight()
    {
        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Santa",
                        Collections.emptyList(),
                        new LocalMentionMatch(
                                true,
                                MatchReason.NORMALIZED_ACCOUNT_NAME,
                                "Santa")));

        final Widget body =
                textWidget(
                        "Hello Santa",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        Assert.assertTrue(
                service.layout()
                        .getLocalHighlights()
                        .isEmpty());

        final ReferenceLayoutService.LayoutResult result =
                service.layout();

        Assert.assertTrue(
                result.getHitboxes()
                        .isEmpty());

        Assert.assertEquals(
                1,
                result.getLocalHighlights()
                        .size());

        final ReferenceLayoutService.LocalHighlight highlight =
                result.getLocalHighlights()
                        .get(
                                0);

        Assert.assertEquals(
                1L,
                highlight.getMessageId());

        Assert.assertEquals(
                ReferenceLayoutService.Surface.CHATBOX,
                highlight.getSurface());

        Assert.assertEquals(
                new Rectangle(
                        40,
                        20,
                        25,
                        14),
                highlight.getBounds());
    }

    @Test
    public void localHighlightDoesNotDuplicatePlayerReferenceGeometry()
    {
        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Santa",
                        Collections.singletonList(
                                reference(
                                        "Santa",
                                        6,
                                        11,
                                        ReferenceType.MENTION,
                                        true)),
                        new LocalMentionMatch(
                                true,
                                MatchReason.NORMALIZED_ACCOUNT_NAME,
                                "Santa")));

        final Widget body =
                textWidget(
                        "Hello Santa",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        final ReferenceLayoutService.LayoutResult firstPass =
                service.layout();

        Assert.assertTrue(
                firstPass.getHitboxes()
                        .isEmpty());

        Assert.assertTrue(
                firstPass.getLocalHighlights()
                        .isEmpty());

        final ReferenceLayoutService.LayoutResult result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        Assert.assertTrue(
                result.getLocalHighlights()
                        .isEmpty());
    }

    @Test
    public void senderOnlyMessageProducesSenderHitboxWhenAllPlayersAreClickable()
    {
        config.clickablePlayers =
                ClickablePlayerMode.ALL;

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "Hello",
                        Collections.emptyList(),
                        LocalMentionMatch.none()));

        final Widget sender =
                textWidget(
                        "Zezima:",
                        new Rectangle(
                                20,
                                20,
                                50,
                                14),
                        font);

        final Widget body =
                textWidget(
                        "Hello",
                        new Rectangle(
                                100,
                                20,
                                60,
                                14),
                        font);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        sender,
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        final List<ReferenceHitbox> hitboxes =
                service.layout()
                        .getHitboxes();

        Assert.assertEquals(
                1,
                hitboxes.size());

        Assert.assertEquals(
                ReferenceType.SENDER,
                hitboxes.get(
                                0)
                        .getReference()
                        .getType());

        Assert.assertEquals(
                "Zezima",
                hitboxes.get(
                                0)
                        .getReference()
                        .getLookupName());

        Assert.assertEquals(
                new Rectangle(
                        20,
                        20,
                        30,
                        14),
                hitboxes.get(
                                0)
                        .getBounds());
    }

    @Test
    public void senderOnlyMessageDoesNotProduceSenderHitboxInMentionsMode()
    {
        config.clickablePlayers =
                ClickablePlayerMode.MENTIONS;

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "Hello",
                        Collections.emptyList(),
                        LocalMentionMatch.none()));

        final Widget sender =
                textWidget(
                        "Zezima:",
                        new Rectangle(
                                20,
                                20,
                                50,
                                14),
                        font);

        final Widget body =
                textWidget(
                        "Hello",
                        new Rectangle(
                                100,
                                20,
                                60,
                                14),
                        font);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        sender,
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());
    }

    @Test
    public void syncMentionFontsAppliesConfiguredFontAndRestoreRestoresNativeFont()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.singletonList(
                                reference(
                                        "Zezima",
                                        6,
                                        12,
                                        ReferenceType.MENTION,
                                        true)),
                        LocalMentionMatch.none()));

        final StatefulWidget body =
                statefulTextWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font,
                        FontID.PLAIN_12);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body.widget);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        service.syncMentionFonts();

        Assert.assertEquals(
                FontID.BOLD_12,
                body.fontId.get());

        service.restoreMentionFonts();

        Assert.assertEquals(
                FontID.PLAIN_12,
                body.fontId.get());
    }

    @Test
    public void fontSynchronizationDoesNotFollowRecycledPhysicalWidget()
    {
        config.fontMentions =
                MentionFont.BOLD;

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.singletonList(
                                reference(
                                        "Zezima",
                                        6,
                                        12,
                                        ReferenceType.MENTION,
                                        true)),
                        LocalMentionMatch.none()));

        final StatefulWidget body =
                statefulTextWidget(
                        "Hello Zezima",
                        new Rectangle(
                                10,
                                20,
                                100,
                                14),
                        font,
                        FontID.PLAIN_12);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                200,
                                100),
                        body.widget);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        service.syncMentionFonts();

        Assert.assertEquals(
                FontID.BOLD_12,
                body.fontId.get());

        repository.clear();

        repository.add(
                message(
                        2L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Hello Zezima",
                        Collections.emptyList(),
                        LocalMentionMatch.none()));

        service.syncMentionFonts();

        Assert.assertEquals(
                FontID.PLAIN_12,
                body.fontId.get());
    }

    @Test
    public void chatboxGeometryAcceptedXSurvivesTemporaryOffscreenRow()
    {
        final AtomicReference<Rectangle> bodyBounds =
                new AtomicReference<>(
                        new Rectangle(
                                180,
                                20,
                                335,
                                14));

        final Widget body =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        body.getText())
                .thenReturn(
                        "Test @Santa");

        Mockito.when(
                        body.getBounds())
                .thenAnswer(
                        ignored ->
                                new Rectangle(
                                        bodyBounds.get()));

        Mockito.when(
                        body.getFont())
                .thenReturn(
                        font);

        Mockito.when(
                        body.getFontId())
                .thenReturn(
                        FontID.PLAIN_12);

        Mockito.when(
                        body.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.LEFT);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                600,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Test @Santa",
                        Collections.singletonList(
                                reference(
                                        "@Santa",
                                        5,
                                        11,
                                        ReferenceType.TAG,
                                        false)),
                        LocalMentionMatch.none()));

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        ReferenceLayoutService.LayoutResult result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        /*
         * The semantic message remains retained, but its physical row is now
         * outside the visible chatbox.
         */
        bodyBounds.set(
                new Rectangle(
                        180,
                        150,
                        335,
                        14));

        result =
                service.layout();

        Assert.assertTrue(
                result.getHitboxes()
                        .isEmpty());

        /*
         * Scroll the same retained row back into view.
         *
         * Its accepted X must still be authoritative, so the highlight returns
         * immediately rather than requiring another stabilization pass.
         */
        bodyBounds.set(
                new Rectangle(
                        180,
                        30,
                        335,
                        14));

        result =
                service.layout();

        Assert.assertEquals(
                1,
                result.getHitboxes()
                        .size());

        Assert.assertEquals(
                205,
                result.getHitboxes()
                        .get(
                                0)
                        .getBounds()
                        .x);

        Assert.assertEquals(
                30,
                result.getHitboxes()
                        .get(
                                0)
                        .getBounds()
                        .y);
    }

    @Test
    public void chatboxGeometryStateIsPrunedWhenMessageLeavesRepository()
    {
        final AtomicReference<Rectangle> bodyBounds =
                new AtomicReference<>(
                        new Rectangle(
                                180,
                                20,
                                335,
                                14));

        final Widget body =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        body.getText())
                .thenReturn(
                        "Test @Santa");

        Mockito.when(
                        body.getBounds())
                .thenAnswer(
                        ignored ->
                                new Rectangle(
                                        bodyBounds.get()));

        Mockito.when(
                        body.getFont())
                .thenReturn(
                        font);

        Mockito.when(
                        body.getFontId())
                .thenReturn(
                        FontID.PLAIN_12);

        Mockito.when(
                        body.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.LEFT);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                600,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        final TaggedMessage taggedMessage =
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Test @Santa",
                        Collections.singletonList(
                                reference(
                                        "@Santa",
                                        5,
                                        11,
                                        ReferenceType.TAG,
                                        false)),
                        LocalMentionMatch.none());

        repository.add(
                taggedMessage);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        Assert.assertEquals(
                1,
                service.layout()
                        .getHitboxes()
                        .size());

        /*
         * Removing the semantic message must also retire its stabilization state.
         */
        repository.clear();

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        /*
         * Reuse the ID only as a deterministic test probe. Production IDs normally
         * advance, but this proves no accepted state survived the prune.
         */
        bodyBounds.set(
                new Rectangle(
                        180,
                        30,
                        335,
                        14));

        repository.add(
                taggedMessage);

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        Assert.assertEquals(
                1,
                service.layout()
                        .getHitboxes()
                        .size());
    }

    @Test
    public void clearChatboxBodyXStateRequiresFreshStabilization()
    {
        final AtomicReference<Rectangle> bodyBounds =
                new AtomicReference<>(
                        new Rectangle(
                                180,
                                20,
                                335,
                                14));

        final Widget body =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        body.getText())
                .thenReturn(
                        "Test @Santa");

        Mockito.when(
                        body.getBounds())
                .thenAnswer(
                        ignored ->
                                new Rectangle(
                                        bodyBounds.get()));

        Mockito.when(
                        body.getFont())
                .thenReturn(
                        font);

        Mockito.when(
                        body.getFontId())
                .thenReturn(
                        FontID.PLAIN_12);

        Mockito.when(
                        body.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.LEFT);

        final Widget chatbox =
                surface(
                        new Rectangle(
                                0,
                                0,
                                600,
                                100),
                        body);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        null);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.SCROLLAREA))
                .thenReturn(
                        chatbox);

        repository.add(
                message(
                        1L,
                        ChatMessageType.PUBLICCHAT,
                        "",
                        "Test @Santa",
                        Collections.singletonList(
                                reference(
                                        "@Santa",
                                        5,
                                        11,
                                        ReferenceType.TAG,
                                        false)),
                        LocalMentionMatch.none()));

        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        Assert.assertEquals(
                1,
                service.layout()
                        .getHitboxes()
                        .size());

        service.clearChatboxBodyXState();

        /*
         * Explicit lifecycle clearing must make the retained physical row prove
         * its horizontal position again.
         */
        Assert.assertTrue(
                service.layout()
                        .getHitboxes()
                        .isEmpty());

        Assert.assertEquals(
                1,
                service.layout()
                        .getHitboxes()
                        .size());
    }

    @Test
    public void performanceSample()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final PerformanceHarness harness =
                new PerformanceHarness();

        final long warmupChecksum =
                warmUp(
                        harness);

        Assert.assertTrue(
                warmupChecksum > 0L);

        long layoutChecksum =
                0L;

        final long layoutStarted =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            final ReferenceLayoutService.LayoutResult result =
                    harness.service.layout();

            layoutChecksum +=
                    result.getHitboxes()
                            .size();
        }

        final long layoutNanos =
                System.nanoTime()
                        - layoutStarted;

        long wrappedChecksum =
                0L;

        final long wrappedStarted =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            wrappedChecksum +=
                    harness.service.measureWrappedLineCount(
                            "Zezima met Santa Clause at Party Hat",
                            harness.font,
                            60);
        }

        final long wrappedNanos =
                System.nanoTime()
                        - wrappedStarted;

        long spanChecksum =
                0L;

        final long spanStarted =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            spanChecksum +=
                    harness.service.layoutSemanticSpan(
                                    harness.body,
                                    6,
                                    12)
                            .size();
        }

        final long spanNanos =
                System.nanoTime()
                        - spanStarted;

        Assert.assertEquals(
                PERFORMANCE_ITERATIONS,
                layoutChecksum);

        Assert.assertTrue(
                wrappedChecksum > 0L);

        Assert.assertEquals(
                PERFORMANCE_ITERATIONS,
                spanChecksum);

        printPerformance(
                layoutNanos,
                wrappedNanos,
                spanNanos);
    }

    /*
     * HELPERS
     */

    private static TaggedMessage message(
            long id,
            ChatMessageType type,
            String sender,
            String originalMessage,
            List<PlayerReference> references,
            LocalMentionMatch localMentionMatch)
    {
        return TaggedMessage.builder()
                .id(
                        id)
                .type(
                        type)
                .originalSender(
                        sender)
                .canonicalSender(
                        sender)
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
                        rawText.startsWith("@")
                                ? rawText.substring(
                                1)
                                : rawText)
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

    private static Set<Widget> identityWidgetSet()
    {
        return Collections.newSetFromMap(
                new IdentityHashMap<>());
    }

    private static Widget textWidget(
            String text,
            Rectangle bounds,
            FontTypeFace font)
    {
        final Widget widget =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        widget.getText())
                .thenReturn(
                        text);

        Mockito.when(
                        widget.getBounds())
                .thenReturn(
                        bounds);

        Mockito.when(
                        widget.getFont())
                .thenReturn(
                        font);

        Mockito.when(
                        widget.getXTextAlignment())
                .thenReturn(
                        WidgetTextAlignment.LEFT);

        return widget;
    }

    private static Widget surface(
            Rectangle bounds,
            Widget... children)
    {
        final Widget widget =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        widget.getBounds())
                .thenReturn(
                        bounds);

        Mockito.when(
                        widget.getChildren())
                .thenReturn(
                        children);

        return widget;
    }

    private static StatefulWidget statefulTextWidget(
            String text,
            Rectangle bounds,
            FontTypeFace font,
            int initialFontId)
    {
        final Widget widget =
                textWidget(
                        text,
                        bounds,
                        font);

        final AtomicInteger fontId =
                new AtomicInteger(
                        initialFontId);

        Mockito.when(
                        widget.getFontId())
                .thenAnswer(
                        ignored ->
                                fontId.get());

        Mockito.when(
                        widget.setFontId(
                                Mockito.anyInt()))
                .thenAnswer(
                        invocation ->
                        {
                            fontId.set(
                                    invocation.getArgument(
                                            0));

                            return widget;
                        });

        return new StatefulWidget(
                widget,
                fontId);
    }

    private static final class StatefulWidget
    {
        private final Widget widget;
        private final AtomicInteger fontId;

        private StatefulWidget(
                Widget widget,
                AtomicInteger fontId)
        {
            this.widget =
                    widget;

            this.fontId =
                    fontId;
        }
    }

    private static final class FixedWidthFont
            implements FontTypeFace
    {
        private final int characterWidth;

        private FixedWidthFont(
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

    private static final class TestConfigurations
            implements Configurations
    {
        private ClickablePlayerMode clickablePlayers =
                ClickablePlayerMode.ALL;

        private MentionFont fontMentions =
                MentionFont.NORMAL;

        @Override
        public ClickablePlayerMode clickablePlayers()
        {
            return clickablePlayers;
        }

        @Override
        public MentionFont fontMentions()
        {
            return fontMentions;
        }

        @Override
        public boolean highlightBackground()
        {
            return true;
        }

        @Override
        public Color selfBackgroundColor()
        {
            return new Color(
                    195,
                    100,
                    185,
                    70);
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
            checksum +=
                    harness.service.layout()
                            .getHitboxes()
                            .size();

            checksum +=
                    harness.service.measureWrappedLineCount(
                            "Zezima met Santa Clause at Party Hat",
                            harness.font,
                            60);

            checksum +=
                    harness.service.layoutSemanticSpan(
                                    harness.body,
                                    6,
                                    12)
                            .size();
        }

        return checksum;
    }

    private static void printPerformance(
            long layoutNanos,
            long wrappedNanos,
            long spanNanos)
    {
        final double layoutTotalMs =
                nanosToMilliseconds(
                        layoutNanos);

        final double wrappedTotalMs =
                nanosToMilliseconds(
                        wrappedNanos);

        final double spanTotalMs =
                nanosToMilliseconds(
                        spanNanos);

        System.out.printf(
                "[RuneTags][ReferenceLayoutServiceTest] Performance= "
                        + "LayoutOneReference: %.3fms (%.6fms) | "
                        + "MeasureWrapped: %.3fms (%.6fms) | "
                        + "SemanticSpan: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                layoutTotalMs,
                layoutTotalMs
                        / PERFORMANCE_ITERATIONS,
                wrappedTotalMs,
                wrappedTotalMs
                        / PERFORMANCE_ITERATIONS,
                spanTotalMs,
                spanTotalMs
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
        private final PerformanceFont font =
                new PerformanceFont(
                        5);

        private final Widget body;

        private final ReferenceLayoutService service;

        private PerformanceHarness()
        {
            final Client client =
                    Mockito.mock(
                            Client.class);

            final PerformanceConfigurations config =
                    new PerformanceConfigurations();

            final TaggedMessageRepository repository =
                    new TaggedMessageRepository(
                            10);

            body =
                    Mockito.mock(
                            Widget.class);

            Mockito.when(
                            body.getText())
                    .thenReturn(
                            "Hello Zezima");

            Mockito.when(
                            body.getBounds())
                    .thenReturn(
                            new Rectangle(
                                    10,
                                    20,
                                    100,
                                    14));

            Mockito.when(
                            body.getFont())
                    .thenReturn(
                            font);

            Mockito.when(
                            body.getXTextAlignment())
                    .thenReturn(
                            WidgetTextAlignment.LEFT);

            final Widget chatbox =
                    Mockito.mock(
                            Widget.class);

            Mockito.when(
                            chatbox.getBounds())
                    .thenReturn(
                            new Rectangle(
                                    0,
                                    0,
                                    200,
                                    100));

            Mockito.when(
                            chatbox.getChildren())
                    .thenReturn(
                            new Widget[]
                                    {
                                            body
                                    });

            Mockito.when(
                            client.getWidget(
                                    InterfaceID.PmChat.CONTAINER))
                    .thenReturn(
                            null);

            Mockito.when(
                            client.getWidget(
                                    InterfaceID.Chatbox.SCROLLAREA))
                    .thenReturn(
                            chatbox);

            repository.add(
                    TaggedMessage.builder()
                            .id(
                                    1L)
                            .type(
                                    ChatMessageType.PUBLICCHAT)
                            .originalSender(
                                    "")
                            .canonicalSender(
                                    "")
                            .originalMessage(
                                    "Hello Zezima")
                            .timestamp(
                                    Instant.EPOCH)
                            .references(
                                    Collections.singletonList(
                                            PlayerReference.builder()
                                                    .rawText(
                                                            "Zezima")
                                                    .normalizedToken(
                                                            "Zezima")
                                                    .lookupName(
                                                            "Zezima")
                                                    .startOffset(
                                                            6)
                                                    .endOffset(
                                                            12)
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
                            .build());

            service =
                    new ReferenceLayoutService(
                            client,
                            config,
                            repository,
                            null);
        }
    }

    private static final class PerformanceConfigurations
            implements Configurations
    {
        @Override
        public ClickablePlayerMode clickablePlayers()
        {
            return ClickablePlayerMode.MENTIONS;
        }

        @Override
        public boolean highlightBackground()
        {
            return false;
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