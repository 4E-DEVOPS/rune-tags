package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.config.MentionFont;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.MatchReason;
import com.runetags.records.LocalPlayerRecordService;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

/**
 * Maps semantic chat references and local-highlight spans onto rendered chat Widgets.
 *
 * CHATBOX and SPLIT_PRIVATE are independent physical surfaces, and one TaggedMessage
 * may resolve on both. Width and wrapping use each Widget's live Jagex FontTypeFace.
 */
public class ReferenceLayoutService {
	/**
	 * Physical surface on which RuneScape rendered a chat reference.
	 *
	 * This is deliberately separate from ChatMessageType.
	 *
	 * For example, PRIVATECHAT may render:
	 *
	 * - on CHATBOX,
	 * - on SPLIT_PRIVATE,
	 * - or on both simultaneously.
	 */
	public enum Surface {
		CHATBOX, SPLIT_PRIVATE
	}

	/**
	 * Complete presentation geometry produced by one shared chat-layout pass.
	 *
	 * Clickable reference/sender hitboxes and non-clickable local-highlight
	 * rectangles deliberately share the same semantic -> physical Widget
	 * association.
	 */
	public static final class LayoutResult {
		private final List<ReferenceHitbox> hitboxes;
		private final List<LocalHighlight> localHighlights;

		private LayoutResult(List<ReferenceHitbox> hitboxes, List<LocalHighlight> localHighlights) {
			this.hitboxes = hitboxes;
			this.localHighlights = localHighlights;
		}

		public List<ReferenceHitbox> getHitboxes() {
			return hitboxes;
		}

		public List<LocalHighlight> getLocalHighlights() {
			return localHighlights;
		}
	}

	/*
	 * One non-clickable physical overlay rectangle for a local alias or normalized-self match.
	 */
	public static final class LocalHighlight {
		private final long messageId;
		private final Rectangle bounds;
		private final Surface surface;

		private LocalHighlight(long messageId, Rectangle bounds, Surface surface) {
			this.messageId = messageId;
			this.bounds = bounds;
			this.surface = surface;
		}

		public long getMessageId() {
			return messageId;
		}

		public Rectangle getBounds() {
			return bounds;
		}

		public Surface getSurface() {
			return surface;
		}
	}

	private static final int MAX_WIDGET_DEPTH = 4;
	private static final int CHATBOX_X_PENDING = Integer.MIN_VALUE;

	/*
	 * One physical line produced by RuneScape's wrapped text widget.
	 *
	 * start/end are semantic plain-text offsets into the complete
	 * widget text. Width is the rendered width of this individual
	 * visual line.
	 */
	private static final class WrappedLine {
		private final int start;
		private final int end;
		private final int width;

		private WrappedLine(int start, int end, int width) {
			this.start = start;
			this.end = end;
			this.width = width;
		}
	}

	/*
	 * Pass-local semantic text and bounds snapshot for one rendered Widget.
	 *
	 * Cached bounds drive indexing only. Final ownership and output geometry still
	 * revalidate against the live Widget so reconstruction races remain observable.
	 */
	private static final class RenderedTextWidget {
		private final Widget widget;
		private final String rawText;
		private final String semanticText;
		private final Rectangle bounds;

		private RenderedTextWidget(Widget widget, String rawText, String semanticText, Rectangle bounds) {
			this.widget = widget;
			this.rawText = rawText;
			this.semanticText = semanticText;
			this.bounds = bounds;
		}
	}

	/*
	 * Exact physical CHATBOX sender widget and the sender's semantic start
	 * offset inside that widget.
	 */
	private static final class RenderedSenderMatch {
		private final RenderedTextWidget rendered;
		private final int semanticStart;

		private RenderedSenderMatch(RenderedTextWidget rendered, int semanticStart) {
			this.rendered = rendered;
			this.semanticStart = semanticStart;
		}
	}

	/*
	 * Native sender text ownership for one Widget currently colored by
	 * RuneTags. Both original and applied strings are retained so a recycled
	 * Widget is never restored over newer RuneScape content.
	 */
	private static final class FavoriteSenderTextState {
		private final String originalText;
		private final String appliedText;

		private FavoriteSenderTextState(String originalText, String appliedText) {
			this.originalText = originalText;
			this.appliedText = appliedText;
		}
	}

	/*
	 * Font ownership for one physical Widget modified by RuneTags.
	 *
	 * Restoration is permitted only while the Widget still contains the exact
	 * FontId RuneTags applied. A different current FontId means another owner has
	 * replaced the presentation and the stale original must not be restored.
	 */
	private static final class MentionFontState {
		private final int originalFontId;
		private final int appliedFontId;

		private MentionFontState(int originalFontId, int appliedFontId) {
			this.originalFontId = originalFontId;
			this.appliedFontId = appliedFontId;
		}
	}

	/*
	 * Indexes semantic text while preserving native candidate order.
	 *
	 * Geometry, visibility, and FontTypeFace remain live reads.
	 */
	private static final class RenderedBodyIndex {
		private final Map<String, List<RenderedTextWidget>> bodyIndex = new HashMap<>();
		private final Map<String, List<RenderedTextWidget>> caseFoldedBodyIndex = new HashMap<>();
		private final List<RenderedTextWidget> whitespaceBodies = new ArrayList<>();
		private final Set<String> exactBodies = new HashSet<>();
		private final Set<String> foldedBodies = new HashSet<>();

		private RenderedBodyIndex(List<RenderedTextWidget> widgets) {
			if (widgets == null) {
				return;
			}

			for (RenderedTextWidget rendered : widgets) {
				if (rendered == null) {
					continue;
				}

				final String semantic = rendered.semanticText;
				if (semantic == null || semantic.isEmpty()) {
					continue;
				}

				exactBodies.add(semantic);
				if (semantic.trim().isEmpty()) {
					whitespaceBodies.add(rendered);
					continue;
				}

				bodyIndex.computeIfAbsent(semantic, ignored -> new ArrayList<>()).add(rendered);

				final String folded = semantic.toLowerCase(Locale.ROOT);
				foldedBodies.add(folded);
				caseFoldedBodyIndex.computeIfAbsent(folded, ignored -> new ArrayList<>()).add(rendered);
			}
		}

		private List<RenderedTextWidget> candidates(String semanticBody) {
			if (semanticBody == null || semanticBody.isEmpty()) {
				return Collections.emptyList();
			}
			if (semanticBody.trim().isEmpty()) {
				return whitespaceBodies;
			}

			final List<RenderedTextWidget> candidates = bodyIndex.get(semanticBody);
			return candidates != null
					? candidates
					: Collections.emptyList();
		}

		private List<RenderedTextWidget> candidatesIgnoreCase(String semanticBody) {
			if (semanticBody == null || semanticBody.isEmpty() || semanticBody.trim().isEmpty()) {
				return Collections.emptyList();
			}

			final List<RenderedTextWidget> candidates = caseFoldedBodyIndex.get(semanticBody.toLowerCase(Locale.ROOT));
			return candidates != null
					? candidates
					: Collections.emptyList();
		}

		private Set<String> exactBodies() {
			return exactBodies;
		}

		private Set<String> foldedBodies() {
			return foldedBodies;
		}
	}

	/*
	 * Stable physical row identity used by RuneScape/Big Bold Chat independently
	 * from the transient canvas Y coordinate.
	 */
	private static final class RowKey {
		private final int originalY;
		private final int relativeY;

		private RowKey(int originalY, int relativeY) {
			this.originalY = originalY;
			this.relativeY = relativeY;
		}

		@SuppressWarnings("deprecation")
		private static RowKey of(Widget widget) {
			return widget != null
					? new RowKey(widget.getOriginalY(), widget.getRelativeY())
					: null;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof RowKey)) {
				return false;
			}

			final RowKey rowKey = (RowKey) other;
			return originalY == rowKey.originalY && relativeY == rowKey.relativeY;
		}

		@Override
		public int hashCode() {
			int result = originalY;
			result = 31 * result + relativeY;
			return result;
		}
	}

	/*
	 * Accepted CHATBOX body ownership and content-space Y for one semantic message.
	 */
	private static final class ChatboxBodyState {
		private final RowKey rowKey;
		private final int contentY;

		private ChatboxBodyState(RowKey rowKey, int contentY) {
			this.rowKey = rowKey;
			this.contentY = contentY;
		}

	}


	/*
	 * One reusable text-bearing Widget discovered beneath a chat surface.
	 *
	 * Membership is stable across ordinary render frames. Raw/semantic text remains
	 * mutable because RuneScape recycles the same Widget for newer chat rows.
	 */
	private static final class CachedRenderedWidget {
		private final Widget widget;
		private String rawText;
		private String semanticText;

		private CachedRenderedWidget(Widget widget) {
			this.widget = widget;
		}
	}

	/*
	 * Cross-frame text-Widget membership cache for one physical chat surface.
	 *
	 * Native reconstruction and repository revision changes both force rediscovery,
	 * while bounds, hidden state, RowKey, and final geometry remain live every pass.
	 */
	private static final class RenderedSurfaceCache {
		private Widget root;
		private final List<CachedRenderedWidget> widgets = new ArrayList<>();
		private int topologySignature;
		private long repositoryRevision = Long.MIN_VALUE;
		private boolean dirty = true;
	}

	/*
	 * Every indexed candidate is revalidated against live bounds before it can confirm message ownership.
	 */
	private static final class RenderedRowIndex {
		private final Map<Integer, List<RenderedTextWidget>> rowIndex = new HashMap<>();

		private RenderedRowIndex(List<RenderedTextWidget> widgets) {
			if (widgets == null) {
				return;
			}

			for (RenderedTextWidget rendered : widgets) {
				if (rendered == null || rendered.widget == null || rendered.bounds == null) {
					continue;
				}

				rowIndex.computeIfAbsent(rendered.bounds.y, ignored -> new ArrayList<>()).add(rendered);
			}
		}

		private List<RenderedTextWidget> candidates(int y) {
			final List<RenderedTextWidget> candidates = rowIndex.get(y);
			return candidates != null
					? candidates
					: Collections.emptyList();
		}
	}

	private final Client client;
	private final Configurations config;
	private final TaggedMessageRepository repository;
	private final LocalPlayerRecordService localPlayerRecordService;

	private final Map<Widget, MentionFontState> mentionFontStates = new IdentityHashMap<>();
	private final Map<Widget, FavoriteSenderTextState> favoriteSenderTextStates = new IdentityHashMap<>();

	/*
	 * A reconstructed CHATBOX body can briefly expose recycled horizontal or
	 * canvas geometry before RuneScape finishes positioning the row.
	 *
	 * X changes are confirmed across consecutive layout passes while rendering
	 * continues from the last accepted X. Y correction applies only while the
	 * physical row identity itself remains unchanged.
	 */
	private final Map<Long, Integer> acceptedChatboxBodyX = new HashMap<>();
	private final Map<Long, Integer> pendingChatboxBodyX = new HashMap<>();
	private final Map<Long, ChatboxBodyState> acceptedChatboxBodyRows = new HashMap<>();

	private final RenderedSurfaceCache chatboxRenderedSurfaceCache = new RenderedSurfaceCache();
	private final RenderedSurfaceCache splitPrivateRenderedSurfaceCache = new RenderedSurfaceCache();
	private long lastGeometryPruneRevision = Long.MIN_VALUE;


	private boolean favoriteSenderRowsDirty = true;
	private long lastFavoriteRevision = Long.MIN_VALUE;
	private boolean lastFavoritesEnabled;
	private int lastFavoriteColorRgb = Integer.MIN_VALUE;

	public ReferenceLayoutService(
			Client client,
			Configurations config,
			TaggedMessageRepository repository,
			LocalPlayerRecordService localPlayerRecordService) {
		this.client = client;
		this.config = config;
		this.repository = repository;
		this.localPlayerRecordService = localPlayerRecordService;
	}

	/*
	 * Native chat reconstruction can replace or recycle descendants while preserving
	 * the same surface root. Invalidate membership before the construction script runs
	 * so the next synchronization/layout pass rediscovers the physical row Widgets.
	 */
	public void invalidateRenderedWidgetCaches() {
		chatboxRenderedSurfaceCache.dirty = true;
		splitPrivateRenderedSurfaceCache.dirty = true;
	}

	/*
	 * Synchronizes configured mention fonts against final post-construction chat Widgets.
	 *
	 * RuneTags-owned font state is restored before current message ownership is resolved
	 * and reapplied.
	 */
	public void syncMentionFonts() {
		/*
		 * Physical Widget instances are recycled by RuneScape. Always restore
		 * native ownership before resolving the current live rows.
		 */
		restoreAllOriginalFonts();

		/*
		 * Dirty synchronization also repairs native expanded-name styling, including NORMAL mode.
		 */
		final List<TaggedMessage> messages = new ArrayList<>(repository.snapshot());
		Collections.reverse(messages);

		syncSurfaceFonts(client.getWidget(InterfaceID.PmChat.CONTAINER), Surface.SPLIT_PRIVATE, messages);
		syncSurfaceFonts(client.getWidget(InterfaceID.Chatbox.SCROLLAREA), Surface.CHATBOX, messages);
	}

	/*
	 * Mark normal-chatbox sender presentation dirty after native row
	 * reconstruction. FontLayoutService calls this alongside font dirtiness
	 * so Favorite color ownership follows the same settled PostClientTick cadence.
	 */
	public void markFavoriteSenderRowsDirty() {
		favoriteSenderRowsDirty = true;
	}

	/*
	 * Synchronize Favorite sender-name colors only when either native rows,
	 * Favorite state, or the Favorites config changed.
	 *
	 * This cheap gate is safe to call every PostClientTick. The expensive
	 * semantic/widget association runs only when presentation is actually dirty.
	 */
	public void syncFavoriteSenderColorsIfNeeded() {
		final long favoriteRevision = localPlayerRecordService != null
				? localPlayerRecordService.getFavoriteRevision()
				: 0L;

		final boolean favoritesEnabled = config.showFavorites();
		final Color favoriteColor = configuredFavoriteColor();
		final int favoriteColorRgb = favoriteColor.getRGB();

		if (!favoriteSenderRowsDirty
				&& favoriteRevision == lastFavoriteRevision
				&& favoritesEnabled == lastFavoritesEnabled
				&& favoriteColorRgb == lastFavoriteColorRgb) {
			return;
		}

		syncFavoriteSenderColors(favoritesEnabled);

		favoriteSenderRowsDirty = false;
		lastFavoriteRevision = favoriteRevision;
		lastFavoritesEnabled = favoritesEnabled;
		lastFavoriteColorRgb = favoriteColorRgb;
	}

	/*
	 * Restore every native sender Widget currently owned by Favorite styling.
	 *
	 * The applied text is compared before restoration. RuneScape can recycle a
	 * Widget for a newer row at any time; if that has already happened, RuneTags
	 * must never write the stale original text back over the new message.
	 */
	public void restoreFavoriteSenderColors() {
		restoreOriginalFavoriteSenderTexts();

		favoriteSenderRowsDirty = true;
		lastFavoriteRevision = Long.MIN_VALUE;
		lastFavoritesEnabled = false;
		lastFavoriteColorRgb = Integer.MIN_VALUE;
	}

	private void syncFavoriteSenderColors(boolean favoritesEnabled) {
		restoreOriginalFavoriteSenderTexts();

		if (!favoritesEnabled || localPlayerRecordService == null) {
			return;
		}

		final List<TaggedMessage> messages = new ArrayList<>(repository.snapshot());
		if (messages.isEmpty()) {
			return;
		}

		Collections.reverse(messages);

		final Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (chatbox == null || chatbox.isHidden()) {
			return;
		}

		final List<RenderedTextWidget> textWidgets = new ArrayList<>();

		/*
		 * Use a null viewport just like font synchronization. Retained off-screen
		 * rows can later scroll into view without native reconstruction, so their
		 * Favorite sender color must already be correct.
		 */
		collectRenderedTextWidgets(chatbox, Surface.CHATBOX, null, textWidgets);
		if (textWidgets.isEmpty()) {
			return;
		}

		final RenderedBodyIndex bodyIndex = new RenderedBodyIndex(textWidgets);
		final RenderedRowIndex rowIndex = new RenderedRowIndex(textWidgets);
		final Set<Widget> usedBodyWidgets = Collections.newSetFromMap(new IdentityHashMap<>());

		for (TaggedMessage message : messages) {
			if (message == null || message.getCanonicalSender() == null
					|| message.getCanonicalSender().trim().isEmpty()) {
				continue;
			}

			/*
			 * Reserve body ownership for every retained message before applying Favorite styling.
			 * This prevents duplicate bodies from being claimed by the wrong message.
			 */
			final RenderedTextWidget messageWidget = findRenderedWidgetForMessageIndexedForFont(
					message, textWidgets, bodyIndex, rowIndex, usedBodyWidgets);
			if (messageWidget == null || messageWidget.widget == null) {
				continue;
			}

			usedBodyWidgets.add(messageWidget.widget);

			if (!localPlayerRecordService.isFavorite(message.getCanonicalSender())) {
				continue;
			}

			final RenderedSenderMatch senderMatch = findChatboxSender(
					messageWidget, message.getCanonicalSender(), textWidgets, rowIndex);
			if (senderMatch == null) {
				continue;
			}

			applyFavoriteSenderColor(senderMatch, message.getCanonicalSender());
		}
	}

	private void applyFavoriteSenderColor(RenderedSenderMatch senderMatch, String sender) {
		if (senderMatch == null || senderMatch.rendered == null || senderMatch.rendered.widget == null
				|| sender == null || sender.isEmpty()) {
			return;
		}

		final String rawText = senderMatch.rendered.rawText;
		final String semanticText = senderMatch.rendered.semanticText;
		if (rawText == null || semanticText == null) {
			return;
		}

		final int semanticEnd = senderMatch.semanticStart + sender.length();
		if (semanticEnd > semanticText.length()) {
			return;
		}

		final MessageMarkupMap map = MessageMarkupMap.create(rawText);
		if (!map.matchesPlain(semanticText)) {
			return;
		}

		final int rawStart = map.rawBoundary(senderMatch.semanticStart);
		final int rawEnd = map.rawBoundary(semanticEnd);
		if (rawStart < 0 || rawEnd <= rawStart || rawEnd > rawText.length()) {
			return;
		}

		final String appliedText = rawText.substring(0, rawStart)
				+ "<col="
				+ colorHex(configuredFavoriteColor())
				+ ">"
				+ rawText.substring(rawStart, rawEnd)
				+ "</col>"
				+ rawText.substring(rawEnd);

		if (appliedText.equals(rawText)) {
			return;
		}

		senderMatch.rendered.widget.setText(appliedText);
		favoriteSenderTextStates.put(senderMatch.rendered.widget, new FavoriteSenderTextState(rawText, appliedText));
	}

	private Color configuredFavoriteColor() {
		final Color configured = config.favoriteColor();
		return configured != null
				? configured
				: new Color(255, 205, 70);
	}

	private void restoreOriginalFavoriteSenderTexts() {
		if (favoriteSenderTextStates.isEmpty()) {
			return;
		}

		for (Map.Entry<Widget, FavoriteSenderTextState> entry : favoriteSenderTextStates.entrySet()) {
			final Widget widget = entry.getKey();
			final FavoriteSenderTextState state = entry.getValue();
			if (widget == null || state == null || state.appliedText == null) {
				continue;
			}

			final String currentText = widget.getText();
			if (state.appliedText.equals(currentText)) {
				widget.setText(state.originalText);
			}
		}

		favoriteSenderTextStates.clear();
	}

	/*
	 * Reconciles RuneScape's expanded player-name wrapper with RuneTags mention styling.
	 *
	 * Only wrapper markup is changed; visible text and the native restore color are preserved.
	 */
	private void repairExpandedReferenceStyles(Widget messageWidget, TaggedMessage message) {
		if (messageWidget == null || message == null
				|| message.getReferences() == null || message.getReferences().isEmpty()) {
			return;
		}

		final String rawWidgetText = messageWidget.getText();
		if (rawWidgetText == null || rawWidgetText.isEmpty()) {
			return;
		}

		final String semanticWidgetText = ChatText.toSemanticPlain(rawWidgetText);
		final String originalMessage = message.getOriginalMessage();
		if (semanticWidgetText == null || originalMessage == null || originalMessage.isEmpty()) {
			return;
		}

		int messageStart = semanticWidgetText.indexOf(originalMessage);
		if (messageStart < 0 && isPrivateMessage(message)) {
			messageStart = indexOfIgnoreCase(semanticWidgetText, originalMessage);
		}
		if (messageStart < 0) {
			return;
		}

		final String localPlayerName = client.getLocalPlayer() != null
				? client.getLocalPlayer().getName()
				: null;

		String repaired = rawWidgetText;

		for (PlayerReference reference : message.getReferences()) {
			if (reference == null) {
				continue;
			}

			final int semanticStart = messageStart + reference.getStartOffset();
			final int semanticEnd = messageStart + reference.getEndOffset();
			if (semanticStart < 0 || semanticEnd <= semanticStart || semanticEnd > semanticWidgetText.length()) {
				continue;
			}

			String renderedToken = semanticWidgetText.substring(semanticStart, semanticEnd);
			if (renderedToken.startsWith("@")) {
				renderedToken = renderedToken.substring(1);
			}
			if (renderedToken.isEmpty()) {
				continue;
			}

			final boolean isSelf = samePlayerName(reference.getLookupName(), localPlayerName)
					|| samePlayerName(renderedToken, localPlayerName);
			final boolean shouldColor = isSelf
					? config.mentionSelf()
					: config.mentionOthers();
			final Color desiredColor = isSelf
					? config.selfMentionColor()
					: config.otherMentionColor();

			/*
			 * Rewrite only the expanded player-name wrapper and preserve its native restore color.
			 */
			final Pattern expandedNamePattern = Pattern.compile(
					"<col=([0-9a-fA-F]{6})><u>(" + Pattern.quote(renderedToken) + ")</u><col=([0-9a-fA-F]{6})>",
					Pattern.CASE_INSENSITIVE);

			final Matcher matcher = expandedNamePattern.matcher(repaired);
			if (!matcher.find()) {
				continue;
			}

			final String openingColor = shouldColor && desiredColor != null
					? colorHex(desiredColor)
					: matcher.group(1);
			final String displayedName = matcher.group(2);
			final String restoreColor = matcher.group(3);
			final String replacement = "<col=" + openingColor + ">" + (config.underlineMentions()
					? "<u>"
					: "") + displayedName + (config.underlineMentions()
					? "</u>"
					: "") + "<col=" + restoreColor + ">";

			repaired = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
		}

		if (!repaired.equals(rawWidgetText)) {
			messageWidget.setText(repaired);
		}
	}

	private static String colorHex(Color color) {
		return String.format(Locale.ROOT, "%06x", color.getRGB() & 0xFFFFFF);
	}

	private static boolean samePlayerName(String left, String right) {
		if (left == null || right == null) {
			return false;
		}

		return playerNameKey(left).equals(playerNameKey(right));
	}

	private static String playerNameKey(String value) {
		return value.replace('\u00A0', ' ')
				.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
	}

	/*
	 * Restore every physical chat font currently owned by RuneTags.
	 *
	 * Used when the plugin is shutting down so RuneScape is never left displaying
	 * a RuneTags FontId after the plugin has been disabled.
	 */
	public void restoreMentionFonts() {
		restoreAllOriginalFonts();
	}

	/*
	 * Resolves message ownership for one surface, repairs expanded reference styling,
	 * and applies the configured mention font.
	 */
	private void syncSurfaceFonts(Widget surfaceWidget, Surface surface, List<TaggedMessage> messages) {
		if (surfaceWidget == null || surfaceWidget.isHidden() || messages == null || messages.isEmpty()) {
			return;
		}

		final List<RenderedTextWidget> textWidgets = new ArrayList<>();
		collectRenderedTextWidgets(surfaceWidget, surface, null, textWidgets);

		final RenderedBodyIndex bodyIndex = new RenderedBodyIndex(textWidgets);
		final RenderedRowIndex rowIndex = new RenderedRowIndex(textWidgets);
		if (textWidgets.isEmpty()) {
			return;
		}

		final Set<Widget> usedWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
		for (TaggedMessage message : messages) {
			if (message == null) {
				continue;
			}

			if (surface == Surface.SPLIT_PRIVATE && !isPrivateMessage(message)) {
				continue;
			}

			final RenderedTextWidget messageWidget =
					findRenderedWidgetForMessageIndexedForFont(message, textWidgets, bodyIndex, rowIndex, usedWidgets);
			if (messageWidget == null || messageWidget.widget == null) {
				continue;
			}

			usedWidgets.add(messageWidget.widget);
			repairExpandedReferenceStyles(messageWidget.widget, message);
			applyMentionFont(messageWidget.widget, message);
		}
	}

	/*
	 * Resolves font ownership from the pass-local body index.
	 *
	 * Exact body matching is primary. Sender confirmation and private case-folded fallback
	 * disambiguate candidates. Candidate geometry is always revalidated live.
	 */
	private RenderedTextWidget findRenderedWidgetForMessageIndexedForFont(
			TaggedMessage message, List<RenderedTextWidget> widgets, RenderedBodyIndex bodyIndex,
			RenderedRowIndex rowIndex, Set<Widget> usedWidgets) {
		if (message == null || widgets == null || bodyIndex == null || rowIndex == null || usedWidgets == null) {
			return null;
		}

		final String needle = message.getOriginalMessage();
		if (needle == null || needle.isEmpty()) {
			return null;
		}

		final RenderedTextWidget exactMatch = selectRenderedFontBodyCandidate(
				message, bodyIndex.candidates(needle), rowIndex, widgets, usedWidgets);
		if (exactMatch != null || !isPrivateMessage(message)) {
			return exactMatch;
		}

		return selectRenderedFontBodyCandidate(
				message, bodyIndex.candidatesIgnoreCase(needle), rowIndex, widgets, usedWidgets);
	}

	/*
	 * Selects one unused body candidate using sender-confirmed ownership.
	 *
	 * Body-only fallback is retained only for semantic records without a sender.
	 */
	private RenderedTextWidget selectRenderedFontBodyCandidate(
			TaggedMessage message, List<RenderedTextWidget> candidates,
			RenderedRowIndex rowIndex, List<RenderedTextWidget> allWidgets, Set<Widget> usedWidgets) {
		if (message == null || candidates == null || candidates.isEmpty()
				|| rowIndex == null || allWidgets == null || usedWidgets == null) {
			return null;
		}

		final String sender = message.getCanonicalSender();
		final boolean senderKnown = sender != null && !sender.isEmpty();
		RenderedTextWidget bodyFallback = null;

		for (RenderedTextWidget rendered : candidates) {
			if (rendered == null || rendered.widget == null
					|| usedWidgets.contains(rendered.widget) || rendered.widget.isHidden()) {
				continue;
			}

			if (!senderKnown && bodyFallback == null) {
				bodyFallback = rendered;
			}

			if (senderKnown && hasRenderedSenderOnRow(rendered, message, rowIndex, allWidgets)) {
				return rendered;
			}
		}

		/*
		 * Player-authored rows require sender confirmation. Body-only ownership is
		 * reserved for semantic records which genuinely have no sender.
		 */
		return senderKnown
				? null
				: bodyFallback;
	}

	/*
	 * Rebuilds reference and local-highlight geometry for both physical chat surfaces.
	 *
	 * A TaggedMessage may resolve independently on CHATBOX and SPLIT_PRIVATE.
	 */
	public LayoutResult layout() {

		final List<ReferenceHitbox> hitboxes = new ArrayList<>();
		final List<LocalHighlight> localHighlights = new ArrayList<>();

		pruneGeometryStateIfRepositoryChanged();

		if (repository.size() == 0) {
			return new LayoutResult(hitboxes, localHighlights);
		}

		final boolean includeLocalHighlights = shouldLayoutLocalHighlights();

		/*
		 * Split Private and normal CHATBOX remain independent physical surfaces.
		 *
		 * One TaggedMessage may legitimately resolve on both.
		 */
		layoutSurface(client.getWidget(InterfaceID.PmChat.CONTAINER), Surface.SPLIT_PRIVATE, hitboxes, localHighlights, includeLocalHighlights);
		layoutSurface(client.getWidget(InterfaceID.Chatbox.SCROLLAREA), Surface.CHATBOX, hitboxes, localHighlights, includeLocalHighlights);

		return new LayoutResult(hitboxes, localHighlights);
	}

	/*
	 * Layout semantic messages against one physical RuneScape chat surface.
	 *
	 * Visible body text seeds the optimized path. Repeated sender/body history can
	 * promote the pass to complete-surface correlation when off-screen ownership
	 * must be preserved. Widget ownership remains unique within this surface pass.
	 */
	private void layoutSurface(Widget surfaceWidget, Surface surface, List<ReferenceHitbox> output,
			List<LocalHighlight> localHighlights, boolean includeLocalHighlights) {
		if (surfaceWidget == null || surfaceWidget.isHidden()) {
			return;
		}

		Rectangle visibleBounds = null;
		if (surface == Surface.CHATBOX) {
			visibleBounds = surfaceWidget.getBounds();
			if (visibleBounds == null || visibleBounds.width <= 0 || visibleBounds.height <= 0) {
				return;
			}
		}

		/*
		 * Keep the optimized visible-row path for ordinary CHATBOX traffic.
		 *
		 * If retained history contains repeated sender/body occurrences which are
		 * not all visible, expand this pass to the complete physical surface so a
		 * newer off-screen occurrence cannot claim an older visible row.
		 */
		final List<RenderedTextWidget> textWidgets = new ArrayList<>();
		collectRenderedTextWidgets(surfaceWidget, surface, visibleBounds, textWidgets);
		if (textWidgets.isEmpty()) {
			return;
		}

		RenderedBodyIndex bodyIndex = new RenderedBodyIndex(textWidgets);
		RenderedRowIndex rowIndex = new RenderedRowIndex(textWidgets);
		List<TaggedMessage> messages = repository.snapshotMatchingBodies(
				bodyIndex.exactBodies(), bodyIndex.foldedBodies(), surface == Surface.SPLIT_PRIVATE);
		if (messages.isEmpty()) {
			return;
		}

		if (surface == Surface.CHATBOX && needsCompleteChatboxCorrelation(messages, bodyIndex, rowIndex, textWidgets)) {
			textWidgets.clear();
			collectRenderedTextWidgets(surfaceWidget, surface, null, textWidgets);
			if (textWidgets.isEmpty()) {
				return;
			}

			bodyIndex = new RenderedBodyIndex(textWidgets);
			rowIndex = new RenderedRowIndex(textWidgets);
			messages = repository.snapshotMatchingBodies(bodyIndex.exactBodies(), bodyIndex.foldedBodies(), false);
			if (messages.isEmpty()) {
				return;
			}
		}

		final Set<Widget> usedWidgets = Collections.newSetFromMap(new IdentityHashMap<>());

		for (TaggedMessage message : messages) {
			if (message == null) {
				continue;
			}

			final RenderedTextWidget messageWidget = findRenderedWidgetForMessage(
					message, textWidgets, bodyIndex, rowIndex, usedWidgets, surface);
			if (messageWidget == null || !isLiveRenderedCandidate(messageWidget)) {
				continue;
			}

			/*
			 * Reserve semantic ownership before deciding whether this row contributes
			 * visible geometry.
			 */
			usedWidgets.add(messageWidget.widget);

			if (!isVisibleForLayout(messageWidget, surface, visibleBounds)) {
				continue;
			}

			/*
			 * Sender interaction belongs to the physical sender Widget, not to the
			 * body's transient X/Y stabilization state.
			 */
			layoutSender(messageWidget, message, surface, textWidgets, rowIndex, output);

			final int horizontalOffset;
			final int verticalOffset;
			if (surface == Surface.CHATBOX) {
				horizontalOffset = resolveChatboxBodyXOffset(message, messageWidget);
				if (horizontalOffset == CHATBOX_X_PENDING) {
					continue;
				}

				verticalOffset = resolveChatboxBodyYOffset(message, messageWidget, surfaceWidget);
			} else {
				horizontalOffset = 0;
				verticalOffset = 0;
			}

			layoutMessage(messageWidget, message, surface, output, horizontalOffset, verticalOffset);

			if (includeLocalHighlights) {
				layoutLocalHighlight(messageWidget, message, surface, localHighlights, horizontalOffset, verticalOffset);
			}
		}
	}

	private boolean needsCompleteChatboxCorrelation(
			List<TaggedMessage> messages,
			RenderedBodyIndex bodyIndex,
			RenderedRowIndex rowIndex,
			List<RenderedTextWidget> textWidgets) {
		if (messages == null || messages.isEmpty() || bodyIndex == null
				|| rowIndex == null || textWidgets == null || textWidgets.isEmpty()) {
			return false;
		}

		final Map<String, Integer> semanticCounts = new HashMap<>();
		final Map<String, TaggedMessage> representatives = new HashMap<>();

		for (TaggedMessage message : messages) {
			if (message == null || message.getOriginalMessage() == null) {
				continue;
			}

			final String sender = message.getCanonicalSender();
			if (sender == null || sender.isEmpty()) {
				continue;
			}

			final String key = senderBodyKey(message);
			semanticCounts.put(key, semanticCounts.getOrDefault(key, 0) + 1);
			representatives.putIfAbsent(key, message);
		}

		for (Map.Entry<String, Integer> entry : semanticCounts.entrySet()) {
			if (entry.getValue() < 2) {
				continue;
			}

			final TaggedMessage representative = representatives.get(entry.getKey());
			if (representative == null) {
				continue;
			}

			List<RenderedTextWidget> candidates = bodyIndex.candidates(representative.getOriginalMessage());
			if (candidates.isEmpty() && isPrivateMessage(representative)) {
				candidates = bodyIndex.candidatesIgnoreCase(representative.getOriginalMessage());
			}

			int physicalCount = 0;
			for (RenderedTextWidget candidate : candidates) {
				if (hasRenderedSenderOnRow(candidate, representative, rowIndex, textWidgets)) {
					physicalCount++;
				}
			}

			if (physicalCount < entry.getValue()) {
				return true;
			}
		}

		return false;
	}

	private static String senderBodyKey(TaggedMessage message) {
		final String sender = message != null && message.getCanonicalSender() != null
				? playerNameKey(message.getCanonicalSender())
				: "";
		final String body = message != null && message.getOriginalMessage() != null
				? message.getOriginalMessage()
				: "";
		final String bodyKey = isPrivateMessage(message)
				? body.toLowerCase(Locale.ROOT)
				: body;
		return sender + '\u0000' + bodyKey;
	}

	private static boolean isVisibleForLayout(
			RenderedTextWidget rendered, Surface surface, Rectangle visibleBounds) {
		if (rendered == null || rendered.widget == null || rendered.widget.isHidden()) {
			return false;
		}

		if (surface != Surface.CHATBOX || visibleBounds == null) {
			return true;
		}

		final Rectangle bounds = rendered.widget.getBounds();
		return bounds != null && bounds.width > 0 && bounds.height > 0 && visibleBounds.intersects(bounds);
	}


	private static boolean isLiveRenderedCandidate(RenderedTextWidget rendered) {
		if (rendered == null || rendered.widget == null || rendered.widget.isHidden()) {
			return false;
		}

		final Rectangle bounds = rendered.widget.getBounds();
		return bounds != null && bounds.width > 0 && bounds.height > 0;
	}

	private void pruneGeometryStateIfRepositoryChanged() {
		final long revision = repository.getRevision();
		if (revision == lastGeometryPruneRevision) {
			return;
		}

		pruneChatboxBodyXState(repository.snapshotRetainedIds());
		lastGeometryPruneRevision = revision;
	}



	/*
	 * Apply the configured mention font to one owned physical message Widget.
	 *
	 * The selected appearance is resolved relative to the live base font. Known
	 * normal/bold companions are substituted while unrelated font families remain
	 * unchanged.
	 */
	private void applyMentionFont(Widget messageWidget, TaggedMessage message) {
		if (messageWidget == null || message == null) {
			return;
		}

		final boolean hasPlayerReference = message.getReferences() != null && !message.getReferences().isEmpty();
		final boolean hasLocalMention = message.getLocalMentionMatch() != null && message.getLocalMentionMatch()
				.isMatchesLocalPlayer();

		if (!hasPlayerReference && !hasLocalMention) {
			restoreOriginalFont(messageWidget);
			return;
		}

		final MentionFont mentionFont = config.fontMentions();
		if (mentionFont == null) {
			restoreOriginalFont(messageWidget);
			return;
		}

		final int currentFontId = messageWidget.getFontId();
		final MentionFontState state = mentionFontStates.get(messageWidget);
		final int baseFontId;
		if (state != null && currentFontId == state.appliedFontId) {
			baseFontId = state.originalFontId;
		} else {
			/*
			 * A different current FontId means RuneScape or another plugin replaced
			 * our previous presentation. Abandon stale ownership and treat the new
			 * live FontId as the active base.
			 */
			mentionFontStates.remove(messageWidget);
			baseFontId = currentFontId;
		}

		final int fontId = MentionFontResolver.fontIdFor(mentionFont, baseFontId);
		if (fontId == baseFontId) {
			return;
		}
		if (currentFontId != fontId) {
			messageWidget.setFontId(fontId);
		}

		mentionFontStates.put(messageWidget, new MentionFontState(baseFontId, fontId));
	}

	/*
	 * Restore one font still owned by RuneTags.
	 *
	 * If another owner has already changed the Widget's FontId,
	 * discard our stale ownership without writing the old value back.
	 */
	private void restoreOriginalFont(Widget widget) {
		if (widget == null) {
			return;
		}

		final MentionFontState state = mentionFontStates.remove(widget);
		if (state == null || widget.getFontId() != state.appliedFontId) {
			return;
		}

		if (state.originalFontId != state.appliedFontId) {
			widget.setFontId(state.originalFontId);
		}
	}

	/*
	 * Restore every physical Widget whose current font is still owned by RuneTags.
	 *
	 * Widgets changed by RuneScape or another plugin are deliberately left alone.
	 */
	private void restoreAllOriginalFonts() {
		if (mentionFontStates.isEmpty()) {
			return;
		}

		final Map<Widget, MentionFontState> fontsToRestore = new IdentityHashMap<>(mentionFontStates);
		mentionFontStates.clear();

		for (Map.Entry<Widget, MentionFontState> entry : fontsToRestore.entrySet()) {
			final Widget widget = entry.getKey();
			final MentionFontState state = entry.getValue();
			if (widget == null || state == null || widget.getFontId() != state.appliedFontId) {
				continue;
			}

			if (state.originalFontId != state.appliedFontId) {
				widget.setFontId(state.originalFontId);
			}
		}
	}

	private RenderedTextWidget findRenderedWidgetForMessage(TaggedMessage message, List<RenderedTextWidget> widgets,
			RenderedBodyIndex bodyIndex, RenderedRowIndex rowIndex, Set<Widget> usedWidgets, Surface surface) {
		if (message == null || widgets == null || bodyIndex == null || rowIndex == null || usedWidgets == null) {
			return null;
		}

		final String needle = message.getOriginalMessage();
		if (needle == null || needle.isEmpty()) {
			return null;
		}

		final List<RenderedTextWidget> exactCandidates = bodyIndex.candidates(needle);
		final RenderedTextWidget exactMatch = selectRenderedBodyCandidate(
				message, exactCandidates, rowIndex, widgets, usedWidgets);
		if (exactMatch != null || !isPrivateMessage(message) || needle.trim().isEmpty()) {
			return exactMatch;
		}

		/*
		 * Private-only compatibility fallback.
		 *
		 * The case-folded index narrows lookup to Widgets whose complete semantic
		 * body differs only by character case. Candidate order remains native
		 * collection order, and normal sender-confirmed/body-fallback selection is
		 * reused unchanged.
		 */
		final List<RenderedTextWidget> foldedCandidates = bodyIndex.candidatesIgnoreCase(needle);
		final RenderedTextWidget foldedMatch = selectRenderedBodyCandidate(
				message, foldedCandidates, rowIndex, widgets, usedWidgets);
		return foldedMatch;
	}

	/*
	 * Select one unused exact-body candidate using sender-confirmed ownership.
	 *
	 * Player-authored rows never fall back to body text alone.
	 */
	private RenderedTextWidget selectRenderedBodyCandidate(
			TaggedMessage message, List<RenderedTextWidget> candidates,
			RenderedRowIndex rowIndex, List<RenderedTextWidget> allWidgets, Set<Widget> usedWidgets) {
		if (message == null || candidates == null || candidates.isEmpty() || rowIndex == null
				|| allWidgets == null || usedWidgets == null) {
			return null;
		}

		final String sender = message.getCanonicalSender();
		final boolean senderKnown = sender != null && !sender.isEmpty();
		RenderedTextWidget bodyFallback = null;

		for (RenderedTextWidget rendered : candidates) {
			if (rendered == null || rendered.widget == null
					|| usedWidgets.contains(rendered.widget) || rendered.widget.isHidden()) {
				continue;
			}

			if (!senderKnown && bodyFallback == null) {
				bodyFallback = rendered;
			}

			if (senderKnown && hasRenderedSenderOnRow(rendered, message, rowIndex, allWidgets)) {
				return rendered;
			}
		}

		/*
		 * Never let a later player-authored message claim another sender's row only
		 * because the visible body text is identical.
		 */
		return senderKnown
				? null
				: bodyFallback;
	}

	/*
	 * Confirm sender ownership using same-row candidates when possible.
	 *
	 * The indexed path uses pass-local bounds and revalidates only a sender match.
	 * If that cannot confirm ownership, the complete semantic Widget list remains
	 * the live reconstruction fallback.
	 */
	private boolean hasRenderedSenderOnRow(RenderedTextWidget messageWidget, TaggedMessage message,
			RenderedRowIndex rowIndex, List<RenderedTextWidget> widgets) {
		if (messageWidget == null || messageWidget.widget == null || message == null
				|| rowIndex == null || widgets == null) {
			return false;
		}

		final Rectangle messageBounds = messageWidget.bounds;
		final String sender = message.getCanonicalSender();
		if (messageBounds == null || sender == null || sender.isEmpty()) {
			return false;
		}

		final List<RenderedTextWidget> indexedCandidates = rowIndex.candidates(messageBounds.y);
		if (hasRenderedSenderInIndexedCandidates(messageWidget, messageBounds, sender, indexedCandidates)) {
			return true;
		}

		/*
		 * The pass-local row index handles the normal path without repeated Widget
		 * reads. Retain the original full live scan only as a reconstruction safety
		 * fallback when the indexed snapshot cannot confirm ownership.
		 */
		return hasRenderedSenderInLiveCandidates(messageWidget, sender, widgets);
	}

	private static boolean hasRenderedSenderInIndexedCandidates(
			RenderedTextWidget messageWidget, Rectangle messageBounds,
			String sender, List<RenderedTextWidget> candidates) {
		if (messageWidget == null || messageBounds == null || sender == null
				|| sender.isEmpty() || candidates == null || candidates.isEmpty()) {
			return false;
		}

		for (RenderedTextWidget candidate : candidates) {
			if (candidate == null || candidate.widget == null || candidate.widget == messageWidget.widget
					|| candidate.bounds == null || candidate.bounds.x > messageBounds.x) {
				continue;
			}
			if (indexOfName(candidate.semanticText, sender) < 0) {
				continue;
			}

			final Rectangle liveMessageBounds = messageWidget.widget.getBounds();
			final Rectangle liveCandidateBounds = candidate.widget.getBounds();
			if (liveMessageBounds != null && liveCandidateBounds != null
					&& liveCandidateBounds.y == liveMessageBounds.y
					&& liveCandidateBounds.x <= liveMessageBounds.x
					&& !candidate.widget.isHidden()) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasRenderedSenderInLiveCandidates(
			RenderedTextWidget messageWidget, String sender, List<RenderedTextWidget> candidates) {
		if (messageWidget == null || messageWidget.widget == null || sender == null
				|| sender.isEmpty() || candidates == null || candidates.isEmpty()) {
			return false;
		}

		final Rectangle messageBounds = messageWidget.widget.getBounds();
		if (messageBounds == null || messageBounds.width <= 0 || messageBounds.height <= 0) {
			return false;
		}

		for (RenderedTextWidget candidate : candidates) {
			if (candidate == null || candidate.widget == null
					|| candidate.widget == messageWidget.widget || candidate.widget.isHidden()) {
				continue;
			}

			final Rectangle candidateBounds = candidate.widget.getBounds();
			if (candidateBounds == null || candidateBounds.width <= 0 || candidateBounds.height <= 0
					|| candidateBounds.y != messageBounds.y || candidateBounds.x > messageBounds.x) {
				continue;
			}

			if (indexOfName(candidate.semanticText, sender) >= 0) {
				return true;
			}
		}

		return false;
	}

	/*
	 * Resolve the physical message-body widget for one semantic TaggedMessage.
	 *
	 * This exposes the same sender-aware association used by clickable reference
	 * layout so other RuneTags rendering layers do not maintain a second,
	 * potentially divergent widget-matching implementation.
	 *
	 * Widget ownership remains local to the caller's physical surface pass through
	 * the supplied usedWidgets set.
	 */
	public Widget findRenderedMessageWidget(TaggedMessage message, List<Widget> widgets, Set<Widget> usedWidgets) {
		if (message == null || widgets == null || usedWidgets == null) {
			return null;
		}

		return findWidgetForMessage(message, widgets, usedWidgets);
	}

	/*
	 * Find the rendered widget containing the semantic message body.
	 *
	 * The known-good live matcher remains authoritative. Private chat gets one
	 * narrow compatibility retry because RuneScape/RuneLite may canonicalize the
	 * local account-name casing after the ChatMessage was recorded
	 * (for example, "@santa" -> "@Santa").
	 */
	private Widget findWidgetForMessage(TaggedMessage message, List<Widget> widgets, Set<Widget> usedWidgets) {
		final Widget exactMatch = findWidgetForMessage(message, widgets, usedWidgets, false);

		if (exactMatch != null || !isPrivateMessage(message)) {
			return exactMatch;
		}

		/*
		 * Private-only fallback. This remains an exact whole-body comparison;
		 * character case is the only allowed difference.
		 */
		return findWidgetForMessage(message, widgets, usedWidgets, true);
	}

	private Widget findWidgetForMessage(TaggedMessage message,
			List<Widget> widgets, Set<Widget> usedWidgets, boolean ignoreCase) {
		if (message == null || widgets == null || usedWidgets == null) {
			return null;
		}

		final String needle = message.getOriginalMessage();
		if (needle == null || needle.isEmpty()) {
			return null;
		}

		final boolean whitespaceOnly = needle.trim().isEmpty();
		final String sender = message.getCanonicalSender();
		final boolean senderKnown = sender != null && !sender.isEmpty();
		Widget bodyFallback = null;

		for (Widget widget : widgets) {
			if (widget == null || usedWidgets.contains(widget) || widget.isHidden()) {
				continue;
			}

			final String raw = widget.getText();
			if (raw == null || raw.isEmpty()) {
				continue;
			}

			final String semantic = ChatText.toSemanticPlain(raw);
			final boolean bodyMatches;

			if (whitespaceOnly) {
				bodyMatches = semantic != null && !semantic.isEmpty() && semantic.trim().isEmpty();
			} else if (ignoreCase) {
				bodyMatches = semantic != null && semantic.equalsIgnoreCase(needle);
			} else {
				bodyMatches = semantic != null && semantic.equals(needle);
			}

			if (!bodyMatches) {
				continue;
			}

			if (!senderKnown && bodyFallback == null) {
				bodyFallback = widget;
			}

			if (senderKnown && hasSenderOnRow(widget, message, widgets)) {
				return widget;
			}
		}

		return senderKnown
				? null
				: bodyFallback;
	}

	/*
	 * Verify that a candidate message body shares its rendered row with the
	 * expected TaggedMessage sender.
	 *
	 * This disambiguates whitespace-only message bodies without treating every
	 * ordinary widget containing a space as a match.
	 */
	private boolean hasSenderOnRow(Widget messageWidget, TaggedMessage message, List<Widget> widgets) {
		if (messageWidget == null || message == null || widgets == null) {
			return false;
		}

		final Rectangle messageBounds = messageWidget.getBounds();
		final String sender = message.getCanonicalSender();
		if (messageBounds == null || sender == null || sender.isEmpty()) {
			return false;
		}

		for (Widget candidate : widgets) {
			if (candidate == null || candidate == messageWidget || candidate.isHidden()) {
				continue;
			}

			final Rectangle candidateBounds = candidate.getBounds();
			if (candidateBounds == null || candidateBounds.width <= 0 || candidateBounds.height <= 0) {
				continue;
			}

			/*
			 * Sender and body must occupy the same rendered chat row.
			 */
			if (!sameRenderedRow(candidate, messageWidget)) {
				continue;
			}

			/*
			 * The sender begins at or to the left of the message body.
			 */
			if (candidateBounds.x > messageBounds.x) {
				continue;
			}

			final String rawCandidateText = candidate.getText();
			if (rawCandidateText == null || rawCandidateText.isEmpty()) {
				continue;
			}

			final String semanticCandidateText = ChatText.toSemanticPlain(rawCandidateText);
			if (indexOfName(semanticCandidateText, sender) >= 0) {
				return true;
			}
		}

		return false;
	}

	/*
	 * Layout explicit @tags and recognized ordinary-name mentions inside the
	 * semantic message body.
	 *
	 * A RuneScape chat message may occupy multiple visual rows while remaining
	 * one Widget. Reference geometry must therefore be resolved against the
	 * widget's actual wrapping rather than treating the complete text as one
	 * horizontal line.
	 *
	 * One PlayerReference may legitimately produce more than one physical
	 * ReferenceHitbox if the reference itself crosses a visual line break.
	 */
	private void layoutMessage(RenderedTextWidget renderedWidget, TaggedMessage message, Surface surface,
			List<ReferenceHitbox> output, int horizontalOffset, int verticalOffset) {
		final Widget widget = renderedWidget != null
				? renderedWidget.widget
				: null;

		if (widget == null || message == null || message.getReferences() == null || message.getReferences().isEmpty()) {
			return;
		}

		final FontTypeFace font = widget.getFont();
		final Rectangle widgetBounds = offsetBounds(widget.getBounds(), horizontalOffset, verticalOffset);
		final String rawWidgetText = renderedWidget.rawText;
		final String semanticWidgetText = renderedWidget.semanticText;
		if (font == null || widgetBounds == null || widgetBounds.width <= 0 || widgetBounds.height <= 0
				|| rawWidgetText == null || semanticWidgetText == null) {
			return;
		}

		final String originalMessage = message.getOriginalMessage();
		if (originalMessage == null || originalMessage.isEmpty()) {
			return;
		}

		int messageStart = semanticWidgetText.indexOf(originalMessage);
		if (messageStart < 0 && isPrivateMessage(message)) {
			messageStart = indexOfIgnoreCase(semanticWidgetText, originalMessage);
		}
		if (messageStart < 0) {
			return;
		}

		final MessageMarkupMap map = MessageMarkupMap.create(rawWidgetText);
		if (!map.matchesPlain(semanticWidgetText)) {
			return;
		}

		final List<WrappedLine> wrappedLines = wrapSemanticLines(
				widget, rawWidgetText, semanticWidgetText, map, font, widgetBounds);
		if (wrappedLines.isEmpty()) {
			return;
		}

		for (PlayerReference reference : message.getReferences()) {
			if (reference == null || !ChatInteractionPolicy.isClickable(reference, config)) {
				continue;
			}

			final int semanticStart = messageStart + reference.getStartOffset();
			final int semanticEnd = messageStart + reference.getEndOffset();
			if (semanticStart < 0 || semanticEnd <= semanticStart || semanticEnd > semanticWidgetText.length()) {
				continue;
			}

			addWrappedSemanticHitboxes(widget, rawWidgetText, semanticWidgetText, map, font, widgetBounds, wrappedLines,
					semanticStart, semanticEnd, message.getId(), reference, surface, output);
		}
	}

	/*
	 * Lays out the rendered sender as an interaction-only SENDER reference.
	 *
	 * SENDER references are not added to TaggedMessage.references and do not affect
	 * mention processing.
	 */
	private void layoutSender(RenderedTextWidget messageWidget, TaggedMessage message, Surface surface,
			List<RenderedTextWidget> textWidgets, RenderedRowIndex rowIndex, List<ReferenceHitbox> output) {
		final String sender = message.getCanonicalSender();
		if (sender == null || sender.isEmpty()) {
			return;
		}

		final PlayerReference senderReference = PlayerReference.builder().rawText(sender).normalizedToken(sender)
				.lookupName(sender).startOffset(0).endOffset(sender.length()).type(ReferenceType.SENDER)
				.locallyResolved(false).identity(null).chatType(message.getType()).build();
		if (!ChatInteractionPolicy.isClickable(senderReference, config)) {
			return;
		}

		switch (surface) {
			case CHATBOX:
				/*
				 * Locates the normal-chatbox sender Widget on the body's rendered row and creates
				 * a hitbox for the account name.
				 */
				layoutChatboxSender(messageWidget, message, senderReference, surface, textWidgets, rowIndex, output);
				break;
			case SPLIT_PRIVATE:
				layoutSplitPrivateSender(
						messageWidget, message, senderReference, surface, textWidgets, rowIndex, output);
				break;
			default:
				break;
		}
	}

	private void layoutChatboxSender(
			RenderedTextWidget messageWidget, TaggedMessage message, PlayerReference senderReference, Surface surface,
			List<RenderedTextWidget> textWidgets, RenderedRowIndex rowIndex, List<ReferenceHitbox> output) {
		if (messageWidget == null || messageWidget.widget == null || rowIndex == null) {
			return;
		}

		final String sender = senderReference.getLookupName();
		final RenderedSenderMatch senderMatch = findChatboxSender(messageWidget, sender, textWidgets, rowIndex);
		if (senderMatch == null || senderMatch.rendered == null || senderMatch.rendered.widget == null) {
			return;
		}

		final int senderEnd = senderMatch.semanticStart + sender.length();
		addSemanticHitbox(senderMatch.rendered.widget, senderMatch.rendered.rawText, senderMatch.rendered.semanticText,
				senderMatch.semanticStart, senderEnd, message.getId(), senderReference, surface, output);
	}

	/*
	 * Resolves the normal-chatbox sender Widget shared by sender interaction
	 * and Favorite sender coloring.
	 */
	private RenderedSenderMatch findChatboxSender(RenderedTextWidget messageWidget, String sender,
			List<RenderedTextWidget> textWidgets, RenderedRowIndex rowIndex) {
		if (messageWidget == null || messageWidget.widget == null || sender == null || sender.isEmpty()
				|| textWidgets == null || rowIndex == null || messageWidget.bounds == null) {
			return null;
		}

		RenderedTextWidget senderWidget = null;
		int senderStart = -1;
		int bestCandidateX = Integer.MIN_VALUE;

		final List<RenderedTextWidget> indexedCandidates = rowIndex.candidates(messageWidget.bounds.y);
		for (RenderedTextWidget candidate : indexedCandidates) {
			if (candidate == null || candidate.widget == null || candidate.widget == messageWidget.widget
					|| candidate.bounds == null || candidate.bounds.x > messageWidget.bounds.x) {
				continue;
			}

			final int candidateSenderStart = indexOfName(candidate.semanticText, sender);
			if (candidateSenderStart < 0 || candidate.bounds.x <= bestCandidateX) {
				continue;
			}

			senderWidget = candidate;
			senderStart = candidateSenderStart;
			bestCandidateX = candidate.bounds.x;
		}

		if (senderWidget != null && senderStart >= 0 && isLiveSenderCandidate(senderWidget, messageWidget)) {
			return new RenderedSenderMatch(senderWidget, senderStart);
		}

		senderWidget = null;
		senderStart = -1;
		bestCandidateX = Integer.MIN_VALUE;

		final Rectangle liveMessageBounds = messageWidget.widget.getBounds();
		if (liveMessageBounds == null) {
			return null;
		}

		for (RenderedTextWidget candidate : textWidgets) {
			if (candidate == null || candidate.widget == null || candidate.widget == messageWidget.widget
					|| candidate.widget.isHidden()) {
				continue;
			}

			final Rectangle candidateBounds = candidate.widget.getBounds();
			if (candidateBounds == null || candidateBounds.width <= 0 || candidateBounds.height <= 0
					|| candidateBounds.y != liveMessageBounds.y || candidateBounds.x > liveMessageBounds.x) {
				continue;
			}

			final int candidateSenderStart = indexOfName(candidate.semanticText, sender);
			if (candidateSenderStart < 0 || candidateBounds.x <= bestCandidateX) {
				continue;
			}

			senderWidget = candidate;
			senderStart = candidateSenderStart;
			bestCandidateX = candidateBounds.x;
		}

		return senderWidget != null && senderStart >= 0
				? new RenderedSenderMatch(senderWidget, senderStart)
				: null;
	}

	private static boolean isLiveSenderCandidate(RenderedTextWidget senderWidget, RenderedTextWidget messageWidget) {
		if (senderWidget == null || senderWidget.widget == null || messageWidget == null
				|| messageWidget.widget == null || senderWidget.widget.isHidden()) {
			return false;
		}

		final Rectangle senderBounds = senderWidget.widget.getBounds();
		final Rectangle messageBounds = messageWidget.widget.getBounds();
		return senderBounds != null && messageBounds != null
				&& senderBounds.width > 0 && senderBounds.height > 0
				&& senderBounds.y == messageBounds.y && senderBounds.x <= messageBounds.x;
	}

	/*
	 * Locates the split-private sender/prefix Widget on the same rendered row
	 * and to the left of the message body.
	 *
	 * Matching does not depend on fixed child indices, account-name length,
	 * or message X position.
	 */
	private void layoutSplitPrivateSender(
			RenderedTextWidget messageWidget, TaggedMessage message, PlayerReference senderReference,
			Surface surface, List<RenderedTextWidget> textWidgets,
			RenderedRowIndex rowIndex, List<ReferenceHitbox> output) {
		if (messageWidget == null || messageWidget.widget == null || rowIndex == null || messageWidget.bounds == null) {
			return;
		}

		final String sender = senderReference.getLookupName();
		if (sender == null || sender.isEmpty()) {
			return;
		}

		RenderedTextWidget senderWidget = null;
		int senderStart = -1;

		final List<RenderedTextWidget> indexedCandidates = rowIndex.candidates(messageWidget.bounds.y);
		for (RenderedTextWidget candidate : indexedCandidates) {
			if (candidate == null || candidate.widget == null || candidate.widget == messageWidget.widget
					|| candidate.bounds == null || candidate.bounds.x > messageWidget.bounds.x) {
				continue;
			}

			final int candidateSenderStart = indexOfName(candidate.semanticText, sender);
			if (candidateSenderStart < 0 || !isLiveSenderCandidate(candidate, messageWidget)) {
				continue;
			}

			senderWidget = candidate;
			senderStart = candidateSenderStart;
			break;
		}

		if (senderWidget == null) {
			final Rectangle liveMessageBounds = messageWidget.widget.getBounds();
			if (liveMessageBounds == null) {
				return;
			}

			for (RenderedTextWidget candidate : textWidgets) {
				if (candidate == null || candidate.widget == null
						|| candidate.widget == messageWidget.widget || candidate.widget.isHidden()) {
					continue;
				}

				final Rectangle candidateBounds = candidate.widget.getBounds();
				if (candidateBounds == null || candidateBounds.width <= 0 || candidateBounds.height <= 0
						|| candidateBounds.y != liveMessageBounds.y || candidateBounds.x > liveMessageBounds.x) {
					continue;
				}

				final int candidateSenderStart = indexOfName(candidate.semanticText, sender);
				if (candidateSenderStart < 0) {
					continue;
				}

				senderWidget = candidate;
				senderStart = candidateSenderStart;
				break;
			}
		}

		if (senderWidget == null || senderWidget.widget == null || senderStart < 0) {
			return;
		}

		final int senderEnd = senderStart + sender.length();
		addSemanticHitbox(senderWidget.widget, senderWidget.rawText, senderWidget.semanticText, senderStart, senderEnd,
				message.getId(), senderReference, surface, output);
	}

	private int resolveChatboxBodyXOffset(TaggedMessage message, RenderedTextWidget renderedWidget) {
		if (message == null || renderedWidget == null || renderedWidget.widget == null) {
			return CHATBOX_X_PENDING;
		}

		final Rectangle bounds = renderedWidget.widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return CHATBOX_X_PENDING;
		}

		final long messageId = message.getId();
		final int currentX = bounds.x;
		final Integer acceptedX = acceptedChatboxBodyX.get(messageId);

		/*
		 * A temporary reconstructed X keeps rendering at the last accepted position.
		 */
		if (acceptedX != null && acceptedX == currentX) {
			pendingChatboxBodyX.remove(messageId);
			return 0;
		}

		final Integer pendingX = pendingChatboxBodyX.get(messageId);
		if (pendingX != null && pendingX == currentX) {
			pendingChatboxBodyX.remove(messageId);
			acceptedChatboxBodyX.put(messageId, currentX);
			return 0;
		}

		pendingChatboxBodyX.put(messageId, currentX);
		return acceptedX != null
				? acceptedX - currentX
				: CHATBOX_X_PENDING;
	}

	private int resolveChatboxBodyYOffset(
			TaggedMessage message, RenderedTextWidget renderedWidget, Widget surfaceWidget) {
		final ChatboxBodyState currentState = chatboxBodyState(renderedWidget, surfaceWidget);
		if (message == null || currentState == null) {
			return 0;
		}

		final long messageId = message.getId();
		final ChatboxBodyState stableState = stableChatboxBodyState(currentState);
		final ChatboxBodyState acceptedState = acceptedChatboxBodyRows.get(messageId);
		if (acceptedState == null) {
			acceptedChatboxBodyRows.put(messageId, stableState);
			return stableState.contentY - currentState.contentY;
		}

		/*
		 * Keep correcting a stale canvas Y only while RuneScape still reports the
		 * same physical row identity. A genuine OriginalY/RelativeY row move is
		 * accepted immediately so the overlay follows the chat text without lag.
		 */
		if (acceptedState.rowKey != null && acceptedState.rowKey.equals(currentState.rowKey)) {
			return acceptedState.contentY - currentState.contentY;
		}

		acceptedChatboxBodyRows.put(messageId, stableState);
		return stableState.contentY - currentState.contentY;
	}

	private ChatboxBodyState chatboxBodyState(RenderedTextWidget renderedWidget, Widget surfaceWidget) {
		if (renderedWidget == null || renderedWidget.widget == null || surfaceWidget == null) {
			return null;
		}

		final Widget widget = renderedWidget.widget;
		final Rectangle bounds = widget.getBounds();
		final Rectangle surfaceBounds = surfaceWidget.getBounds();
		final RowKey rowKey = RowKey.of(widget);
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0
				|| surfaceBounds == null || surfaceBounds.height <= 0 || rowKey == null) {
			return null;
		}

		final int contentY = bounds.y - surfaceBounds.y + surfaceWidget.getScrollY();
		return new ChatboxBodyState(rowKey, contentY);
	}

	private static ChatboxBodyState stableChatboxBodyState(ChatboxBodyState state) {
		if (state == null || state.rowKey == null) {
			return state;
		}

		return new ChatboxBodyState(state.rowKey, state.rowKey.originalY);
	}

	private static boolean sameRenderedRow(Widget left, Widget right) {
		if (left == null || right == null) {
			return false;
		}

		final Rectangle leftBounds = left.getBounds();
		final Rectangle rightBounds = right.getBounds();
		return leftBounds != null && rightBounds != null && leftBounds.y == rightBounds.y;
	}

	private static Rectangle offsetBounds(Rectangle bounds, int horizontalOffset, int verticalOffset) {
		if (bounds == null || (horizontalOffset == 0 && verticalOffset == 0)) {
			return bounds;
		}

		final Rectangle adjusted = new Rectangle(bounds);
		adjusted.x += horizontalOffset;
		adjusted.y += verticalOffset;
		return adjusted;
	}

	/*
	 * Prunes CHATBOX geometry state only for messages no longer retained by RuneTags.
	 */
	private void pruneChatboxBodyXState(Set<Long> retainedMessageIds) {
		if (acceptedChatboxBodyX.isEmpty() && pendingChatboxBodyX.isEmpty() && acceptedChatboxBodyRows.isEmpty()) {
			return;
		}

		if (retainedMessageIds == null || retainedMessageIds.isEmpty()) {
			clearChatboxBodyXState();
			return;
		}

		acceptedChatboxBodyX.keySet().removeIf(messageId -> !retainedMessageIds.contains(messageId));
		pendingChatboxBodyX.keySet().removeIf(messageId -> !retainedMessageIds.contains(messageId));
		acceptedChatboxBodyRows.keySet().removeIf(messageId -> !retainedMessageIds.contains(messageId));
	}

	public void clearChatboxBodyXState() {
		acceptedChatboxBodyX.clear();
		pendingChatboxBodyX.clear();
		acceptedChatboxBodyRows.clear();
		lastGeometryPruneRevision = Long.MIN_VALUE;
	}

	/*
	 * Converts one semantic span into physical hitbox fragments across its wrapped
	 * visual lines.
	 */
	private void addSemanticHitbox(
			Widget widget, String rawWidgetText, String semanticWidgetText, int semanticStart, int semanticEnd,
			long messageId, PlayerReference reference, Surface surface, List<ReferenceHitbox> output) {
		if (widget == null || rawWidgetText == null || semanticWidgetText == null || semanticStart < 0
				|| semanticEnd <= semanticStart || semanticEnd > semanticWidgetText.length()) {
			return;
		}

		final Rectangle widgetBounds = widget.getBounds();
		final FontTypeFace font = widget.getFont();
		if (widgetBounds == null || widgetBounds.width <= 0 || widgetBounds.height <= 0 || font == null) {
			return;
		}

		final MessageMarkupMap map = MessageMarkupMap.create(rawWidgetText);
		if (!map.matchesPlain(semanticWidgetText)) {
			return;
		}

		final List<WrappedLine> wrappedLines = wrapSemanticLines(
				widget, rawWidgetText, semanticWidgetText, map, font, widgetBounds);
		if (wrappedLines.isEmpty()) {
			return;
		}

		addWrappedSemanticHitboxes(widget, rawWidgetText, semanticWidgetText, map, font, widgetBounds, wrappedLines,
				semanticStart, semanticEnd, messageId, reference, surface, output);
	}

	/*
	 * Measures the visual line count for raw RuneScape text using the supplied font
	 * and body width.
	 *
	 * FontLayoutService uses the same wrapping model before native row construction.
	 */
	public int measureWrappedLineCount(String rawWidgetText, FontTypeFace font, int availableWidth) {
		if (rawWidgetText == null || rawWidgetText.isEmpty() || font == null || availableWidth <= 0) {
			return 1;
		}

		final String semanticWidgetText = ChatText.toSemanticPlain(rawWidgetText);
		if (semanticWidgetText == null || semanticWidgetText.isEmpty()) {
			return 1;
		}

		final MessageMarkupMap map = MessageMarkupMap.create(rawWidgetText);
		if (!map.matchesPlain(semanticWidgetText)) {
			return 1;
		}

		/*
		 * PRE construction has no physical Widget; wrapSemanticLines() uses only
		 * the supplied bounds width.
		 */
		final Rectangle measurementBounds = new Rectangle(0, 0, availableWidth, 1);
		final List<WrappedLine> lines = wrapSemanticLines(
				null, rawWidgetText, semanticWidgetText, map, font, measurementBounds);
		return Math.max(1, lines.size());
	}

	/*
	 * Whether non-clickable local-token geometry can contribute an overlay
	 * decoration to the current frame.
	 */
	private boolean shouldLayoutLocalHighlights() {
		if (config.underlineMentions()) {
			return true;
		}

		if (!config.highlightBackground()) {
			return false;
		}

		return config.selfBackgroundColor() != null && config.selfBackgroundColor().getAlpha() > 0;
	}

	/*
	 * Layout Unique Highlight / normalized-account-name local matches which are
	 * not already represented by normal PlayerReference objects.
	 *
	 * The physical message Widget has already been resolved by layoutSurface().
	 */
	private void layoutLocalHighlight(RenderedTextWidget renderedWidget, TaggedMessage message, Surface surface,
			List<LocalHighlight> output, int horizontalOffset, int verticalOffset) {
		final Widget widget = renderedWidget != null
				? renderedWidget.widget
				: null;
		if (widget == null || message == null || output == null) {
			return;
		}

		final LocalMentionMatch localMatch = message.getLocalMentionMatch();
		if (!shouldDrawLocalToken(localMatch)) {
			return;
		}

		final String rawWidgetText = renderedWidget.rawText;
		if (rawWidgetText == null || rawWidgetText.isEmpty()) {
			return;
		}

		final String semanticWidgetText = renderedWidget.semanticText;
		final String originalMessage = message.getOriginalMessage();
		if (semanticWidgetText == null || originalMessage == null || originalMessage.isEmpty()) {
			return;
		}

		int messageStart = semanticWidgetText.indexOf(originalMessage);
		if (messageStart < 0 && isPrivateMessage(message)) {
			messageStart = indexOfIgnoreCase(semanticWidgetText, originalMessage);
		}
		if (messageStart < 0) {
			return;
		}

		final String token = localMatch.getMatchedToken();
		if (token == null || token.trim().isEmpty()) {
			return;
		}

		/*
		 * Mirror MessageFormatter's token semantics exactly.
		 */
		final String loweredMessage = originalMessage.toLowerCase(Locale.ROOT);
		final String loweredToken = token.toLowerCase(Locale.ROOT);
		int from = 0;
		while (from <= loweredMessage.length() - loweredToken.length()) {
			final int start = loweredMessage.indexOf(loweredToken, from);
			if (start < 0) {
				break;
			}

			final int end = start + loweredToken.length();
			if (hasBoundaries(loweredMessage, start, end) && !overlapsPlayerReference(start, end, message)) {
				final List<Rectangle> rectangles = layoutSemanticSpan(widget, rawWidgetText, semanticWidgetText,
						messageStart + start, messageStart + end, horizontalOffset, verticalOffset);
				for (Rectangle bounds : rectangles) {
					if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
						continue;
					}
					output.add(new LocalHighlight(message.getId(), bounds, surface));
				}
			}

			/*
			 * Preserve MessageFormatter's overlapping-search behavior.
			 */
			from = start + 1;
		}
	}

	/*
	 * Only local matches without a PlayerReference require additional overlay geometry.
	 */
	private static boolean shouldDrawLocalToken(LocalMentionMatch localMatch) {
		if (localMatch == null || !localMatch.isMatchesLocalPlayer() || localMatch.getReason() == null) {
			return false;
		}

		return localMatch.getReason() == MatchReason.UNIQUE_HIGHLIGHT
				|| localMatch.getReason() == MatchReason.NORMALIZED_ACCOUNT_NAME;
	}

	private static boolean overlapsPlayerReference(int start, int end, TaggedMessage message) {
		if (message == null || message.getReferences() == null) {
			return false;
		}

		for (PlayerReference reference : message.getReferences()) {
			if (reference == null) {
				continue;
			}

			if (start < reference.getEndOffset() && end > reference.getStartOffset()) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBoundaries(String text, int start, int end) {
		final boolean leftBoundary = start == 0 || !isNameChar(text.charAt(start - 1));
		final boolean rightBoundary = end == text.length() || !isNameChar(text.charAt(end));
		return leftBoundary && rightBoundary;
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '-';
	}

	/*
	 * Resolves one semantic span into physical rectangles across the Widget's
	 * wrapped visual lines.
	 */
	public List<Rectangle> layoutSemanticSpan(Widget widget, int semanticStart, int semanticEnd) {
		final List<Rectangle> output = new ArrayList<>();
		if (widget == null || semanticStart < 0 || semanticEnd <= semanticStart) {
			return output;
		}

		final String rawWidgetText = widget.getText();
		if (rawWidgetText == null) {
			return output;
		}

		final String semanticWidgetText = ChatText.toSemanticPlain(rawWidgetText);
		return layoutSemanticSpan(widget, rawWidgetText, semanticWidgetText, semanticStart, semanticEnd, 0, 0);
	}

	/*
	 * Cached-text form used by the render pass. Bounds and FontTypeFace remain
	 * live reads so no reconstruction geometry is frozen in the semantic cache.
	 */
	private List<Rectangle> layoutSemanticSpan(Widget widget, String rawWidgetText, String semanticWidgetText,
			int semanticStart, int semanticEnd, int horizontalOffset, int verticalOffset) {
		final List<Rectangle> output = new ArrayList<>();

		if (widget == null || rawWidgetText == null || semanticWidgetText == null
				|| semanticStart < 0 || semanticEnd <= semanticStart) {
			return output;
		}

		final Rectangle widgetBounds = offsetBounds(widget.getBounds(), horizontalOffset, verticalOffset);
		final FontTypeFace font = widget.getFont();
		if (widgetBounds == null || widgetBounds.width <= 0 || widgetBounds.height <= 0 || font == null) {
			return output;
		}
		if (semanticEnd > semanticWidgetText.length()) {
			return output;
		}

		final MessageMarkupMap map = MessageMarkupMap.create(rawWidgetText);
		if (!map.matchesPlain(semanticWidgetText)) {
			return output;
		}

		final List<WrappedLine> wrappedLines = wrapSemanticLines(
				widget, rawWidgetText, semanticWidgetText, map, font, widgetBounds);
		if (wrappedLines.isEmpty()) {
			return output;
		}

		final int physicalLineHeight = resolvePhysicalLineHeight(widget, widgetBounds, wrappedLines.size());
		for (int lineIndex = 0; lineIndex < wrappedLines.size(); lineIndex++) {
			final WrappedLine line = wrappedLines.get(lineIndex);
			final int segmentStart = Math.max(semanticStart, line.start);
			final int segmentEnd = Math.min(semanticEnd, line.end);
			if (segmentStart >= segmentEnd) {
				continue;
			}

			/*
			 * Include leading raw markup on the first visual line so rendered images
			 * contribute their physical width.
			 */
			final int rawLineStart = line.start == 0
					? 0
					: map.rawBoundary(line.start);
			final int rawSegmentStart = map.rawBoundary(segmentStart);
			final int rawSegmentEnd = map.rawBoundary(segmentEnd);
			if (rawLineStart < 0 || rawSegmentStart < rawLineStart
					|| rawSegmentEnd < rawSegmentStart || rawSegmentEnd > rawWidgetText.length()) {
				continue;
			}

			final int prefixWidth = font.getTextWidth(rawWidgetText.substring(rawLineStart, rawSegmentStart));
			final int segmentWidth = Math.max(1, font.getTextWidth(
					rawWidgetText.substring(rawSegmentStart, rawSegmentEnd)));
			final int lineOriginX = alignedLineX(widget, widgetBounds, line.width);
			final int lineY = widgetBounds.y + (lineIndex * physicalLineHeight);
			output.add(new Rectangle(lineOriginX + prefixWidth, lineY, segmentWidth, physicalLineHeight));
		}
		return output;
	}

	/*
	 * Add the physical pieces of one semantic span.
	 *
	 * The span is intersected independently with every visual line occupied by
	 * the widget. If a name crosses a wrap boundary, each visible piece receives
	 * its own ReferenceHitbox pointing to the same PlayerReference.
	 */
	private void addWrappedSemanticHitboxes(
			Widget widget, String rawWidgetText, String semanticWidgetText, MessageMarkupMap map, FontTypeFace font,
			Rectangle widgetBounds, List<WrappedLine> wrappedLines, int semanticStart, int semanticEnd,
			long messageId, PlayerReference reference, Surface surface, List<ReferenceHitbox> output) {
		if (wrappedLines == null || wrappedLines.isEmpty()) {
			return;
		}

		/*
		 * Prefer the live Widget line height and fall back to allocated bounds when
		 * no usable line height is exposed.
		 */
		final int physicalLineHeight = resolvePhysicalLineHeight(widget, widgetBounds, wrappedLines.size());
		for (int lineIndex = 0; lineIndex < wrappedLines.size(); lineIndex++) {
			final WrappedLine line = wrappedLines.get(lineIndex);
			final int segmentStart = Math.max(semanticStart, line.start);
			final int segmentEnd = Math.min(semanticEnd, line.end);
			if (segmentStart >= segmentEnd) {
				continue;
			}

			/*
			 * Semantic offset zero excludes leading markup, but geometry must include rendered
			 * prefixes such as <img=...>. Continuation lines begin at their semantic raw boundary.
			 */
			final int rawLineStart = line.start == 0
					? 0
					: map.rawBoundary(line.start);
			final int rawSegmentStart = map.rawBoundary(segmentStart);
			final int rawSegmentEnd = map.rawBoundary(segmentEnd);
			if (rawLineStart < 0
					|| rawSegmentStart < rawLineStart
					|| rawSegmentEnd < rawSegmentStart
					|| rawSegmentEnd > rawWidgetText.length()) {
				continue;
			}

			final int prefixWidth = font.getTextWidth(rawWidgetText.substring(rawLineStart, rawSegmentStart));
			final int segmentWidth = Math.max(1, font.getTextWidth(
					rawWidgetText.substring(rawSegmentStart, rawSegmentEnd)));

			/*
			 * Resolve horizontal alignment independently for each visual line.
			 */
			final int lineOriginX = alignedLineX(widget, widgetBounds, line.width);
			final int lineY = widgetBounds.y + (lineIndex * physicalLineHeight);
			final Rectangle bounds = new Rectangle(lineOriginX + prefixWidth, lineY, segmentWidth, physicalLineHeight);
			output.add(new ReferenceHitbox(messageId, bounds, reference, surface));
		}
	}

	/*
	 * Reproduce the visual line ranges used by a RuneScape text widget.
	 *
	 * RuneScape exposes the final widget width and total expanded height but does
	 * not expose an API containing the semantic start/end offset of each rendered
	 * line. Reconstruct those ranges using the same Jagex FontTypeFace used by the
	 * widget.
	 *
	 * Wrapping prefers whitespace. If one unbroken token is wider than the
	 * widget, it falls back to a hard character boundary so layout can still
	 * progress.
	 */
	private List<WrappedLine> wrapSemanticLines(Widget widget, String rawWidgetText, String semanticWidgetText,
			MessageMarkupMap map, FontTypeFace font, Rectangle widgetBounds) {
		final List<WrappedLine> lines = new ArrayList<>();
		if (semanticWidgetText == null || semanticWidgetText.isEmpty() || widgetBounds.width <= 0) {
			return lines;
		}

		final int textLength = semanticWidgetText.length();
		int lineStart = 0;
		while (lineStart < textLength) {
			if (isExplicitLineBreak(semanticWidgetText.charAt(lineStart))) {
				lineStart++;
				continue;
			}

			int cursor = lineStart;
			int lastWhitespace = -1;
			int acceptedEnd = lineStart;
			while (cursor < textLength) {
				final char current = semanticWidgetText.charAt(cursor);
				if (isExplicitLineBreak(current)) {
					acceptedEnd = cursor;
					break;
				}

				if (Character.isWhitespace(current)) {
					lastWhitespace = cursor;
				}

				final int candidateEnd = cursor + 1;
				final int candidateWidth = semanticWidth(rawWidgetText, map, font, lineStart, candidateEnd);
				if (candidateWidth <= widgetBounds.width) {
					acceptedEnd = candidateEnd;
					cursor++;
					continue;
				}

				/*
				 * The candidate no longer fits.
				 *
				 * Prefer the last whitespace belonging to this line. The space
				 * itself remains semantically between the two words but is not
				 * treated as visible leading content on the following line.
				 */
				if (lastWhitespace >= lineStart) {
					acceptedEnd = lastWhitespace;
				} else if (acceptedEnd <= lineStart) {
					/*
					 * One token/character itself is wider than the widget.
					 * Force progress by allowing one semantic character.
					 */
					acceptedEnd = candidateEnd;
				}
				break;
			}

			if (cursor >= textLength) {
				acceptedEnd = textLength;
			}
			if (acceptedEnd < lineStart) {
				acceptedEnd = lineStart;
			}
			/*
			 * Avoid zero-length visual lines caused by unusual whitespace.
			 */
			if (acceptedEnd == lineStart) {
				acceptedEnd = Math.min(textLength, lineStart + 1);
			}

			final int lineWidth = semanticWidth(rawWidgetText, map, font, lineStart, acceptedEnd);
			lines.add(new WrappedLine(lineStart, acceptedEnd, Math.max(0, lineWidth)));

			int nextStart = acceptedEnd;
			while (nextStart < textLength) {
				final char next = semanticWidgetText.charAt(nextStart);
				if (isExplicitLineBreak(next)) {
					nextStart++;
					break;
				}
				/*
				 * RuneScape does not visually preserve the wrapping space at the
				 * start of the following line.
				 */
				if (Character.isWhitespace(next)) {
					nextStart++;
					continue;
				}
				break;
			}

			if (nextStart <= lineStart) {
				nextStart = Math.min(textLength, lineStart + 1);
			}
			lineStart = nextStart;
		}

		/*
		 * Normally the number of reconstructed lines will agree with the widget
		 * height. If the widget reports one physical row, preserving a single
		 * line avoids introducing artificial vertical geometry.
		 */
		if (lines.isEmpty()) {
			lines.add(new WrappedLine(0, textLength, semanticWidth(
					rawWidgetText, map, font, 0, textLength)));
		}

		return lines;
	}

	/*
	 * Resolve the physical height of one rendered text line.
	 *
	 * Prefer the Widget's live line height when available so RuneTags follows
	 * font/layout changes made by RuneScape or other plugins. Fall back to the
	 * Widget's allocated bounds when no useful line height is exposed.
	 */
	private static int resolvePhysicalLineHeight(Widget widget, Rectangle widgetBounds, int lineCount) {
		if (widget != null) {
			final int lineHeight = widget.getLineHeight();
			if (lineHeight > 0) {
				return lineHeight;
			}
		}
		return Math.max(1, widgetBounds.height / Math.max(1, lineCount));
	}

	/*
	 * Measure one semantic range using the actual raw RuneScape markup and the
	 * widget's own font.
	 */
	private static int semanticWidth(String rawWidgetText, MessageMarkupMap map, FontTypeFace font,
			int semanticStart, int semanticEnd) {
		if (semanticStart < 0 || semanticEnd < semanticStart) {
			return 0;
		}

		final int rawStart = map.rawBoundary(semanticStart);
		final int rawEnd = map.rawBoundary(semanticEnd);
		if (rawStart < 0 || rawEnd < rawStart || rawEnd > rawWidgetText.length()) {
			return 0;
		}
		return font.getTextWidth(rawWidgetText.substring(rawStart, rawEnd));
	}

	/*
	 * Resolve horizontal alignment for one visual line rather than the complete
	 * multiline widget.
	 */
	private static int alignedLineX(Widget widget, Rectangle bounds, int lineWidth) {
		switch (widget.getXTextAlignment()) {
			case WidgetTextAlignment.CENTER:
				return bounds.x + Math.max(0, (bounds.width - lineWidth) / 2);
			case WidgetTextAlignment.RIGHT:
				return bounds.x + Math.max(0, bounds.width - lineWidth);
			case WidgetTextAlignment.LEFT:
			default:
				return bounds.x;
		}
	}

	/*
	 * Semantic line-break characters which should terminate the current visual
	 * row immediately.
	 */
	private static boolean isExplicitLineBreak(char value) {
		return value == '\n' || value == '\r';
	}

	/*
	 * Find an account name without depending on capitalization or
	 * RuneScape's alternate space characters.
	 *
	 * NBSP and narrow-NBSP replacement is length-preserving, so the returned
	 * offset remains valid against the original semantic text and
	 * MessageMarkupMap.
	 */
	private static int indexOfIgnoreCase(String text, String needle) {
		if (text == null || needle == null || needle.isEmpty() || needle.length() > text.length()) {
			return -1;
		}

		final int lastStart = text.length() - needle.length();
		for (int i = 0; i <= lastStart; i++) {
			if (text.regionMatches(true, i, needle, 0, needle.length())) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfName(String text, String name) {
		if (text == null || text.isEmpty() || name == null || name.isEmpty()) {
			return -1;
		}

		final String comparableText = comparableNameText(text);
		final String comparableName = comparableNameText(name);
		if (comparableName.isEmpty() || comparableName.length() > comparableText.length()) {
			return -1;
		}

		final int maxStart = comparableText.length() - comparableName.length();
		for (int start = 0; start <= maxStart; start++) {
			if (comparableText.regionMatches(true, start, comparableName, 0, comparableName.length())) {
				return start;
			}
		}
		return -1;
	}

	private static String comparableNameText(String value) {
		return value.replace('\u00A0', ' ').replace('\u202F', ' ');
	}

	/*
	 * The split-private PmChat surface must only match private-message
	 * semantic records.
	 */
	private static boolean isPrivateMessage(TaggedMessage message) {
		if (message == null || message.getType() == null) {
			return false;
		}

		final ChatMessageType type = message.getType();
		return type == ChatMessageType.PRIVATECHAT
				|| type == ChatMessageType.MODPRIVATECHAT
				|| type == ChatMessageType.PRIVATECHATOUT;
	}

	/*
	 * Collects pass-local semantic text for eligible rendered Widgets.
	 *
	 * Cache text-bearing Widget membership instead of every descendant.
	 * Native reconstruction or semantic repository revision changes
	 * force rediscovery, while text, visibility, and geometry remain live reads.
	 */
	private void collectRenderedTextWidgets(Widget surfaceWidget, Surface surface, Rectangle visibleBounds,
			List<RenderedTextWidget> output) {
		if (surfaceWidget == null || surface == null || output == null) {
			return;
		}

		final RenderedSurfaceCache cache = renderedSurfaceCache(surface);
		final int topologySignature = surfaceTopologySignature(surfaceWidget);
		final long repositoryRevision = repository.getRevision();
		final boolean requiresRebuild = cache.root != surfaceWidget || cache.dirty
				|| cache.topologySignature != topologySignature || cache.repositoryRevision != repositoryRevision;

		boolean staleMembership = false;
		if (!requiresRebuild) {
			for (CachedRenderedWidget cached : cache.widgets) {
				if (cached == null || cached.widget == null || !isDescendantOrSelf(surfaceWidget, cached.widget)) {
					staleMembership = true;
					break;
				}
			}
		}

		if (requiresRebuild || staleMembership) {
			rebuildRenderedSurfaceCache(cache, surfaceWidget, topologySignature, repositoryRevision);
		}


		for (CachedRenderedWidget cached : cache.widgets) {
			addCachedRenderedTextWidget(cached, surface, visibleBounds, output);
		}
	}

	private void addCachedRenderedTextWidget(CachedRenderedWidget cached, Surface surface, Rectangle visibleBounds,
			List<RenderedTextWidget> output) {
		if (cached == null || cached.widget == null || cached.widget.isHidden()) {
			return;
		}

		final String rawText = cached.widget.getText();
		if (rawText == null || rawText.isEmpty()) {
			return;
		}

		final Rectangle bounds = cached.widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return;
		}

		if (surface == Surface.CHATBOX && visibleBounds != null && !visibleBounds.intersects(bounds)) {
			return;
		}

		if (cached.widget.getFont() == null) {
			return;
		}

		final String semanticText;
		if (rawText.equals(cached.rawText) && cached.semanticText != null) {
			semanticText = cached.semanticText;
		} else {
			semanticText = ChatText.toSemanticPlain(rawText);
			cached.rawText = rawText;
			cached.semanticText = semanticText;
		}

		output.add(new RenderedTextWidget(cached.widget, rawText, semanticText, new Rectangle(bounds)));
	}

	private void rebuildRenderedSurfaceCache(RenderedSurfaceCache cache, Widget surfaceWidget, int topologySignature,
			long repositoryRevision) {
		cache.root = surfaceWidget;
		cache.widgets.clear();

		final Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		discoverRenderedSurfaceWidgets(surfaceWidget, cache.widgets, visited, 0);

		cache.topologySignature = topologySignature;
		cache.repositoryRevision = repositoryRevision;
		cache.dirty = false;
	}

	private void discoverRenderedSurfaceWidgets(
			Widget widget, List<CachedRenderedWidget> output, Set<Widget> visited, int depth) {
		if (widget == null || depth > MAX_WIDGET_DEPTH || !visited.add(widget)) {
			return;
		}

		final String rawText = widget.getText();
		if (rawText != null && !rawText.isEmpty()) {
			final CachedRenderedWidget cached = new CachedRenderedWidget(widget);
			cached.rawText = rawText;
			cached.semanticText = ChatText.toSemanticPlain(rawText);
			output.add(cached);
		}

		discoverRenderedSurfaceChildren(widget.getChildren(), output, visited, depth + 1);
		discoverRenderedSurfaceChildren(widget.getStaticChildren(), output, visited, depth + 1);
		discoverRenderedSurfaceChildren(widget.getNestedChildren(), output, visited, depth + 1);
	}

	private void discoverRenderedSurfaceChildren(
			Widget[] children, List<CachedRenderedWidget> output, Set<Widget> visited, int depth) {
		if (children == null) {
			return;
		}

		for (Widget child : children) {
			discoverRenderedSurfaceWidgets(child, output, visited, depth);
		}
	}

	private RenderedSurfaceCache renderedSurfaceCache(Surface surface) {
		return surface == Surface.SPLIT_PRIVATE
				? splitPrivateRenderedSurfaceCache
				: chatboxRenderedSurfaceCache;
	}

	private static int surfaceTopologySignature(Widget surfaceWidget) {
		if (surfaceWidget == null) {
			return 0;
		}

		int result = System.identityHashCode(surfaceWidget);
		result = 31 * result + widgetArrayIdentitySignature(surfaceWidget.getChildren());
		result = 31 * result + widgetArrayIdentitySignature(surfaceWidget.getStaticChildren());
		result = 31 * result + widgetArrayIdentitySignature(surfaceWidget.getNestedChildren());
		return result;
	}

	private static int widgetArrayIdentitySignature(Widget[] widgets) {
		if (widgets == null || widgets.length == 0) {
			return 0;
		}

		int result = widgets.length;
		for (Widget widget : widgets) {
			result = 31 * result + System.identityHashCode(widget);
		}
		return result;
	}

	private static boolean isDescendantOrSelf(Widget root, Widget widget) {
		for (Widget current = widget; current != null; current = current.getParent()) {
			if (current == root) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Recursively collects rendered text Widgets beneath one physical chat surface.
	 */
	private void collectTextWidgets(Widget widget, List<Widget> output, Set<Widget> visited, int depth) {
		if (widget == null || depth > MAX_WIDGET_DEPTH || !visited.add(widget)) {
			return;
		}

		final String text = widget.getText();
		if (!widget.isHidden() && text != null && !text.isEmpty() && widget.getFont() != null) {
			final Rectangle bounds = widget.getBounds();

			/*
			 * Do not trim rendered text here. RuneScape can represent a real chat message
			 * body as markup containing only whitespace, and the Widget is still required
			 * as the physical row anchor used to locate its sender.
			 */
			if (bounds != null && bounds.width > 0 && bounds.height > 0) {
				output.add(widget);
			}
		}
		collectChildren(widget.getChildren(), output, visited, depth + 1);
		collectChildren(widget.getStaticChildren(), output, visited, depth + 1);
		collectChildren(widget.getNestedChildren(), output, visited, depth + 1);
	}

	private void collectChildren(Widget[] children, List<Widget> output, Set<Widget> visited, int depth) {
		if (children == null) {
			return;
		}

		for (Widget child : children) {
			collectTextWidgets(child, output, visited, depth);
		}
	}
}
