package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.TargetMode;
import com.runetags.target.TargetController;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TargetOverlayTest
{
    private Client client;
    private Configurations config;
    private TargetController targetController;
    private ModelOutlineRenderer outlineRenderer;

    private BufferedImage image;
    private Graphics2D graphics;

    private TargetOverlay overlay;

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

        targetController =
                Mockito.mock(
                        TargetController.class);

        outlineRenderer =
                Mockito.mock(
                        ModelOutlineRenderer.class);

        image =
                new BufferedImage(
                        300,
                        300,
                        BufferedImage.TYPE_INT_ARGB);

        graphics =
                image.createGraphics();

        overlay =
                new TargetOverlay(
                        client,
                        config,
                        targetController,
                        outlineRenderer);

        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        true);

        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.OFF);

        Mockito.when(
                        config.targetColor())
                .thenReturn(
                        Color.RED);

        Mockito.when(
                        config.targetLine())
                .thenReturn(
                        false);

        Mockito.when(
                        config.targetName())
                .thenReturn(
                        false);
    }

    @After
    public void tearDown()
    {
        graphics.dispose();
    }

    @Test
    public void disabledTargetOptionDoesNotRequestTarget()
    {
        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                targetController);

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void loggedOutClientDoesNotRequestTarget()
    {
        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGIN_SCREEN);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                targetController);

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void missingTargetDoesNothing()
    {
        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void offModeDoesNotDrawOutlineOrTile()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.OFF);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);

        Mockito.verify(
                        target,
                        Mockito.never())
                .getCanvasTilePoly();
    }

    @Test
    public void outlineModeDrawsConfiguredOutline()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.OUTLINE);

        Mockito.when(
                        config.targetColor())
                .thenReturn(
                        Color.MAGENTA);

        overlay.render(
                graphics);

        Mockito.verify(
                        outlineRenderer,
                        Mockito.times(
                                1))
                .drawOutline(
                        target,
                        2,
                        Color.MAGENTA,
                        4);

        Mockito.verify(
                        target,
                        Mockito.never())
                .getCanvasTilePoly();
    }

    @Test
    public void tileModeRequestsTilePolygon()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        final Polygon polygon =
                new Polygon(
                        new int[] {20, 40, 40, 20},
                        new int[] {20, 20, 40, 40},
                        4);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getCanvasTilePoly())
                .thenReturn(
                        polygon);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.TILE);

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.times(
                                1))
                .getCanvasTilePoly();

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void bothModeDrawsOutlineAndRequestsTile()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getCanvasTilePoly())
                .thenReturn(
                        new Polygon());

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.BOTH);

        overlay.render(
                graphics);

        Mockito.verify(
                        outlineRenderer,
                        Mockito.times(
                                1))
                .drawOutline(
                        Mockito.eq(
                                target),
                        Mockito.eq(
                                2),
                        Mockito.eq(
                                Color.RED),
                        Mockito.eq(
                                4));

        Mockito.verify(
                        target,
                        Mockito.times(
                                1))
                .getCanvasTilePoly();
    }

    @Test
    public void nullTilePolygonIsSafe()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.TILE);

        Mockito.when(
                        target.getCanvasTilePoly())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.times(
                                1))
                .getCanvasTilePoly();
    }

    @Test
    public void targetNameDisabledDoesNotRequestName()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetName())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.never())
                .getName();
    }

    @Test
    public void nullTargetNameDoesNotRequestTextLocation()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetName())
                .thenReturn(
                        true);

        Mockito.when(
                        target.getName())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.never())
                .getCanvasTextLocation(
                        Mockito.any(
                                Graphics2D.class),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void emptyTargetNameDoesNotRequestTextLocation()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetName())
                .thenReturn(
                        true);

        Mockito.when(
                        target.getName())
                .thenReturn(
                        "");

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.never())
                .getCanvasTextLocation(
                        Mockito.any(
                                Graphics2D.class),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void targetNameRequestsCanvasTextLocation()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetName())
                .thenReturn(
                        true);

        Mockito.when(
                        target.getName())
                .thenReturn(
                        "Santa");

        Mockito.when(
                        target.getLogicalHeight())
                .thenReturn(
                        100);

        Mockito.when(
                        target.getCanvasTextLocation(
                                Mockito.any(
                                        Graphics2D.class),
                                Mockito.eq(
                                        "Santa"),
                                Mockito.eq(
                                        140)))
                .thenReturn(
                        new Point(
                                100,
                                100));

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.times(
                                1))
                .getCanvasTextLocation(
                        Mockito.any(
                                Graphics2D.class),
                        Mockito.eq(
                                "Santa"),
                        Mockito.eq(
                                140));
    }

    @Test
    public void targetLineWithNoLocalPlayerDoesNothing()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        config.targetLine())
                .thenReturn(
                        true);

        Mockito.when(
                        client.getLocalPlayer())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.never())
                .getLocalLocation();
    }
}