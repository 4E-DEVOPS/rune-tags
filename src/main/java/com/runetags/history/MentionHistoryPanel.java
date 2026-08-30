package com.runetags.history;

import com.runetags.quickprofile.QuickProfileController;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Point;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class MentionHistoryPanel extends PluginPanel
{
    private final Client client;
    private final MentionHistoryService historyService;
    private final QuickProfileController quickProfileController;

    private final JPanel entriesPanel =
            new JPanel();

    private final JLabel countLabel =
            new JLabel();

    /*
     * Age labels are refreshed independently from the history entries so relative
     * timestamps continue advancing even when no new mentions arrive.
     */
    private final List<AgeLabelBinding> ageLabels =
            new ArrayList<>();

    private final Timer ageRefreshTimer;

    public MentionHistoryPanel(
            Client client,
            MentionHistoryService historyService,
            QuickProfileController quickProfileController)
    {
        super(false);

        this.client =
                client;

        this.historyService =
                historyService;

        this.quickProfileController =
                quickProfileController;

        setLayout(
                new BorderLayout());

        add(
                buildHeader(),
                BorderLayout.NORTH);

        entriesPanel.setLayout(
                new BoxLayout(
                        entriesPanel,
                        BoxLayout.Y_AXIS));

        entriesPanel.setBackground(
                ColorScheme.DARK_GRAY_COLOR);

        final JScrollPane scrollPane =
                new JScrollPane(
                        entriesPanel);

        scrollPane.setBorder(null);

        scrollPane.getVerticalScrollBar()
                .setUnitIncrement(16);

        add(
                scrollPane,
                BorderLayout.CENTER);

        /*
         * Fixed footer.
         *
         * The footer remains visible while only the history entries scroll.
         */
        add(
                buildFooter(),
                BorderLayout.SOUTH);

        /*
         * Relative ages only have minute-level precision after "now", so refreshing
         * once every 30 seconds is frequent enough to keep the display current
         * without rebuilding the history panel.
         */
        ageRefreshTimer =
                new Timer(
                        30_000,
                        event -> refreshAgeLabels());

        ageRefreshTimer.start();

        reload();
    }

    private JPanel buildHeader()
    {
        final JPanel wrapper =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                0,
                                0));

        wrapper.setBackground(
                ColorScheme.DARK_GRAY_COLOR);

        wrapper.setBorder(
                BorderFactory.createEmptyBorder(
                        8,
                        8,
                        8,
                        8));

        final JLabel title =
                new JLabel(
                        "[ Mention History ]");

        title.setFont(
                title.getFont()
                        .deriveFont(
                                Font.BOLD,
                                16f));

        wrapper.add(title);

        return wrapper;
    }

    private JPanel buildFooter()
    {
        final JPanel footer =
                new JPanel();

        footer.setLayout(
                new BoxLayout(
                        footer,
                        BoxLayout.Y_AXIS));

        footer.setBackground(
                ColorScheme.DARK_GRAY_COLOR);

        footer.setBorder(
                BorderFactory.createEmptyBorder(
                        6,
                        8,
                        8,
                        8));

        /*
         * Mention count.
         */
        final JPanel countRow =
                new JPanel(
                        new BorderLayout());

        countRow.setBackground(
                ColorScheme.DARK_GRAY_COLOR);

        countLabel.setHorizontalAlignment(
                JLabel.RIGHT);

        countRow.add(
                countLabel,
                BorderLayout.EAST);

        footer.add(countRow);

        footer.add(
                Box.createVerticalStrut(5));

        /*
         * Centered Clear History button.
         */
        final JPanel buttonRow =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.CENTER,
                                0,
                                0));

        buttonRow.setBackground(
                ColorScheme.DARK_GRAY_COLOR);

        final JButton clearButton =
                new JButton(
                        "Clear History");

        clearButton.addActionListener(event ->
        {
            historyService.clear();
            reload();
        });

        buttonRow.add(clearButton);

        footer.add(buttonRow);

        return footer;
    }

    private static class RoundedPanel extends JPanel
    {
        private final int arc;
        private final Color borderColor;

        private RoundedPanel(
                int arc,
                Color borderColor)
        {
            this.arc =
                    arc;

            this.borderColor =
                    borderColor;

            setOpaque(false);
        }

        @Override
        protected void paintComponent(
                java.awt.Graphics graphics)
        {
            super.paintComponent(graphics);

            final java.awt.Graphics2D graphics2D =
                    (java.awt.Graphics2D) graphics.create();

            try
            {
                graphics2D.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                graphics2D.setColor(
                        getBackground());

                final int inset =
                        1;

                final int width =
                        Math.max(
                                0,
                                getWidth()
                                        - (inset * 2)
                                        - 1);

                final int height =
                        Math.max(
                                0,
                                getHeight()
                                        - (inset * 2)
                                        - 1);

                graphics2D.fillRoundRect(
                        inset,
                        inset,
                        width,
                        height,
                        arc,
                        arc);

                if (borderColor != null)
                {
                    graphics2D.setColor(
                            borderColor);

                    graphics2D.drawRoundRect(
                            inset,
                            inset,
                            width,
                            height,
                            arc,
                            arc);
                }
            }
            finally
            {
                graphics2D.dispose();
            }
        }
    }

    public void reload()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(
                    this::reload);
            return;
        }

        final List<MentionHistoryEntry> entries =
                historyService.snapshot();

        countLabel.setText(
                "Total Mentions: "
                        + entries.size());

        entriesPanel.removeAll();
        ageLabels.clear();

        if (entries.isEmpty())
        {
            final JLabel empty =
                    new JLabel(
                            "No mentions yet.");

            empty.setForeground(
                    ColorScheme.LIGHT_GRAY_COLOR);

            empty.setBorder(
                    BorderFactory.createEmptyBorder(
                            12,
                            8,
                            12,
                            8));

            entriesPanel.add(empty);
        }
        else
        {
            for (MentionHistoryEntry entry : entries)
            {
                entriesPanel.add(
                        buildEntry(entry));

                entriesPanel.add(
                        Box.createVerticalStrut(5));
            }
        }

        entriesPanel.revalidate();
        entriesPanel.repaint();
    }

    private JPanel buildEntry(
            MentionHistoryEntry entry)
    {
        final JPanel panel =
                new RoundedPanel(4, ColorScheme.MEDIUM_GRAY_COLOR);

        panel.setLayout(
                new BoxLayout(
                        panel,
                        BoxLayout.Y_AXIS));

        panel.setAlignmentX(
                LEFT_ALIGNMENT);

        panel.setBackground(
                ColorScheme.DARKER_GRAY_COLOR);

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        7,
                        8,
                        7,
                        8));

        panel.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR));

        final String sender =
                safe(
                        entry.getSender(),
                        "Unknown");

        final JPanel senderRow =
                new JPanel(
                        new BorderLayout());

        senderRow.setOpaque(false);

        senderRow.setAlignmentX(
                LEFT_ALIGNMENT);

        senderRow.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        senderRow.getPreferredSize().height));

        final JLabel senderLabel =
                new JLabel(
                        sender);

        senderLabel.setFont(
                senderLabel.getFont()
                        .deriveFont(
                                Font.BOLD));

        final JLabel ageLabel =
                new JLabel(
                        ageText(
                                entry.getTimestamp()));

        ageLabels.add(
                new AgeLabelBinding(
                        ageLabel,
                        entry.getTimestamp()));

        ageLabel.setFont(
                ageLabel.getFont()
                        .deriveFont(
                                Font.BOLD));

        senderRow.add(
                senderLabel,
                BorderLayout.WEST);

        senderRow.add(
                ageLabel,
                BorderLayout.EAST);

        panel.add(senderRow);

        /*
         * Small separation between the entry header and contextual metadata.
         */
        panel.add(
                Box.createVerticalStrut(3));

        final String context =
                contextText(entry);

        JLabel contextLabel = null;
        if (!context.isEmpty())
        {
            contextLabel =
                    smallLabel(context);

            panel.add(
                    contextLabel);
        }

        final String channel =
                channelText(entry);

        JLabel channelLabel = null;
        if (!channel.isEmpty())
        {
            channelLabel =
                    smallLabel(channel);

            panel.add(
                    channelLabel);
        }

        panel.add(
                Box.createVerticalStrut(5));

        /*
         * Mention message field.
         *
         * This intentionally uses RuneLite's medium gray rather than the darker
         * Quick Profile inset treatment. The blue mention text remains the visual
         * focus of each history entry.
         */
        final JPanel messageField =
                new RoundedPanel(8, ColorScheme.MEDIUM_GRAY_COLOR);

        messageField.setLayout(
                new BorderLayout());

        messageField.setAlignmentX(
                LEFT_ALIGNMENT);

        messageField.setBackground(
                ColorScheme.DARK_GRAY_COLOR);

        messageField.setBorder(
                BorderFactory.createEmptyBorder(
                        4,
                        6,
                        4,
                        6));

        messageField.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR));

        final JTextArea message =
                new JTextArea(
                        safe(
                                entry.getMessage(),
                                ""))
                {
                    @Override
                    public Dimension getPreferredSize()
                    {
                        final Dimension preferred =
                                super.getPreferredSize();

                        /*
                         * Once the component has a real width, calculate the wrapped
                         * height against that width instead of retaining an earlier
                         * single-line preferred size.
                         */
                        if (getWidth() > 0)
                        {
                            setSize(
                                    new Dimension(
                                            getWidth(),
                                            Short.MAX_VALUE));

                            return super.getPreferredSize();
                        }

                        return preferred;
                    }
                };

        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setOpaque(false);

        /*
         * Mention message only.
         * Sender/channel/world/location retain their existing RuneLite colors.
         */
        message.setForeground(
                new Color(
                        144,
                        144,
                        255));

        message.setFont(
                panel.getFont());

        message.setBorder(null);

        message.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR));

        messageField.add(
                message,
                BorderLayout.CENTER);

        panel.add(messageField);

        final MouseAdapter opener =
                new MouseAdapter()
                {
                    @Override
                    public void mouseEntered(
                            MouseEvent event)
                    {
                        panel.setBackground(new Color(20,20,20));

                        panel.repaint();
                    }

                    @Override
                    public void mouseExited(
                            MouseEvent event)
                    {
                        /*
                         * Only restore the normal card color once the mouse has
                         * actually left the whole history entry.
                         */
                        final Point mousePoint =
                                SwingUtilities.convertPoint(
                                        event.getComponent(),
                                        event.getPoint(),
                                        panel);

                        if (!panel.contains(mousePoint))
                        {
                            panel.setBackground(
                                    ColorScheme.DARKER_GRAY_COLOR);

                            panel.repaint();
                        }
                    }

                    @Override
                    public void mouseClicked(
                            MouseEvent event)
                    {
                        if (event.getButton()
                                != MouseEvent.BUTTON1)
                        {
                            return;
                        }

                        final Point clickPoint =
                                event.getLocationOnScreen();

                        SwingUtilities.convertPointFromScreen(
                                clickPoint,
                                client.getCanvas());

                        quickProfileController.openPlayer(
                                entry.getSender(),
                                entry.getChatType(),
                                clickPoint);
                    }
                };

        /*
         * Every visible part of the mention history card entry participates in the same hover
         * and click behavior; however, outer card is the object whose background changes.
         */
        panel.addMouseListener(opener);
        senderRow.addMouseListener(opener);
        senderLabel.addMouseListener(opener);
        ageLabel.addMouseListener(opener);
        if (contextLabel != null)
        {
            contextLabel.addMouseListener(opener);
        }
        if (channelLabel != null)
        {
            channelLabel.addMouseListener(opener);
        }
        messageField.addMouseListener(opener);
        message.addMouseListener(opener);

        return panel;
    }

    private static JLabel smallLabel(
            String text)
    {
        final JLabel label =
                new JLabel(text);

        label.setAlignmentX(
                LEFT_ALIGNMENT);

        label.setForeground(
                ColorScheme.LIGHT_GRAY_COLOR);

        label.setFont(
                label.getFont()
                        .deriveFont(12f));

        return label;
    }

    private static String channelText(
            MentionHistoryEntry entry)
    {
        final String type =
                readableChatType(
                        entry.getChatType());

        final String channelName =
                entry.getChannelName();

        if (channelName == null
                || channelName.trim().isEmpty())
        {
            return type;
        }

        return type
                + " \u2022 "
                + channelName;
    }

    private static String contextText(
            MentionHistoryEntry entry)
    {
        final StringBuilder text =
                new StringBuilder();

        if (entry.getWorld() != null)
        {
            text.append(
                            "World ")
                    .append(
                            entry.getWorld());
        }

        if (entry.getLocationName() != null
                && !entry.getLocationName()
                .trim()
                .isEmpty())
        {
            if (text.length() > 0)
            {
                text.append(
                        " \u2022 ");
            }

            text.append(
                    entry.getLocationName());
        }

        return text.toString();
    }

    private static String readableChatType(
            ChatMessageType type)
    {
        if (type == null)
        {
            return "";
        }

        switch (type)
        {
            case PUBLICCHAT:
            case MODCHAT:
                return "Public";

            case FRIENDSCHAT:
                return "Friends Chat";

            case CLAN_CHAT:
                return "Clan";

            case CLAN_GUEST_CHAT:
                return "Guest Clan";

            case PRIVATECHAT:
            case PRIVATECHATOUT:
                return "Private";

            default:
                return type.name();
        }
    }

    private static String ageText(
            Instant timestamp)
    {
        if (timestamp == null)
        {
            return "";
        }

        final Instant now =
                Instant.now();

        final long seconds =
                Math.max(
                        0,
                        Duration.between(
                                        timestamp,
                                        now)
                                .getSeconds());

        if (seconds < 60)
        {
            return "now";
        }

        final long minutes =
                seconds / 60;

        if (minutes < 60)
        {
            return minutes + "m";
        }

        final long hours =
                minutes / 60;

        if (hours < 24)
        {
            return hours + "h";
        }

        final long days =
                hours / 24;

        if (days < 7)
        {
            return days + "d";
        }

        final java.time.ZoneId zone =
                java.time.ZoneId.systemDefault();

        final java.time.LocalDate date =
                timestamp.atZone(zone)
                        .toLocalDate();

        final java.time.LocalDate today =
                now.atZone(zone)
                        .toLocalDate();

        final java.time.format.DateTimeFormatter formatter =
                date.getYear() == today.getYear()
                        ? java.time.format.DateTimeFormatter.ofPattern("MMM dd")
                        : java.time.format.DateTimeFormatter.ofPattern("MMM dd, yy");

        return formatter.format(date);
    }

    private void refreshAgeLabels()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(
                    this::refreshAgeLabels);
            return;
        }

        for (AgeLabelBinding binding : ageLabels)
        {
            binding.label.setText(
                    ageText(
                            binding.timestamp));
        }
    }

    private static class AgeLabelBinding
    {
        private final JLabel label;
        private final Instant timestamp;

        private AgeLabelBinding(
                JLabel label,
                Instant timestamp)
        {
            this.label = label;
            this.timestamp = timestamp;
        }
    }

    private static String safe(
            String value,
            String fallback)
    {
        return value == null
                || value.trim().isEmpty()
                ? fallback
                : value;
    }
}