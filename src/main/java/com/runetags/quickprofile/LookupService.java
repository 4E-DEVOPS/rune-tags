package com.runetags.quickprofile;

import com.runetags.RuneTagsConfig;
import com.runetags.config.LookupProvider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.util.LinkBrowser;

@Slf4j
public class LookupService
{
    private final RuneTagsConfig config;

    public LookupService(RuneTagsConfig config)
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

        log.debug(
                "[RuneTags] Lookup Player='{}' | Provider={}",
                playerName,
                provider);
    }

    private static void openWiseOldMan(String playerName)
    {
        LinkBrowser.browse(
                "https://wiseoldman.net/players/"
                        + encodePath(playerName));
    }

    private static void openRuneProfile(String playerName)
    {
        /*
         * RuneProfile uses:
         * runeprofile.com/<username>
         */
        LinkBrowser.browse(
                "https://runeprofile.com/"
                        + encodePath(playerName));
    }

    private static void openHiscores(String playerName)
    {
        /*
         * Official OSRS personal Hiscores lookup.
         */
        LinkBrowser.browse(
                "https://secure.runescape.com/m=hiscore_oldschool/hiscorepersonal?user1="
                        + encodeQuery(playerName));
    }

    private static String encodePath(String value)
    {
        /*
         * RuneScape spaces are accepted as URL-encoded spaces.
         * URLEncoder uses '+' for spaces, so convert those for path usage.
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