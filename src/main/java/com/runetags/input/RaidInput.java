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
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.Text;

/**
 * Adds RuneTags profile actions to player names exposed through raid interfaces.
 */
public class RaidInput extends MouseAdapter {
	private static final String MENU_OPEN_PROFILE = "Open Profile";

	private static final int TOB_APPLICANT_STRIDE = 20;
	private static final int TOB_APPLICANT_NAME = 1;
	private static final int TOB_APPLICANT_CONTENT_START = 1;
	private static final int TOB_APPLICANT_CONTENT_END = 18;
	private static final int TOB_APPLICANT_STATS_START = 2;
	private static final int TOB_APPLICANT_STATS_END = 16;

	private static final int TOB_CURRENT_STRIDE = 11;
	private static final int TOB_CURRENT_NAME = 1;
	private static final int TOB_CURRENT_CONTENT_START = 1;
	private static final int TOB_CURRENT_CONTENT_END = 10;

	private static final int TOA_MEMBER_STRIDE = 13;
	private static final int TOA_MEMBER_NAME = 1;
	private static final int TOA_MEMBER_CONTENT_START = 1;
	private static final int TOA_MEMBER_CONTENT_END = 10;

	private static final int TOA_APPLICANT_STRIDE = 20;
	private static final int TOA_APPLICANT_NAME = 1;
	private static final int TOA_APPLICANT_STATS_END = 16;
	private static final int TOA_APPLICANT_CONTENT = 18;

	private final Client client;
	private final Configurations config;
	private final QuickProfileController quickProfileController;

	private boolean suppressLeftClick;
	private volatile String nativeApplicantPlayer;

	public RaidInput(Client client, Configurations config, QuickProfileController quickProfileController) {
		this.client = client;
		this.config = config;
		this.quickProfileController = quickProfileController;
	}

	public void onMenuEntryAdded(MenuEntryAdded event) {
		if (event == null
				|| !allowsRightClick()
				|| (event.getType() != MenuAction.CC_OP.getId()
				&& event.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId())) {
			return;
		}

		final String option = event.getOption() != null
				? Text.removeTags(event.getOption())
				: "";
		if (!isRaidAnchor(event.getActionParam1(), option)) {
			return;
		}

		final String playerName = cleanPlayerName(event.getTarget());
		if (playerName.isEmpty()) {
			return;
		}

		addProfileMenu(playerName, event.getTarget(), currentMousePoint(), profileMenuIndex(playerName));
	}

	public void onPostMenuSort(PostMenuSort event) {
		if (client.isMenuOpen()) {
			return;
		}

		final Point point = currentMousePoint();
		updateApplicantState(point);

		if (!allowsLeftClick()) {
			return;
		}

		final RaidHit hit = leftClickHit(point);
		if (hit != null) {
			moveProfileToTop(hit.playerName, point);
		}
	}

	public void onMenuOpened(MenuOpened event) {
		final Point point = currentMousePoint();
		final RaidHit hit = raidPlayerHit(point);
		if (hit == null) {
			return;
		}

		removeProfileMenu(hit.playerName);
		if (!allowsRightClick()) {
			return;
		}

		final String target = profileMenuTarget(hit.playerName);
		addProfileMenu(hit.playerName, target, point, profileMenuIndex(hit.playerName));
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event) {
		suppressLeftClick = false;

		if (event == null || event.getButton() != MouseEvent.BUTTON1 || client.isMenuOpen()) {
			return event;
		}

		final ChatInteractionMode interactionMode = config.chatInteractionMode();
		if (interactionMode == null) {
			return event;
		}

		final Point point = event.getPoint();

		if (interactionMode.allowsLeftClick()) {
			final RaidHit hit = leftClickHit(point);
			if (hit != null) {
				openProfile(hit.playerName, point);
				return consumeLeftClick(event);
			}
		}

		final RaidHit currentHit = tobCurrentHit(point);
		if (currentHit != null && interactionMode.allowsRightClick()) {
			return consumeLeftClick(event);
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

	private MouseEvent consumeLeftClick(MouseEvent event) {
		suppressLeftClick = true;
		event.consume();
		return event;
	}

	private RaidHit leftClickHit(Point point) {
		RaidHit hit = tobApplicantClick(point);
		if (hit != null) {
			return hit;
		}

		hit = tobCurrentHit(point);
		if (hit != null) {
			return hit;
		}

		hit = toaMemberHit(point);
		if (hit != null) {
			return hit;
		}

		return toaApplicantClick(point);
	}

	private RaidHit raidPlayerHit(Point point) {
		RaidHit hit = tobApplicantHit(point);
		if (hit != null) {
			return hit;
		}

		hit = tobCurrentHit(point);
		if (hit != null) {
			return hit;
		}

		hit = toaMemberHit(point);
		if (hit != null) {
			return hit;
		}

		return toaApplicantHit(point);
	}

	private RaidHit tobApplicantClick(Point point) {
		final RaidHit nameHit = tobApplicantName(point);
		if (nameHit != null) {
			return nameHit;
		}

		final RaidHit statsHit = tobApplicantStats(point);
		if (statsHit == null || hasNativeAction(statsHit.playerName)) {
			return null;
		}

		return statsHit;
	}

	private RaidHit tobApplicantName(Point point) {
		return playerHit(InterfaceID.TobPartydetails.APPLICANTS,
				point,
				TOB_APPLICANT_STRIDE,
				TOB_APPLICANT_NAME,
				TOB_APPLICANT_NAME,
				TOB_APPLICANT_NAME);
	}

	private RaidHit tobApplicantStats(Point point) {
		return playerHit(InterfaceID.TobPartydetails.APPLICANTS,
				point,
				TOB_APPLICANT_STRIDE,
				TOB_APPLICANT_NAME,
				TOB_APPLICANT_STATS_START,
				TOB_APPLICANT_STATS_END);
	}

	private RaidHit tobApplicantHit(Point point) {
		return playerHit(InterfaceID.TobPartydetails.APPLICANTS,
				point,
				TOB_APPLICANT_STRIDE,
				TOB_APPLICANT_NAME,
				TOB_APPLICANT_CONTENT_START,
				TOB_APPLICANT_CONTENT_END);
	}

	private RaidHit tobCurrentHit(Point point) {
		return playerHit(InterfaceID.TobPartydetails.CURRENT,
				point,
				TOB_CURRENT_STRIDE,
				TOB_CURRENT_NAME,
				TOB_CURRENT_CONTENT_START,
				TOB_CURRENT_CONTENT_END);
	}

	private RaidHit toaMemberHit(Point point) {
		return playerHit(InterfaceID.ToaPartydetails.MEMBERS_LIST,
				point,
				TOA_MEMBER_STRIDE,
				TOA_MEMBER_NAME,
				TOA_MEMBER_CONTENT_START,
				TOA_MEMBER_CONTENT_END);
	}

	private RaidHit toaApplicantClick(Point point) {
		final RaidHit hit = toaApplicantHit(point);
		if (hit == null || hasNativeAction(hit.playerName)) {
			return null;
		}

		return hit;
	}

	private RaidHit toaApplicantHit(Point point) {
		final RaidHit statsHit = playerHit(InterfaceID.ToaPartydetails.APPLICANTS_LIST,
				point,
				TOA_APPLICANT_STRIDE,
				TOA_APPLICANT_NAME,
				TOA_APPLICANT_NAME,
				TOA_APPLICANT_STATS_END);
		if (statsHit != null) {
			return statsHit;
		}

		return playerHit(InterfaceID.ToaPartydetails.APPLICANTS_LIST,
				point,
				TOA_APPLICANT_STRIDE,
				TOA_APPLICANT_NAME,
				TOA_APPLICANT_CONTENT,
				TOA_APPLICANT_CONTENT);
	}

	private RaidHit playerHit(int componentId, Point point, int stride, int nameOffset, int hitStart, int hitEnd) {
		if (point == null) {
			return null;
		}

		final Widget list = client.getWidget(componentId);
		if (list == null || list.isSelfHidden()) {
			return null;
		}

		final Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return null;
		}

		for (int base = 0; base + nameOffset < children.length; base += stride) {
			final Widget username = children[base + nameOffset];
			if (username == null || username.getType() != WidgetType.TEXT || username.isSelfHidden()) {
				continue;
			}

			final String playerName = cleanPlayerName(username.getText());
			if (playerName.isEmpty()) {
				continue;
			}

			final int end = Math.min(base + hitEnd, children.length - 1);
			for (int i = base + hitStart; i <= end; i++) {
				if (contains(children[i], point)) {
					return new RaidHit(playerName);
				}
			}
		}

		return null;
	}

	private static boolean contains(Widget widget, Point point) {
		if (widget == null || widget.isSelfHidden()) {
			return false;
		}

		final Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.contains(point);
	}

	private void updateApplicantState(Point point) {
		final RaidHit applicantHit = applicantStateHit(point);
		nativeApplicantPlayer = applicantHit != null && hasApplicantAction(applicantHit.playerName)
				? applicantHit.playerName
				: null;
	}

	private RaidHit applicantStateHit(Point point) {
		final RaidHit tobHit = tobApplicantHit(point);
		return tobHit != null
				? tobHit
				: toaApplicantHit(point);
	}

	private boolean hasApplicantAction(String playerName) {
		for (MenuEntry entry : client.getMenu().getMenuEntries()) {
			if (entry == null
					|| (!"Accept".equals(entry.getOption()) && !"Reject".equals(entry.getOption()))
					|| !playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()))) {
				continue;
			}

			return true;
		}

		return false;
	}

	private boolean hasNativeAction(String playerName) {
		return nativeApplicantPlayer != null && nativeApplicantPlayer.equalsIgnoreCase(playerName);
	}

	private void moveProfileToTop(String playerName, Point anchorPoint) {
		final String target = profileMenuTarget(playerName);
		removeProfileMenu(playerName);
		addProfileMenu(playerName, target, anchorPoint, -1);
	}

	private void addProfileMenu(String playerName, String target, Point anchorPoint, int index) {
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

	private int profileMenuIndex(String playerName) {
		final MenuEntry[] entries = client.getMenu().getMenuEntries();

		for (int i = 0; i < entries.length; i++) {
			final MenuEntry entry = entries[i];
			if (entry != null && isMenuAnchor(entry.getOption())
					&& playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()))) {
				return i;
			}
		}

		return -1;
	}

	private String profileMenuTarget(String playerName) {
		for (MenuEntry entry : client.getMenu().getMenuEntries()) {
			if (isProfileEntry(entry, playerName)) {
				return entry.getTarget();
			}

			if (entry != null && isMenuAnchor(entry.getOption())
					&& playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()))) {
				return entry.getTarget();
			}
		}

		return playerName;
	}

	private static boolean isRaidAnchor(int componentId, String option) {
		if (componentId == InterfaceID.TobPartydetails.APPLICANTS) {
			return "Reject".equals(option);
		}

		if (componentId == InterfaceID.ToaPartydetails.MEMBERS_LIST) {
			return "Kick".equals(option);
		}

		if (componentId == InterfaceID.ToaPartydetails.APPLICANTS_LIST) {
			return "Reject".equals(option);
		}

		return false;
	}

	private static boolean isMenuAnchor(String option) {
		return "Reject".equals(option) || "Kick".equals(option);
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

	private static String cleanPlayerName(String value) {
		if (value == null) {
			return "";
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}

	private static final class RaidHit {
		private final String playerName;

		private RaidHit(String playerName) {
			this.playerName = playerName;
		}
	}
}