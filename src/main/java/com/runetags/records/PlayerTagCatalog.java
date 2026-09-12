package com.runetags.records;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed, user-selectable local player Tags. */
public final class PlayerTagCatalog
{
    public static final int MAX_TAGS_PER_PLAYER = 5;

    public static final List<String> ALL =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "Alt",
                            "Avoid",
                            "BiS",
                            "Carrier",
                            "Chill",
                            "Drama",
                            "Elite",
                            "FFA",
                            "Hybrid",
                            "Impatient",
                            "Learner",
                            "Leech",
                            "Lucky",
                            "Magic",
                            "Melee",
                            "Mentor",
                            "Mobile",
                            "Muted",
                            "MVP",
                            "Noob",
                            "Plank",
                            "PvM",
                            "PvP",
                            "Raider",
                            "Ranged",
                            "Risks",
                            "Safe",
                            "Scammer",
                            "Skipper",
                            "Splits",
                            "Spoon",
                            "Sweaty",
                            "Tank",
                            "Toxic",
                            "Troll",
                            "Trusted",
                            "Unreliable",
                            "Verified"));

    private PlayerTagCatalog()
    {
    }

    public static String canonical(String value)
    {
        if (value == null)
        {
            return null;
        }

        final String clean = value.trim();

        for (String tag : ALL)
        {
            if (tag.equalsIgnoreCase(clean))
            {
                return tag;
            }
        }

        return null;
    }
}
