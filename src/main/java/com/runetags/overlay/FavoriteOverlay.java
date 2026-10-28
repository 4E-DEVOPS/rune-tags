package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.FavoriteOptions.FavoriteHighlightMode;
import com.runetags.records.LocalPlayerRecordService;
import com.runetags.target.TargetController;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

/**
 * Favorite tile/model outline only;
 * Names are rendered on UNDER_WIDGETS by NameOverlay.
 */
public class FavoriteOverlay extends Overlay {
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	private final Client client;
	private final Configurations config;
	private final LocalPlayerRecordService records;
	private final TargetController targetController;
	private final ModelOutlineRenderer outlines;
	private final PartyService partyService;

	public FavoriteOverlay(Client client, Configurations config, LocalPlayerRecordService records, TargetController targetController, ModelOutlineRenderer outlines, PartyService partyService) {
		this.client = client;
		this.config = config;
		this.records = records;
		this.targetController = targetController;
		this.outlines = outlines;
		this.partyService = partyService;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGH);
	}

	public boolean canHighlightFavorite(Player player) {
		if (player == null) {
			return false;
		}
		return true;
		// TODO: return hasSupportedRelationship(player);
	}

	@SuppressWarnings("unused")
	private boolean hasSupportedRelationship(Player player) {
		if (player == null) {
			return false;
		}

		if (player == client.getLocalPlayer() || player.isFriend() || player.isFriendsChatMember() || player.isClanMember()) {
			return true;
		}

		final Player self = client.getLocalPlayer();
		if (self != null && player.getTeam() > 0 && player.getTeam() == self.getTeam()) {
			return true;
		}

		final ClanChannel guest = client.getGuestClanChannel();
		if (guest != null && player.getName() != null && guest.findMember(player.getName()) != null) {
			return true;
		}
		return partyService != null && partyService.isInParty() && player.getName() != null && partyService.getMemberByDisplayName(player.getName()) != null;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (!config.showFavorites() || records == null || client.getGameState() != GameState.LOGGED_IN) {
			return null;
		}

		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null) {
			return null;
		}

		final FavoriteHighlightMode mode = config.favoriteHighlightMode() != null
				? config.favoriteHighlightMode()
				: FavoriteHighlightMode.BOTH;
		if (mode == FavoriteHighlightMode.OFF) {
			return null;
		}

		final Color configured = config.favoriteColor() != null
				? config.favoriteColor()
				: new Color(255, 205, 70);

		final Color sceneColor = new Color(configured.getRed(), configured.getGreen(), configured.getBlue(), Math.min(200, configured.getAlpha()));
		for (Player player : worldView.players()) {
			if (player == null || player.getName() == null || player.getName().trim().isEmpty() || !records.isFavorite(player.getName()) || !canHighlightFavorite(player)) {
				continue;
			}

			// TargetOverlay draws the targeted Favorite once, with this same mode and color.
			if (targetController != null && targetController.isTargetingName(player.getName())) {
				continue;
			}

			if (mode.showsOutline()) {
				outlines.drawOutline(player, OUTLINE_WIDTH, sceneColor, OUTLINE_FEATHER);
			}

			if (mode.showsTile()) {
				final Polygon tile = player.getCanvasTilePoly();
				if (tile != null) {
					OverlayUtil.renderPolygon(graphics, tile, sceneColor);
				}
			}
		}
		return null;
	}
}
