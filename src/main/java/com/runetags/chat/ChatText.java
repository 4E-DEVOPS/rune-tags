package com.runetags.chat;

import net.runelite.client.util.Text;

public final class ChatText
{
    private ChatText() {}

    public static String toSemanticPlain(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return "";
        }

        String protectedText = raw.replace("<at>", "\u0001");
        return Text.removeTags(protectedText).replace('\u0001', '@');
    }
}
