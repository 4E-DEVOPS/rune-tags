package com.runetags.input;

import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ChatReferenceHitbox;
import com.runetags.quickprofile.QuickProfileController;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Optional;
import net.runelite.client.input.MouseAdapter;

public class ChatReferenceMouseListener extends MouseAdapter
{
    private final ChatHitboxRegistry registry;
    private final QuickProfileController quickProfileController;
    private boolean suppressCurrentLeftClick;

    public ChatReferenceMouseListener(ChatHitboxRegistry registry, QuickProfileController quickProfileController)
    {
        this.registry = registry;
        this.quickProfileController = quickProfileController;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        if (event.getButton() != MouseEvent.BUTTON1)
        {
            return super.mousePressed(event);
        }

        Point point = event.getPoint();

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

        Optional<ChatReferenceHitbox> hit = registry.find(point);
        if (hit.isPresent())
        {
            quickProfileController.open(hit.get().getReference(), point);
            suppressCurrentLeftClick = true;
            event.consume();
            return event;
        }

        if (quickProfileController.isOpen())
        {
            quickProfileController.close();
        }

        return super.mousePressed(event);
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event)
    {
        if (event.getButton() == MouseEvent.BUTTON1 && suppressCurrentLeftClick)
        {
            event.consume();
        }
        return super.mouseReleased(event);
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event)
    {
        if (event.getButton() == MouseEvent.BUTTON1 && suppressCurrentLeftClick)
        {
            event.consume();
            suppressCurrentLeftClick = false;
            return event;
        }
        return super.mouseClicked(event);
    }
}
