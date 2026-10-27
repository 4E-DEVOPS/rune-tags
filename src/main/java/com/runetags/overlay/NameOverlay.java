package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.FavoriteOptions.FavoriteNameLocation;
import com.runetags.records.LocalPlayerRecordService;
import com.runetags.target.TargetController;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.playerindicators.PlayerIndicatorsConfig;
import net.runelite.client.plugins.playerindicators.PlayerIndicatorsOverlay;
import net.runelite.client.plugins.playerindicators.PlayerNameLocation;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/**
 * Text-only overlay, drawn after the native PI name overlay but below game widgets.
 * Rank icons are always left to PI; when PI is drawing a ranked name at the same
 * position, calculate the same text offset to overpaint its name rather than icon.
 */
public class NameOverlay extends Overlay {
    private static final int ABOVE_MARGIN = 40;
    private static final int RIGHT_MARGIN = 10;
    private static final int BELOW_SCREEN_MARGIN = 5;

    private final Client client;
    private final Configurations config;
    private final LocalPlayerRecordService records;
    private final TargetController targetController;
    private final FavoriteOverlay favoriteOverlay;
    private final OverlayManager overlayManager;
    private final ChatIconManager icons;
    private final PartyService partyService;
    private final PlayerIndicatorsConfig piConfig;

    public NameOverlay(Client client, Configurations config, LocalPlayerRecordService records, TargetController targetController, FavoriteOverlay favoriteOverlay, OverlayManager overlayManager, ChatIconManager icons, PartyService partyService, ConfigManager configManager) {
        this.client = client;
        this.config = config;
        this.records = records;
        this.targetController = targetController;
        this.favoriteOverlay = favoriteOverlay;
        this.overlayManager = overlayManager;
        this.icons = icons;
        this.partyService = partyService;
        this.piConfig = configManager.getConfig(PlayerIndicatorsConfig.class);

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        final Player target = config.targetPlayerOption() && targetController != null
                ? targetController.getTargetPlayer()
				: null;
        if (config.showFavorites() && records != null) {
            final WorldView view = client.getTopLevelWorldView();
            if (view != null) {
                for (Player player : view.players()) {
                    if (player == null || player == target || player.getName() == null || !records.isFavorite(player.getName()) || !favoriteOverlay.canHighlightFavorite(player)) {
                        continue;
                    }
                    final FavoriteNameLocation location = favoriteLocation();
                    if (location != FavoriteNameLocation.DISABLED) {
                        drawName(graphics, player, location, favoriteColor());
                    }
                }
            }
        }
        // Target is rendered once and last.
        if (target != null && target.getName() != null) {
            final boolean favorite = config.showFavorites() && records != null && records.isFavorite(target.getName()) && favoriteOverlay.canHighlightFavorite(target);
            if (favorite) {
                if (favoriteLocation() != FavoriteNameLocation.DISABLED) {
                    drawName(graphics, target, favoriteLocation(), favoriteColor());
                }
            } else if (config.targetName()) {
                drawName(graphics, target, FavoriteNameLocation.ABOVE, config.targetColor() != null ? config.targetColor() : new Color(80, 100, 255));
            }
        }
        return null;
    }

    private FavoriteNameLocation favoriteLocation() {
        return config.favoriteNameLocation() != null
				? config.favoriteNameLocation()
				: FavoriteNameLocation.ABOVE;
    }

    private Color favoriteColor() {
        return config.favoriteColor() != null ? config.favoriteColor() : new Color(255, 205, 70);
    }

    private void drawName(Graphics2D graphics, Player player, FavoriteNameLocation location, Color color) {
        final String name = player.getName() == null
				? null
				: Text.sanitize(player.getName());
        if (name == null || name.isEmpty() || location == FavoriteNameLocation.DISABLED) {
            return;
        }
        final int height;
        switch (location) {
            case CENTER:
            case RIGHT:
                height = player.getLogicalHeight() / 2;
                break;
            case BELOW:
                height = 0; // Project at the player's feet.
                break;
            default:
                height = player.getLogicalHeight() + ABOVE_MARGIN;
        }
        Point pos = location == FavoriteNameLocation.RIGHT
                ? player.getCanvasTextLocation(graphics, "", height)
                : player.getCanvasTextLocation(graphics, name, height);
        if (pos == null) {
            return;
        }
        if (location == FavoriteNameLocation.RIGHT) {
            pos = new Point(pos.getX() + RIGHT_MARGIN, pos.getY());
        } else if (location == FavoriteNameLocation.BELOW) {
            pos = new Point(pos.getX(), pos.getY() + BELOW_SCREEN_MARGIN);
        }
        // Only offset when PI is actually drawing a rank icon at this *same* name position.
        if (location != FavoriteNameLocation.BELOW && isPiDrawingNameAt(player, location)) {
            final BufferedImage rankIcon = getPiRankIcon(player);
            if (rankIcon != null) {
                pos = new Point(pos.getX() + (location == FavoriteNameLocation.RIGHT
                        ? rankIcon.getWidth()
						: rankIcon.getWidth() / 2), pos.getY());
            }
        }
        OverlayUtil.renderTextLocation(graphics, pos, name, color);
    }

    private boolean isPiDrawingNameAt(Player player, FavoriteNameLocation location) {
        if (piConfig == null || overlayManager == null || !overlayManager.anyMatch(o -> o instanceof PlayerIndicatorsOverlay)) {
            return false;
        }
        final PlayerNameLocation nativeLocation = piConfig.playerNamePosition();
        if ((location == FavoriteNameLocation.ABOVE && nativeLocation != PlayerNameLocation.ABOVE_HEAD)
                || (location == FavoriteNameLocation.CENTER && nativeLocation != PlayerNameLocation.MODEL_CENTER)
                || (location == FavoriteNameLocation.RIGHT && nativeLocation != PlayerNameLocation.MODEL_RIGHT)) {
            return false;
        }
        return piDrawsPlayer(player);
    }

    /** Matches the PI highlight category gating, not its colors, for rank-offset purposes. */
    private boolean piDrawsPlayer(Player player) {
        if (player == client.getLocalPlayer()) {
            return piEnabled(piConfig.highlightOwnPlayer());
        }
        if (partyService != null && partyService.isInParty() && player.getName() != null && piEnabled(piConfig.highlightPartyMembers()) && partyService.getMemberByDisplayName(player.getName()) != null) {
            return true;
        }
        if (player.isFriend() && piEnabled(piConfig.highlightFriends())) {
            return true;
        }
        if (player.isFriendsChatMember() && piEnabled(piConfig.highlightFriendsChat())) {
            return true;
        }
        final Player own = client.getLocalPlayer();
        if (own != null && player.getTeam() > 0 && player.getTeam() == own.getTeam() && piEnabled(piConfig.highlightTeamMembers())) {
            return true;
        }
        if (player.isClanMember() && piEnabled(piConfig.highlightClanMembers())) {
            return true;
        }
        return !player.isFriendsChatMember() && !player.isClanMember() && piEnabled(piConfig.highlightOthers());
    }

    private boolean piEnabled(PlayerIndicatorsConfig.HighlightSetting setting) {
        return setting == PlayerIndicatorsConfig.HighlightSetting.ENABLED || (setting == PlayerIndicatorsConfig.HighlightSetting.PVP && (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1 || client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1));
    }

    private BufferedImage getPiRankIcon(Player player) {
        if (icons == null) {
            return null;
        }
        // PI's precedence: Friends Chat rank first, then native Clan title.
        if (player.isFriendsChatMember() && piConfig.showFriendsChatRanks()) {
            final FriendsChatManager manager = client.getFriendsChatManager();
            final FriendsChatMember member = manager != null
                    ? manager.findByName(Text.removeTags(player.getName()))
					: null;
            final FriendsChatRank rank = member != null
					? member.getRank()
					: FriendsChatRank.UNRANKED;
            if (rank != null && rank != FriendsChatRank.UNRANKED) {
                return icons.getRankImage(rank);
            }
            // PI does not fall back to a clan title when FC rank is UNRANKED.
            return null;
        }
        if (player.isClanMember() && piConfig.showClanChatRanks()) {
            final ClanChannel channel = client.getClanChannel();
            final ClanSettings settings = client.getClanSettings();
            if (channel == null || settings == null) {
                return null;
            }
            final ClanChannelMember member = channel.findMember(player.getName());
            if (member == null || member.getRank() == null) {
                return null;
            }
            final ClanTitle title = settings.titleForRank(member.getRank());
            return title != null
					? icons.getRankImage(title)
					: null;
        }
        return null;
    }
}
