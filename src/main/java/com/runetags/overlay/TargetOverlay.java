package com.runetags.overlay;

import com.runetags.RuneTagsConfig;
import com.runetags.config.TargetMode;
import com.runetags.context.TargetController;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

public class TargetOverlay extends Overlay
{
    private static final int OUTLINE_WIDTH = 2;
    private static final int OUTLINE_FEATHER = 4;
    private static final int NAME_HEIGHT_OFFSET = 40;

    private final Client client;
    private final RuneTagsConfig config;
    private final TargetController targetController;
    private final ModelOutlineRenderer modelOutlineRenderer;

    public TargetOverlay(
            Client client,
            RuneTagsConfig config,
            TargetController targetController,
            ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.config = config;
        this.targetController = targetController;
        this.modelOutlineRenderer = modelOutlineRenderer;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.targetPlayerOption()
                || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        final Player target = targetController.getTargetPlayer();

        if (target == null)
        {
            return null;
        }

        final Color targetColor = config.targetColor();

        final TargetMode targetMode =
                config.targetMode();

        if (targetMode.showsOutline())
        {
            modelOutlineRenderer.drawOutline(
                    target,
                    OUTLINE_WIDTH,
                    targetColor,
                    OUTLINE_FEATHER);
        }

        if (targetMode.showsTile())
        {
            final Polygon tilePoly =
                    target.getCanvasTilePoly();

            if (tilePoly != null)
            {
                OverlayUtil.renderPolygon(
                        graphics,
                        tilePoly,
                        targetColor);
            }
        }

        if (config.targetLine())
        {
            drawTargetLine(graphics, target, targetColor);
        }

        if (config.targetName())
        {
            drawTargetName(graphics, target, targetColor);
        }

        return null;
    }

    private void drawTargetLine(
            Graphics2D graphics,
            Player target,
            Color color)
    {
        final Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            return;
        }

        final LocalPoint localPoint = localPlayer.getLocalLocation();
        final LocalPoint targetPoint = target.getLocalLocation();

        if (localPoint == null || targetPoint == null)
        {
            return;
        }

        final int plane = localPlayer.getWorldLocation().getPlane();

        final Point localCanvas =
                net.runelite.api.Perspective.localToCanvas(
                        client,
                        localPoint,
                        plane);

        final Point targetCanvas =
                net.runelite.api.Perspective.localToCanvas(
                        client,
                        targetPoint,
                        plane);

        if (localCanvas == null || targetCanvas == null)
        {
            return;
        }

        final Color oldColor = graphics.getColor();
        final java.awt.Stroke oldStroke = graphics.getStroke();

        try
        {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(2.0f));

            graphics.drawLine(
                    localCanvas.getX(),
                    localCanvas.getY(),
                    targetCanvas.getX(),
                    targetCanvas.getY());
        }
        finally
        {
            graphics.setStroke(oldStroke);
            graphics.setColor(oldColor);
        }
    }

    private static void drawTargetName(
            Graphics2D graphics,
            Player target,
            Color color)
    {
        final String name = target.getName();

        if (name == null || name.isEmpty())
        {
            return;
        }

        final Point textLocation =
                target.getCanvasTextLocation(
                        graphics,
                        name,
                        target.getLogicalHeight() + NAME_HEIGHT_OFFSET);

        if (textLocation != null)
        {
            OverlayUtil.renderTextLocation(
                    graphics,
                    textLocation,
                    name,
                    color);
        }
    }
}
