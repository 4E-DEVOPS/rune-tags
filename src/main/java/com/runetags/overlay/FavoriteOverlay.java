package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.TargetMode;
import com.runetags.records.LocalPlayerRecordService;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

/**
 * Scene presentation for locally favorited players.
 *
 * Favorite names use RuneTags gold while scene indicators reuse the configured
 * Target Mode. TargetOverlay has higher priority, so an actively targeted
 * Favorite retains Target as the stronger live interaction state.
 */
public class FavoriteOverlay extends Overlay
{
    private static final int OUTLINE_WIDTH = 2;
    private static final int OUTLINE_FEATHER = 4;
    private static final int NAME_HEIGHT_OFFSET = 40;

    private final Client client;
    private final Configurations config;
    private final LocalPlayerRecordService localPlayerRecordService;
    private final ModelOutlineRenderer modelOutlineRenderer;

    public FavoriteOverlay(
            Client client,
            Configurations config,
            LocalPlayerRecordService localPlayerRecordService,
            ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.config = config;
        this.localPlayerRecordService =
                localPlayerRecordService;
        this.modelOutlineRenderer =
                modelOutlineRenderer;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showFavorites()
                || localPlayerRecordService == null
                || client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        final WorldView worldView =
                client.getTopLevelWorldView();

        if (worldView == null)
        {
            return null;
        }

        final TargetMode mode =
                config.targetMode() != null
                        ? config.targetMode()
                        : TargetMode.OFF;

        final Color favoriteColor =
                configuredFavoriteColor();

        final Color sceneColor =
                sceneColor(
                        favoriteColor);

        for (Player player : worldView.players())
        {
            if (player == null
                    || player.getName() == null
                    || player.getName().trim().isEmpty()
                    || !localPlayerRecordService.isFavorite(
                    player.getName()))
            {
                continue;
            }

            if (mode.showsOutline())
            {
                modelOutlineRenderer.drawOutline(
                        player,
                        OUTLINE_WIDTH,
                        sceneColor,
                        OUTLINE_FEATHER);
            }

            if (mode.showsTile())
            {
                final Polygon tilePoly =
                        player.getCanvasTilePoly();

                if (tilePoly != null)
                {
                    OverlayUtil.renderPolygon(
                            graphics,
                            tilePoly,
                            sceneColor);
                }
            }

            drawFavoriteName(
                    graphics,
                    player,
                    favoriteColor);
        }

        return null;
    }

    private Color configuredFavoriteColor()
    {
        final Color configured =
                config.favoriteColor();

        return configured != null
                ? configured
                : new Color(255, 205, 70);
    }

    private static Color sceneColor(
            Color color)
    {
        final Color source =
                color != null
                        ? color
                        : new Color(255, 205, 70);

        return new Color(
                source.getRed(),
                source.getGreen(),
                source.getBlue(),
                Math.min(200, source.getAlpha()));
    }

    private static void drawFavoriteName(
            Graphics2D graphics,
            Player player,
            Color favoriteColor)
    {
        final String rawName =
                player.getName();

        if (rawName == null
                || rawName.isEmpty())
        {
            return;
        }

        final String name =
                Text.sanitize(
                        rawName);

        final Point textLocation =
                player.getCanvasTextLocation(
                        graphics,
                        name,
                        player.getLogicalHeight()
                                + NAME_HEIGHT_OFFSET);

        if (textLocation != null)
        {
            OverlayUtil.renderTextLocation(
                    graphics,
                    textLocation,
                    name,
                    favoriteColor);
        }
    }
}
