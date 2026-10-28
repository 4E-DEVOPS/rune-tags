package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.TargetMode;
import com.runetags.config.FavoriteOptions.FavoriteHighlightMode;
import com.runetags.records.LocalPlayerRecordService;
import com.runetags.target.TargetController;

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
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

public class TargetOverlay extends Overlay {
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	private final Client client;
	private final Configurations config;
	private final TargetController targetController;
	private final ModelOutlineRenderer modelOutlineRenderer;
	private final LocalPlayerRecordService records;
	private final FavoriteOverlay favoriteOverlay;

	public TargetOverlay(Client client, Configurations config, TargetController targetController, ModelOutlineRenderer modelOutlineRenderer, LocalPlayerRecordService records, FavoriteOverlay favoriteOverlay) {
		this.client = client;
		this.config = config;
		this.targetController = targetController;
		this.modelOutlineRenderer = modelOutlineRenderer;
		this.records = records;
		this.favoriteOverlay = favoriteOverlay;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (!config.targetPlayerOption() || client.getGameState() != GameState.LOGGED_IN) {
			return null;
		}

		final Player target = targetController.getTargetPlayer();
		if (target == null) {
			return null;
		}

		final boolean favorite = config.showFavorites() && records != null && target.getName() != null && records.isFavorite(target.getName()) && favoriteOverlay != null && favoriteOverlay.canHighlightFavorite(target);
		final Color configuredColor = favorite
				? config.favoriteColor()
				: config.targetColor();
		final Color targetColor = configuredColor != null
				? configuredColor
				: (favorite
						? new Color(255, 205, 70)
						: new Color(80, 100, 255, 200));

		final TargetMode targetMode = config.targetMode() != null
				? config.targetMode()
				: TargetMode.OFF;
		final FavoriteHighlightMode favoriteMode = config.favoriteHighlightMode() != null
				? config.favoriteHighlightMode()
				: FavoriteHighlightMode.BOTH;
		final boolean outline = favorite
				? favoriteMode.showsOutline()
				: targetMode.showsOutline();
		final boolean tile = favorite
				? favoriteMode.showsTile()
				: targetMode.showsTile();

		if (outline) {
			// Preserve FavoriteOverlay's scene alpha behavior for gold highlights.
			final Color outlineColor = favorite
					? new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), Math.min(200, targetColor.getAlpha()))
					: targetColor;
			modelOutlineRenderer.drawOutline(target, OUTLINE_WIDTH, outlineColor, OUTLINE_FEATHER);
		}
		if (tile) {
			final Polygon tilePoly = target.getCanvasTilePoly();
			if (tilePoly != null) {
				final Color tileColor = favorite
						? new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), Math.min(200, targetColor.getAlpha()))
						: targetColor;
				OverlayUtil.renderPolygon(graphics, tilePoly, tileColor);
			}
		}

		if (config.targetLine()) {
			drawTargetLine(graphics, target, targetColor);
		}

		return null;
	}

	private void drawTargetLine(Graphics2D graphics, Player target, Color color) {
		final Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null) {
			return;
		}

		final LocalPoint localPoint = localPlayer.getLocalLocation();
		final LocalPoint targetPoint = target.getLocalLocation();
		if (localPoint == null || targetPoint == null) {
			return;
		}
		final int plane = localPlayer.getWorldLocation().getPlane();
		final Point localCanvas = net.runelite.api.Perspective.localToCanvas(client, localPoint, plane);
		final Point targetCanvas = net.runelite.api.Perspective.localToCanvas(client, targetPoint, plane);
		if (localCanvas == null || targetCanvas == null) {
			return;
		}
		final Color oldColor = graphics.getColor();
		final java.awt.Stroke oldStroke = graphics.getStroke();

		try {
			graphics.setColor(color);
			graphics.setStroke(new BasicStroke(2.0f));

			graphics.drawLine(localCanvas.getX(), localCanvas.getY(), targetCanvas.getX(), targetCanvas.getY());
		} finally {
			graphics.setStroke(oldStroke);
			graphics.setColor(oldColor);
		}
	}
}
