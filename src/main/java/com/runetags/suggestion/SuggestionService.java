package com.runetags.suggestion;

import com.runetags.Configurations;
import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/**
 * Maintains passive @username suggestions for the local chat input.
 *
 * Completion only replaces the trailing @query in CHATINPUT. RuneScape remains
 * responsible for the later Enter/send action performed by the user.
 */
public final class SuggestionService {
	private static final int MAX_SUGGESTIONS = 6;

	private final Client client;
	private final ClientThread clientThread;
	private final Configurations config;
	private final PlayerDirectory playerDirectory;

	private final List<String> suggestions = new ArrayList<>();

	private String observedInput = "";
	private String query = "";
	private String suppressedInput;
	private int mentionStart = -1;
	private int selectedIndex;

	public SuggestionService(
			Client client,
			ClientThread clientThread,
			Configurations config,
			PlayerDirectory playerDirectory) {
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.playerDirectory = playerDirectory;
	}

	public void refresh() {
		if (!config.suggestUsernames()) {
			clear();
			return;
		}

		final String input = safe(client.getVarcStrValue(VarClientID.CHATINPUT));
		if (input.equals(suppressedInput)) {
			clear();
			observedInput = input;
			return;
		}

		suppressedInput = null;

		final int at = trailingMentionStart(input);
		if (at < 0) {
			clear();
			observedInput = input;
			return;
		}

		final String nextQuery = input.substring(at + 1);
		final boolean queryChanged = !input.equals(observedInput) || at != mentionStart;

		observedInput = input;
		mentionStart = at;
		query = nextQuery;

		final List<String> next = buildSuggestions(nextQuery);
		if (!next.equals(suggestions)) {
			suggestions.clear();
			suggestions.addAll(next);
		}

		if (queryChanged) {
			selectedIndex = 0;
		} else if (selectedIndex >= suggestions.size()) {
			selectedIndex = Math.max(0, suggestions.size() - 1);
		}
	}

	public boolean handleKeyPressed(KeyEvent event) {
		if (event == null || !config.suggestUsernames()) {
			return false;
		}

		refresh();

		if (suggestions.isEmpty()) {
			return false;
		}

		if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
			clear();
			event.consume();
			return true;
		}

		if (event.getKeyCode() == KeyEvent.VK_UP) {
			selectedIndex = (selectedIndex - 1 + suggestions.size()) % suggestions.size();
			event.consume();
			return true;
		}

		if (event.getKeyCode() == KeyEvent.VK_DOWN) {
			selectedIndex = (selectedIndex + 1) % suggestions.size();
			event.consume();
			return true;
		}

		if (config.completeSuggestionHotkey().matches(event)) {
			completeSelected();
			event.consume();
			return true;
		}

		return false;
	}

	public List<String> getSuggestions() {
		return new ArrayList<>(suggestions);
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public boolean isActive() {
		return config.suggestUsernames() && mentionStart >= 0 && !suggestions.isEmpty();
	}

	public void clear() {
		suggestions.clear();
		query = "";
		mentionStart = -1;
		selectedIndex = 0;
	}

	private void completeSelected() {
		if (mentionStart < 0 || selectedIndex < 0 || selectedIndex >= suggestions.size()) {
			return;
		}

		// Capture the selection before client-thread mutation.
		final int completionMentionStart = mentionStart;

		// Complete RuneScape name separators with underscores.
		final String completionName = suggestions.get(selectedIndex).replace(' ', '_').replace('\u00A0', '_');

		// Close suggestions before mutating CHATINPUT.
		clear();

		clientThread.invokeLater(() -> {
			final String input = safe(client.getVarcStrValue(VarClientID.CHATINPUT));

			// Leave changed input untouched if the captured @ position is no longer valid.
			if (completionMentionStart < 0
					|| completionMentionStart >= input.length()
					|| input.charAt(completionMentionStart) != '@') {
				return;
			}

			final String completed = input.substring(0, completionMentionStart) + "@" + completionName + " ";

			client.setVarcStrValue(VarClientID.CHATINPUT, completed);

			// Keep the rendered input Widget synchronized with CHATINPUT.
			final Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
			if (chatboxInput != null) {
				final String widgetText = chatboxInput.getText();
				if (widgetText != null) {
					final int separator = widgetText.indexOf(':');
					if (separator >= 0) {
						final int colorStart = widgetText.indexOf("<col=", separator + 1);
						final int colorEnd = colorStart >= 0
								? widgetText.indexOf('>', colorStart)
								: -1;
						final String colorTag = colorStart >= 0 && colorEnd > colorStart
								? widgetText.substring(colorStart, colorEnd + 1)
								: "";
						final String prefix = widgetText.substring(0, separator + 1);

						if (!colorTag.isEmpty()) {
							chatboxInput.setText(
									prefix + " " + colorTag + completed + "</col>" + colorTag + "*</col" + ">");
						} else {
							chatboxInput.setText(prefix + " " + completed + "*");
						}
					}
				}
			}

			suppressedInput = completed;
			observedInput = completed;
		});
	}

	private List<String> buildSuggestions(String rawQuery) {
		// Require two raw query characters before separator normalization.
		final String rawNeedle = safe(rawQuery).toLowerCase(Locale.ENGLISH);
		if (rawNeedle.length() < 2) { // 2+ characters to begin offering suggestions
			return new ArrayList<>();
		}

		final String needle = normalizeSuggestionMatchText(rawNeedle);
		final List<Candidate> candidates = new ArrayList<>();

		for (PlayerIdentity identity : playerDirectory.all()) {
			if (identity == null || identity.getCanonicalName() == null || !sourceEnabled(identity.getSources())) {
				continue;
			}

			final String name = identity.getCanonicalName().trim();
			if (name.isEmpty()) {
				continue;
			}

			// Compare RuneScape name separators interchangeably without altering completion text.
			final String comparableName = normalizeSuggestionMatchText(name);
			final int matchIndex = comparableName.indexOf(needle);
			if (matchIndex < 0) {
				continue;
			}

			candidates.add(new Candidate(name, matchIndex == 0));
		}

		candidates.sort(Comparator.comparing(Candidate::isPrefixMatch).reversed()
				.thenComparing(Candidate::getName, String.CASE_INSENSITIVE_ORDER));

		final List<String> output = new ArrayList<>();
		for (Candidate candidate : candidates) {
			output.add(candidate.getName());

			if (output.size() >= MAX_SUGGESTIONS) {
				break;
			}
		}

		return output;
	}

	private static String normalizeSuggestionMatchText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}

		return value.toLowerCase(Locale.ENGLISH).replace('_', ' ').replace('-', ' ').replace('\u00A0', ' ');
	}

	private boolean sourceEnabled(Set<PlayerSource> sources) {
		if (sources == null || sources.isEmpty()) {
			return false;
		}

		return (config.autocompleteFriends() && sources.contains(PlayerSource.FRIEND))
				|| (config.autocompleteClan()
				&& (sources.contains(PlayerSource.CLAN) || sources.contains(PlayerSource.GUEST_CLAN)))
				|| (config.autocompleteFriendsChat() && sources.contains(PlayerSource.FRIENDS_CHAT))
				|| (config.autocompleteParty() && sources.contains(PlayerSource.PARTY))
				|| (config.autocompleteNearby() && sources.contains(PlayerSource.NEARBY));
	}

	private static int trailingMentionStart(String input) {
		if (input == null || input.isEmpty()) {
			return -1;
		}

		final int at = input.lastIndexOf('@');
		if (at < 0) {
			return -1;
		}

		// Accept @ at input start, after a channel shortcut, or after whitespace.
		if (at > 0 && !Character.isWhitespace(input.charAt(at - 1)) && !isLeadingChannelShortcut(input, at)) {
			return -1;
		}

		final String tail = input.substring(at + 1);
		if (tail.indexOf('\n') >= 0 || tail.indexOf('\r') >= 0) {
			return -1;
		}

		return at;
	}

	private static boolean isLeadingChannelShortcut(String input, int mentionStart) {
		if (input == null || mentionStart < 1 || mentionStart > 3 || input.length() <= mentionStart) {
			return false;
		}

		for (int i = 0; i < mentionStart; i++) {
			if (input.charAt(i) != '/') {
				return false;
			}
		}

		return true;
	}

	private static String safe(String value) {
		return value == null
				? ""
				: value;
	}

	private static final class Candidate {
		private final String name;
		private final boolean prefixMatch;

		private Candidate(String name, boolean prefixMatch) {
			this.name = name;
			this.prefixMatch = prefixMatch;
		}

		private String getName() {
			return name;
		}

		private boolean isPrefixMatch() {
			return prefixMatch;
		}
	}
}
