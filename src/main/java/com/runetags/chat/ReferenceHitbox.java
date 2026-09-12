package com.runetags.chat;

import com.runetags.reference.PlayerReference;

import java.awt.Rectangle;

import lombok.Value;

@Value
public class ReferenceHitbox
{
    long messageId;
    Rectangle bounds;
    PlayerReference reference;
    ReferenceLayoutService.Surface surface;

    public boolean contains(java.awt.Point point)
    {
        return bounds != null
                && point != null
                && bounds.contains(point);
    }
}