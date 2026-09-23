package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.MatchReason;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.runelite.client.chat.ChatColorType;
import net.runelite.client.util.ColorUtil;

/**
 * Applies RuneTags foreground, underline, and shadow markup to semantic message spans.
 *
 * Existing RuneLite/Jagex markup is preserved. Backgrounds are rendered by ChatReferenceOverlay.
 */
public class MessageFormatter {
	private final Configurations config;
	private final LocalMentionMatcher localMentionMatcher;

	public MessageFormatter(Configurations config, LocalMentionMatcher localMentionMatcher) {
		this.config = config;
		this.localMentionMatcher = localMentionMatcher;
	}

	public String format(TaggedMessage message, String rawMessage, String localPlayerName) {
		if (message == null || rawMessage == null) {
			return rawMessage;
		}

		final MessageMarkupMap markupMap = MessageMarkupMap.create(rawMessage);
		if (!markupMap.matchesPlain(message.getOriginalMessage())) {
			/*
			 * Skip formatting when raw and semantic offsets no longer map safely.
			 */
			return rawMessage;
		}

		/*
		 * Local matches include account-name variants and Unique Highlights.
		 * Mention Whole Message applies Self Mention Color to the complete body.
		 */
		final boolean mentionWholeMessage = config.mentionWholeMessage()
				&& config.mentionSelf() && message.getLocalMentionMatch().isMatchesLocalPlayer();

		final String wholeMessageColorTag = mentionWholeMessage
				? ColorUtil.colorTag(config.selfMentionColor())
				: null;

		final List<StyleSpan> spans = new ArrayList<>();

		/*
		 * PlayerReference spans retain their own mention styling when the complete message
		 * uses Self Mention Color.
		 */
		for (PlayerReference reference : message.getReferences()) {
			final LocalMentionMatch localMatch = localMentionMatcher.match(reference, localPlayerName);
			final boolean isSelf = localMatch.isMatchesLocalPlayer();

			/*
			 * Self Mention / Others Mention control foreground color only.
			 * Underline Mentions controls underline only.
			 * Shadow Mentions controls shadow only.
			 */
			final boolean shouldColor = isSelf
					? config.mentionSelf()
					: config.mentionOthers();

			/*
			 * Resolved references keep native <u> markup.
			 * Unresolved tags omit native <u> because their underline is rendered as a dotted overlay.
			 */
			final boolean unresolvedTag = reference.getType() == ReferenceType.TAG && !reference.isLocallyResolved();
			final boolean underline = config.underlineMentions() && !unresolvedTag;

			final Color shadowColor = config.shadowMentions()
					? config.shadowMentionColor()
					: null;

			if (!shouldColor && !underline && shadowColor == null) {
				continue;
			}

			spans.add(new StyleSpan(reference.getStartOffset(), reference.getEndOffset(), shouldColor
					? (isSelf ? config.selfMentionColor()
					: config.otherMentionColor()) : null, underline, shadowColor));
		}

		/*
		 * Add local-token spans not represented by PlayerReference objects.
		 * Whole-message coloring and token decoration remain independent.
		 */
		addLocalMessageHighlightSpans(message, spans);

		/*
		 * Right-to-left insertion keeps the original semantic/raw offsets valid.
		 */
		spans.sort(Comparator.comparingInt(StyleSpan::getStartOffset).reversed());

		String formatted = rawMessage;

		for (StyleSpan span : spans) {
			final int rawStart = markupMap.rawBoundary(span.getStartOffset());
			final int rawEnd = markupMap.rawBoundary(span.getEndOffset());
			if (rawStart < 0 || rawEnd < rawStart || rawEnd > formatted.length()) {
				continue;
			}

			/*
			 * If the complete message is using Self Mention Color, temporarily
			 * styled spans must restore that color afterward.
			 *
			 * Otherwise restore whatever RuneLite/Jagex color was active before
			 * the span.
			 */
			final String restoreColor = wholeMessageColorTag != null
					? wholeMessageColorTag
					: getLastColor(formatted.substring(0, rawStart));
			final String openingColor = span.getColor() != null
					? ColorUtil.colorTag(span.getColor())
					: "";
			final String closingColor = span.getColor() != null
					? restoreColor
					: "";
			final String openingUnderline = span.isUnderline()
					? "<u>"
					: "";
			final String closingUnderline = span.isUnderline()
					? "</u>"
					: "";
			final String openingShadow = span.getShadowColor() != null
					? "<shad=" + String.format("%06x", span.getShadowColor().getRGB() & 0xFFFFFF) + ">"
					: "";
			final String closingShadow = span.getShadowColor() != null
					? "</shad>"
					: "";
			formatted = formatted.substring(0, rawStart)
					+ openingColor
					+ openingShadow
					+ openingUnderline
					+ formatted.substring(rawStart, rawEnd)
					+ closingUnderline
					+ closingShadow
					+ closingColor
					+ formatted.substring(rawEnd);
		}

		/*
		 * Apply the complete-message foreground color last.
		 *
		 * Individual span colors inserted above still override it locally, then
		 * restore back to Self Mention Color.
		 */
		if (wholeMessageColorTag != null) {
			formatted = wholeMessageColorTag + formatted + ColorUtil.CLOSING_COLOR_TAG;
		}

		return formatted;
	}

	/*
	 * Adds styling spans for local matches that do not create PlayerReference identity
	 * or interaction.
	 */
	private void addLocalMessageHighlightSpans(TaggedMessage message, List<StyleSpan> spans) {
		final boolean shouldColor = config.mentionSelf();
		final boolean underline = config.underlineMentions();
		final Color shadowColor = config.shadowMentions()
				? config.shadowMentionColor()
				: null;

		if (!shouldColor && !underline && shadowColor == null) {
			return;
		}

		final LocalMentionMatch localMatch = message.getLocalMentionMatch();
		if (localMatch == null || !localMatch.isMatchesLocalPlayer()) {
			return;
		}

		/*
		 * Skip ACCOUNT_NAME matches already represented by PlayerReference.
		 * Normalized account variants and Unique Highlights remain eligible.
		 */
		if (localMatch.getReason() != MatchReason.NORMALIZED_ACCOUNT_NAME
				&& localMatch.getReason() != MatchReason.UNIQUE_HIGHLIGHT) {
			return;
		}

		final String token = localMatch.getMatchedToken();
		if (token == null || token.trim().isEmpty()) {
			return;
		}

		final String messageText = message.getOriginalMessage();
		if (messageText == null || messageText.isEmpty()) {
			return;
		}

		final String loweredMessage = messageText.toLowerCase(Locale.ROOT);
		final String loweredToken = token.toLowerCase(Locale.ROOT);

		int from = 0;

		while (from <= loweredMessage.length() - loweredToken.length()) {
			final int start = loweredMessage.indexOf(loweredToken, from);
			if (start < 0) {
				break;
			}
			final int end = start + loweredToken.length();
			if (hasBoundaries(loweredMessage, start, end) && !overlapsExistingSpan(start, end, spans)) {
				spans.add(new StyleSpan(start, end, shouldColor
					? config.selfMentionColor()
					: null, underline, shadowColor));
			}

			from = start + 1;
		}
	}

	private static boolean overlapsExistingSpan(int start, int end, List<StyleSpan> spans) {
		for (StyleSpan span : spans) {
			if (start < span.getEndOffset() && end > span.getStartOffset()) {
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
	 * Determine which chat color was active before RuneTags temporarily
	 * changed the foreground of a span.
	 */
	private static String getLastColor(String text) {
		if (text == null || text.isEmpty()) {
			return normalColorTag();
		}

		final int colorStart = text.lastIndexOf("<col=");
		final int colorEnd = text.lastIndexOf("</col>");
		if (colorEnd > colorStart) {
			return normalColorTag();
		}
		if (colorStart < 0) {
			return normalColorTag();
		}

		final int tagEnd = text.indexOf('>', colorStart);
		if (tagEnd < 0) {
			return normalColorTag();
		}

		return text.substring(colorStart, tagEnd + 1);
	}

	private static String normalColorTag() {
		return "<col" + ChatColorType.NORMAL + ">";
	}

	/*
	 * Formatting span with no PlayerReference identity or interaction.
	 */
	private static final class StyleSpan {
		private final int startOffset;
		private final int endOffset;
		private final Color color;
		private final boolean underline;
		private final Color shadowColor;

		private StyleSpan(int startOffset, int endOffset, Color color, boolean underline, Color shadowColor) {
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.color = color;
			this.underline = underline;
			this.shadowColor = shadowColor;
		}

		private int getStartOffset() {
			return startOffset;
		}

		private int getEndOffset() {
			return endOffset;
		}

		private Color getColor() {
			return color;
		}

		private boolean isUnderline() {
			return underline;
		}

		private Color getShadowColor() {
			return shadowColor;
		}
	}
}