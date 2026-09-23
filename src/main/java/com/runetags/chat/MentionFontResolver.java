package com.runetags.chat;

import com.runetags.config.MentionFont;

import net.runelite.api.FontID;

/**
 * Resolves RuneTags mention-font substitutions from the active chat font.
 */
public final class MentionFontResolver {
	private MentionFontResolver() {
	}

	public static int fontIdFor(MentionFont mentionFont, int fontId) {
		if (mentionFont == null) {
			return fontId;
		}

		switch (mentionFont) {
			case BOLD:
				return boldFontId(fontId);
			case NORMAL:
			default:
				return normalFontId(fontId);
		}
	}

	public static int normalFontId(int fontId) {
		switch (fontId) {
			case FontID.BOLD_12:
				return FontID.PLAIN_12;
			case FontID.VERDANA_11_BOLD:
				return FontID.VERDANA_11;
			case FontID.VERDANA_13_BOLD:
				return FontID.VERDANA_13;
			default:
				return fontId;
		}
	}

	public static int boldFontId(int fontId) {
		switch (fontId) {
			case FontID.PLAIN_12:
				return FontID.BOLD_12;
			case FontID.BOLD_12:
				return FontID.BOLD_12;
			case FontID.VERDANA_11:
				return FontID.VERDANA_11_BOLD;
			case FontID.VERDANA_11_BOLD:
				return FontID.VERDANA_11_BOLD;
			case FontID.VERDANA_13:
				return FontID.VERDANA_13_BOLD;
			case FontID.VERDANA_13_BOLD:
				return FontID.VERDANA_13_BOLD;
			default:
				return fontId;
		}
	}
}