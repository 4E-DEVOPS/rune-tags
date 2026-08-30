package com.runetags.chat;

import java.util.Arrays;
import net.runelite.client.util.Text;

/**
 * Maps offsets from RuneTags' plain semantic message back to the raw RuneLite
 * message string, preserving any existing Jagex/RuneLite formatting tags.
 *
 * This lets RuneTags add formatting around exact semantic spans without
 * rebuilding or visibly rewriting the sender's original message.
 */
public final class MessageMarkupMap
{
    private static final String AT_TAG = "<at>";

    private final String plain;
    private final int[] plainBoundaryToRaw;

    private MessageMarkupMap(String plain, int[] plainBoundaryToRaw)
    {
        this.plain = plain;
        this.plainBoundaryToRaw = plainBoundaryToRaw;
    }

    public static MessageMarkupMap create(String raw)
    {
        if (raw == null)
        {
            raw = "";
        }

        String expectedPlain = ChatText.toSemanticPlain(raw);
        int[] boundaries = new int[expectedPlain.length() + 1];
        Arrays.fill(boundaries, -1);

        int plainIndex = 0;
        int rawIndex = 0;
        boundaries[0] = 0;

        while (rawIndex < raw.length())
        {
            if (raw.startsWith(AT_TAG, rawIndex))
            {
                boundaries[plainIndex] = rawIndex;
                plainIndex++;
                rawIndex += AT_TAG.length();
                boundaries[plainIndex] = rawIndex;
                continue;
            }

            char c = raw.charAt(rawIndex);
            if (c == '<')
            {
                int close = raw.indexOf('>', rawIndex);
                if (close >= 0)
                {
                    // Text.removeTags() removes RuneLite/Jagex markup from the
                    // semantic message. Skip the same markup here.
                    rawIndex = close + 1;
                    continue;
                }
            }

            if (plainIndex < expectedPlain.length())
            {
                boundaries[plainIndex] = rawIndex;
                plainIndex++;
                boundaries[plainIndex] = rawIndex + 1;
            }

            rawIndex++;
        }

        // Empty trailing markup can make the final boundary point before the
        // physical end of the raw string. For insertion at the end of visible
        // text we want to remain before trailing tags, which is intentional.
        if (expectedPlain.isEmpty())
        {
            boundaries[0] = 0;
        }

        return new MessageMarkupMap(expectedPlain, boundaries);
    }

    public boolean matchesPlain(String semanticPlain)
    {
        return plain.equals(semanticPlain == null ? "" : semanticPlain);
    }

    public int rawBoundary(int plainOffset)
    {
        if (plainOffset < 0 || plainOffset >= plainBoundaryToRaw.length)
        {
            return -1;
        }
        return plainBoundaryToRaw[plainOffset];
    }
}