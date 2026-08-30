package com.runetags.chat;

import com.runetags.model.PlayerReference;

import java.awt.Rectangle;

import lombok.Value;

@Value
public class ChatReferenceHitbox
{
    long messageId;
    Rectangle bounds;
    PlayerReference reference;
    ChatReferenceLayoutService.Surface surface;

    public boolean contains(java.awt.Point point)
    {
        return bounds != null
                && point != null
                && bounds.contains(point);
    }
}