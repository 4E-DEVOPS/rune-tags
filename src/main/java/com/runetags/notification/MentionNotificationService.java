package com.runetags.notification;

import com.runetags.RuneTagsConfig;
import com.runetags.model.TaggedMessage;

import java.awt.TrayIcon;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;

public class MentionNotificationService
{
    private final Client client;
    private final RuneTagsConfig config;
    private final Notifier notifier;

    public MentionNotificationService(
            Client client,
            RuneTagsConfig config,
            Notifier notifier)
    {
        this.client = client;
        this.config = config;
        this.notifier = notifier;
    }

    public void notifyMention(
            TaggedMessage taggedMessage)
    {
        if (taggedMessage == null
                || taggedMessage.getLocalMentionMatch() == null
                || !taggedMessage
                .getLocalMentionMatch()
                .isMatchesLocalPlayer())
        {
            return;
        }

        /*
         * RuneLite owns focus and flash behavior.
         *
         * This Notification is explicitly initialized + overridden so RuneTags'
         * settings are used instead of RuneLite's global notification config.
         *
         * RuneTags intentionally disables:
         * - tray notifications
         * - native/custom RuneLite notification sounds
         * - game-message notifications
         *
         * Our separate in-game mention sound is handled below.
         */
        final Notification notification =
                new Notification(
                        true,                               // enabled
                        true,                               // initialized
                        true,                               // override
                        false,                              // tray
                        TrayIcon.MessageType.NONE,          // tray icon type
                        config.requestFocusOnMention(),     // request focus
                        NotificationSound.OFF,              // RuneLite sound
                        null,                               // custom sound name
                        100,                                // unused volume
                        0,                                  // unused timeout
                        false,                              // game message
                        config.flashOnMention(),            // flash mode
                        config.flashColor(),                // flash color
                        config.sendNotificationsWhenFocused());

        notifier.notify(
                notification,
                notificationText(taggedMessage));

        /*
         * The RuneTags game sound is intentionally independent from the
         * "Send Notifications When Focused" setting.
         *
         * Therefore:
         *
         * focused + Send Notifications When Focused OFF
         * -> no focus request
         * -> no flash
         * -> sound still plays
         */
        playMentionSound();
    }

    private void playMentionSound()
    {
        if (!config.playMentionSound())
        {
            return;
        }

        if (client.getGameState()
                != GameState.LOGGED_IN)
        {
            return;
        }

        final int soundId =
                config.mentionSoundId();

        if (soundId <= 0)
        {
            return;
        }

        client.playSoundEffect(
                soundId);
    }

    private static String notificationText(
            TaggedMessage taggedMessage)
    {
        final String sender =
                taggedMessage.getCanonicalSender();

        final String message =
                taggedMessage.getOriginalMessage();

        if (sender == null
                || sender.isEmpty())
        {
            return message != null
                    && !message.isEmpty()
                    ? message
                    : "[RuneTags] Player mentioned";
        }

        if (message == null
                || message.isEmpty())
        {
            return sender;
        }

        return sender
                + ": "
                + message;
    }
}