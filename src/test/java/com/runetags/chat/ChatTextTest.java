package com.runetags.chat;

import org.junit.Assert;
import org.junit.Test;

public class ChatTextTest
{
    @Test
    public void preservesVisibleAtControlTag()
    {
        Assert.assertEquals("@definitelyunknown123",
            ChatText.toSemanticPlain("<at>definitelyunknown123"));
    }

    @Test
    public void stripsFormattingButPreservesAt()
    {
        Assert.assertEquals("@Jeff",
            ChatText.toSemanticPlain("<col=ff0000><at>Jeff</col>"));
    }
}
