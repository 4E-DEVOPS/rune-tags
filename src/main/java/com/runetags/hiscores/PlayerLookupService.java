package com.runetags.hiscores;

import com.runetags.Configurations;
import com.runetags.config.LookupProvider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.util.LinkBrowser;

@Slf4j
public class PlayerLookupService
{
    private final Configurations config;

    public PlayerLookupService(Configurations config)
    {
        this.config = config;
    }

    public void lookup(String playerName)
    {
        if (playerName == null || playerName.trim().isEmpty())
        {
            return;
        }

        final LookupProvider provider =
                config.lookupProvider();

        switch (provider)
        {
            case WISE_OLD_MAN:
                openWiseOldMan(playerName);
                break;

            case RUNE_PROFILE:
                openRuneProfile(playerName);
                break;

            case HISCORES:
            default:
                openHiscores(playerName);
                break;
        }

        //log.debug("[RuneTags][Lookup] Player='{}' | Provider={}", playerName, provider);
    }

    private static void openWiseOldMan(String playerName)
    {
        LinkBrowser.browse(
                "https://wiseoldman.net/players/"
                        + encodePath(playerName));
    }

    private static void openRuneProfile(String playerName)
    {
        LinkBrowser.browse(
                "https://runeprofile.com/"
                        + encodePath(playerName));
    }

    private static void openHiscores(String playerName)
    {
        LinkBrowser.browse(
                "https://secure.runescape.com/m=hiscore_oldschool/hiscorepersonal?user1="
                        + encodeQuery(playerName));
    }

    private static String encodePath(String value)
    {
        /*
         * URLEncoder emits '+' for spaces;
         * path segments require percent-encoded spaces.
         */
        return URLEncoder.encode(
                        value.trim(),
                        StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String encodeQuery(String value)
    {
        return URLEncoder.encode(
                value.trim(),
                StandardCharsets.UTF_8);
    }
}