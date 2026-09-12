package com.runetags.notification;

import com.runetags.Configurations;
import com.runetags.chat.TaggedMessage;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.MatchReason;

import java.awt.Color;
import java.awt.TrayIcon;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.Notifier;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class MentionNotificationServiceTest
{
    /*
     * TESTS
     */

    @Test
    public void nullTaggedMessageDoesNothing()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                null);

        Mockito.verifyNoInteractions(
                harness.notifier);

        Mockito.verifyNoInteractions(
                harness.client);
    }

    @Test
    public void nullLocalMentionMatchDoesNothing()
    {
        final TestHarness harness =
                createHarness();

        final TaggedMessage taggedMessage =
                TaggedMessage.builder()
                        .canonicalSender(
                                "Santa")
                        .originalMessage(
                                "Hello")
                        .localMentionMatch(
                                null)
                        .build();

        harness.service.notifyMention(
                taggedMessage);

        Mockito.verifyNoInteractions(
                harness.notifier);

        Mockito.verifyNoInteractions(
                harness.client);
    }

    @Test
    public void nonLocalMentionDoesNothing()
    {
        final TestHarness harness =
                createHarness();

        final TaggedMessage taggedMessage =
                TaggedMessage.builder()
                        .canonicalSender(
                                "Santa")
                        .originalMessage(
                                "Hello")
                        .localMentionMatch(
                                LocalMentionMatch.none())
                        .build();

        harness.service.notifyMention(
                taggedMessage);

        Mockito.verifyNoInteractions(
                harness.notifier);

        Mockito.verifyNoInteractions(
                harness.client);
    }

    @Test
    public void matchingLocalMentionNotifies()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "Santa: Hello"));
    }

    @Test
    public void notificationUsesRuneTagsConfiguration()
    {
        final TestHarness harness =
                createHarness();

        final Color flashColor =
                new Color(
                        20,
                        40,
                        60,
                        80);

        Mockito.when(
                        harness.config.requestFocusOnMention())
                .thenReturn(
                        RequestFocusType.FORCE);

        Mockito.when(
                        harness.config.flashOnMention())
                .thenReturn(
                        FlashNotification.SOLID_TWO_SECONDS);

        Mockito.when(
                        harness.config.flashColor())
                .thenReturn(
                        flashColor);

        Mockito.when(
                        harness.config.sendNotificationsWhenFocused())
                .thenReturn(
                        true);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        final ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(
                        Notification.class);

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        notificationCaptor.capture(),
                        Mockito.eq(
                                "Santa: Hello"));

        final Notification notification =
                notificationCaptor.getValue();

        Assert.assertTrue(
                notification.isEnabled());

        Assert.assertTrue(
                notification.isInitialized());

        Assert.assertTrue(
                notification.isOverride());

        Assert.assertFalse(
                notification.isTray());

        Assert.assertEquals(
                TrayIcon.MessageType.NONE,
                notification.getTrayIconType());

        Assert.assertEquals(
                RequestFocusType.FORCE,
                notification.getRequestFocus());

        Assert.assertEquals(
                NotificationSound.OFF,
                notification.getSound());

        Assert.assertNull(
                notification.getSoundName());

        Assert.assertEquals(
                100,
                notification.getVolume());

        Assert.assertEquals(
                0,
                notification.getTimeout());

        Assert.assertFalse(
                notification.isGameMessage());

        Assert.assertEquals(
                FlashNotification.SOLID_TWO_SECONDS,
                notification.getFlash());

        Assert.assertEquals(
                flashColor,
                notification.getFlashColor());

        Assert.assertTrue(
                notification.isSendWhenFocused());
    }

    @Test
    public void senderAndMessageProduceCombinedNotificationText()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        "Zezima",
                        "Hello Santa"));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "Zezima: Hello Santa"));
    }

    @Test
    public void missingSenderUsesMessageAsNotificationText()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        null,
                        "Hello Santa"));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "Hello Santa"));
    }

    @Test
    public void emptySenderUsesMessageAsNotificationText()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        "",
                        "Hello Santa"));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "Hello Santa"));
    }

    @Test
    public void missingMessageUsesSenderAsNotificationText()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        "Zezima",
                        null));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "Zezima"));
    }

    @Test
    public void emptyMessageUsesSenderAsNotificationText()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        "Zezima",
                        ""));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "Zezima"));
    }

    @Test
    public void missingSenderAndMessageUseFallbackNotificationText()
    {
        final TestHarness harness =
                createHarness();

        harness.service.notifyMention(
                matchingMessage(
                        null,
                        null));

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        Mockito.any(
                                Notification.class),
                        Mockito.eq(
                                "[RuneTags][Notification] Player Mentioned"));
    }

    @Test
    public void configuredMentionSoundPlaysWhenLoggedIn()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.playMentionSound())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.config.mentionSoundId())
                .thenReturn(
                        2218);

        Mockito.when(
                        harness.client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        Mockito.verify(
                        harness.client,
                        Mockito.times(
                                1))
                .playSoundEffect(
                        2218);
    }

    @Test
    public void disabledMentionSoundDoesNotPlay()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.playMentionSound())
                .thenReturn(
                        false);

        Mockito.when(
                        harness.client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .playSoundEffect(
                        Mockito.anyInt());

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .getGameState();
    }

    @Test
    public void soundDoesNotPlayWhenClientIsNotLoggedIn()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.playMentionSound())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.client.getGameState())
                .thenReturn(
                        GameState.LOGIN_SCREEN);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .playSoundEffect(
                        Mockito.anyInt());

        Mockito.verify(
                        harness.config,
                        Mockito.never())
                .mentionSoundId();
    }

    @Test
    public void zeroSoundIdDoesNotPlay()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.playMentionSound())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        Mockito.when(
                        harness.config.mentionSoundId())
                .thenReturn(
                        0);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .playSoundEffect(
                        Mockito.anyInt());
    }

    @Test
    public void negativeSoundIdDoesNotPlay()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.playMentionSound())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        Mockito.when(
                        harness.config.mentionSoundId())
                .thenReturn(
                        -1);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .playSoundEffect(
                        Mockito.anyInt());
    }

    @Test
    public void soundRemainsIndependentFromFocusedNotificationSetting()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.sendNotificationsWhenFocused())
                .thenReturn(
                        false);

        Mockito.when(
                        harness.config.playMentionSound())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.config.mentionSoundId())
                .thenReturn(
                        2218);

        Mockito.when(
                        harness.client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        harness.service.notifyMention(
                matchingMessage(
                        "Santa",
                        "Hello"));

        final ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(
                        Notification.class);

        Mockito.verify(
                        harness.notifier,
                        Mockito.times(
                                1))
                .notify(
                        notificationCaptor.capture(),
                        Mockito.eq(
                                "Santa: Hello"));

        Assert.assertFalse(
                notificationCaptor
                        .getValue()
                        .isSendWhenFocused());

        Mockito.verify(
                        harness.client,
                        Mockito.times(
                                1))
                .playSoundEffect(
                        2218);
    }

    /*
     * HELPERS
     */

    private static TestHarness createHarness()
    {
        final Client client =
                Mockito.mock(
                        Client.class);

        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        final Notifier notifier =
                Mockito.mock(
                        Notifier.class);

        Mockito.when(
                        config.requestFocusOnMention())
                .thenReturn(
                        RequestFocusType.REQUEST);

        Mockito.when(
                        config.flashOnMention())
                .thenReturn(
                        FlashNotification.FLASH_TWO_SECONDS);

        Mockito.when(
                        config.flashColor())
                .thenReturn(
                        new Color(
                                255,
                                150,
                                0,
                                70));

        Mockito.when(
                        config.sendNotificationsWhenFocused())
                .thenReturn(
                        false);

        Mockito.when(
                        config.playMentionSound())
                .thenReturn(
                        false);

        return new TestHarness(
                client,
                config,
                notifier,
                new MentionNotificationService(
                        client,
                        config,
                        notifier));
    }

    private static TaggedMessage matchingMessage(
            String sender,
            String message)
    {
        return TaggedMessage.builder()
                .canonicalSender(
                        sender)
                .originalMessage(
                        message)
                .localMentionMatch(
                        new LocalMentionMatch(
                                true,
                                MatchReason.ACCOUNT_NAME,
                                "Santa"))
                .build();
    }

    private static final class TestHarness
    {
        private final Client client;
        private final Configurations config;
        private final Notifier notifier;
        private final MentionNotificationService service;

        private TestHarness(
                Client client,
                Configurations config,
                Notifier notifier,
                MentionNotificationService service)
        {
            this.client =
                    client;

            this.config =
                    config;

            this.notifier =
                    notifier;

            this.service =
                    service;
        }
    }
}