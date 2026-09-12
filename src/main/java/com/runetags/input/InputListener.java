package com.runetags.input;

import com.runetags.Configurations;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ReferenceHitbox;
import com.runetags.config.ChatInteractionMode;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;
import com.runetags.quickprofile.QuickProfileController;
import com.runetags.suggestion.SuggestionService;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Optional;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class InputListener extends MouseAdapter
        implements KeyListener
{
    private static final String MENU_OPEN_PROFILE = "Open Profile";
    private static final String MENU_TARGET = "Target";
    private static final String MENU_LOOKUP = "Lookup";

    /*
     * RuneTags never creates Report. RuneScape's native Report entry identifies
     * the real chat-sender username context menu.
     */
    private static final String NATIVE_MENU_REPORT = "Report";

    private final Client client;
    private final Configurations config;
    private final ChatHitboxRegistry registry;
    private final QuickProfileController quickProfileController;
    private final SuggestionService suggestionService;

    private boolean suppressCurrentLeftClick;
    private boolean suppressCurrentEscape;
    private boolean suppressCurrentSuggestionTyped;

    public InputListener(
            Client client,
            Configurations config,
            ChatHitboxRegistry registry,
            QuickProfileController quickProfileController,
            SuggestionService suggestionService)
    {
        this.client = client;
        this.config = config;
        this.registry = registry;
        this.quickProfileController = quickProfileController;
        this.suggestionService = suggestionService;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        /*
         * Close any open QuickCard before the context
         * menu becomes the active interaction.
         */
        if (event.getButton() == MouseEvent.BUTTON3)
        {
            if (quickProfileController.isOpen())
            {
                quickProfileController.close();
            }

            return super.mousePressed(event);
        }

        if (event.getButton() != MouseEvent.BUTTON1)
        {
            return super.mousePressed(event);
        }

        /*
         * An open RuneScape menu owns the click. Do not route it to an underlying
         * RuneTags hitbox, and do not consume it because RuneScape must execute the
         * selected menu action.
         */
        if (client.isMenuOpen())
        {
            suppressCurrentLeftClick = false;
            return super.mousePressed(event);
        }

        final Point point =
                event.getPoint();

        if (quickProfileController.isOpen())
        {
            final String tagToRemove =
                    quickProfileController.tagRemovalAt(
                            point);

            if (tagToRemove != null)
            {
                quickProfileController.removeTag(
                        tagToRemove);

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isCloseButton(
                    point))
            {
                quickProfileController.close();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isTagButton(
                    point))
            {
                quickProfileController.editTags();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isNoteButton(
                    point))
            {
                quickProfileController.editNote();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isFavoriteButton(
                    point))
            {
                quickProfileController.toggleFavorite();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isTargetButton(
                    point))
            {
                quickProfileController.target();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isLookupButton(
                    point))
            {
                quickProfileController.lookup();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isClanLink(
                    point))
            {
                quickProfileController.lookupClan();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isReportCaseLink(
                    point))
            {
                quickProfileController.openReportCase();

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }

            if (quickProfileController.isInsideCard(
                    point))
            {
                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }
        }

        final ChatInteractionMode interactionMode =
                config.chatInteractionMode();

        /*
         * LEFT_CLICK and BOTH permit direct QuickCard activation;
         * RIGHT_CLICK deliberately leaves a normal left-click untouched.
         */
        if (interactionMode != null
                && interactionMode.allowsLeftClick())
        {
            final Optional<ReferenceHitbox> hit =
                    registry.find(
                            point);

            if (hit.isPresent())
            {
                quickProfileController.open(
                        hit.get().getReference(),
                        point);

                suppressCurrentLeftClick = true;

                event.consume();
                return event;
            }
        }

        /*
         * Clicking outside an open QuickCard closes it.
         */
        if (quickProfileController.isOpen())
        {
            quickProfileController.close();
        }

        return super.mousePressed(
                event);
    }

    /**
     * Augment RuneScape context menus with RuneTags actions.
     *
     * Native sender menus retain their normal actions. MENTION and TAG references
     * use the RuneTags Open Profile / Target / Lookup menu when right-click
     * interaction is enabled.
     */
    public void onMenuOpened(
            MenuOpened event)
    {
        /*
         * Menu ownership takes priority over an open QuickCard, including menus opened
         * through paths other than an ordinary BUTTON3 press.
         */
        if (quickProfileController.isOpen())
        {
            quickProfileController.close();
        }

        final ChatInteractionMode interactionMode =
                config.chatInteractionMode();

        /*
         * RIGHT_CLICK and BOTH permit RuneTags context-menu actions;
         * LEFT_CLICK leaves RuneScape's native right-click menu untouched.
         */
        if (interactionMode == null
                || !interactionMode.allowsRightClick())
        {
            return;
        }

        final net.runelite.api.Point mouseCanvasPoint =
                client.getMouseCanvasPosition();

        if (mouseCanvasPoint == null)
        {
            return;
        }

        final Point mousePoint =
                new Point(
                        mouseCanvasPoint.getX(),
                        mouseCanvasPoint.getY());

        /*
         * RuneTags references take priority over native sender menus.
         *
         * A mention inside another player's chat message belongs to the
         * referenced player, not the sender of the message.
         */
        final Optional<ReferenceHitbox> hit =
                registry.find(
                        mousePoint);

        if (hit.isPresent()
                && handleReferenceMenu(
                hit.get(),
                mousePoint))
        {
            return;
        }

        /*
         * If no RuneTags reference owns the interaction, preserve RuneScape's
         * native sender menu and add RuneTags actions where appropriate.
         */
        if (augmentNativeSenderMenu(
                event,
                mousePoint))
        {
            return;
        }

    }

    private boolean handleReferenceMenu(
            ReferenceHitbox referenceHitbox,
            Point mousePoint)
    {
        final PlayerReference reference =
                referenceHitbox.getReference();

        if (reference == null)
        {
            return false;
        }

        /*
         * Sender references belong to RuneScape's native menu.
         * They should never be handled here.
         */
        if (reference.getType()
                == ReferenceType.SENDER)
        {
            return false;
        }

        /*
         * Only mentions and tags receive RuneTags menus.
         */
        if (reference.getType()
                != ReferenceType.MENTION
                && reference.getType()
                != ReferenceType.TAG)
        {
            return false;
        }
        suppressNativeMenu(nativeSenderName());

        final Point anchorPoint =
                new Point(
                        mousePoint);

        final String target =
                menuTarget(
                        reference);


        client.createMenuEntry(-1)
                .setOption(
                        MENU_OPEN_PROFILE)
                .setTarget(
                        target)
                .setType(
                        MenuAction.RUNELITE)
                .onClick(entry ->
                        quickProfileController.open(
                                reference,
                                anchorPoint));


        if (config.targetPlayerOption()
                && quickProfileController.canTarget(
                reference))
        {
            client.createMenuEntry(-2)
                    .setOption(
                            MENU_TARGET)
                    .setTarget(
                            target)
                    .setType(
                            MenuAction.RUNELITE)
                    .onClick(entry ->
                            quickProfileController.target(
                                    reference));
        }


        client.createMenuEntry(-3)
                .setOption(
                        MENU_LOOKUP)
                .setTarget(
                        target)
                .setType(
                        MenuAction.RUNELITE)
                .onClick(entry ->
                        quickProfileController.lookup(
                                reference));


        return true;
    }

    /**
     * Remove the native sender actions created for the chat message author.
     *
     * A RuneTags mention/tag belongs to the referenced player, not the player
     * who sent the message containing the reference.
     */
    private String nativeSenderName()
    {
        final MenuEntry[] entries =
                client.getMenuEntries();

        for (MenuEntry entry : entries)
        {
            if (isNativeChatReportMenuEntry(entry))
            {
                return cleanPlayerName(
                        entry.getTarget());
            }
        }

        return "";
    }

    private boolean isNativePlayerMenu(
            MenuEntry entry,
            String senderName)
    {
        if (entry == null)
        {
            return false;
        }

        final String target =
                cleanPlayerName(
                        entry.getTarget());

        if (!target.equalsIgnoreCase(
                senderName))
        {
            return false;
        }

        final String option =
                entry.getOption();

        return "WALK HERE".equalsIgnoreCase(option)
                || "ADD FRIEND".equalsIgnoreCase(option)
                || "ADD IGNORE".equalsIgnoreCase(option)
                || "MESSAGE".equalsIgnoreCase(option)
                || "LOOKUP".equalsIgnoreCase(option)
                || "WOM LOOKUP".equalsIgnoreCase(option)
                || "TCG TRADE REQUEST".equalsIgnoreCase(option)
                || "REPORT".equalsIgnoreCase(option)
                || "COPY TO CLIPBOARD".equalsIgnoreCase(option);
    }

    private boolean isNativeMovementMenu(
            MenuEntry entry)
    {
        if (entry == null)
        {
            return false;
        }

        return "WALK HERE".equalsIgnoreCase(
                entry.getOption());
    }

    private void suppressNativeMenu(
            String senderName)
    {
        final MenuEntry[] entries =
                client.getMenuEntries();

        if (entries == null
                || entries.length == 0)
        {
            return;
        }

        final boolean hasSender =
                senderName != null
                        && !senderName.isEmpty();

        client.setMenuEntries(
                Arrays.stream(entries)
                        .filter(entry ->
                                !hasSender
                                        || !isNativePlayerMenu(
                                        entry,
                                        senderName))
                        .filter(entry ->
                                !isNativeMovementMenu(
                                        entry))
                        .toArray(MenuEntry[]::new));
    }

    /**
     * Hotkey or Escape pressing
     */
    @Override
    public void keyPressed(
            KeyEvent event)
    {
        if (event == null)
        {
            return;
        }

        if (!quickProfileController.isEditingNote()
                && !quickProfileController.isEditingTag()
                && suggestionService != null
                && suggestionService.handleKeyPressed(event))
        {
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                suppressCurrentEscape = true;
            }

            if (event.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
            {
                suppressCurrentSuggestionTyped = true;
            }

            return;
        }

        if (event.getKeyCode() != KeyEvent.VK_ESCAPE)
        {
            return;
        }

        /*
         * While the Note editor is open, RuneLite's ChatboxTextInput owns ESC.
         * Let it close normally so QuickProfileController can restore the profile.
         */
        if (quickProfileController.isEditingNote()
                || quickProfileController.isEditingTag())
        {
            return;
        }

        /*
         * When a Quick Profile is open, consume ESC after closing the card so
         * RuneScape does not process the same key press.
         */
        if (quickProfileController.isOpen())
        {
            quickProfileController.close();

            suppressCurrentEscape = true;

            event.consume();
        }
    }

    @Override
    public void keyReleased(
            KeyEvent event)
    {
        if (event == null)
        {
            return;
        }

        if (suppressCurrentSuggestionTyped)
        {
            event.consume();
        }

        if (event.getKeyCode() == KeyEvent.VK_ESCAPE
                && suppressCurrentEscape)
        {
            event.consume();
            suppressCurrentEscape = false;
        }
    }

    @Override
    public void keyTyped(
            KeyEvent event)
    {
        if (event == null)
        {
            return;
        }

        if (suppressCurrentSuggestionTyped)
        {
            event.consume();
            suppressCurrentSuggestionTyped = false;
        }
    }

    @Override
    public MouseEvent mouseReleased(
            MouseEvent event)
    {
        if (event.getButton()
                == MouseEvent.BUTTON1
                && suppressCurrentLeftClick)
        {
            event.consume();
        }

        return super.mouseReleased(
                event);
    }

    @Override
    public MouseEvent mouseClicked(
            MouseEvent event)
    {
        if (event.getButton()
                == MouseEvent.BUTTON1
                && suppressCurrentLeftClick)
        {
            event.consume();

            suppressCurrentLeftClick =
                    false;

            return event;
        }

        return super.mouseClicked(
                event);
    }

    /**
     * Augment RuneScape sender menus with RuneTags actions when the cursor
     * is over a native chat sender entry.
     *
     * RuneTags-owned mentions and tags are handled separately and replace
     * conflicting sender actions with their own player context menu.
     */
    private boolean augmentNativeSenderMenu(
            MenuOpened event,
            Point anchorPoint)
    {
        if (event == null
                || event.getMenuEntries() == null)
        {
            return false;
        }

        final MenuEntry[] menuEntries =
                event.getMenuEntries();

        for (int i = 0;
             i < menuEntries.length;
             i++)
        {
            final MenuEntry entry =
                    menuEntries[i];

            if (!isNativeChatReportMenuEntry(
                    entry))
            {
                continue;
            }

            final String playerName =
                    cleanPlayerName(
                            entry.getTarget());

            if (playerName.isEmpty())
            {
                return false;
            }

            /*
             * Preserve RuneScape's target markup for display; use the cleaned name only
             * for RuneTags identity resolution.
             */
            final String nativeTarget =
                    entry.getTarget() != null
                            ? entry.getTarget()
                            : playerName;

            /*
             * Retain the original right-click point so Open Profile anchors to the
             * username interaction that opened the menu.
             */
            final Point profileAnchor =
                    anchorPoint != null
                            ? new Point(
                            anchorPoint)
                            : null;

            /*
             * Reuse a RuneTags SENDER reference when available to preserve semantic state
             * such as ChatMessageType. If sender hitboxes are disabled, fall back to a
             * lightweight reference built from RuneScape's native username.
             */
            final PlayerReference senderReference =
                    nativeSenderReference(
                            playerName,
                            anchorPoint);

            /*
             * RuneScape's backing menu order is the reverse of its visual order.
             * Inserting immediately after Report therefore places Open Profile directly
             * above Report without moving the other native actions.
             */
            client.createMenuEntry(
                            i + 1)
                    .setOption(
                            MENU_OPEN_PROFILE)
                    .setTarget(
                            nativeTarget)
                    .setType(
                            MenuAction.RUNELITE)
                    .onClick(menuEntry ->
                            quickProfileController.open(
                                    senderReference,
                                    profileAnchor));

            /*
             * Target belongs at the top of the sender menu
             * and is re-resolved when selected.
             */
            if (config.targetPlayerOption()
                    && quickProfileController.canTarget(
                    senderReference))
            {
                client.createMenuEntry(-1)
                        .setOption(
                                ColorUtil.prependColorTag(
                                        MENU_TARGET,
                                        config.targetColor()))
                        .setTarget(
                                nativeTarget)
                        .setType(
                                MenuAction.RUNELITE)
                        .onClick(menuEntry ->
                                quickProfileController.target(
                                        senderReference));
            }
            return true;
        }
        return false;
    }

    /**
     * Resolve the PlayerReference used by native sender-menu actions.
     *
     * Reuse a SENDER hitbox reference when available so semantic data such as the
     * originating chat type is retained; otherwise build a minimal reference from
     * RuneScape's native username.
     */
    private PlayerReference nativeSenderReference(
            String playerName,
            Point mousePoint)
    {
        if (mousePoint != null)
        {
            final Optional<ReferenceHitbox> hit =
                    registry.find(
                            mousePoint);

            if (hit.isPresent())
            {
                final PlayerReference reference =
                        hit.get().getReference();

                if (reference != null
                        && reference.getType()
                        == ReferenceType.SENDER)
                {
                    return reference;
                }
            }
        }

        return PlayerReference.builder()
                .rawText(
                        playerName)
                .normalizedToken(
                        playerName)
                .lookupName(
                        playerName)
                .startOffset(0)
                .endOffset(
                        playerName.length())
                .type(
                        ReferenceType.SENDER)
                .locallyResolved(
                        false)
                .identity(
                        null)
                .chatType(
                        null)
                .build();
    }

    /**
     * Identify RuneScape's native Report action only when
     * it belongs to a supported player-chat surface.
     *
     * RuneTags never creates Report.
     */
    private boolean isNativeChatReportMenuEntry(
            MenuEntry entry)
    {
        return entry != null
                && entry.getOption() != null
                && NATIVE_MENU_REPORT.equals(
                Text.removeTags(
                        entry.getOption()))
                && isSupportedChatMessageEntry(
                entry.getParam1());
    }

    /**
     * Confirm that a native menu entry belongs to either the normal chatbox
     * or split-private-chat surface.
     */
    private boolean isSupportedChatMessageEntry(
            int packedWidgetId)
    {
        final int groupId =
                WidgetUtil.componentToInterface(
                        packedWidgetId);

        if (groupId == InterfaceID.CHATBOX)
        {
            return isChatboxMessageEntry(
                    packedWidgetId);
        }

        if (groupId == InterfaceID.PM_CHAT)
        {
            return isSplitPrivateMessageEntry(
                    packedWidgetId);
        }

        return false;
    }

    /**
     * Confirm that a native menu entry belongs to a normal chatbox message row.
     */
    private boolean isChatboxMessageEntry(
            int packedWidgetId)
    {
        final int groupId =
                WidgetUtil.componentToInterface(
                        packedWidgetId);

        final int childId =
                WidgetUtil.componentToId(
                        packedWidgetId);

        if (groupId != InterfaceID.CHATBOX)
        {
            return false;
        }

        final Widget widget =
                client.getWidget(
                        groupId,
                        childId);

        if (widget == null)
        {
            return false;
        }

        final Widget parent =
                widget.getParent();

        return parent != null
                && parent.getId()
                == InterfaceID.Chatbox.SCROLLAREA;
    }

    /**
     * Confirm that a native menu entry belongs to one of RuneScape's
     * split-private-chat message components.
     */
    private boolean isSplitPrivateMessageEntry(
            int packedWidgetId)
    {
        final int groupId =
                WidgetUtil.componentToInterface(
                        packedWidgetId);

        if (groupId != InterfaceID.PM_CHAT)
        {
            return false;
        }

        final int childId =
                WidgetUtil.componentToId(
                        packedWidgetId);

        final Widget widget =
                client.getWidget(
                        groupId,
                        childId);

        if (widget == null)
        {
            return false;
        }

        final Widget container =
                client.getWidget(
                        InterfaceID.PmChat.CONTAINER);

        if (container == null)
        {
            return false;
        }

        final Widget parent =
                widget.getParent();

        return widget == container
                || parent == container
                || isChildOf(
                widget,
                container);
    }

    private static boolean isChildOf(
            Widget widget,
            Widget ancestor)
    {
        if (widget == null
                || ancestor == null)
        {
            return false;
        }

        Widget current =
                widget.getParent();

        while (current != null)
        {
            if (current == ancestor
                    || current.getId()
                    == ancestor.getId())
            {
                return true;
            }

            current =
                    current.getParent();
        }

        return false;
    }

    /**
     * Convert RuneScape's menu target into a plain player name suitable for
     * RuneTags identity resolution.
     */
    private static String cleanPlayerName(
            String value)
    {
        if (value == null)
        {
            return "";
        }

        return Text.removeTags(
                        value)
                .replace(
                        '\u00A0',
                        ' ')
                .trim();
    }

    private static String menuTarget(
            PlayerReference reference)
    {
        if (reference == null)
        {
            return "";
        }

        if (reference.getLookupName() != null
                && !reference.getLookupName()
                .trim()
                .isEmpty())
        {
            return reference
                    .getLookupName()
                    .trim();
        }

        if (reference.getRawText() != null
                && !reference.getRawText()
                .trim()
                .isEmpty())
        {
            return reference
                    .getRawText()
                    .trim();
        }

        return "";
    }
}