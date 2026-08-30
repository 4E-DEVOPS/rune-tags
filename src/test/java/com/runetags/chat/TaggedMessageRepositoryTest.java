package com.runetags.chat;

import com.runetags.model.TaggedMessage;
import org.junit.Assert;
import org.junit.Test;

public class TaggedMessageRepositoryTest
{
    @Test
    public void trimsOldestMessages()
    {
        TaggedMessageRepository repository = new TaggedMessageRepository(2);
        repository.add(TaggedMessage.builder().id(1).originalMessage("one").build());
        repository.add(TaggedMessage.builder().id(2).originalMessage("two").build());
        repository.add(TaggedMessage.builder().id(3).originalMessage("three").build());

        Assert.assertEquals(2, repository.size());
        Assert.assertFalse(repository.get(1).isPresent());
        Assert.assertTrue(repository.get(2).isPresent());
        Assert.assertTrue(repository.get(3).isPresent());
    }
}
