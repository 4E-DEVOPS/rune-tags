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

@ConfigGroup(RuneTagsConstants.CONFIG_GROUP)
public interface RuneTagsConfig extends Config {
    /*
     * Quick Profile
     */
    @ConfigSection(
            name = "Quick-Card Profile",
            description = "Quick-Card profile fields.",
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
            keyName = "quickCardOpacity",
            name = "Background Opacity",
            description = "Controls the opacity of the Quick-Card Profile background.",
            section = quickProfileSection,
            position = 1
    )
    @Range(
            min = 0,
            max = 100
    )
    default int quickCardOpacity() {
        return 95;
    }

    @ConfigItem(
            keyName = "showStats",
            name = "Show Stats (Combat / Total)",
            description = "Show both Combat and Total Level within Quick-Card profiles.",
            section = quickProfileSection,
            position = 2
    )
    default boolean showStats() {
        return true;
    }

    @ConfigItem(
            keyName = "showKillcount",
            name = "Show Killcount",
            description = "Show contextually relevant boss or minigame killcount when available.",
            section = quickProfileSection,
            position = 3
    )
    default boolean showKillcount() {
        return true;
    }

    @ConfigItem(
            keyName = "lookupProvider",
            name = "Lookup Provider",
            description = "Choose which provider is utilized when the Quick-Card 'Lookup' button is clicked.",
            section = quickProfileSection,
            position = 4
    )
    default LookupProvider lookupProvider() {
        return LookupProvider.HISCORES;
    }

    /*
     * Expose Details
     */
    @ConfigSection(
            name = "Expose Details",
            description = "Details RuneTags may expose for your local player.",
            position = 1
    )
    String exposeDetailsSection = "exposeDetails";

    @ConfigItem(
            keyName = "shareChannel",
            name = "Share Channel",
            description = "Expose the highest-priority shared Party, Clan, Guest Clan, or Friends Chat channel on Quick-Cards.",
            section = exposeDetailsSection,
            position = 0
    )
    default boolean shareChannel() {
        return true;
    }

    @ConfigItem(
            keyName = "shareRank",
            name = "Share Rank",
            description = "Expose the player's rank in the displayed channel when available.",
            section = exposeDetailsSection,
            position = 1
    )
    default boolean shareRank() {
        return true;
    }

    @ConfigItem(
            keyName = "shareLocation",
            name = "Share Location",
            description = "Expose coarse locations whenever available.",
            section = exposeDetailsSection,
            position = 2
    )
    default boolean shareLocation() {
        return true;
    }

    @ConfigItem(
            keyName = "shareStatus",
            name = "Share Status",
            description = "Expose Online/Offline statuses.",
            section = exposeDetailsSection,
            position = 3
    )
    default boolean shareStatus() {
        return true;
    }

    @ConfigItem(
            keyName = "shareWorld",
            name = "Share World",
            description = "Expose the current world.",
            section = exposeDetailsSection,
            position = 4
    )
    default boolean shareWorld() {
        return true;
    }

    /*
     * Mention Appearance
     */
    @ConfigSection(
            name = "Mention Appearance",
            description = "Mention recognition and styling.",
            position = 2
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
            name = "Shadow Color",
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
            name = "Self Mention Color",
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
            name = "Others Mention Color",
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
            position = 3
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
            name = "Self Highlights",
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
            name = "Others Highlights",
            description = "Background highlight color for references to other players. Fully transparent disables it.",
            section = highlightMessagesSection,
            position = 2
    )
    default Color otherBackgroundColor() {
        /*
         * Fully transparent by default.
         */
        return new Color(0, 0, 0, 0);
    }

    /*
     * Notifications
     */
    @ConfigSection(
            name = "Notifications",
            description = "Local mention notifications.",
            position = 4
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
            name = "Flash Color",
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
            name = "Mention Sound ID",
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
            position = 5
    )
    String interactionSection = "interaction";

    @ConfigItem(
            keyName = "clickablePlayers",
            name = "Clickable Players",
            description = "Which player references RuneTags makes quick-card clickable.",
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
            position = 6
    )
    String targetingSection = "targeting";

    @ConfigItem(
            keyName = "targetPlayerOption",
            name = "Target Player Option",
            description = "Show Target on quick cards for nearby players.",
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
            name = "Target Color",
            description = "Target name, line, and outline color.",
            section = targetingSection,
            position = 4
    )
    default Color targetColor() {
        return new Color(0, 40, 255, 200);
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
            name = "Minimap Dot Color",
            description = "Target minimap dot color.",
            section = targetingSection,
            position = 6
    )
    default Color minimapDotColor() {
        return new Color(0, 40, 255, 200);
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
            position = 7
    )
    String autocompleteSection = "autocomplete";

    @ConfigItem(
            keyName = "autocompleteFriends",
            name = "Friends",
            description = "Include friends in tag suggestions.",
            section = autocompleteSection,
            position = 0
    )
    default boolean autocompleteFriends() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteClan",
            name = "Clan",
            description = "Include clan members in tag suggestions.",
            section = autocompleteSection,
            position = 1
    )
    default boolean autocompleteClan() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteFriendsChat",
            name = "Friends Chat",
            description = "Include Friends Chat members in tag suggestions.",
            section = autocompleteSection,
            position = 2
    )
    default boolean autocompleteFriendsChat() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteParty",
            name = "Party",
            description = "Include party members in tag suggestions.",
            section = autocompleteSection,
            position = 3
    )
    default boolean autocompleteParty() {
        return true;
    }

    @ConfigItem(
            keyName = "autocompleteNearby",
            name = "Nearby Players",
            description = "Include nearby players in tag suggestions.",
            section = autocompleteSection,
            position = 4
    )
    default boolean autocompleteNearby() {
        return true;
    }

    @ConfigItem(
            keyName = "suggestUsernames",
            name = "Suggest Usernames",
            description = "Show a passive username suggestion overlay while typing an explicit @tag.",
            section = autocompleteSection,
            position = 5
    )
    default boolean suggestUsernames() {
        return true;
    }

    @ConfigItem(
            keyName = "completeSuggestionHotkey",
            name = "Complete Hotkey",
            description = "Hotkey used to complete the selected username suggestion.",
            section = autocompleteSection,
            position = 6
    )
    default Keybind completeSuggestionHotkey() {
        return new Keybind(
                java.awt.event.KeyEvent.VK_END,
                0);
    }

    /*
     * History
     */
    @ConfigSection(
            name = "History",
            description = "Local mention history.",
            position = 8
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
            name = "Maximum History",
            description = "Maximum number of local mention-history entries.",
            section = historySection,
            position = 1
    )
    default int maximumHistory() {
        return 50;
    }
}