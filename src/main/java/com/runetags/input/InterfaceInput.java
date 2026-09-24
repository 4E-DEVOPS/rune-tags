package com.runetags.input;

import com.runetags.Configurations;
import com.runetags.config.ChatInteractionMode;
import com.runetags.quickprofile.QuickProfileController;

import java.awt.Point;
import java.awt.event.MouseEvent;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.Text;

/**
 * Adds RuneTags profile actions to player names exposed through RuneScape interfaces.
 */
@Slf4j
public class InterfaceInput extends MouseAdapter {
	private static final String MENU_OPEN_PROFILE = "Open Profile";

	private static final String GROUPING_ADD_IGNORE = "Add ignore ";
	private static final String GROUPING_REMOVE_IGNORE = "Remove ignore ";

	private final Client client;
	private final Configurations config;
	private final QuickProfileController quickProfileController;

	private boolean suppressLeftClick;

	public InterfaceInput(Client client, Configurations config, QuickProfileController quickProfileController) {
		this.client = client;
		this.config = config;
		this.quickProfileController = quickProfileController;
	}

	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (event == null || (event.getType() != MenuAction.CC_OP.getId()
				&& event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId())) {
			return;
		}

		log.debug(
				"[RuneTags][InterfaceInput] Option='{}' | Target='{}' | Type={} | Identifier={} "
						+ "| Param0={} | Param1={}",
				event.getOption(),
				event.getTarget(),
				event.getType(),
				event.getIdentifier(),
				event.getActionParam0(),
				event.getActionParam1());

		final String option = event.getOption() != null
				? Text.removeTags(event.getOption())
				: "";
		final int componentId = event.getActionParam1();
		final int groupId = WidgetUtil.componentToInterface(componentId);

		if (componentId == InterfaceID.ChatchannelSetup.LIST) {
			if (allowsRightClick() && "Not ranked".equals(option)) {
				addProfileEntry(event, 1);
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

	public void onPostMenuSort(PostMenuSort event) {
		if (client.isMenuOpen() || !allowsLeftClick()) {
			return;
		}

		final Point point = currentMousePoint();
		final ChatChannelHit chatChannelHit = chatChannelSetup(point);
		if (chatChannelHit != null) {
			moveProfileToTop(chatChannelHit.playerName, point);
			return;
		}

		final Widget clanMember = clanMemberList(point);
		if (clanMember == null) {
			return;
		}

		final String playerName = cleanPlayerName(clanMember.getText());
		if (!playerName.isEmpty()) {
			moveProfileToTop(playerName, point);
		}
	}

	public void onMenuOpened(MenuOpened event) {
		final Point point = currentMousePoint();
		final ChatChannelHit chatChannelHit = chatChannelSetup(point);
		if (chatChannelHit != null) {
			prepareOpenedMenu(chatChannelHit.playerName, point);
			return;
		}

		final Widget clanMember = clanMemberList(point);
		if (clanMember == null) {
			return;
		}

		final String playerName = cleanPlayerName(clanMember.getText());
		if (!playerName.isEmpty()) {
			prepareOpenedMenu(playerName, point);
		}
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event) {
		suppressLeftClick = false;

		if (event == null || event.getButton() != MouseEvent.BUTTON1 || client.isMenuOpen() || !allowsLeftClick()) {
			return event;
		}

		final ChatChannelHit chatChannelHit = chatChannelSetup(event.getPoint());
		if (chatChannelHit != null) {
			suppressLeftClick = true;
			openProfile(chatChannelHit.playerName, event.getPoint());
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

	private void addProfileEntry(String playerName, String target, Point anchorPoint, int index) {
		client.createMenuEntry(index)
				.setOption(MENU_OPEN_PROFILE)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> openProfile(playerName, anchorPoint));
	}

	private void moveProfileToTop(String playerName, Point anchorPoint) {
		final String target = profileMenuTarget(playerName);
		removeProfileEntry(playerName);
		addProfileEntry(playerName, target, anchorPoint, -1);
	}

	private void prepareOpenedMenu(String playerName, Point anchorPoint) {
		final String target = profileMenuTarget(playerName);
		removeProfileEntry(playerName);
		if (allowsRightClick()) {
			addProfileEntry(playerName, target, anchorPoint, 1);
		}
	}

	private void removeProfileEntry(String playerName) {
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
			if (username == null || username.getType() != WidgetType.TEXT || username.isSelfHidden()) {
				continue;
			}

			final String playerName = cleanPlayerName(username.getText());
			if (playerName.isEmpty()) {
				continue;
			}

			if (username.getBounds().contains(point)) {
				return new ChatChannelHit(playerName);
			}

			if (rank != null && rank.getType() == WidgetType.TEXT && !rank.isSelfHidden()
					&& rank.getBounds().contains(point) && isRankOption(cleanPlayerName(rank.getText()))) {
				return new ChatChannelHit(playerName);
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
			if (username == null || username.getType() != WidgetType.TEXT || username.isSelfHidden()
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

	private void openProfile(String target) {
		openProfile(target, currentMousePoint());
	}

	private void openProfile(String target, Point anchorPoint) {
		final String playerName = cleanPlayerName(target);
		if (playerName.isEmpty()) {
			return;
		}

		quickProfileController.openPlayer(playerName, anchorPoint);
	}

	private static final class ChatChannelHit {
		private final String playerName;

		private ChatChannelHit(String playerName) {
			this.playerName = playerName;
		}
	}

	private static String cleanPlayerName(String value) {
		if (value == null) {
			return "";
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}
}
