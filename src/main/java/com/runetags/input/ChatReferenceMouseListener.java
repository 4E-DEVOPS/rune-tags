package com.runetags.input;

import com.runetags.RuneTagsConfig;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ChatReferenceHitbox;
import com.runetags.config.ChatInteractionMode;
import com.runetags.model.PlayerReference;
import com.runetags.model.ReferenceType;
import com.runetags.quickprofile.QuickProfileController;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Optional;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.input.MouseAdapter;

public class ChatReferenceMouseListener extends MouseAdapter
{
    private static final String MENU_OPEN_PROFILE = "Open Profile";
    private static final String MENU_LOOKUP = "Lookup";

    private final Client client;
    private final RuneTagsConfig config;
    private final ChatHitboxRegistry registry;
    private final QuickProfileController quickProfileController;

    private boolean suppressCurrentLeftClick;

    public ChatReferenceMouseListener(
            Client client,
            RuneTagsConfig config,
            ChatHitboxRegistry registry,
            QuickProfileController quickProfileController)
    {
        this.client = client;
        this.config = config;
        this.registry = registry;
        this.quickProfileController = quickProfileController;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        /*
         * A right-click begins a new context-menu interaction.
         *
         * Close any currently open QuickCard immediately. The native menu or
         * RuneTags tag menu which follows should become the active interaction.
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
         * Absolute input priority:
         *
         * If RuneScape already has a context menu open, this left-click belongs
         * to that menu. Never allow an underlying RuneTags hitbox to receive it.
         *
         * Importantly, do NOT consume the event here. RuneScape must receive the
         * click so its selected menu option can execute normally.
         */
        if (client.isMenuOpen())
        {
            suppressCurrentLeftClick = false;
            return super.mousePressed(event);
        }

        final Point point = event.getPoint();

        /*
         * Existing QuickCard controls retain priority over chat references.
         */
        if (quickProfileController.isOpen())
        {
            if (quickProfileController.isCloseButton(point))
            {
                quickProfileController.close();
                suppressCurrentLeftClick = true;
                event.consume();
                return event;
            }

            if (quickProfileController.isTargetButton(point))
            {
                quickProfileController.target();
                suppressCurrentLeftClick = true;
                event.consume();
                return event;
            }

            if (quickProfileController.isLookupButton(point))
            {
                quickProfileController.lookup();
                suppressCurrentLeftClick = true;
                event.consume();
                return event;
            }

            if (quickProfileController.isClanLink(point))
            {
                quickProfileController.lookupClan();
                suppressCurrentLeftClick = true;
                event.consume();
                return event;
            }

            if (quickProfileController.isInsideCard(point))
            {
                suppressCurrentLeftClick = true;
                event.consume();
                return event;
            }
        }

        final ChatInteractionMode interactionMode =
                config.chatInteractionMode();

        /*
         * LEFT_CLICK and BOTH permit direct QuickCard activation.
         *
         * RIGHT_CLICK deliberately leaves a normal left-click untouched.
         */
        if (interactionMode != null
                && interactionMode.allowsLeftClick())
        {
            final Optional<ChatReferenceHitbox> hit =
                    registry.find(point);

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
         * Preserve the existing behavior where clicking elsewhere closes an
         * already-open QuickCard.
         */
        if (quickProfileController.isOpen())
        {
            quickProfileController.close();
        }

        return super.mousePressed(event);
    }

    /**
     * Called from RuneTagsPlugin when RuneScape opens a context menu.
     *
     * Sender references deliberately retain RuneScape's native menu.
     *
     * MENTION and TAG references may receive RuneTags-only client actions when
     * RIGHT_CLICK or BOTH interaction mode is enabled.
     */
    public void onMenuOpened(MenuOpened event)
    {
        /*
         * Menu ownership always wins over an existing QuickCard.
         *
         * This is also a second safety net in case a menu was opened through a
         * path other than an ordinary BUTTON3 mouse press.
         */
        if (quickProfileController.isOpen())
        {
            quickProfileController.close();
        }

        final ChatInteractionMode interactionMode =
                config.chatInteractionMode();

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

        final Optional<ChatReferenceHitbox> hit =
                registry.find(mousePoint);

        if (!hit.isPresent())
        {
            return;
        }

        final ChatReferenceHitbox referenceHitbox =
                hit.get();

        final PlayerReference reference =
                referenceHitbox.getReference();

        if (reference == null)
        {
            return;
        }

        /*
         * Never replace or augment the real sender-name interaction.
         *
         * RuneScape already owns that context menu and its Add/Message/Lookup/
         * Ignore/etc. semantics.
         *
         * RuneTags' menu exists only for semantic references inside message text.
         */
        if (reference.getType() == ReferenceType.SENDER)
        {
            return;
        }

        if (reference.getType() != ReferenceType.MENTION
                && reference.getType() != ReferenceType.TAG)
        {
            return;
        }

        final Point anchorPoint =
                new Point(mousePoint);

        final String target =
                menuTarget(reference);

        /*
         * These are RuneTags/client-side actions only.
         *
         * We intentionally do NOT synthesize native Add Friend, Message,
         * Ignore, Report, or other game actions.
         */
        client.createMenuEntry(-1)
                .setOption(MENU_OPEN_PROFILE)
                .setTarget(target)
                .setType(MenuAction.RUNELITE)
                .onClick(entry ->
                        quickProfileController.open(
                                reference,
                                anchorPoint));

        client.createMenuEntry(-2)
                .setOption(MENU_LOOKUP)
                .setTarget(target)
                .setType(MenuAction.RUNELITE)
                .onClick(entry ->
                        quickProfileController.lookup(
                                reference));
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event)
    {
        if (event.getButton() == MouseEvent.BUTTON1
                && suppressCurrentLeftClick)
        {
            event.consume();
        }

        return super.mouseReleased(event);
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event)
    {
        if (event.getButton() == MouseEvent.BUTTON1
                && suppressCurrentLeftClick)
        {
            event.consume();
            suppressCurrentLeftClick = false;
            return event;
        }

        return super.mouseClicked(event);
    }

    private static String menuTarget(
            PlayerReference reference)
    {
        if (reference == null)
        {
            return "";
        }

        if (reference.getLookupName() != null
                && !reference.getLookupName().trim().isEmpty())
        {
            return reference.getLookupName().trim();
        }

        if (reference.getRawText() != null
                && !reference.getRawText().trim().isEmpty())
        {
            return reference.getRawText().trim();
        }

        return "";
    }
}