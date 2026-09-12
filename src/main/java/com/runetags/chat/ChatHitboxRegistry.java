package com.runetags.chat;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ChatHitboxRegistry
{
    private volatile List<ReferenceHitbox> hitboxes = Collections.emptyList();

    public void replace(List<ReferenceHitbox> newHitboxes)
    {
        hitboxes = Collections.unmodifiableList(new ArrayList<>(newHitboxes));
    }

    public Optional<ReferenceHitbox> find(Point point)
    {
        for (ReferenceHitbox hitbox : hitboxes)
        {
            if (hitbox.contains(point))
            {
                return Optional.of(hitbox);
            }
        }

        return Optional.empty();
    }

    public List<ReferenceHitbox> snapshot()
    {
        return hitboxes;
    }

    public void clear()
    {
        hitboxes = Collections.emptyList();
    }
}
