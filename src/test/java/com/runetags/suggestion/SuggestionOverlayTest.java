package com.runetags.suggestion;

import com.runetags.Configurations;
import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SuggestionOverlayTest
{
    private Client client;
    private Configurations config;
    private PlayerDirectory playerDirectory;
    private Widget inputWidget;
    private Graphics2D graphics;
    private FontMetrics fontMetrics;

    private SuggestionService suggestionService;
    private SuggestionOverlay overlay;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        config =
                Mockito.mock(
                        Configurations.class);

        playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        inputWidget =
                Mockito.mock(
                        Widget.class);

        graphics =
                Mockito.mock(
                        Graphics2D.class);

        fontMetrics =
                Mockito.mock(
                        FontMetrics.class);

        suggestionService =
                new SuggestionService(
                        client,
                        Mockito.mock(
                                ClientThread.class),
                        config,
                        playerDirectory);

        overlay =
                new SuggestionOverlay(
                        client,
                        suggestionService);

        Mockito.when(
                        config.suggestUsernames())
                .thenReturn(
                        true);

        Mockito.when(
                        config.autocompleteFriends())
                .thenReturn(
                        true);

        Mockito.when(
                        client.getCanvasWidth())
                .thenReturn(
                        765);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.INPUT))
                .thenReturn(
                        inputWidget);

        Mockito.when(
                        inputWidget.isHidden())
                .thenReturn(
                        false);

        Mockito.when(
                        inputWidget.getBounds())
                .thenReturn(
                        new Rectangle(
                                20,
                                400,
                                500,
                                20));

        Mockito.when(
                        graphics.getFontMetrics())
                .thenReturn(
                        fontMetrics);

        Mockito.when(
                        fontMetrics.getHeight())
                .thenReturn(
                        12);

        Mockito.when(
                        fontMetrics.getAscent())
                .thenReturn(
                        9);
    }

    /*
     * TESTS
     */

    @Test
    public void inactiveSuggestionsRenderNothing()
    {
        Mockito.when(
                        client.getVarcStrValue(
                                VarClientID.CHATINPUT))
                .thenReturn(
                        "");

        Assert.assertNull(
                overlay.render(
                        graphics));

        Mockito.verify(
                        client,
                        Mockito.never())
                .getWidget(
                        InterfaceID.Chatbox.INPUT);

        Mockito.verify(
                        graphics,
                        Mockito.never())
                .drawString(
                        Mockito.anyString(),
                        Mockito.anyInt(),
                        Mockito.anyInt());
    }

    @Test
    public void missingInputWidgetRenderNothing()
    {
        prepareSuggestions(
                "Santa");

        Mockito.when(
                        client.getWidget(
                                InterfaceID.Chatbox.INPUT))
                .thenReturn(
                        null);

        Assert.assertNull(
                overlay.render(
                        graphics));

        Mockito.verify(
                        graphics,
                        Mockito.never())
                .drawString(
                        Mockito.anyString(),
                        Mockito.anyInt(),
                        Mockito.anyInt());
    }

    @Test
    public void hiddenInputWidgetRendersNothing()
    {
        prepareSuggestions(
                "Santa");

        Mockito.when(
                        inputWidget.isHidden())
                .thenReturn(
                        true);

        Assert.assertNull(
                overlay.render(
                        graphics));

        Mockito.verify(
                        graphics,
                        Mockito.never())
                .drawString(
                        Mockito.anyString(),
                        Mockito.anyInt(),
                        Mockito.anyInt());
    }

    @Test
    public void activeSuggestionsRenderRowsAndAtPrefix()
    {
        prepareSuggestions(
                "Santa",
                "Santa Clause");

        Assert.assertNull(
                overlay.render(
                        graphics));

        Mockito.verify(
                        graphics)
                .fillRoundRect(
                        20,
                        353,
                        180,
                        44,
                        6,
                        6);

        Mockito.verify(
                        graphics)
                .drawString(
                        "@Santa",
                        27,
                        369);

        Mockito.verify(
                        graphics)
                .drawString(
                        "@Santa Clause",
                        27,
                        387);
    }

    @Test
    public void overlayClampsAgainstLeftAndRightCanvasEdges()
    {
        prepareSuggestions(
                "Santa");

        Mockito.when(
                        inputWidget.getBounds())
                .thenReturn(
                        new Rectangle(
                                -50,
                                40,
                                500,
                                20));

        overlay.render(
                graphics);

        Mockito.verify(
                        graphics)
                .fillRoundRect(
                        4,
                        11,
                        180,
                        26,
                        6,
                        6);

        Mockito.clearInvocations(
                graphics);

        Mockito.when(
                        inputWidget.getBounds())
                .thenReturn(
                        new Rectangle(
                                750,
                                400,
                                500,
                                20));

        overlay.render(
                graphics);

        Mockito.verify(
                        graphics)
                .fillRoundRect(
                        581,
                        371,
                        180,
                        26,
                        6,
                        6);
    }

    @Test
    public void selectedSuggestionReceivesSelectedRowBackground()
    {
        prepareSuggestions(
                "Santa",
                "Santa Clause");

        suggestionService.handleKeyPressed(
                new java.awt.event.KeyEvent(
                        new java.awt.Canvas(),
                        java.awt.event.KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(),
                        0,
                        java.awt.event.KeyEvent.VK_DOWN,
                        java.awt.event.KeyEvent.CHAR_UNDEFINED));

        overlay.render(
                graphics);

        /*
         * Two fillRoundRect calls exist:
         *
         * 1. Overall suggestion background.
         * 2. Selected second row.
         */
        Mockito.verify(
                        graphics)
                .fillRoundRect(
                        22,
                        375,
                        176,
                        18,
                        4,
                        4);
    }

    /*
     * HELPERS
     */

    private void prepareSuggestions(
            String... names)
    {
        final PlayerIdentity[] identities =
                Arrays.stream(
                                names)
                        .map(name ->
                                PlayerIdentity.builder()
                                        .canonicalName(
                                                name)
                                        .sources(
                                                Collections.singleton(
                                                        PlayerSource.FRIEND))
                                        .build())
                        .toArray(
                                PlayerIdentity[]::new);

        Mockito.when(
                        playerDirectory.all())
                .thenReturn(
                        Arrays.asList(
                                identities));

        Mockito.when(
                        client.getVarcStrValue(
                                VarClientID.CHATINPUT))
                .thenReturn(
                        "@Sa");
    }
}