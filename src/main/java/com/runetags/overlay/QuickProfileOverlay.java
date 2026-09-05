package com.runetags.overlay;

import com.runetags.RuneTagsConfig;
import com.runetags.context.ContextMetricValue;
import com.runetags.model.OnlineState;
import com.runetags.model.PlayerAccountType;
import com.runetags.model.PlayerSource;
import com.runetags.quickprofile.ProfileEnrichmentState;
import com.runetags.quickprofile.QuickProfileController;
import com.runetags.quickprofile.QuickProfileModel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.ImageUtil;

public class QuickProfileOverlay extends Overlay
{
    private static final int CARD_MIN_WIDTH = 250;
    private static final int CARD_MAX_WIDTH = 420;
    private static final int CARD_MIN_HEIGHT = 126;
    private static final int PADDING = 10;

    private static final int LINE_HEIGHT = 16;
    private static final int HEADER_GAP = 4;
    private static final int SECTION_GAP = 8;

    private static final int SECTION_PADDING = 7;
    private static final int SECTION_TITLE_HEIGHT = 14;
    private static final int SECTION_TITLE_GAP = 2;
    private static final int SECTION_CORNER_RADIUS = 8;
    private static final int SECTION_DARKEN_PERCENT = 24;

    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_TOP_GAP = 8;
    private static final int ANCHOR_OFFSET = 8;

    private static final int PLAYER_NAME_BASELINE_OFFSET = 12;
    private static final int PLAYER_NAME_FONT_INCREASE = 2;
    private static final int PLAYER_NAME_INDENT = 3;

    private static final int ACCOUNT_ICON_SLOT_WIDTH = 13;
    private static final int ACCOUNT_ICON_SLOT_HEIGHT = 13;
    private static final int ACCOUNT_ICON_GAP = 3;

    private static final Map<PlayerAccountType, BufferedImage> ACCOUNT_ICONS = loadAccountIcons();

    /*
     * Quick Card theme:
     *
     * - Background follows RuneLite's global Overlay Color.
     * - Section backgrounds use the same Overlay Color, darkened by a fixed
     *   percentage to create an inset/code-block appearance.
     * - Section titles use a RuneTags gold accent.
     * - Secondary/status colors remain RuneTags-specific.
     */
    private static final Color CARD_BORDER = new Color(140, 140, 140, 224);
    private static final Color TEXT_PRIMARY = new Color(235, 230, 240, 255);
    private static final Color TEXT_SECONDARY = new Color(190, 185, 200, 255);
    private static final Color SECTION_TITLE = new Color(230, 185, 85, 255);
    private static final Color ONLINE = new Color(100, 220, 120, 255);
    private static final Color OFFLINE = new Color(230, 90, 90, 255);
    private static final Color HOVER_TEXT = new Color(255, 255, 255, 255);

    private final Client client;
    private final RuneTagsConfig config;
    private final RuneLiteConfig runeLiteConfig;
    private final QuickProfileController controller;

    public QuickProfileOverlay(
            Client client,
            RuneTagsConfig config,
            RuneLiteConfig runeLiteConfig,
            QuickProfileController controller)
    {
        this.client = client;
        this.config = config;
        this.runeLiteConfig = runeLiteConfig;
        this.controller = controller;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGHEST);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showProfile() || !controller.isOpen())
        {
            controller.updateLayoutBounds(
                    null,
                    null,
                    null,
                    null,
                    null);

            return null;
        }

        final QuickProfileModel model =
                controller.getModel();

        final Point anchor =
                controller.getAnchorPoint();

        if (model == null || anchor == null)
        {
            return null;
        }

        final Font normalFont =
                graphics.getFont();

        final Font bracketFont =
                normalFont.deriveFont(
                        Font.BOLD,
                        normalFont.getSize2D()
                                + PLAYER_NAME_FONT_INCREASE);

        final int playerNameDescent =
                Math.max(
                        graphics.getFontMetrics(normalFont).getDescent(),
                        graphics.getFontMetrics(bracketFont).getDescent());

        /*
         * Visibility state.
         */
        final boolean showTarget =
                config.targetPlayerOption()
                        && model.isNearby();

        final boolean showStatus =
                config.shareStatus();

        final boolean showWorld =
                showStatus
                        && config.shareWorld();

        final boolean showLocation =
                config.shareLocation()
                        && model.getLocationName() != null
                        && !model.getLocationName().isEmpty();

        final boolean showStatusLine =
                showStatus || showLocation;

        final boolean showChannel =
                config.shareChannel()
                        && model.getChannelName() != null
                        && !model.getChannelName().isEmpty();

        final boolean showRank =
                showChannel
                        && config.shareRank()
                        && model.getChannelRank() != null
                        && !model.getChannelRank().isEmpty();

        final boolean clickableClan =
                showChannel
                        && (model.getChannelSource() == PlayerSource.CLAN
                        || model.getChannelSource() == PlayerSource.GUEST_CLAN);

        final boolean hasLoadedStats =
                model.getEnrichmentState()
                        == ProfileEnrichmentState.LOADED
                        && (model.getCombatLevel() != null
                        || model.getTotalLevel() != null);

        final boolean showStats =
                config.showStats()
                        && (model.isResolved()
                        || hasLoadedStats);

        final boolean showContextMetrics =
                model.isResolved()
                        && config.showKillcount()
                        && model.getContextMetrics() != null
                        && !model.getContextMetrics().isEmpty();

        final boolean showUnresolvedState =
                !model.isResolved()
                        && model.getEnrichmentState()
                        != ProfileEnrichmentState.LOADED;

        /*
         * Logical sections.
         *
         * Status and Channel intentionally share one IDENTITY section. Every
         * other visible section is separated by SECTION_GAP.
         */
        final boolean showIdentitySection =
                !showUnresolvedState
                        && (showStatusLine || showChannel);

        final boolean showStatsSection =
                !showUnresolvedState
                        && showStats;

        final boolean showMetricsSection =
                !showUnresolvedState
                        && showContextMetrics;

        final int identityRows =
                (showStatusLine ? 1 : 0)
                        + (showChannel ? 1 : 0);

        final int contextMetricCount =
                showMetricsSection
                        ? model.getContextMetrics().size()
                        : 0;

        final int contextMetricRows =
                (contextMetricCount + 2) / 3;

        final int visibleSectionCount =
                (showIdentitySection ? 1 : 0)
                        + (showStatsSection ? 1 : 0)
                        + (showMetricsSection ? 1 : 0);

        final boolean hasContentAfterName =
                showUnresolvedState
                        || visibleSectionCount > 0;

        /*
         * Dynamic card width.
         */
        final int CARD_WIDTH =
                preferredCardWidth(
                        graphics,
                        model,
                        showStatus,
                        showWorld,
                        showLocation,
                        showChannel,
                        showRank,
                        showStatsSection,
                        showMetricsSection,
                        showUnresolvedState);

        final int sectionWidth =
                CARD_WIDTH - (PADDING * 2);

        final int sectionTextMaxWidth =
                sectionWidth - (SECTION_PADDING * 2);

        /*
         * The player-name header is outside all inset sections. Resolved
         * content is measured as complete logical sections; unresolved state
         * uses a titleless inset section.
         */
        final int playerNameHeight =
                PLAYER_NAME_BASELINE_OFFSET
                        + playerNameDescent;

        int contentHeight = playerNameHeight;

        if (hasContentAfterName)
        {
            contentHeight += HEADER_GAP;
        }

        if (showUnresolvedState)
        {
            contentHeight += titlelessSectionHeight(1);
        }
        else
        {
            if (showIdentitySection)
            {
                contentHeight += sectionHeight(identityRows);
            }

            if (showStatsSection)
            {
                if (showIdentitySection)
                {
                    contentHeight += SECTION_GAP;
                }

                contentHeight += sectionHeight(1);
            }

            if (showMetricsSection)
            {
                if (showIdentitySection || showStatsSection)
                {
                    contentHeight += SECTION_GAP;
                }

                contentHeight += sectionHeight(contextMetricRows);
            }
        }

        final int cardHeight =
                Math.max(
                        CARD_MIN_HEIGHT,
                        (PADDING * 2)
                                + contentHeight
                                + BUTTON_TOP_GAP
                                + BUTTON_HEIGHT);

        /*
         * Clamp the card to the RuneLite canvas.
         */
        final int x =
                Math.max(
                        4,
                        Math.min(
                                anchor.x + ANCHOR_OFFSET,
                                client.getCanvasWidth()
                                        - CARD_WIDTH
                                        - 4));

        final int y =
                Math.max(
                        4,
                        Math.min(
                                anchor.y + ANCHOR_OFFSET,
                                client.getCanvasHeight()
                                        - cardHeight
                                        - 4));

        final Rectangle cardBounds =
                new Rectangle(
                        x,
                        y,
                        CARD_WIDTH,
                        cardHeight);

        final Rectangle closeBounds =
                new Rectangle(
                        x + CARD_WIDTH - 22,
                        y + 5,
                        16,
                        16);

        /*
         * Bottom action buttons.
         */
        final int buttonY =
                y + cardHeight
                        - PADDING
                        - BUTTON_HEIGHT;

        final Rectangle targetBounds;
        final Rectangle lookupBounds;

        if (showTarget)
        {
            final int availableWidth =
                    CARD_WIDTH
                            - (PADDING * 2)
                            - BUTTON_GAP;

            final int halfWidth =
                    availableWidth / 2;

            targetBounds =
                    new Rectangle(
                            x + PADDING,
                            buttonY,
                            halfWidth,
                            BUTTON_HEIGHT);

            lookupBounds =
                    new Rectangle(
                            targetBounds.x
                                    + targetBounds.width
                                    + BUTTON_GAP,
                            buttonY,
                            availableWidth
                                    - halfWidth,
                            BUTTON_HEIGHT);
        }
        else
        {
            targetBounds = null;

            lookupBounds =
                    new Rectangle(
                            x + PADDING,
                            buttonY,
                            CARD_WIDTH
                                    - (PADDING * 2),
                            BUTTON_HEIGHT);
        }

        /*
         * Capture the mouse position once per render and reuse it for every
         * interactive region on the Quick Card.
         */
        final Point mouse =
                new Point(
                        client.getMouseCanvasPosition().getX(),
                        client.getMouseCanvasPosition().getY());

        final boolean targetHovered =
                targetBounds != null
                        && targetBounds.contains(mouse);

        final boolean lookupHovered =
                lookupBounds.contains(mouse);

        final boolean closeHovered =
                closeBounds.contains(mouse);

        /*
         * Card shell.
         */
        drawCard(
                graphics,
                cardBounds);

        drawCloseButton(
                graphics,
                closeBounds,
                closeHovered);

        Rectangle clanBounds = null;

        /*
         * Player-name header.
         */
        int textY =
                y + PADDING + PLAYER_NAME_BASELINE_OFFSET;

        final String playerName =
                safe(model.getDisplayName());

        final int playerNameX =
                x + PADDING + PLAYER_NAME_INDENT;

        int nameX =
                playerNameX;

        graphics.setColor(TEXT_PRIMARY);

        /*
         * Every Quick-Card reserves the same 13px account-icon slot BEFORE the
         * bracketed player name, including UNKNOWN.
         */
        final PlayerAccountType accountType =
                model.getAccountType() != null
                        ? model.getAccountType()
                        : PlayerAccountType.UNKNOWN;

        final BufferedImage accountIcon =
                ACCOUNT_ICONS.get(
                        accountType);

        if (accountIcon != null)
        {
            final FontMetrics nameMetrics =
                    graphics.getFontMetrics(
                            normalFont);

            final int iconX =
                    nameX
                            + Math.max(
                            0,
                            (ACCOUNT_ICON_SLOT_WIDTH
                                    - accountIcon.getWidth()) / 2);

            final int iconY =
                    textY
                            - nameMetrics.getAscent()
                            + Math.max(
                            0,
                            (nameMetrics.getHeight()
                                    - accountIcon.getHeight()) / 2);

            graphics.drawImage(
                    accountIcon,
                    iconX,
                    iconY,
                    null);
        }

        /*
         * Advance past the fixed account-icon slot before drawing the name frame.
         */
        nameX +=
                ACCOUNT_ICON_SLOT_WIDTH
                        + ACCOUNT_ICON_GAP;

        graphics.setFont(bracketFont);

        graphics.drawString(
                "[ ",
                nameX,
                textY);

        nameX +=
                graphics.getFontMetrics()
                        .stringWidth("[ ");

        graphics.setFont(normalFont);

        graphics.drawString(
                playerName,
                nameX,
                textY);

        nameX +=
                graphics.getFontMetrics()
                        .stringWidth(playerName);

        graphics.setFont(bracketFont);

        graphics.drawString(
                " ]",
                nameX,
                textY);

        graphics.setFont(normalFont);

        textY += playerNameDescent;

        if (hasContentAfterName)
        {
            textY += HEADER_GAP;
        }

        /*
         * Unresolved lookup state uses the same inset background treatment as the
         * resolved profile sections, but intentionally has no section title.
         */
        if (showUnresolvedState)
        {
            final int unresolvedHeight =
                    titlelessSectionHeight(1);

            final Rectangle unresolvedBounds =
                    new Rectangle(
                            x + PADDING,
                            textY,
                            sectionWidth,
                            unresolvedHeight);

            drawSectionBackground(
                    graphics,
                    unresolvedBounds);

            drawUnresolvedState(
                    graphics,
                    model,
                    unresolvedBounds.x + SECTION_PADDING,
                    unresolvedBounds.y + SECTION_PADDING + 12);

            textY += unresolvedHeight;
        }
        else
        {
            /*
             * IDENTITY
             *
             * Status/World/Location and Channel/Rank intentionally live in
             * one section with no SECTION_GAP between their rows.
             */
            if (showIdentitySection)
            {
                final int identityHeight =
                        sectionHeight(identityRows);

                final Rectangle identityBounds =
                        new Rectangle(
                                x + PADDING,
                                textY,
                                sectionWidth,
                                identityHeight);

                drawSectionBackground(
                        graphics,
                        identityBounds);

                drawSectionTitle(
                        graphics,
                        normalFont,
                        "IDENTITY",
                        identityBounds.x + SECTION_PADDING,
                        sectionTitleBaseline(identityBounds));

                int sectionTextY =
                        sectionContentBaseline(identityBounds);

                if (showStatusLine)
                {
                    drawStatusLine(
                            graphics,
                            model,
                            identityBounds.x + SECTION_PADDING,
                            sectionTextY,
                            showStatus,
                            showWorld,
                            showLocation,
                            sectionTextMaxWidth);

                    sectionTextY += LINE_HEIGHT;
                }

                if (showChannel)
                {
                    final int channelX =
                            identityBounds.x + SECTION_PADDING;

                    final FontMetrics metrics =
                            graphics.getFontMetrics();

                    final String rawChannelName =
                            channelNameText(model);

                    /*
                     * Preserve the channel/clan name whenever possible.
                     * Rank is shortened first if the complete row exceeds the
                     * available section width.
                     */
                    final String channelName =
                            ellipsize(
                                    graphics,
                                    rawChannelName,
                                    sectionTextMaxWidth);

                    final int channelWidth =
                            metrics.stringWidth(
                                    channelName);

                    final int rankAvailableWidth =
                            Math.max(
                                    0,
                                    sectionTextMaxWidth
                                            - channelWidth);

                    final String rankText =
                            showRank
                                    && rankAvailableWidth > 0
                                    ? ellipsize(
                                    graphics,
                                    "  •  "
                                            + model.getChannelRank(),
                                    rankAvailableWidth)
                                    : "";

                    final boolean renderRank =
                            !rankText.isEmpty();

                    if (clickableClan)
                    {
                        clanBounds =
                                new Rectangle(
                                        channelX,
                                        sectionTextY
                                                - metrics.getAscent(),
                                        channelWidth,
                                        metrics.getHeight());

                        final boolean hovered =
                                clanBounds.contains(mouse);

                        graphics.setColor(
                                hovered
                                        ? TEXT_PRIMARY
                                        : TEXT_SECONDARY);

                        graphics.drawString(
                                channelName,
                                channelX,
                                sectionTextY);

                        if (hovered)
                        {
                            graphics.drawLine(
                                    channelX,
                                    sectionTextY + 1,
                                    channelX
                                            + channelWidth,
                                    sectionTextY + 1);
                        }
                    }
                    else
                    {
                        graphics.setColor(
                                TEXT_SECONDARY);

                        graphics.drawString(
                                channelName,
                                channelX,
                                sectionTextY);
                    }

                    if (renderRank)
                    {
                        graphics.setColor(
                                TEXT_SECONDARY);

                        graphics.drawString(
                                rankText,
                                channelX
                                        + channelWidth,
                                sectionTextY);
                    }
                }

                textY += identityHeight;
            }

            /*
             * STATS
             */
            if (showStatsSection)
            {
                if (showIdentitySection)
                {
                    textY += SECTION_GAP;
                }

                final int statsHeight =
                        sectionHeight(1);

                final Rectangle statsBounds =
                        new Rectangle(
                                x + PADDING,
                                textY,
                                sectionWidth,
                                statsHeight);

                drawSectionBackground(
                        graphics,
                        statsBounds);

                drawSectionTitle(
                        graphics,
                        normalFont,
                        "STATS",
                        statsBounds.x + SECTION_PADDING,
                        sectionTitleBaseline(statsBounds));

                graphics.setColor(
                        TEXT_SECONDARY);

                graphics.drawString(
                        ellipsize(
                                graphics,
                                statsText(model),
                                sectionTextMaxWidth),
                        statsBounds.x + SECTION_PADDING,
                        sectionContentBaseline(statsBounds));

                textY += statsHeight;
            }

            /*
             * METRICS
             *
             * Contextual values are only present when the controller has
             * usable context. Up to three values are rendered per row.
             */
            if (showMetricsSection)
            {
                if (showIdentitySection || showStatsSection)
                {
                    textY += SECTION_GAP;
                }

                final int metricsHeight =
                        sectionHeight(contextMetricRows);

                final Rectangle metricsBounds =
                        new Rectangle(
                                x + PADDING,
                                textY,
                                sectionWidth,
                                metricsHeight);

                drawSectionBackground(
                        graphics,
                        metricsBounds);

                drawSectionTitle(
                        graphics,
                        normalFont,
                        "METRICS",
                        metricsBounds.x + SECTION_PADDING,
                        sectionTitleBaseline(metricsBounds));

                graphics.setColor(
                        TEXT_SECONDARY);

                int sectionTextY =
                        sectionContentBaseline(metricsBounds);

                final java.util.List<ContextMetricValue> metrics =
                        model.getContextMetrics();

                for (int i = 0;
                     i < metrics.size();
                     i += 3)
                {
                    final StringBuilder rowText =
                            new StringBuilder();

                    for (int j = 0;
                         j < 3
                                 && i + j < metrics.size();
                         j++)
                    {
                        final ContextMetricValue metric =
                                metrics.get(i + j);

                        if (j > 0)
                        {
                            rowText.append(
                                    "  •  ");
                        }

                        rowText.append(
                                        metric.getLabel())
                                .append(": ")
                                .append(
                                        metric.getValue());
                    }

                    graphics.drawString(
                            ellipsize(
                                    graphics,
                                    rowText.toString(),
                                    sectionTextMaxWidth),
                            metricsBounds.x + SECTION_PADDING,
                            sectionTextY);

                    sectionTextY += LINE_HEIGHT;
                }

                textY += metricsHeight;
            }
        }

        /*
         * Action buttons.
         */
        if (targetBounds != null)
        {
            drawButton(
                    graphics,
                    targetBounds,
                    controller.isCurrentProfileTargeted()
                            ? "Untarget"
                            : "Target",
                    targetHovered);
        }

        drawButton(
                graphics,
                lookupBounds,
                "Lookup",
                lookupHovered);

        /*
         * Publish interactive bounds back to the controller.
         */
        controller.updateLayoutBounds(
                cardBounds,
                closeBounds,
                targetBounds,
                lookupBounds,
                clanBounds);

        return null;
    }

    private static int sectionHeight(
            int contentRows)
    {
        return SECTION_PADDING
                + SECTION_TITLE_HEIGHT
                + SECTION_TITLE_GAP
                + (Math.max(0, contentRows) * LINE_HEIGHT)
                + SECTION_PADDING;
    }

    private static int titlelessSectionHeight(
            int contentRows)
    {
        return SECTION_PADDING
                + (Math.max(0, contentRows) * LINE_HEIGHT)
                + SECTION_PADDING;
    }

    private static int sectionTitleBaseline(
            Rectangle bounds)
    {
        return bounds.y
                + SECTION_PADDING
                + 11;
    }

    private static int sectionContentBaseline(
            Rectangle bounds)
    {
        return bounds.y
                + SECTION_PADDING
                + SECTION_TITLE_HEIGHT
                + SECTION_TITLE_GAP
                + 12;
    }

    private void drawSectionBackground(
            Graphics2D graphics,
            Rectangle bounds)
    {
        final Color old =
                graphics.getColor();

        try
        {
            graphics.setColor(
                    sectionBackgroundColor());

            graphics.fillRoundRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    SECTION_CORNER_RADIUS,
                    SECTION_CORNER_RADIUS);
        }
        finally
        {
            graphics.setColor(old);
        }
    }

    private static void drawSectionTitle(
            Graphics2D graphics,
            Font normalFont,
            String title,
            int x,
            int y)
    {
        final Color oldColor =
                graphics.getColor();

        final Font oldFont =
                graphics.getFont();

        try
        {
            graphics.setFont(
                    normalFont.deriveFont(Font.BOLD));

            graphics.setColor(
                    SECTION_TITLE);

            graphics.drawString(
                    title,
                    x,
                    y);
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }
    }

    private static void drawUnresolvedState(
            Graphics2D graphics,
            QuickProfileModel model,
            int x,
            int y)
    {
        final ProfileEnrichmentState state =
                model.getEnrichmentState();

        if (state == null)
        {
            return;
        }

        switch (state)
        {
            case LOADING:
                graphics.setColor(TEXT_SECONDARY);

                graphics.drawString(
                        loadingText(),
                        x,
                        y);
                break;

            case NOT_FOUND:
                graphics.setColor(TEXT_SECONDARY);
                graphics.drawString(
                        "Player not found.",
                        x,
                        y);
                break;

            case ERROR:
                graphics.setColor(OFFLINE);
                graphics.drawString(
                        "HiScores temporarily unavailable.",
                        x,
                        y);
                break;

            case LOCAL:
            default:
                graphics.drawString(
                        "Unable to resolve player name.",
                        x,
                        y);
                break;
        }
    }

    private static String unresolvedStateText(
            QuickProfileModel model)
    {
        final ProfileEnrichmentState state =
                model.getEnrichmentState();

        if (state == null)
        {
            return "";
        }

        switch (state)
        {
            case LOADING:
                return loadingText();

            case NOT_FOUND:
                return "Player not found.";

            case ERROR:
                return "HiScores temporarily unavailable.";

            case LOCAL:
            default:
                return "Unable to resolve player name.";
        }
    }

    private Color overlayBackgroundColor()
    {
        final Color base =
                runeLiteConfig.overlayBackgroundColor();

        final int opacity =
                Math.max(0, Math.min(100, config.quickCardOpacity()));

        final int alpha =
                Math.round(opacity * 255f / 100f);

        return new Color(
                base.getRed(),
                base.getGreen(),
                base.getBlue(),
                alpha);
    }

    private Color sectionBackgroundColor()
    {
        final Color base =
                overlayBackgroundColor();

        final float factor =
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                (100f - SECTION_DARKEN_PERCENT) / 100f));

        return new Color(
                clampColor(Math.round(base.getRed() * factor)),
                clampColor(Math.round(base.getGreen() * factor)),
                clampColor(Math.round(base.getBlue() * factor)),
                base.getAlpha());
    }

    private static Color themedButtonBackground(
            Color base,
            boolean hovered)
    {
        if (base == null)
        {
            base = new Color(30, 30, 30, 238);
        }

        /*
         * Make buttons slightly distinct from the card while preserving
         * the user's configured overlay hue and alpha.
         */
        final int adjustment =
                hovered
                        ? 32
                        : 18;

        final int brightness =
                (base.getRed()
                        + base.getGreen()
                        + base.getBlue()) / 3;

        final boolean lightBackground =
                brightness > 150;

        final int direction =
                lightBackground
                        ? -adjustment
                        : adjustment;

        return new Color(
                clampColor(base.getRed() + direction),
                clampColor(base.getGreen() + direction),
                clampColor(base.getBlue() + direction),
                base.getAlpha());
    }

    private static int clampColor(int value)
    {
        return Math.max(
                0,
                Math.min(
                        255,
                        value));
    }

    private void drawCard(
            Graphics2D graphics,
            Rectangle bounds)
    {
        final Color old =
                graphics.getColor();

        try
        {
            graphics.setColor(overlayBackgroundColor());

            graphics.fillRoundRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    8,
                    8);

            graphics.setColor(CARD_BORDER);

            graphics.drawRoundRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    8,
                    8);
        }
        finally
        {
            graphics.setColor(old);
        }
    }

    private static String loadingText()
    {
        final int phase =
                (int) ((System.currentTimeMillis() / 350L) % 4L);

        switch (phase)
        {
            case 0:
                return "● • ∙ •";

            case 1:
                return "• ● • ∙";

            case 2:
                return "∙ • ● •";

            default:
                return "• ∙ • ●";
        }
    }

    private static Map<PlayerAccountType, BufferedImage> loadAccountIcons()
    {
        final Map<PlayerAccountType, BufferedImage> icons =
                new EnumMap<>(
                        PlayerAccountType.class);

        for (PlayerAccountType accountType
                : PlayerAccountType.values())
        {
            if (accountType == null
                    || accountType.getIconFileName() == null)
            {
                continue;
            }

            icons.put(
                    accountType,
                    ImageUtil.loadImageResource(
                            QuickProfileOverlay.class,
                            "/com/runetags/icons/"
                                    + accountType.getIconFileName()));
        }

        return icons;
    }

    private static void drawStatusLine(
            Graphics2D graphics,
            QuickProfileModel model,
            int x,
            int y,
            boolean showStatus,
            boolean showWorld,
            boolean showLocation,
            int maxWidth)
    {
        String statusText = null;
        Color statusColor = TEXT_SECONDARY;

        /*
         * Status and world are related but separately configurable.
         *
         * Share Status OFF:
         * -> no status
         * -> no world, regardless of Share World
         *
         * Share Status ON + Share World OFF:
         * -> Online / Offline / Status unknown
         *
         * Share Status ON + Share World ON:
         * -> World # when known, otherwise normal status
         */
        if (showStatus)
        {
            final OnlineState state =
                    model.getOnlineState() != null
                            ? model.getOnlineState()
                            : OnlineState.UNKNOWN;

            if (state == OnlineState.OFFLINE)
            {
                statusText = "Offline";
                statusColor = OFFLINE;
            }
            else if (showWorld
                    && model.getWorld() != null
                    && model.getWorld() > 0)
            {
                statusText =
                        "World " + model.getWorld();

                statusColor = ONLINE;
            }
            else if (state == OnlineState.ONLINE)
            {
                statusText = "Online";
                statusColor = ONLINE;
            }
            else
            {
                statusText = "Status: Unknown";
                statusColor = TEXT_SECONDARY;
            }

            graphics.setColor(statusColor);

            graphics.drawString(
                    statusText,
                    x,
                    y);
        }

        /*
         * Location is independent from status/world.
         *
         * If status is hidden but location is enabled, location begins at the
         * normal left edge rather than being prefixed by a separator.
         */
        if (showLocation)
        {
            final String locationName =
                    locationText(
                            model.getLocationName());

            if (locationName != null
                    && !locationName.isEmpty())
            {
                graphics.setColor(TEXT_SECONDARY);

                if (statusText != null)
                {
                    final int statusWidth =
                            graphics.getFontMetrics()
                                    .stringWidth(statusText);

                    final int remainingWidth =
                            Math.max(
                                    0,
                                    maxWidth - statusWidth);

                    graphics.drawString(
                            ellipsize(
                                    graphics,
                                    " • " + locationName,
                                    remainingWidth),
                            x + statusWidth,
                            y);
                }
                else
                {
                    graphics.drawString(
                            ellipsize(
                                    graphics,
                                    locationName,
                                    maxWidth),
                            x,
                            y);
                }
            }
        }
    }

    private static String statusLineText(
            QuickProfileModel model,
            boolean showStatus,
            boolean showWorld,
            boolean showLocation)
    {
        String statusText = null;

        if (showStatus)
        {
            final OnlineState state =
                    model.getOnlineState() != null
                            ? model.getOnlineState()
                            : OnlineState.UNKNOWN;

            if (state == OnlineState.OFFLINE)
            {
                statusText = "Offline";
            }
            else if (showWorld
                    && model.getWorld() != null
                    && model.getWorld() > 0)
            {
                statusText =
                        "World " + model.getWorld();
            }
            else if (state == OnlineState.ONLINE)
            {
                statusText = "Online";
            }
            else
            {
                statusText = "Status: Unknown";
            }
        }

        if (showLocation
                && model.getLocationName() != null
                && !model.getLocationName().isEmpty())
        {
            final String locationText =
                    locationText(
                            model.getLocationName());

            if (statusText != null)
            {
                return statusText
                        + " • "
                        + locationText;
            }

            return locationText;
        }

        return statusText != null
                ? statusText
                : "";
    }

    private static String channelNameText(
            QuickProfileModel model)
    {
        final String channelName =
                model.getChannelName();

        if (channelName == null
                || channelName.isEmpty())
        {
            return "";
        }

        if (model.getChannelSource() == PlayerSource.PARTY
                && !channelName.regionMatches(
                true,
                0,
                "Party:",
                0,
                "Party:".length()))
        {
            return "Party: " + channelName;
        }

        return channelName;
    }

    private static String locationText(
            String locationName)
    {
        if (locationName == null
                || locationName.isEmpty())
        {
            return "";
        }

        return "Unknown".equalsIgnoreCase(locationName)
                ? "Location: Unknown"
                : locationName;
    }

    private static String combatText(
            QuickProfileModel model)
    {
        if (model.getCombatLevel() != null)
        {
            return String.valueOf(
                    model.getCombatLevel());
        }

        if (model.getEnrichmentState()
                == ProfileEnrichmentState.LOADING)
        {
            return "...";
        }

        return "—";
    }

    private static String totalText(
            QuickProfileModel model)
    {
        if (model.getTotalLevel() != null)
        {
            return String.valueOf(model.getTotalLevel());
        }

        if (model.getEnrichmentState() == ProfileEnrichmentState.LOADING)
        {
            return "...";
        }

        return "—";
    }

    private static String statsText(
            QuickProfileModel model)
    {
        return "Combat: "
                + combatText(model)
                + "  •  Total: "
                + totalText(model);
    }

    private static void drawCloseButton(
            Graphics2D graphics,
            Rectangle bounds,
            boolean hovered)
    {
        final Color old =
                graphics.getColor();

        try
        {
            graphics.setColor(
                    hovered
                            ? HOVER_TEXT
                            : TEXT_SECONDARY);

            graphics.drawString(
                    "×",
                    bounds.x + 4,
                    bounds.y + 12);
        }
        finally
        {
            graphics.setColor(old);
        }
    }

    private void drawButton(
            Graphics2D graphics,
            Rectangle bounds,
            String label,
            boolean hovered)
    {
        final Color old =
                graphics.getColor();

        try
        {
            graphics.setColor(overlayBackgroundColor());

            final Color buttonBackground =
                    themedButtonBackground(
                            overlayBackgroundColor(),
                            hovered);

            graphics.setColor(buttonBackground);

            graphics.fillRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height);

            graphics.setColor(CARD_BORDER);

            graphics.drawRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height);

            graphics.setColor(
                    hovered
                            ? HOVER_TEXT
                            : TEXT_PRIMARY);

            final FontMetrics metrics =
                    graphics.getFontMetrics();

            final int textX =
                    bounds.x
                            + Math.max(
                            2,
                            (bounds.width
                                    - metrics.stringWidth(label)) / 2);

            final int textY =
                    bounds.y
                            + ((bounds.height
                            - metrics.getHeight()) / 2)
                            + metrics.getAscent();

            graphics.drawString(
                    label,
                    textX,
                    textY);
        }
        finally
        {
            graphics.setColor(old);
        }
    }

    private static int preferredCardWidth(
            Graphics2D graphics,
            QuickProfileModel model,
            boolean showStatus,
            boolean showWorld,
            boolean showLocation,
            boolean showChannel,
            boolean showRank,
            boolean showStats,
            boolean showContextMetrics,
            boolean showUnresolvedState)
    {
        final FontMetrics metrics =
                graphics.getFontMetrics();

        int contentWidth =
                ACCOUNT_ICON_SLOT_WIDTH
                        + ACCOUNT_ICON_GAP
                        + metrics.stringWidth(
                        safe(model.getDisplayName()));

        int sectionContentWidth = 0;

        if (showStatus || showLocation)
        {
            final String statusLine =
                    statusLineText(
                            model,
                            showStatus,
                            showWorld,
                            showLocation);

            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            metrics.stringWidth(statusLine));
        }

        if (showChannel)
        {
            String channelText =
                    channelNameText(model);

            if (showRank)
            {
                channelText +=
                        "  •  " + model.getChannelRank();
            }

            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            metrics.stringWidth(channelText));
        }

        if (showStats)
        {
            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            metrics.stringWidth(
                                    statsText(model)));
        }

        if (showContextMetrics
                && model.getContextMetrics() != null)
        {
            for (int i = 0;
                 i < model.getContextMetrics().size();
                 i += 3)
            {
                final StringBuilder row =
                        new StringBuilder();

                for (int j = 0;
                     j < 3
                             && i + j < model.getContextMetrics().size();
                     j++)
                {
                    final ContextMetricValue metric =
                            model.getContextMetrics().get(i + j);

                    if (j > 0)
                    {
                        row.append("  •  ");
                    }

                    row.append(metric.getLabel())
                            .append(": ")
                            .append(metric.getValue());
                }

                sectionContentWidth =
                        Math.max(
                                sectionContentWidth,
                                metrics.stringWidth(
                                        row.toString()));
            }
        }

        if (sectionContentWidth > 0)
        {
            contentWidth =
                    Math.max(
                            contentWidth,
                            sectionContentWidth
                                    + (SECTION_PADDING * 2));
        }

        if (showUnresolvedState)
        {
            contentWidth =
                    Math.max(
                            contentWidth,
                            metrics.stringWidth(
                                    unresolvedStateText(model)));
        }

        return Math.max(
                CARD_MIN_WIDTH,
                Math.min(
                        CARD_MAX_WIDTH,
                        contentWidth + (PADDING * 2) + 8));
    }

    private static String ellipsize(
            Graphics2D graphics,
            String text,
            int maxWidth)
    {
        if (text == null)
        {
            return "";
        }

        final FontMetrics metrics =
                graphics.getFontMetrics();

        if (metrics.stringWidth(text) <= maxWidth)
        {
            return text;
        }

        final String ellipsis = "…";

        int end =
                text.length();

        while (end > 0)
        {
            final String candidate =
                    text.substring(0, end)
                            + ellipsis;

            if (metrics.stringWidth(candidate) <= maxWidth)
            {
                return candidate;
            }

            end--;
        }

        return ellipsis;
    }

    private static String safe(String value)
    {
        return value == null || value.isEmpty()
                ? "Unknown Player"
                : value;
    }
}

