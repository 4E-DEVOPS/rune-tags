package com.runetags.chat;

import com.runetags.model.TaggedMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class TaggedMessageRepository
{
    private final int capacity;
    private final Deque<TaggedMessage> messages = new ArrayDeque<>();

    public TaggedMessageRepository(int capacity)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    public synchronized void add(TaggedMessage message)
    {
        if (message == null)
        {
            return;
        }
        messages.addLast(message);
        while (messages.size() > capacity)
        {
            messages.removeFirst();
        }
    }

    public synchronized Optional<TaggedMessage> get(long id)
    {
        return messages.stream().filter(m -> m.getId() == id).findFirst();
    }

    public synchronized List<TaggedMessage> snapshot()
    {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public synchronized int size()
    {
        return messages.size();
    }

    public synchronized void clear()
    {
        messages.clear();
    }
}
