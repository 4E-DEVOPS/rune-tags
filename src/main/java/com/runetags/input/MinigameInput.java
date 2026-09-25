package com.runetags.input;

import com.runetags.Configurations;
import com.runetags.config.ChatInteractionMode;
import com.runetags.quickprofile.QuickProfileController;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.Text;

/**
 * Adds RuneTags profile actions to player names exposed through minigame interfaces.
 */
public class MinigameInput extends MouseAdapter {
	private static final String MENU_OPEN_PROFILE = "Open Profile";

	private static final int[] BA_TEAM_NAMES = {
			InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN1,
			InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN2,
			InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN3,
			InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN4,
			InterfaceID.BarbassaultScrollPl1.BARBASSAULT_SCROLL_PL1_TN5,
			InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN1,
			InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN2,
			InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN3,
			InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN4,
			InterfaceID.BarbassaultScrollPl2.BARBASSAULT_SCROLL_PL2_TN5
	};

	private static final int[] BA_ROLE_NAMES = {
			InterfaceID.MessagescrollHandwriting.MESSAGESCROLL4_HW,
			InterfaceID.MessagescrollHandwriting.MESSAGESCROLL5_HW,
			InterfaceID.MessagescrollHandwriting.MESSAGESCROLL6_HW,
			InterfaceID.MessagescrollHandwriting.MESSAGESCROLL7_HW
	};

	private final Client client;
	private final Configurations config;
	private final QuickProfileController quickProfileController;

	private boolean suppressLeftClick;
	private volatile MinigameHit leftClickHit;

	public MinigameInput(Client client, Configurations config, QuickProfileController quickProfileController) {
		this.client = client;
		this.config = config;
		this.quickProfileController = quickProfileController;
	}

	public void onPostMenuSort(PostMenuSort event) {
		if (client.isMenuOpen() || !allowsLeftClick()) {
			leftClickHit = null;
			return;
		}

		final Point point = currentMousePoint();
		final MinigameHit hit = minigamePlayerHit(point);
		if (hit == null) {
			leftClickHit = null;
			return;
		}

		leftClickHit = hit;
		moveProfileToTop(hit, point);
	}

	public void onMenuOpened(MenuOpened event) {
		leftClickHit = null;

		final Point point = currentMousePoint();
		final MinigameHit hit = minigamePlayerHit(point);
		if (hit == null) {
			return;
		}

		final boolean rightClick = allowsRightClick();
		final String target = rightClick
				? profileMenuTarget(hit.playerName)
				: null;

		removeProfileMenu(hit.playerName);
		if (!rightClick) {
			return;
		}

		addProfileMenu(hit.playerName, target, -1, point);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event) {
		suppressLeftClick = false;

		if (event == null || event.getButton() != MouseEvent.BUTTON1 || client.isMenuOpen()) {
			return event;
		}

		final ChatInteractionMode interactionMode = config.chatInteractionMode();
		if (interactionMode == null || !interactionMode.allowsLeftClick()) {
			return event;
		}

		final MinigameHit hit = leftClickHit;
		if (hit == null || hit.bounds == null || !hit.bounds.contains(event.getPoint())) {
			return event;
		}

		openProfile(hit.playerName, event.getPoint());
		return consumeLeftClick(event);
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event) {
		if (event != null && event.getButton() == MouseEvent.BUTTON1 && suppressLeftClick) {
			event.consume();
		}

		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event) {
		if (event != null && event.getButton() == MouseEvent.BUTTON1 && suppressLeftClick) {
			suppressLeftClick = false;
			event.consume();
		}

		return event;
	}

	private MouseEvent consumeLeftClick(MouseEvent event) {
		suppressLeftClick = true;
		event.consume();
		return event;
	}

	private MinigameHit minigamePlayerHit(Point point) {
		return barbarianAssaultHit(point);
	}

	private MinigameHit barbarianAssaultHit(Point point) {
		if (point == null) {
			return null;
		}

		for (int componentId : BA_TEAM_NAMES) {
			final MinigameHit hit = barbarianNameHit(componentId, point);
			if (hit != null) {
				return hit;
			}
		}

		for (int componentId : BA_ROLE_NAMES) {
			final MinigameHit hit = barbarianRoleHit(componentId, point);
			if (hit != null) {
				return hit;
			}
		}

		return null;
	}

	private MinigameHit barbarianNameHit(int componentId, Point point) {
		final Widget username = client.getWidget(componentId);
		if (username == null
				|| username.getType() != WidgetType.TEXT
				|| username.isHidden()
				|| !containsVisible(username, point)) {
			return null;
		}

		final String playerName = cleanPlayerName(username.getText());
		if (isEmptyPlayerName(playerName) || "none set".equalsIgnoreCase(playerName)) {
			return null;
		}

		return new MinigameHit(playerName, username.getBounds());
	}

	private MinigameHit barbarianRoleHit(int componentId, Point point) {
		final Widget role = client.getWidget(componentId);
		if (role == null
				|| role.getType() != WidgetType.TEXT
				|| role.isHidden()
				|| !containsVisible(role, point)) {
			return null;
		}

		final String playerName = barbarianRolePlayer(role.getText());
		if (isEmptyPlayerName(playerName)) {
			return null;
		}

		return new MinigameHit(playerName, role.getBounds());
	}

	private static boolean containsVisible(Widget widget, Point point) {
		final Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.contains(point);
	}

	private void moveProfileToTop(MinigameHit hit, Point anchorPoint) {
		final MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length > 0 && isProfileEntry(entries[entries.length - 1], hit.playerName)) {
			final String playerName = hit.playerName;
			entries[entries.length - 1].onClick(entry -> openProfile(playerName, anchorPoint));
			return;
		}

		final String target = profileMenuTarget(hit.playerName);
		removeProfileMenu(hit.playerName);
		addProfileMenu(hit.playerName, target, -1, anchorPoint);
	}

	private void addProfileMenu(String playerName, String target, int index, Point anchorPoint) {
		final String menuTarget = target != null && !target.trim().isEmpty()
				? target
				: playerName;

		client.createMenuEntry(index)
				.setOption(MENU_OPEN_PROFILE)
				.setTarget(menuTarget)
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> openProfile(playerName, anchorPoint));
	}

	private void removeProfileMenu(String playerName) {
		final MenuEntry[] entries = client.getMenu().getMenuEntries();
		int retained = 0;

		for (MenuEntry entry : entries) {
			if (!isProfileEntry(entry, playerName)) {
				retained++;
			}
		}

		if (retained == entries.length) {
			return;
		}

		final MenuEntry[] updated = new MenuEntry[retained];
		int index = 0;
		for (MenuEntry entry : entries) {
			if (!isProfileEntry(entry, playerName)) {
				updated[index++] = entry;
			}
		}

		client.getMenu().setMenuEntries(updated);
	}

	private static boolean isProfileEntry(MenuEntry entry, String playerName) {
		return entry != null && MENU_OPEN_PROFILE.equals(entry.getOption())
				&& playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()));
	}

	private String profileMenuTarget(String playerName) {
		for (MenuEntry entry : client.getMenu().getMenuEntries()) {
			if (isProfileEntry(entry, playerName)) {
				return entry.getTarget();
			}
		}

		return playerName;
	}

	private void openProfile(String playerName, Point anchorPoint) {
		quickProfileController.openPlayer(playerName, anchorPoint);
	}

	private Point currentMousePoint() {
		final net.runelite.api.Point canvasPoint = client.getMouseCanvasPosition();

		return canvasPoint != null
				? new Point(canvasPoint.getX(), canvasPoint.getY())
				: null;
	}

	private boolean allowsLeftClick() {
		final ChatInteractionMode interactionMode = config.chatInteractionMode();
		return interactionMode != null && interactionMode.allowsLeftClick();
	}

	private boolean allowsRightClick() {
		final ChatInteractionMode interactionMode = config.chatInteractionMode();
		return interactionMode != null && interactionMode.allowsRightClick();
	}

	private static String barbarianRolePlayer(String value) {
		final String text = cleanPlayerName(value);
		final int separator = text.lastIndexOf(':');
		if (separator <= 0 || separator >= text.length() - 1) {
			return "";
		}

		final String role = text.substring(separator + 1).trim();
		if (!isBarbarianRole(role)) {
			return "";
		}

		return cleanPlayerName(text.substring(0, separator));
	}

	private static boolean isBarbarianRole(String value) {
		return "Attacker".equalsIgnoreCase(value)
				|| "Collector".equalsIgnoreCase(value)
				|| "Defender".equalsIgnoreCase(value)
				|| "Healer".equalsIgnoreCase(value);
	}

	private static boolean isEmptyPlayerName(String value) {
		return value == null || value.isEmpty() || "-".equals(value);
	}

	private static String cleanPlayerName(String value) {
		if (value == null) {
			return "";
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}

	private static final class MinigameHit {
		private final String playerName;
		private final Rectangle bounds;

		private MinigameHit(String playerName, Rectangle bounds) {
			this.playerName = playerName;
			this.bounds = bounds != null
					? new Rectangle(bounds)
					: null;
		}
	}
}
