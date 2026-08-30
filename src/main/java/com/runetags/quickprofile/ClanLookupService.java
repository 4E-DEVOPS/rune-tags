package com.runetags.quickprofile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.util.LinkBrowser;

@Slf4j
public class ClanLookupService
{
    private static final String WOM_GROUP_SEARCH_URL =
            "https://wiseoldman.net/groups?search=";

    public void search(String clanName)
    {
        if (clanName == null
                || clanName.trim().isEmpty())
        {
            return;
        }

        final String encodedName =
                URLEncoder.encode(
                        clanName.trim(),
                        StandardCharsets.UTF_8);

        final String url =
                WOM_GROUP_SEARCH_URL + encodedName;

        log.debug(
                "[RuneTags] Wise Old Man clan search | Clan='{}'",
                clanName);

        LinkBrowser.browse(url);
    }
}