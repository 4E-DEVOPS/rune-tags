package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.config.ClickablePlayerMode;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

public final class ChatInteractionPolicy
{
    private ChatInteractionPolicy()
    {
    }

    public static boolean isClickable(PlayerReference reference, Configurations config)
    {
        if (reference == null || config == null)
        {
            return false;
        }

        final ClickablePlayerMode mode = config.clickablePlayers();

        switch (mode)
        {
            case ALL:
                return true;

            case MENTIONS:
                return reference.getType() == ReferenceType.MENTION
                    || reference.getType() == ReferenceType.TAG;

            case TAGGED_ONLY:
                return reference.getType() == ReferenceType.TAG;

            default:
                return false;
        }
    }
}
