package com.runetags.history;

import java.lang.reflect.Method;
import java.time.Instant;

import net.runelite.api.ChatMessageType;

import org.junit.Assert;
import org.junit.Test;

public class MentionHistoryPanelTest
{
    /*
     * TESTS
     */

    @Test
    public void publicChatTypesAreReadable()
            throws Exception
    {
        Assert.assertEquals(
                "Public",
                readableChatType(
                        ChatMessageType.PUBLICCHAT));

        Assert.assertEquals(
                "Public",
                readableChatType(
                        ChatMessageType.MODCHAT));
    }

    @Test
    public void friendsChatTypeIsReadable()
            throws Exception
    {
        Assert.assertEquals(
                "Friends Chat",
                readableChatType(
                        ChatMessageType.FRIENDSCHAT));
    }

    @Test
    public void clanChatTypesAreReadable()
            throws Exception
    {
        Assert.assertEquals(
                "Clan",
                readableChatType(
                        ChatMessageType.CLAN_CHAT));

        Assert.assertEquals(
                "Clan",
                readableChatType(
                        ChatMessageType.CLAN_GIM_CHAT));
    }

    @Test
    public void guestClanTypeIsReadable()
            throws Exception
    {
        Assert.assertEquals(
                "Guest Clan",
                readableChatType(
                        ChatMessageType.CLAN_GUEST_CHAT));
    }

    @Test
    public void privateChatTypesAreReadable()
            throws Exception
    {
        Assert.assertEquals(
                "Private",
                readableChatType(
                        ChatMessageType.PRIVATECHAT));

        Assert.assertEquals(
                "Private",
                readableChatType(
                        ChatMessageType.MODPRIVATECHAT));

        Assert.assertEquals(
                "Private",
                readableChatType(
                        ChatMessageType.PRIVATECHATOUT));
    }

    @Test
    public void nullChatTypeProducesEmptyText()
            throws Exception
    {
        Assert.assertEquals(
                "",
                readableChatType(
                        null));
    }

    @Test
    public void unhandledChatTypeUsesEnumName()
            throws Exception
    {
        Assert.assertEquals(
                ChatMessageType.GAMEMESSAGE.name(),
                readableChatType(
                        ChatMessageType.GAMEMESSAGE));
    }

    @Test
    public void contextIncludesWorldAndLocation()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        301,
                        "Lumbridge",
                        null,
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "World 301 • Lumbridge",
                contextText(
                        entry));
    }

    @Test
    public void contextSupportsWorldOnly()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        301,
                        null,
                        null,
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "World 301",
                contextText(
                        entry));
    }

    @Test
    public void contextSupportsLocationOnly()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        null,
                        "Lumbridge",
                        null,
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "Lumbridge",
                contextText(
                        entry));
    }

    @Test
    public void contextIgnoresBlankLocation()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        301,
                        "   ",
                        null,
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "World 301",
                contextText(
                        entry));
    }

    @Test
    public void emptyContextProducesEmptyText()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        null,
                        null,
                        null,
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "",
                contextText(
                        entry));
    }

    @Test
    public void channelIncludesTypeAndChannelName()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        null,
                        null,
                        "Party Hat",
                        ChatMessageType.FRIENDSCHAT);

        Assert.assertEquals(
                "Friends Chat • Party Hat",
                channelText(
                        entry));
    }

    @Test
    public void channelWithoutNameUsesChatTypeOnly()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        null,
                        null,
                        null,
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "Public",
                channelText(
                        entry));
    }

    @Test
    public void blankChannelNameUsesChatTypeOnly()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        null,
                        null,
                        "   ",
                        ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                "Public",
                channelText(
                        entry));
    }

    @Test
    public void nullChatTypeWithChannelStillShowsChannel()
            throws Exception
    {
        final MentionHistoryEntry entry =
                entry(
                        null,
                        null,
                        "Party Hat",
                        null);

        Assert.assertEquals(
                " • Party Hat",
                channelText(
                        entry));
    }

    @Test
    public void safeReturnsOriginalValue()
            throws Exception
    {
        Assert.assertEquals(
                "Santa",
                safe(
                        "Santa",
                        "Unknown"));
    }

    @Test
    public void safeUsesFallbackForNullAndBlankValues()
            throws Exception
    {
        Assert.assertEquals(
                "Unknown",
                safe(
                        null,
                        "Unknown"));

        Assert.assertEquals(
                "Unknown",
                safe(
                        "",
                        "Unknown"));

        Assert.assertEquals(
                "Unknown",
                safe(
                        "   ",
                        "Unknown"));
    }

    @Test
    public void nullTimestampProducesEmptyAge()
            throws Exception
    {
        Assert.assertEquals(
                "",
                ageText(
                        null));
    }

    @Test
    public void veryRecentTimestampDisplaysNow()
            throws Exception
    {
        Assert.assertEquals(
                "now",
                ageText(
                        Instant.now()));
    }

    @Test
    public void futureTimestampIsClampedToNow()
            throws Exception
    {
        Assert.assertEquals(
                "now",
                ageText(
                        Instant.now()
                                .plusSeconds(
                                        60)));
    }

    @Test
    public void minuteAgeUsesMinuteSuffix()
            throws Exception
    {
        final String text =
                ageText(
                        Instant.now()
                                .minusSeconds(
                                        5 * 60L));

        Assert.assertTrue(
                text.equals(
                        "5m")
                        || text.equals(
                        "4m"));
    }

    @Test
    public void hourAgeUsesHourSuffix()
            throws Exception
    {
        final String text =
                ageText(
                        Instant.now()
                                .minusSeconds(
                                        3 * 60L * 60L));

        Assert.assertTrue(
                text.equals(
                        "3h")
                        || text.equals(
                        "2h"));
    }

    @Test
    public void dayAgeUsesDaySuffix()
            throws Exception
    {
        final String text =
                ageText(
                        Instant.now()
                                .minusSeconds(
                                        2 * 24L * 60L * 60L));

        Assert.assertTrue(
                text.equals(
                        "2d")
                        || text.equals(
                        "1d"));
    }

    /*
     * HELPERS
     */

    private static MentionHistoryEntry entry(
            Integer world,
            String location,
            String channel,
            ChatMessageType chatType)
    {
        return new MentionHistoryEntry(
                1L,
                "Santa",
                "Message",
                chatType,
                null,
                world,
                location,
                channel,
                Instant.EPOCH);
    }

    private static String readableChatType(
            ChatMessageType type)
            throws Exception
    {
        return (String) invokeStatic(
                "readableChatType",
                new Class<?>[]
                        {
                                ChatMessageType.class
                        },
                type);
    }

    private static String contextText(
            MentionHistoryEntry entry)
            throws Exception
    {
        return (String) invokeStatic(
                "contextText",
                new Class<?>[]
                        {
                                MentionHistoryEntry.class
                        },
                entry);
    }

    private static String channelText(
            MentionHistoryEntry entry)
            throws Exception
    {
        return (String) invokeStatic(
                "channelText",
                new Class<?>[]
                        {
                                MentionHistoryEntry.class
                        },
                entry);
    }

    private static String ageText(
            Instant timestamp)
            throws Exception
    {
        return (String) invokeStatic(
                "ageText",
                new Class<?>[]
                        {
                                Instant.class
                        },
                timestamp);
    }

    private static String safe(
            String value,
            String fallback)
            throws Exception
    {
        return (String) invokeStatic(
                "safe",
                new Class<?>[]
                        {
                                String.class,
                                String.class
                        },
                value,
                fallback);
    }

    private static Object invokeStatic(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments)
            throws Exception
    {
        final Method method =
                MentionHistoryPanel.class.getDeclaredMethod(
                        methodName,
                        parameterTypes);

        method.setAccessible(
                true);

        return method.invoke(
                null,
                arguments);
    }
}