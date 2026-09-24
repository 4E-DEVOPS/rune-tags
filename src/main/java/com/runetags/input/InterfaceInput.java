package com.runetags.input;

import com.runetags.quickprofile.QuickProfileController;

import java.awt.Point;
import java.awt.event.MouseEvent;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.Text;

/**
 * Adds RuneTags profile actions to player names exposed through RuneScape interfaces.
 */
public class InterfaceInput extends MouseAdapter {
	private static final String MENU_OPEN_PROFILE = "Open Profile";

	private static final String GROUPING_ADD_IGNORE = "Add ignore ";
	private static final String GROUPING_REMOVE_IGNORE = "Remove ignore ";

	private final Client client;
	private final QuickProfileController quickProfileController;

	private boolean suppressLeftClick;

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

		if (componentId == InterfaceID.ChatchannelSetup.LIST) {
			if ("Not ranked".equals(option)) {
				addProfileEntry(event, 0);
			}

			return;
		}

		if (componentId == InterfaceID.Grouping.PLAYERLIST) {
			if (isGroupOption(option)) {
				final String playerName = groupingPlayerName(option);
				if (!playerName.isEmpty()) {
					addProfileEntry(event, playerName, -2);
				}
			}

			return;
		}

		if (!isPlayerEntry(groupId, componentId, option)) {
			return;
		}

		addProfileEntry(event, -2);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event) {
		suppressLeftClick = false;

		if (event == null || event.getButton() != MouseEvent.BUTTON1 || client.isMenuOpen()) {
			return event;
		}

		final ChatChannelHit chatChannelHit = chatChannelSetup(event.getPoint());
		if (chatChannelHit != null) {
			suppressLeftClick = true;

			if (chatChannelHit.username) {
				final String playerName = cleanPlayerName(chatChannelHit.widget.getText());
				if (!playerName.isEmpty()) {
					openProfile(playerName, event.getPoint());
				}
			}

			event.consume();
			return event;
		}

		final Widget clanMember = clanMemberList(event.getPoint());
		if (clanMember != null) {
			final String playerName = cleanPlayerName(clanMember.getText());
			if (!playerName.isEmpty()) {
				suppressLeftClick = true;
				openProfile(playerName, event.getPoint());
				event.consume();
			}
		}

		return event;
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

	private void addProfileEntry(MenuEntryAdded event, int index) {
		client.createMenuEntry(index)
				.setOption(MENU_OPEN_PROFILE)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.setIdentifier(event.getIdentifier())
				.onClick(entry -> openProfile(entry.getTarget()));
	}

	private void addProfileEntry(MenuEntryAdded event, String playerName, int index) {
		client.createMenuEntry(index)
				.setOption(MENU_OPEN_PROFILE)
				.setTarget(playerName)
				.setType(MenuAction.RUNELITE)
				.setIdentifier(event.getIdentifier())
				.onClick(entry -> openProfile(playerName));
	}

	private ChatChannelHit chatChannelSetup(Point point) {
		if (point == null) {
			return null;
		}

		final Widget list = client.getWidget(InterfaceID.ChatchannelSetup.LIST);
		if (list == null || list.isSelfHidden()) {
			return null;
		}

		final Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return null;
		}

		for (int i = 0; i + 2 < children.length; i += 4) {
			final Widget rank = children[i + 1];
			final Widget username = children[i + 2];

			if (username != null
					&& username.getType() == WidgetType.TEXT
					&& !username.isSelfHidden()
					&& username.getBounds().contains(point)) {
				return new ChatChannelHit(username, true);
			}

			if (rank != null
					&& rank.getType() == WidgetType.TEXT
					&& !rank.isSelfHidden()
					&& rank.getBounds().contains(point)
					&& isRankOption(cleanPlayerName(rank.getText()))) {
				return new ChatChannelHit(rank, false);
			}
		}

		return null;
	}

	private Widget clanMemberList(Point point) {
		if (point == null) {
			return null;
		}

		final Widget nameList = client.getWidget(InterfaceID.ClansMembers.NAME);
		if (nameList == null || nameList.isSelfHidden()) {
			return null;
		}

		final Widget[] children = nameList.getDynamicChildren();
		if (children == null) {
			return null;
		}

		for (int i = 1; i < children.length; i += 3) {
			final Widget username = children[i];
			if (username == null
					|| username.getType() != WidgetType.TEXT
					|| username.isSelfHidden()
					|| !username.getBounds().contains(point)) {
				continue;
			}

			final String playerName = cleanPlayerName(username.getText());
			if (!playerName.isEmpty()) {
				return username;
			}
		}

		return null;
	}

	private static boolean isPlayerEntry(int groupId, int componentId, String option) {
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
			return isIronOption(option);
		}

		return false;
	}

	private static boolean isFriendOption(String option) {
		return "Add ignore".equals(option) || "Remove friend".equals(option);
	}

	private static boolean isIronOption(String option) {
		return "Add friend".equals(option) || "Remove friend".equals(option) || "Remove ignore".equals(option);
	}

	private static boolean isGroupOption(String option) {
		return option.startsWith(GROUPING_ADD_IGNORE) || option.startsWith(GROUPING_REMOVE_IGNORE);
	}

	private static boolean isRankOption(String option) {
		return "Not ranked".equals(option)
				|| "Recruit".equals(option)
				|| "Corporal".equals(option)
				|| "Sergeant".equals(option)
				|| "Lieutenant".equals(option)
				|| "Captain".equals(option)
				|| "General".equals(option);
	}

	private static String groupingPlayerName(String option) {
		if (option.startsWith(GROUPING_ADD_IGNORE)) {
			return cleanPlayerName(option.substring(GROUPING_ADD_IGNORE.length()));
		}

		if (option.startsWith(GROUPING_REMOVE_IGNORE)) {
			return cleanPlayerName(option.substring(GROUPING_REMOVE_IGNORE.length()));
		}

		return "";
	}

	private void openProfile(String target) {
		final net.runelite.api.Point canvasPoint = client.getMouseCanvasPosition();
		final Point anchorPoint = canvasPoint != null
				? new Point(canvasPoint.getX(), canvasPoint.getY())
				: null;

		openProfile(target, anchorPoint);
	}

	private void openProfile(String target, Point anchorPoint) {
		final String playerName = cleanPlayerName(target);
		if (playerName.isEmpty()) {
			return;
		}

		quickProfileController.openPlayer(playerName, anchorPoint);
	}

	private static final class ChatChannelHit {
		private final Widget widget;
		private final boolean username;

		private ChatChannelHit(Widget widget, boolean username) {
			this.widget = widget;
			this.username = username;
		}
	}

	private static String cleanPlayerName(String value) {
		if (value == null) {
			return "";
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}
}