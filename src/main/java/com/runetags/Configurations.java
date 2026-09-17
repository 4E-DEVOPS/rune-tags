package com.runetags;

import com.runetags.config.*;

import java.awt.Color;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.RequestFocusType;

@ConfigGroup(Constants.CONFIG_GROUP)
public interface Configurations extends Config {
    /*
     * Quick Profile
     */
    @ConfigSection(
            name = "Quick-Card Profile",
            description = "Quick-Card profile controls.",
            position = 0
    )
    String quickProfileSection = "quickProfile";

    @ConfigItem(
            keyName = "showProfile",
            name = "Show Profile",
            description = "Enable RuneTags quick profile cards.",
            section = quickProfileSection,
            position = 0
    )
    default boolean showProfile() {
        return true;
    }

    @ConfigItem(
            keyName = "showPreviousRsns",
            name = "Show Previous RSNs",
            description = "Show known previous RuneScape names when hovering over a player's name on their Quick-Card.",
            section = quickProfileSection,
            position = 1
    )
    default boolean showPreviousRsns() {
        return true;
    }

    @ConfigItem(
            keyName = "quickCardOpacity",
            name = "Background Opacity",
            description = "Controls the opacity of the Quick-Card Profile background.",
            section = quickProfileSection,
            position = 2
    )
    @Range(
            min = 0,
            max = 100
    )
    default int quickCardOpacity() {
        return 95;
    }

    @ConfigItem(
            keyName = "lookupProvider",
            name = "Lookup Provider",
            description = "Choose which provider is utilized when the Quick-Card 'Lookup' button is clicked.",
            section = quickProfileSection,
            position = 3
    )
    default LookupProvider lookupProvider() {
        return LookupProvider.HISCORES;
    }

    /*
     * Record Details
     */
    @ConfigSection(
            name = "Record Details",
            description = "Persistent/local and published player record details.",
            position = 1
    )
    String recordDetailsSection = "recordDetails";

    @ConfigItem(
            keyName = "showTags",
            name = "Tags",
            description = "Enable persistent local player Tags within RuneTags profiles.",
            section = recordDetailsSection,
            position = 0
    )
    default boolean showTags() {
        return true;
    }

    @ConfigItem(
            keyName = "showFavorites",
            name = "Favorites",
            description = "Enable Favorite controls and gold highlighting for favorited players.",
            section = recordDetailsSection,
            position = 1
    )
    default boolean showFavorites() {
        return true;
    }

    @ConfigItem(
            keyName = "favoriteColor",
            name = " └─ Color",
            description = "Color used for favorited player names and indicators.",
            section = recordDetailsSection,
            position = 2
    )
    default Color favoriteColor() {
        return new Color(255, 205, 70);
    }

    @ConfigItem(
            keyName = "showNotes",
            name = "Notes",
            description = "Enable player Notes within RuneTags profiles.",
            section = recordDetailsSection,
            position = 3
    )
    default boolean showNotes() {
        return true;
    }

    @ConfigItem(
            keyName = "showReports",
            name = "RuneWatch Reports",
            description = "Display report records for players with a valid RuneWatch or We Do Raids case.<br>"
                    + "NOTE: Connects to RuneWatch report feed and caches the public report list.",
            warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers.",
            section = recordDetailsSection,
            position = 4
    )
    default boolean showReports() {
        return false;
    }

    /*
     * Identity Details
     */
    @ConfigSection(
            name = "Identity Details",
            description = "Identity details RuneTags may expose for players.",
            position = 2
    )
    String identityDetailsSection = "identityDetails";

    @ConfigItem(
            keyName = "shareChannel",
            name = "Share Channel",
            description = "Expose the highest-priority shared Party, Clan, Guest Clan, or Friends Chat channel on Quick-Cards.",
            section = identityDetailsSection,
            position = 0
    )
    default boolean shareChannel() {
        return true;
    }

    @ConfigItem(
            keyName = "shareRank",
            name = " └─ Share Rank",
            description = "Expose the player's rank in the displayed channel when available.",
            section = identityDetailsSection,
            position = 1
    )
    default boolean shareRank() {
        return true;
    }

    @ConfigItem(
            keyName = "shareLocation",
            name = "Share Location",
            description = "Expose coarse locations whenever available.",
            section = identityDetailsSection,
            position = 2
    )
    default boolean shareLocation() {
        return true;
    }

    @ConfigItem(
            keyName = "shareStatus",
            name = "Share Status",
            description = "Expose Online/Offline statuses.",
            section = identityDetailsSection,
            position = 3
    )
    default boolean shareStatus() {
        return true;
    }

    @ConfigItem(
            keyName = "shareWorld",
            name = " └─ Share World",
            description = "Expose the current world.",
            section = identityDetailsSection,
            position = 4
    )
    default boolean shareWorld() {
        return true;
    }

    /*
     * Metric Details
     */
    @ConfigSection(
            name = "Metric Details",
            description = "Stats and contextual player hiscores shown on Quick-Cards.",
            position = 3
    )
    String metricDetailsSection = "metricDetails";

    @ConfigItem(
            keyName = "showStats",
            name = "Show Stats (Combat / Total)",
            description = "Show both Combat and Total Level within Quick-Card profiles.",
            section = metricDetailsSection,
            position = 0
    )
    default boolean showStats() {
        return true;
    }

    @ConfigItem(
            keyName = "wiseOldManMetrics",
            name = "Wise Old Man Metrics",
            description = "Allow RuneTags to retrieve data from the Wise Old Man server.",
            warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
            section = metricDetailsSection,
            position = 1
    )
    default boolean wiseOldManMetrics()
    {
        return false;
    }

    @ConfigItem(
            keyName = "showEhp",
            name = " ├─ Efficient Hours Played",
            description = "Show a players Efficient Hours Played (EHP).<br>"
                            + "NOTE: Must enable 'Wise Old Man Metrics'",
            section = metricDetailsSection,
            position = 2
    )
    default boolean showEhp() {
        return true;
    }

    @ConfigItem(
            keyName = "showEhb",
            name = " └─ Efficient Hours Bossed",
            description = "Show a players Efficient Hours Bossed (EHB).<br>"
                    + "NOTE: Must enable 'Wise Old Man Metrics'",
            section = metricDetailsSection,
            position = 3
    )
    default boolean showEhb() {
        return true;
    }

    @ConfigItem(
            keyName = "showKillcount",
            name = "Show Killcount",
            description = "Show contextually relevant boss or minigame killcount when available.",
            section = metricDetailsSection,
            position = 4
    )
    default boolean showKillcount() {
        return true;
    }

    /*
     * Mention Appearance
     */
    @ConfigSection(
            name = "Mention Appearance",
            description = "Mention recognition and styling.",
            position = 4
    )
    String mentionAppearanceSection = "mentionAppearance";

    @ConfigItem(
            keyName = "fontMentions",
            name = "Font",
            description = "Font used for messages containing recognized mentions.",
            section = mentionAppearanceSection,
            position = 0
    )
    default MentionFont fontMentions() { return MentionFont.NORMAL; }

    @ConfigItem(
            keyName = "underlineMentions",
            name = "Underline Mentions",
            description = "Underline recognized mentions.",
            section = mentionAppearanceSection,
            position = 1
    )
    default boolean underlineMentions() {
        return true;
    }

    @ConfigItem(
            keyName = "shadowMentions",
            name = "Shadow Mentions",
            description = "Add a shadow to mentions and tags.",
            section = mentionAppearanceSection,
            position = 2
    )
    default boolean shadowMentions() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "shadowMentionColor",
            name = " └─ Color",
            description = "Color used for mention and tag text shadows.",
            section = mentionAppearanceSection,
            position = 3
    )
    default Color shadowMentionColor() {
        return new Color(255,200,0,255);
    }

    @ConfigItem(
            keyName = "mentionSelf",
            name = "Self Mention",
            description = "Mention references matching your player name or local Unique Mentions.",
            section = mentionAppearanceSection,
            position = 4
    )
    default boolean mentionSelf() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "selfMentionColor",
            name = " └─ Color",
            description = "Foreground color for your player name and Unique Mentions.",
            section = mentionAppearanceSection,
            position = 5
    )
    default Color selfMentionColor() {
        return Color.RED;
    }

    @ConfigItem(
            keyName = "mentionOthers",
            name = "Others Mention",
            description = "Mention recognized references to other players.",
            section = mentionAppearanceSection,
            position = 6
    )
    default boolean mentionOthers() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "otherMentionColor",
            name = " └─ Color",
            description = "Foreground color for references to other players and unresolved explicit tags.",
            section = mentionAppearanceSection,
            position = 7
    )
    default Color otherMentionColor() {
        return Color.WHITE;
    }

    @ConfigItem(
            keyName = "uniqueMentions",
            name = "Unique Mentions",
            description = "Comma-separated local notification/highlight triggers. These never remap player identity.",
            section = mentionAppearanceSection,
            position = 8
    )
    default String uniqueMentions() {
        return "";
    }

    @ConfigItem(
            keyName = "mentionWholeMessage",
            name = "Mention Whole Message",
            description = "Mentions the complete message when it locally references you or matches a Unique Mention.",
            section = mentionAppearanceSection,
            position = 9
    )
    default boolean mentionWholeMessage() {
        return true;
    }

    /*
     * Highlight Messages
     */
    @ConfigSection(
            name = "Highlight Messages",
            description = "Highlight recognition and styling.",
            position = 5
    )
    String highlightMessagesSection = "highlightMessages";

    @ConfigItem(
            keyName = "highlightBackground",
            name = "Highlight Backgrounds",
            description = "Enable background highlighting for locally matched messages and recognized player references.",
            section = highlightMessagesSection,
            position = 0
    )
    default boolean highlightBackground() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "selfBackgroundColor",
            name = " └─ Self",
            description = "Background highlight color for messages and references that locally match you.",
            section = highlightMessagesSection,
            position = 1
    )
    default Color selfBackgroundColor() {
        /*
         * Pastel yellow with partial transparency.
         */
        return new Color(195, 100, 185, 70);
    }

    @Alpha
    @ConfigItem(
            keyName = "otherBackgroundColor",
            name = " └─ Others",
            description = "Background highlight color for references to other players.<br>"
                            + "Fully transparent disables it.",
            section = highlightMessagesSection,
            position = 2
    )
    default Color otherBackgroundColor() {
        //Fully transparent by default.
        return new Color(175, 175, 175, 75);
    }

    /*
     * Notifications
     */
    @ConfigSection(
            name = "Notifications",
            description = "Local mention notifications.",
            position = 6
    )
    String notificationSection = "notifications";

    @ConfigItem(
            keyName = "requestFocusOnMention",
            name = "Request Focus",
            description = "Controls how RuneLite requests your attention when a mention is detected.",
            section = notificationSection,
            position = 0
    )
    default RequestFocusType requestFocusOnMention() {
        return RequestFocusType.REQUEST;
    }

    @ConfigItem(
            keyName = "sendNotificationsWhenFocused",
            name = "Send Notifications When Focused",
            description = "Send mention notifications while the RuneLite client is focused.",
            section = notificationSection,
            position = 1
    )
    default boolean sendNotificationsWhenFocused() {
        return false;
    }

    @ConfigItem(
            keyName = "flashOnMention",
            name = "Flash",
            description = "Controls how the game frame flashes when a mention is detected.",
            section = notificationSection,
            position = 2
    )
    default FlashNotification flashOnMention() {
        return FlashNotification.FLASH_TWO_SECONDS;
    }

    @Alpha
    @ConfigItem(
            keyName = "flashColor",
            name = " └─ Color",
            description = "Color used for mention flashes.",
            section = notificationSection,
            position = 3
    )
    default Color flashColor() {
        return new Color(
                255,
                150,
                0,
                70);
    }

    @ConfigItem(
            keyName = "playMentionSound",
            name = "Mention Sound",
            description = "Play a sound when a mention is detected.",
            section = notificationSection,
            position = 4
    )
    default boolean playMentionSound() {
        return true;
    }

    @ConfigItem(
            keyName = "mentionSoundId",
            name = " └─ Sound ID",
            description = "RuneScape sound ID to play for mentions.",
            section = notificationSection,
            position = 5
    )
    default int mentionSoundId() {
        return 2218;
    }

    /*
     * Interaction
     */
    @ConfigSection(
            name = "Interaction",
            description = "Click interaction rules.",
            position = 7
    )
    String interactionSection = "interaction";

    @ConfigItem(
            keyName = "clickablePlayers",
            name = "Clickable Players",
            description = "Which player references RuneTags makes quick-card clickable.<br>"
                            + "All: Sender Included | Both: Mentions + Tags | Tagged: Only @Users Tags",
            section = interactionSection,
            position = 0
    )
    default ClickablePlayerMode clickablePlayers() {
        return ClickablePlayerMode.ALL;
    }

    @ConfigItem(
            keyName = "chatInteractionMode",
            name = "Interaction Mode",
            description = "Choose whether RuneTags player references use left-click, right-click, or both.",
            section = interactionSection,
            position = 1
    )
    default ChatInteractionMode chatInteractionMode()
    {
        return ChatInteractionMode.BOTH;
    }

    /*
     * Targeting
     */
    @ConfigSection(
            name = "Targeting",
            description = "Nearby player targeting.",
            position = 8
    )
    String targetingSection = "targeting";

    @ConfigItem(
            keyName = "targetPlayerOption",
            name = "Target Player Option",
            description = "Show Target on Quick-Cards and chat menus for nearby players.",
            section = targetingSection,
            position = 0
    )
    default boolean targetPlayerOption() {
        return true;
    }

    @ConfigItem(
            keyName = "targetName",
            name = "Target Name",
            description = "Render the target player's name.",
            section = targetingSection,
            position = 1
    )
    default boolean targetName() {
        return true;
    }

    @ConfigItem(
            keyName = "targetMode",
            name = "Target Mode",
            description = "Controls how the targeted player is highlighted.",
            section = targetingSection,
            position = 2
    )
    default TargetMode targetMode() {
        return TargetMode.BOTH;
    }

    @ConfigItem(
            keyName = "targetLine",
            name = "Target Line",
            description = "Render a line from you to the target.",
            section = targetingSection,
            position = 3
    )
    default boolean targetLine() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "targetColor",
            name = " └─ Color",
            description = "Target name, line, outline, and chat menu color.",
            section = targetingSection,
            position = 4
    )
    default Color targetColor() {
        return new Color(80, 100, 255, 200);
    }

    @ConfigItem(
            keyName = "minimapIndicatorMode",
            name = "Minimap Indicator",
            description = "Controls the size of the targeted player's minimap indicator.",
            section = targetingSection,
            position = 5
    )
    default MinimapIndicatorMode minimapIndicator() {
        return MinimapIndicatorMode.MEDIUM;
    }

    @Alpha
    @ConfigItem(
            keyName = "minimapDotColor",
            name = " └─ Dot Color",
            description = "Target minimap dot color.",
            section = targetingSection,
            position = 6
    )
    default Color minimapDotColor() {
        return new Color(50, 80, 255, 200);
    }

    @Range(
            min = 0,
            max = 3600
    )
    @ConfigItem(
            keyName = "targetTimeout",
            name = "Target Timeout",
            description = "Clear the target after this many seconds. 0 means until invalid.",
            section = targetingSection,
            position = 7
    )
    default int targetTimeout() {
        return 0;
    }

    @ConfigItem(
            keyName = "hideAllOthers",
            name = "Hide All Others",
            description = "Hide other players except the current target.",
            section = targetingSection,
            position = 8
    )
    default HideOthersMode hideAllOthers() {
        return HideOthersMode.OFF;
    }

    /*
     * Autocomplete
     */
    @ConfigSection(
            name = "Autocomplete",
            description = "Tag username suggestions.",
            position = 9
    )
    String autocompleteSection = "autocomplete";

    @ConfigItem(
            keyName = "suggestUsernames",
            name = "Suggest Usernames",
            description = "Show a passive username suggestion overlay while typing an explicit @tag.",
            section = autocompleteSection,
            position = 0
    )
    default boolean suggestUsernames() {
        return true;
    }

    @ConfigItem(
            keyName = "completeSuggestionHotkey",
            name = "Complete Hotkey",
            description = "Hotkey used to complete the selected username suggestion.",
            section = autocompleteSection,
            position = 1
    )
    default Keybind completeSuggestionHotkey() {
        return new Keybind(
                java.awt.event.KeyEvent.VK_TAB,
                0);
    }

    @ConfigItem(
            keyName = "autocompleteNearby",
            name = " ├─ Nearby Players",
            description = "Include nearby players in tag suggestions.",
            section = autocompleteSection,
            position = 2
    )
    default boolean autocompleteNearby() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteFriends",
            name = " ├─ Friends",
            description = "Include friends in tag suggestions.",
            section = autocompleteSection,
            position = 3
    )
    default boolean autocompleteFriends() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteFriendsChat",
            name = " ├─ Friends Chat",
            description = "Include Friends Chat members in tag suggestions.",
            section = autocompleteSection,
            position = 4
    )
    default boolean autocompleteFriendsChat() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteClan",
            name = " ├─ Clan",
            description = "Include clan members in tag suggestions.",
            section = autocompleteSection,
            position = 5
    )
    default boolean autocompleteClan() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteParty",
            name = " └─ Party",
            description = "Include party members in tag suggestions.",
            section = autocompleteSection,
            position = 6
    )
    default boolean autocompleteParty() {
        return true;
    }

    /*
     * History
     */
    @ConfigSection(
            name = "History",
            description = "Local mention history.",
            position = 10
    )
    String historySection = "history";

    @ConfigItem(
            keyName = "mentionHistory",
            name = "Mention History",
            description = "Keep a side-panel history of messages that locally referenced you.",
            section = historySection,
            position = 0
    )
    default boolean mentionHistory() {
        return true;
    }

    @ConfigItem(
            keyName = "maximumHistory",
            name = " └─ Maximum",
            description = "Maximum number of local mention-history entries.",
            section = historySection,
            position = 1
    )
    default int maximumHistory() {
        return 50;
    }
}