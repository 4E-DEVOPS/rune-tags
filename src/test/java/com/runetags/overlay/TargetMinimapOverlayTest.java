package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.MinimapIndicatorMode;
import com.runetags.target.TargetController;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TargetMinimapOverlayTest
{
    private Client client;
    private Configurations config;
    private TargetController targetController;

    private BufferedImage image;
    private Graphics2D graphics;

    private TargetMinimapOverlay overlay;

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

        image =
                new BufferedImage(
                        200,
                        200,
                        BufferedImage.TYPE_INT_ARGB);

        graphics =
                image.createGraphics();

        overlay =
                new TargetMinimapOverlay(
                        client,
                        config,
                        targetController);

        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        true);

        Mockito.when(
                        config.minimapIndicator())
                .thenReturn(
                        MinimapIndicatorMode.NORMAL);

        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        Mockito.when(
                        config.minimapDotColor())
                .thenReturn(
                        Color.RED);
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
    }

    @Test
    public void offIndicatorDoesNotRequestTarget()
    {
        Mockito.when(
                        config.minimapIndicator())
                .thenReturn(
                        MinimapIndicatorMode.OFF);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                targetController);
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

        Mockito.verify(
                        targetController,
                        Mockito.times(
                                1))
                .getTargetPlayer();
    }

    @Test
    public void targetWithoutMinimapLocationDoesNotDraw()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getMinimapLocation())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verify(
                        target,
                        Mockito.times(
                                1))
                .getMinimapLocation();
    }

    @Test
    public void normalIndicatorDrawsConfiguredColor()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getMinimapLocation())
                .thenReturn(
                        new Point(
                                100,
                                100));

        Mockito.when(
                        config.minimapDotColor())
                .thenReturn(
                        Color.RED);

        Mockito.when(
                        config.minimapIndicator())
                .thenReturn(
                        MinimapIndicatorMode.NORMAL);

        overlay.render(
                graphics);

        org.junit.Assert.assertEquals(
                Color.RED.getRGB(),
                image.getRGB(
                        100,
                        100));
    }

    @Test
    public void mediumIndicatorUsesLargerDiameter()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getMinimapLocation())
                .thenReturn(
                        new Point(
                                100,
                                100));

        Mockito.when(
                        config.minimapIndicator())
                .thenReturn(
                        MinimapIndicatorMode.MEDIUM);

        Mockito.when(
                        config.minimapDotColor())
                .thenReturn(
                        Color.RED);

        overlay.render(
                graphics);

        org.junit.Assert.assertEquals(
                Color.RED.getRGB(),
                image.getRGB(
                        97,
                        100));
    }

    @Test
    public void largeIndicatorUsesLargestDiameter()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getMinimapLocation())
                .thenReturn(
                        new Point(
                                100,
                                100));

        Mockito.when(
                        config.minimapIndicator())
                .thenReturn(
                        MinimapIndicatorMode.LARGE);

        Mockito.when(
                        config.minimapDotColor())
                .thenReturn(
                        Color.RED);

        overlay.render(
                graphics);

        org.junit.Assert.assertEquals(
                Color.RED.getRGB(),
                image.getRGB(
                        96,
                        100));
    }

    @Test
    public void renderRestoresOriginalGraphicsColor()
    {
        final Player target =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        targetController.getTargetPlayer())
                .thenReturn(
                        target);

        Mockito.when(
                        target.getMinimapLocation())
                .thenReturn(
                        new Point(
                                100,
                                100));

        final Color original =
                Color.BLUE;

        graphics.setColor(
                original);

        overlay.render(
                graphics);

        org.junit.Assert.assertEquals(
                original,
                graphics.getColor());
    }
}