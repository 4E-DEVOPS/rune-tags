package com.runetags.overlay;

import com.runetags.RuneTagsConfig;
import com.runetags.config.MinimapIndicatorMode;
import com.runetags.context.TargetController;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class TargetMinimapOverlay extends Overlay
{
    private final Client client;
    private final RuneTagsConfig config;
    private final TargetController targetController;

    public TargetMinimapOverlay(
            Client client,
            RuneTagsConfig config,
            TargetController targetController)
    {
        this.client = client;
        this.config = config;
        this.targetController = targetController;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    private static void drawMinimapIndicator(
            Graphics2D graphics,
            Point point,
            Color color,
            int diameter)
    {
        if (point == null || diameter <= 0)
        {
            return;
        }

        final Color oldColor =
                graphics.getColor();

        try
        {
            final int radius =
                    diameter / 2;

            graphics.setColor(Color.BLACK);
            graphics.fillOval(
                    point.getX() - radius,
                    point.getY() - radius + 1,
                    diameter,
                    diameter);

            graphics.setColor(color);
            graphics.fillOval(
                    point.getX() - radius,
                    point.getY() - radius,
                    diameter,
                    diameter);
        }
        finally
        {
            graphics.setColor(oldColor);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        final MinimapIndicatorMode indicatorMode =
                config.minimapIndicator();

        if (!config.targetPlayerOption()
                || indicatorMode == MinimapIndicatorMode.OFF
                || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        final Player target = targetController.getTargetPlayer();

        if (target == null)
        {
            return null;
        }

        final Point minimapPoint = target.getMinimapLocation();

        if (minimapPoint != null)
        {
            drawMinimapIndicator(
                    graphics,
                    minimapPoint,
                    config.minimapDotColor(),
                    indicatorMode.getDiameter());
        }

        return null;
    }
}
