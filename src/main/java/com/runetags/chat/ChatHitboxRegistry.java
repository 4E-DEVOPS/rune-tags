package com.runetags.chat;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ChatHitboxRegistry
{
    private volatile List<ChatReferenceHitbox> hitboxes = Collections.emptyList();

    public void replace(List<ChatReferenceHitbox> newHitboxes)
    {
        hitboxes = Collections.unmodifiableList(new ArrayList<>(newHitboxes));
    }

    public Optional<ChatReferenceHitbox> find(Point point)
    {
        for (ChatReferenceHitbox hitbox : hitboxes)
        {
            if (hitbox.contains(point))
            {
                return Optional.of(hitbox);
            }
        }

        return Optional.empty();
    }

    public List<ChatReferenceHitbox> snapshot()
    {
        return hitboxes;
    }

    public void clear()
    {
        hitboxes = Collections.emptyList();
    }
}
