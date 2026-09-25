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
public class RaidInterfaces extends MouseAdapter {
	private static final String MENU_OPEN_PROFILE = "Open Profile";
	private static final String MENU_KICK = "Kick";
	private static final String MENU_REJECT = "Reject";

	private static final RaidLayout TOA_ACCEPTED =
			new RaidLayout(InterfaceID.ToaPartydetails.MEMBERS_LIST, 13, 1, 2, 10);
	private static final RaidLayout TOA_APPLICANTS =
			new RaidLayout(InterfaceID.ToaPartydetails.APPLICANTS_LIST, 20, 1, 2, 18);
	private static final RaidLayout TOB_ACCEPTED =
			new RaidLayout(InterfaceID.TobPartydetails.CURRENT, 11, 1, 2, 10);
	private static final RaidLayout TOB_APPLICANTS =
			new RaidLayout(InterfaceID.TobPartydetails.APPLICANTS, 20, 1, 2, 18);

	private final Client client;
	private final Configurations config;
	private final QuickProfileController quickProfileController;

	private boolean suppressLeftClick;
	private volatile RaidHit leftClickHit;
	private volatile String nativeApplicantPlayer;

	public RaidInterfaces(Client client, Configurations config, QuickProfileController quickProfileController) {
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

		final int componentId = event.getActionParam1();
		final String anchor = menuAnchor(componentId);
		if (anchor == null) {
			return;
		}

		final String option = event.getOption() != null
				? Text.removeTags(event.getOption())
				: "";
		if (!anchor.equals(option)) {
			return;
		}

		final String playerName = cleanPlayerName(event.getTarget());
		if (isEmptyPlayerName(playerName)) {
			return;
		}

		addProfileMenu(playerName, event.getTarget(), profileMenuIndex(playerName, componentId));
	}

	public void onPostMenuSort(PostMenuSort event) {
		if (client.isMenuOpen() || !allowsLeftClick()) {
			leftClickHit = null;
			return;
		}

		final Point point = currentMousePoint();
		final RaidHit hit = raidPlayerHit(point);

		updateApplicantState(hit);
		if (hit == null || !isProfileClick(hit)) {
			leftClickHit = null;
			return;
		}

		leftClickHit = hit;
		moveProfileToTop(hit, point);
	}

	public void onMenuOpened(MenuOpened event) {
		leftClickHit = null;

		final Point point = currentMousePoint();
		final RaidHit hit = raidPlayerHit(point);
		if (hit == null) {
			return;
		}

		final boolean rightClick = allowsRightClick();
		final String target = rightClick
				? profileMenuTarget(hit.playerName, hit.componentId)
				: null;

		removeProfileMenu(hit.playerName);
		if (!rightClick) {
			return;
		}

		addProfileMenu(hit.playerName, target, profileMenuIndex(hit.playerName, hit.componentId));
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

		final RaidHit hit = leftClickHit;
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

	private RaidHit raidPlayerHit(Point point) {
		RaidHit hit = playerHit(TOB_APPLICANTS, point);
		if (hit != null) {
			return hit;
		}

		hit = playerHit(TOB_ACCEPTED, point);
		if (hit != null) {
			return hit;
		}

		hit = playerHit(TOA_ACCEPTED, point);
		if (hit != null) {
			return hit;
		}

		return playerHit(TOA_APPLICANTS, point);
	}

	private RaidHit playerHit(RaidLayout layout, Point point) {
		if (point == null) {
			return null;
		}

		final Widget list = client.getWidget(layout.componentId);
		if (list == null || list.isSelfHidden()) {
			return null;
		}

		final Widget[] children = list.getDynamicChildren();
		if (children == null) {
			return null;
		}

		for (int base = 0; base + layout.nameOffset < children.length; base += layout.stride) {
			final int offset = hitOffset(layout, children, base, point);
			if (offset < 0) {
				continue;
			}

			final Widget username = children[base + layout.nameOffset];
			if (username == null || username.getType() != WidgetType.TEXT || username.isSelfHidden()) {
				continue;
			}

			final String playerName = cleanPlayerName(username.getText());
			if (isEmptyPlayerName(playerName)) {
				continue;
			}

			final Rectangle bounds = children[base + offset] != null
					? children[base + offset].getBounds()
					: null;

			return new RaidHit(layout.componentId, offset, playerName, bounds);
		}

		return null;
	}

	private static int hitOffset(RaidLayout layout, Widget[] children, int base, Point point) {
		final int nameIndex = base + layout.nameOffset;
		if (nameIndex < children.length && contains(children[nameIndex], point)) {
			return layout.nameOffset;
		}

		final int end = Math.min(base + layout.statsEnd, children.length - 1);
		for (int i = base + layout.statsStart; i <= end; i++) {
			if (contains(children[i], point)) {
				return i - base;
			}
		}

		return -1;
	}

	private static boolean contains(Widget widget, Point point) {
		if (widget == null || widget.isSelfHidden()) {
			return false;
		}

		final Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.contains(point);
	}

	private boolean isProfileClick(RaidHit hit) {
		if (isApplicantComponent(hit.componentId)) {
			final RaidLayout layout = hit.componentId == TOB_APPLICANTS.componentId
					? TOB_APPLICANTS
					: TOA_APPLICANTS;

			if (hit.offset == layout.nameOffset) {
				return true;
			}

			return !hasNativeAction(hit.playerName)
					&& hit.offset >= layout.statsStart
					&& hit.offset <= layout.statsEnd;
		}

		return true;
	}

	private void updateApplicantState(RaidHit hit) {
		if (hit == null || !isApplicantComponent(hit.componentId)) {
			nativeApplicantPlayer = null;
			return;
		}

		nativeApplicantPlayer = hasApplicantAction(hit)
				? hit.playerName
				: null;
	}

	private boolean hasApplicantAction(RaidHit hit) {
		final String anchor = menuAnchor(hit.componentId);
		if (anchor == null) {
			return false;
		}

		for (MenuEntry entry : client.getMenu().getMenuEntries()) {
			if (entry != null
					&& anchor.equals(entry.getOption())
					&& hit.playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()))) {
				return true;
			}
		}

		return false;
	}

	private boolean hasNativeAction(String playerName) {
		return nativeApplicantPlayer != null && nativeApplicantPlayer.equalsIgnoreCase(playerName);
	}

	private void moveProfileToTop(RaidHit hit, Point anchorPoint) {
		final MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length > 0 && isProfileEntry(entries[entries.length - 1], hit.playerName)) {
			final String playerName = hit.playerName;
			entries[entries.length - 1].onClick(entry -> openProfile(playerName, anchorPoint));
			return;
		}

		final String target = profileMenuTarget(hit.playerName, hit.componentId);
		removeProfileMenu(hit.playerName);
		addProfileMenu(hit.playerName, target, -1, anchorPoint);
	}

	private void addProfileMenu(String playerName, String target, int index) {
		addProfileMenu(playerName, target, index, currentMousePoint());
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

	private int profileMenuIndex(String playerName, int componentId) {
		final String anchor = menuAnchor(componentId);
		if (anchor == null) {
			return -1;
		}

		final MenuEntry[] entries = client.getMenu().getMenuEntries();
		for (int i = 0; i < entries.length; i++) {
			final MenuEntry entry = entries[i];
			if (entry != null
					&& anchor.equals(entry.getOption())
					&& playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()))) {
				return i;
			}
		}

		return -1;
	}

	private String profileMenuTarget(String playerName, int componentId) {
		final String anchor = menuAnchor(componentId);

		for (MenuEntry entry : client.getMenu().getMenuEntries()) {
			if (isProfileEntry(entry, playerName)) {
				return entry.getTarget();
			}

			if (anchor != null
					&& entry != null
					&& anchor.equals(entry.getOption())
					&& playerName.equalsIgnoreCase(cleanPlayerName(entry.getTarget()))) {
				return entry.getTarget();
			}
		}

		return playerName;
	}

	private static String menuAnchor(int componentId) {
		if (componentId == TOB_APPLICANTS.componentId || componentId == TOA_APPLICANTS.componentId) {
			return MENU_REJECT;
		}

		if (componentId == TOA_ACCEPTED.componentId) {
			return MENU_KICK;
		}

		return null;
	}

	private static boolean isApplicantComponent(int componentId) {
		return componentId == TOB_APPLICANTS.componentId || componentId == TOA_APPLICANTS.componentId;
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

	private static boolean isEmptyPlayerName(String value) {
		return value == null || value.isEmpty() || "-".equals(value);
	}

	private static String cleanPlayerName(String value) {
		if (value == null) {
			return "";
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}

	private static final class RaidLayout {
		private final int componentId;
		private final int stride;
		private final int nameOffset;
		private final int statsStart;
		private final int statsEnd;

		private RaidLayout(int componentId, int stride, int nameOffset, int statsStart, int statsEnd) {
			this.componentId = componentId;
			this.stride = stride;
			this.nameOffset = nameOffset;
			this.statsStart = statsStart;
			this.statsEnd = statsEnd;
		}
	}

	private static final class RaidHit {
		private final int componentId;
		private final int offset;
		private final String playerName;
		private final Rectangle bounds;

		private RaidHit(int componentId, int offset, String playerName, Rectangle bounds) {
			this.componentId = componentId;
			this.offset = offset;
			this.playerName = playerName;
			this.bounds = bounds != null
					? new Rectangle(bounds)
					: null;
		}
	}
}
