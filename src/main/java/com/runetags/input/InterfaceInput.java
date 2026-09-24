package com.runetags.input;

import com.runetags.quickprofile.QuickProfileController;

import java.awt.Point;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * Adds RuneTags profile actions to player names exposed through RuneScape interfaces.
 */
public class InterfaceInput {
	private static final String MENU_OPEN_PROFILE = "Open Profile";

	private final Client client;
	private final QuickProfileController quickProfileController;

	public InterfaceInput(Client client, QuickProfileController quickProfileController) {
		this.client = client;
		this.quickProfileController = quickProfileController;
	}

	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (event == null || (event.getType() != MenuAction.CC_OP.getId()
				&& event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId())) {
			return;
		}

		final String option = event.getOption() != null
				? Text.removeTags(event.getOption())
				: "";
		final int componentId = event.getActionParam1();
		final int groupId = WidgetUtil.componentToInterface(componentId);

		if (!isSupportedPlayerEntry(groupId, componentId, option)) {
			return;
		}

		client.createMenuEntry(-2).setOption(MENU_OPEN_PROFILE).setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE).setIdentifier(event.getIdentifier())
				.onClick(entry -> openProfile(entry.getTarget()));
	}

	private static boolean isSupportedPlayerEntry(int groupId, int componentId, String option) {
		if (groupId == InterfaceID.FRIENDS) {
			return "Delete".equals(option);
		}

		if (groupId == InterfaceID.CHATCHANNEL_CURRENT) {
			return isFriendOption(option);
		}

		if (groupId == InterfaceID.IGNORE) {
			return "Delete".equals(option);
		}

		if (componentId == InterfaceID.ClansSidepanel.PLAYERLIST
				|| componentId == InterfaceID.ClansGuestSidepanel.PLAYERLIST) {
			return isFriendOption(option);
		}

		if (groupId == InterfaceID.GIM_SIDEPANEL) {
			return isGroupOption(option);
		}

		return false;
	}

	private static boolean isFriendOption(String option) {
		return "Add ignore".equals(option) || "Remove friend".equals(option);
	}

	private static boolean isGroupOption(String option) {
		return "Add friend".equals(option) || "Remove friend".equals(option) || "Remove ignore".equals(option);
	}

	private void openProfile(String target) {
		final String playerName = cleanPlayerName(target);
		if (playerName.isEmpty()) {
			return;
		}

		final net.runelite.api.Point canvasPoint = client.getMouseCanvasPosition();
		final Point anchorPoint = canvasPoint != null
				? new Point(canvasPoint.getX(), canvasPoint.getY())
				: null;

		quickProfileController.openPlayer(playerName, anchorPoint);
	}

	private static String cleanPlayerName(String value) {
		if (value == null) {
			return "";
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}
}
