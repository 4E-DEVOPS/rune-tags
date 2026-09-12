package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.context.ProfileMetricValue;
import com.runetags.player.OnlineState;
import com.runetags.player.AccountType;
import com.runetags.player.PlayerSource;
import com.runetags.input.NoteTextLayout;
import com.runetags.hiscores.HiscoreEnrichmentState;
import com.runetags.quickprofile.QuickProfileController;
import com.runetags.quickprofile.QuickProfileModel;
import com.runetags.reports.ReportSummary;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.Client;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

public class QuickProfileOverlay extends Overlay
{
    private static final int CARD_MIN_WIDTH = 250;
    private static final int CARD_MAX_WIDTH = 420;
    private static final int CARD_MIN_HEIGHT = 126;
    private static final int PADDING = 10;

    private static final int LINE_HEIGHT = 16;
    private static final int HEADER_GAP = 4;
    private static final int SECTION_GAP = 6;

    private static final int SECTION_PADDING = 7;
    private static final int SECTION_TITLE_HEIGHT = 14;
    private static final int SECTION_TITLE_GAP = 2;
    private static final int SECTION_CORNER_RADIUS = 8;
    private static final int SECTION_DARKEN_PERCENT = 24;

    private static final int REPORT_PADDING = 6;
    private static final int REPORT_BLOCK_GAP = 5;
    private static final int REPORT_CORNER_RADIUS = 6;
    private static final float REPORT_TINT_STRENGTH = 0.46f;

    private static final int NOTE_PADDING = 6;
    private static final int NOTE_LABEL_HEIGHT = 14;
    private static final int NOTE_TEXT_GAP = 2;
    private static final int NOTE_BLOCK_GAP = 5;
    private static final int NOTE_CORNER_RADIUS = 6;
    private static final int NOTE_MAX_DISPLAY_LINES = NoteTextLayout.MAX_RENDERED_ROWS;
    private static final int NOTE_PREFERRED_TEXT_WIDTH = 330;

    private static final int TAG_BLOCK_PADDING_X_LEFT = 8;
    private static final int TAG_BLOCK_PADDING_X_RIGHT = 0;
    private static final int TAG_BLOCK_PADDING_Y = 6;
    private static final int TAG_BLOCK_CORNER_RADIUS = 6;
    private static final int TAG_PILL_HEIGHT = 18;
    private static final int TAG_PILL_PAD_X = 7;
    private static final int TAG_PILL_GAP_X = 4;
    private static final int TAG_PILL_GAP_Y = 4;
    private static final int TAG_CORNER_RADIUS = 10;
    private static final int TAG_TEXT_BASELINE_OFFSET = 15;
    private static final int TAG_REMOVE_GAP = 5;
    private static final int TAG_REMOVE_WIDTH = 7;

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

    private static final Map<AccountType, BufferedImage> ACCOUNT_ICONS = loadAccountIcons();

    /*
     * Quick Card theme:
     *
     * - Background follows RuneLite's global Overlay Color.
     * - Section backgrounds use the same Overlay Color, darkened by a fixed
     *   percentage to create an inset/code-block appearance.
     * - Section titles use a RuneTags gold accent.
     * - Secondary/status colors remain RuneTags-specific.
     */
    private static final Color CARD_BORDER = new Color(140, 140, 140, 225);
    private static final Color TEXT_PRIMARY = new Color(235, 230, 240, 255);
    private static final Color TEXT_SECONDARY = new Color(190, 185, 200, 255);
    private static final Color SECTION_TITLE = new Color(230, 185, 85, 255);
    private static final Color REPORT_TINT = new Color(145, 52, 58, 255);
    private static final Color REPORT_TITLE = new Color(255, 155, 160, 255);
    private static final Color ONLINE = new Color(100, 220, 120, 255);
    private static final Color OFFLINE = new Color(230, 90, 90, 255);
    private static final Color HOVER_TEXT = new Color(255, 255, 255, 255);
    private static final Color TAG_BACKGROUND = new Color(20, 20, 20, 150);

    private final Client client;
    private final Configurations config;
    private final RuneLiteConfig runeLiteConfig;
    private final QuickProfileController controller;
    private final TooltipManager tooltipManager;

    public QuickProfileOverlay(
            Client client,
            Configurations config,
            RuneLiteConfig runeLiteConfig,
            QuickProfileController controller,
            TooltipManager tooltipManager)
    {
        this.client = client;
        this.config = config;
        this.runeLiteConfig = runeLiteConfig;
        this.controller = controller;
        this.tooltipManager = tooltipManager;

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
                    null,
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

        final boolean showTags =
                config.showTags()
                        && model.getTags() != null
                        && !model.getTags().isEmpty();

        final boolean showNote =
                config.showNotes()
                        && model.getNote() != null
                        && !model.getNote().trim().isEmpty();

        final boolean showReports =
                config.showReports()
                        && model.getReportSummaries() != null
                        && !model.getReportSummaries().isEmpty();

        final boolean hasLoadedStats =
                model.getEnrichmentState()
                        == HiscoreEnrichmentState.LOADED
                        && (model.getCombatLevel() != null
                        || model.getTotalLevel() != null);

        final boolean showStats =
                config.showStats()
                        && (model.isResolved()
                        || hasLoadedStats);

        final boolean showEhp =
                config.wiseOldManMetrics()
                        && config.showEhp();

        final boolean showEhb =
                config.wiseOldManMetrics()
                        && config.showEhb();

        final boolean showEfficiencyMetrics =
                showEhp || showEhb;

        final boolean showContextMetrics =
                model.isResolved()
                        && config.showKillcount()
                        && model.getContextMetrics() != null
                        && !model.getContextMetrics().isEmpty();

        final boolean showUnresolvedState =
                !model.isResolved()
                        && model.getEnrichmentState()
                        != HiscoreEnrichmentState.LOADED;

        /*
         * Status and Channel share one IDENTITY section;
         * other visible sections are separated by SECTION_GAP.
         */
        final boolean showRecordsSection =
                showTags || showNote || showReports;

        final boolean showIdentitySection =
                !showUnresolvedState
                        && (showStatusLine || showChannel);

        final boolean showStatsSection =
                !showUnresolvedState
                        && showStats;

        final boolean showMetricsSection =
                !showUnresolvedState
                        && (showEfficiencyMetrics || showContextMetrics);

        final int identityRows =
                (showStatusLine ? 1 : 0)
                        + (showChannel ? 1 : 0);

        final int contextMetricCount =
                showContextMetrics
                        ? model.getContextMetrics().size()
                        : 0;

        final int contextMetricRows =
                (contextMetricCount + 2) / 3;

        final int metricRows =
                (showEfficiencyMetrics ? 1 : 0)
                        + contextMetricRows;

        final int visibleSectionCount =
                (showRecordsSection ? 1 : 0)
                        + (showIdentitySection ? 1 : 0)
                        + (showStatsSection ? 1 : 0)
                        + (showMetricsSection ? 1 : 0);

        final boolean hasContentAfterName =
                showUnresolvedState
                        || visibleSectionCount > 0;

        final int CARD_WIDTH =
                preferredCardWidth(
                        graphics,
                        model,
                        showStatus,
                        showWorld,
                        showLocation,
                        showChannel,
                        showRank,
                        showTags,
                        showNote,
                        showReports,
                        showStatsSection,
                        showEhp,
                        showEhb,
                        showContextMetrics,
                        showUnresolvedState);

        final int sectionWidth =
                CARD_WIDTH - (PADDING * 2);

        final int sectionTextMaxWidth =
                sectionWidth - (SECTION_PADDING * 2);

        controller.updateNoteLayoutMetrics(
                graphics.getFontMetrics(
                        normalFont),
                Math.max(
                        0,
                        sectionTextMaxWidth
                                - (NOTE_PADDING * 2)));

        /*
         * The player-name header sits outside the inset sections. Resolved content is
         * measured by logical section; unresolved state uses one titleless inset.
         */
        final int playerNameHeight =
                PLAYER_NAME_BASELINE_OFFSET
                        + playerNameDescent;

        int contentHeight = playerNameHeight;

        if (hasContentAfterName)
        {
            contentHeight += HEADER_GAP;
        }

        if (showRecordsSection)
        {
            contentHeight += recordsSectionHeight(
                    graphics,
                    model.getTags(),
                    showTags,
                    model.getNote(),
                    showNote,
                    model.getReportSummaries(),
                    showReports,
                    sectionTextMaxWidth);
        }

        if (showUnresolvedState)
        {
            if (showRecordsSection)
            {
                contentHeight += SECTION_GAP;
            }

            contentHeight += titlelessSectionHeight(1);
        }
        else
        {
            if (showIdentitySection)
            {
                if (showRecordsSection)
                {
                    contentHeight += SECTION_GAP;
                }

                contentHeight += sectionHeight(identityRows);
            }

            if (showStatsSection)
            {
                if (showRecordsSection
                        || showIdentitySection)
                {
                    contentHeight += SECTION_GAP;
                }

                contentHeight += sectionHeight(1);
            }

            if (showMetricsSection)
            {
                if (showRecordsSection
                        || showIdentitySection
                        || showStatsSection)
                {
                    contentHeight += SECTION_GAP;
                }

                contentHeight += sectionHeight(metricRows);
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

        int nextHeaderControlX =
                closeBounds.x - 18;

        final Rectangle favoriteBounds;

        if (config.showFavorites())
        {
            favoriteBounds =
                    new Rectangle(
                            nextHeaderControlX,
                            y + 5,
                            16,
                            16);

            nextHeaderControlX -= 18;
        }
        else
        {
            favoriteBounds = null;
        }

        final Rectangle noteBounds;

        if (config.showNotes())
        {
            noteBounds =
                    new Rectangle(
                            nextHeaderControlX,
                            y + 5,
                            16,
                            16);
            nextHeaderControlX -= 18;
        }
        else
        {
            noteBounds = null;
        }

        final Rectangle tagBounds =
                config.showTags()
                        ? new Rectangle(
                        nextHeaderControlX,
                        y + 5,
                        16,
                        16)
                        : null;

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
         * Capture the mouse position once per render and reuse it
         * for every interactive region on the Quick Card.
         */
        final Point mouse =
                new Point(
                        client.getMouseCanvasPosition().getX(),
                        client.getMouseCanvasPosition().getY());

        final Map<String, Rectangle> tagRemoveBounds =
                new LinkedHashMap<>();

        final boolean targetHovered =
                targetBounds != null
                        && targetBounds.contains(mouse);

        final boolean lookupHovered =
                lookupBounds.contains(mouse);

        final boolean closeHovered =
                closeBounds.contains(mouse);

        final boolean favoriteHovered =
                favoriteBounds != null
                        && favoriteBounds.contains(mouse);

        final boolean noteHovered =
                noteBounds != null
                        && noteBounds.contains(mouse);

        final boolean tagHovered =
                tagBounds != null
                        && tagBounds.contains(mouse);

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

        if (tagBounds != null)
        {
            drawTagButton(
                    graphics,
                    tagBounds,
                    tagHovered);
        }

        if (noteBounds != null)
        {
            drawNoteButton(
                    graphics,
                    noteBounds,
                    noteHovered);
        }

        if (favoriteBounds != null)
        {
            drawFavoriteButton(
                    graphics,
                    favoriteBounds,
                    model.isFavorite(),
                    favoriteHovered);
        }

        Rectangle clanBounds = null;
        Rectangle reportCaseLinkBounds = null;

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
         * Reserve the same account-icon slot for every profile,
         * including UNKNOWN, so player-name alignment remains stable.
         */
        final AccountType accountType =
                model.getAccountType() != null
                        ? model.getAccountType()
                        : AccountType.UNKNOWN;

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

        graphics.setColor(
                config.showFavorites()
                        && model.isFavorite()
                        ? favoriteColor()
                        : TEXT_PRIMARY);

        final int playerNameDrawX = nameX;
        final FontMetrics playerNameMetrics = graphics.getFontMetrics();
        final int playerNameWidth = playerNameMetrics.stringWidth(playerName);

        graphics.drawString(
                playerName,
                nameX,
                textY);

        if (config.showPreviousRsns()
                && tooltipManager != null
                && model.getPreviousRsns() != null
                && !model.getPreviousRsns().isEmpty())
        {
            final Rectangle playerNameBounds =
                    new Rectangle(
                            playerNameDrawX,
                            textY - playerNameMetrics.getAscent(),
                            playerNameWidth,
                            playerNameMetrics.getHeight());

            if (playerNameBounds.contains(mouse))
            {
                final StringBuilder tooltip = new StringBuilder("Previously:");

                for (String previousRsn : model.getPreviousRsns())
                {
                    if (previousRsn != null && !previousRsn.trim().isEmpty())
                    {
                        tooltip.append("<br>").append(previousRsn.trim());
                    }
                }

                tooltipManager.add(new Tooltip(tooltip.toString()));
            }
        }

        nameX += playerNameWidth;

        graphics.setColor(
                TEXT_PRIMARY);

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
         * RECORDS
         *
         * Report records are independent from HiScore resolution, so valid RuneWatch
         * or WDR records may be shown while profile enrichment is still loading.
         * Local Tags and Notes share this section with published report records.
         */
        if (showRecordsSection)
        {
            final int recordsHeight =
                    recordsSectionHeight(
                            graphics,
                            model.getTags(),
                            showTags,
                            model.getNote(),
                            showNote,
                            model.getReportSummaries(),
                            showReports,
                            sectionTextMaxWidth);

            final Rectangle recordsBounds =
                    new Rectangle(
                            x + PADDING,
                            textY,
                            sectionWidth,
                            recordsHeight);

            drawSectionBackground(
                    graphics,
                    recordsBounds);

            drawSectionTitle(
                    graphics,
                    normalFont,
                    "RECORDS",
                    recordsBounds.x + SECTION_PADDING,
                    sectionTitleBaseline(recordsBounds));

            int recordsContentY =
                    recordsBounds.y
                            + SECTION_PADDING
                            + SECTION_TITLE_HEIGHT
                            + SECTION_TITLE_GAP;

            if (showTags)
            {
                recordsContentY +=
                        drawTags(
                                graphics,
                                normalFont,
                                model.getTags(),
                                recordsBounds.x + SECTION_PADDING,
                                recordsContentY,
                                sectionTextMaxWidth,
                                mouse,
                                tagRemoveBounds);

                if (showNote || showReports)
                {
                    recordsContentY += NOTE_BLOCK_GAP;
                }
            }

            if (showNote)
            {
                recordsContentY +=
                        drawLocalNote(
                                graphics,
                                normalFont,
                                model.getNote(),
                                recordsBounds.x
                                        + SECTION_PADDING,
                                recordsContentY,
                                sectionTextMaxWidth);

                if (showReports)
                {
                    recordsContentY +=
                            NOTE_BLOCK_GAP;
                }
            }

            if (showReports)
            {
                reportCaseLinkBounds =
                        drawReportSummaries(
                                graphics,
                                normalFont,
                                model.getReportSummaries(),
                                recordsBounds,
                                recordsContentY,
                                sectionTextMaxWidth,
                                mouse);
            }

            textY += recordsHeight;
        }

        /*
         * Unresolved state uses the normal inset treatment without a section title.
         */
        if (showUnresolvedState)
        {
            if (showRecordsSection)
            {
                textY += SECTION_GAP;
            }

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
             * Status/World/Location and Channel/Rank share
             * one section without SECTION_GAP between their rows.
             */
            if (showIdentitySection)
            {
                if (showRecordsSection)
                {
                    textY += SECTION_GAP;
                }

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
                     * Preserve the channel/clan name when possible;
                     * shorten Rank first if the row exceeds the available width.
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
                if (showRecordsSection
                        || showIdentitySection)
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
             * Contextual values require usable context and render up to three per row.
             */
            if (showMetricsSection)
            {
                if (showRecordsSection
                        || showIdentitySection
                        || showStatsSection)
                {
                    textY += SECTION_GAP;
                }

                final int metricsHeight =
                        sectionHeight(metricRows);

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

                if (showEfficiencyMetrics)
                {
                    graphics.drawString(
                            ellipsize(
                                    graphics,
                                    efficiencyMetricsText(model, showEhp, showEhb),
                                    sectionTextMaxWidth),
                            metricsBounds.x + SECTION_PADDING,
                            sectionTextY);

                    sectionTextY += LINE_HEIGHT;
                }

                if (showContextMetrics)
                {
                    final java.util.List<ProfileMetricValue> metrics =
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
                            final ProfileMetricValue metric =
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
                tagBounds,
                noteBounds,
                favoriteBounds,
                targetBounds,
                lookupBounds,
                clanBounds,
                reportCaseLinkBounds,
                tagRemoveBounds);

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

    private static int recordsSectionHeight(
            Graphics2D graphics,
            List<String> tags,
            boolean showTags,
            String note,
            boolean showNote,
            List<ReportSummary> summaries,
            boolean showReports,
            int sectionTextMaxWidth)
    {
        int contentHeight = 0;

        if (showTags)
        {
            contentHeight += tagsBlockHeight(
                    graphics,
                    tags,
                    sectionTextMaxWidth);
        }

        if (showNote)
        {
            if (contentHeight > 0)
            {
                contentHeight += NOTE_BLOCK_GAP;
            }
            final int noteTextWidth =
                    Math.max(
                            0,
                            sectionTextMaxWidth
                                    - (NOTE_PADDING * 2));

            final List<String> noteLines =
                    NoteTextLayout.layout(
                                    graphics.getFontMetrics(),
                                    note,
                                    noteTextWidth,
                                    NOTE_MAX_DISPLAY_LINES)
                            .getRows();

            contentHeight +=
                    (NOTE_PADDING * 2)
                            + NOTE_LABEL_HEIGHT
                            + NOTE_TEXT_GAP
                            + (Math.max(1, noteLines.size())
                            * LINE_HEIGHT);
        }

        if (showReports
                && summaries != null
                && !summaries.isEmpty())
        {
            if (contentHeight > 0)
            {
                contentHeight +=
                        NOTE_BLOCK_GAP;
            }

            int blockCount = 0;

            for (ReportSummary summary : summaries)
            {
                if (summary == null)
                {
                    continue;
                }

                if (blockCount > 0)
                {
                    contentHeight +=
                            REPORT_BLOCK_GAP;
                }

                contentHeight +=
                        reportBlockHeight(
                                summary);

                ++blockCount;
            }
        }

        if (contentHeight == 0)
        {
            return 0;
        }

        return SECTION_PADDING
                + SECTION_TITLE_HEIGHT
                + SECTION_TITLE_GAP
                + contentHeight
                + SECTION_PADDING;
    }

    private static int reportBlockHeight(
            ReportSummary summary)
    {
        final boolean hasDate =
                summary != null
                        && !summary.getFormattedDate()
                        .isEmpty();

        final int rows =
                hasDate
                        ? 3
                        : 2;

        return REPORT_PADDING
                + (rows * LINE_HEIGHT)
                + REPORT_PADDING;
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

    private static int tagsBlockHeight(
            Graphics2D graphics,
            List<String> tags,
            int maxWidth)
    {
        if (graphics == null
                || tags == null
                || tags.isEmpty()
                || maxWidth <= 0)
        {
            return 0;
        }

        final FontMetrics metrics = graphics.getFontMetrics();
        final int pillAreaWidth = Math.max(0, maxWidth - TAG_BLOCK_PADDING_X_LEFT - TAG_BLOCK_PADDING_X_RIGHT);
        int rows = 1;
        int x = 0;

        for (String tag : tags)
        {
            final int width = metrics.stringWidth(tag)
                    + (TAG_PILL_PAD_X * 2)
                    + TAG_REMOVE_GAP
                    + TAG_REMOVE_WIDTH;

            if (x > 0 && x + width > pillAreaWidth)
            {
                ++rows;
                x = 0;
            }

            x += width + TAG_PILL_GAP_X;
        }

        return (TAG_BLOCK_PADDING_Y * 2)
                + (rows * TAG_PILL_HEIGHT)
                + ((rows - 1) * TAG_PILL_GAP_Y);
    }

    private int drawTags(
            Graphics2D graphics,
            Font normalFont,
            List<String> tags,
            int x,
            int y,
            int maxWidth,
            Point mouse,
            Map<String, Rectangle> tagRemoveBounds)
    {
        if (tags == null || tags.isEmpty())
        {
            return 0;
        }

        final Color oldColor = graphics.getColor();
        final Font oldFont = graphics.getFont();
        final int blockHeight = tagsBlockHeight(graphics, tags, maxWidth);

        try
        {
            graphics.setColor(noteBackgroundColor());
            graphics.fillRoundRect(
                    x,
                    y,
                    maxWidth,
                    blockHeight,
                    TAG_BLOCK_CORNER_RADIUS,
                    TAG_BLOCK_CORNER_RADIUS);

            graphics.setFont(normalFont);
            final FontMetrics metrics = graphics.getFontMetrics();
            final int pillAreaX = x + TAG_BLOCK_PADDING_X_LEFT;
            final int pillAreaWidth = Math.max(0, maxWidth - TAG_BLOCK_PADDING_X_LEFT - TAG_BLOCK_PADDING_X_RIGHT);
            int pillX = pillAreaX;
            int pillY = y + TAG_BLOCK_PADDING_Y;

            for (String tag : tags)
            {
                if (tag == null || tag.trim().isEmpty())
                {
                    continue;
                }

                final int tagTextWidth = metrics.stringWidth(tag);
                final int pillWidth = tagTextWidth
                        + (TAG_PILL_PAD_X * 2)
                        + TAG_REMOVE_GAP
                        + TAG_REMOVE_WIDTH;

                if (pillX > pillAreaX && pillX + pillWidth > pillAreaX + pillAreaWidth)
                {
                    pillX = pillAreaX;
                    pillY += TAG_PILL_HEIGHT + TAG_PILL_GAP_Y;
                }

                graphics.setColor(TAG_BACKGROUND);
                graphics.fillRoundRect(
                        pillX,
                        pillY,
                        pillWidth,
                        TAG_PILL_HEIGHT,
                        TAG_CORNER_RADIUS,
                        TAG_CORNER_RADIUS);

                graphics.setColor(TEXT_PRIMARY);
                graphics.drawString(tag, pillX + TAG_PILL_PAD_X, pillY + TAG_TEXT_BASELINE_OFFSET);

                final int removeX = pillX
                        + TAG_PILL_PAD_X
                        + tagTextWidth
                        + TAG_REMOVE_GAP;

                final Rectangle removeBounds = new Rectangle(
                        removeX - 2,
                        pillY,
                        TAG_REMOVE_WIDTH + 4,
                        TAG_PILL_HEIGHT);

                if (tagRemoveBounds != null)
                {
                    tagRemoveBounds.put(tag, removeBounds);
                }

                final boolean removeHovered = mouse != null && removeBounds.contains(mouse);

                graphics.setColor(
                        removeHovered
                                ? OFFLINE
                                : TEXT_SECONDARY);

                graphics.drawString("x", removeX, pillY + TAG_TEXT_BASELINE_OFFSET);
                pillX += pillWidth + TAG_PILL_GAP_X;
            }
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }

        return blockHeight;
    }

    private int drawLocalNote(
            Graphics2D graphics,
            Font normalFont,
            String note,
            int x,
            int y,
            int maxWidth)
    {
        final int noteTextWidth =
                Math.max(
                        0,
                        maxWidth
                                - (NOTE_PADDING * 2));

        final List<String> lines =
                NoteTextLayout.layout(
                                graphics.getFontMetrics(
                                        normalFont),
                                note,
                                noteTextWidth,
                                NOTE_MAX_DISPLAY_LINES)
                        .getRows();

        if (lines.isEmpty())
        {
            return 0;
        }

        final int noteHeight =
                (NOTE_PADDING * 2)
                        + NOTE_LABEL_HEIGHT
                        + NOTE_TEXT_GAP
                        + (lines.size()
                        * LINE_HEIGHT);

        final Rectangle noteBounds =
                new Rectangle(
                        x,
                        y,
                        maxWidth,
                        noteHeight);

        drawNoteBackground(
                graphics,
                noteBounds);

        final Color oldColor =
                graphics.getColor();

        final Font oldFont =
                graphics.getFont();

        try
        {
            graphics.setFont(
                    normalFont.deriveFont(
                            Font.BOLD));

            graphics.setColor(
                    TEXT_SECONDARY);

            graphics.drawString(
                    "NOTES",
                    x + NOTE_PADDING,
                    y + NOTE_PADDING + 11);

            graphics.setFont(
                    normalFont);

            graphics.setColor(
                    TEXT_PRIMARY);

            int lineY =
                    y
                            + NOTE_PADDING
                            + NOTE_LABEL_HEIGHT
                            + NOTE_TEXT_GAP
                            + 12;

            for (String line : lines)
            {
                final String displayLine =
                        line != null
                                && line.startsWith(
                                NoteTextLayout.BULLET_PREFIX)
                                ? "▪ "
                                + line.substring(
                                NoteTextLayout.BULLET_PREFIX.length())
                                : line;

                graphics.drawString(
                        displayLine,
                        x + NOTE_PADDING,
                        lineY);

                lineY +=
                        LINE_HEIGHT;
            }
        }
        finally
        {
            graphics.setFont(
                    oldFont);

            graphics.setColor(
                    oldColor);
        }

        return noteHeight;
    }

    private Rectangle drawReportSummaries(
            Graphics2D graphics,
            Font normalFont,
            List<ReportSummary> summaries,
            Rectangle recordsBounds,
            int startY,
            int sectionTextMaxWidth,
            Point mouse)
    {
        if (summaries == null
                || summaries.isEmpty())
        {
            return null;
        }

        Rectangle reportCaseLinkBounds =
                null;

        int blockY =
                startY;

        final int blockX =
                recordsBounds.x
                        + SECTION_PADDING;

        final int blockWidth =
                Math.max(
                        0,
                        sectionTextMaxWidth);

        graphics.setFont(
                normalFont);

        for (ReportSummary summary : summaries)
        {
            if (summary == null)
            {
                continue;
            }

            final int currentBlockHeight =
                    reportBlockHeight(
                            summary);

            final Rectangle reportBounds =
                    new Rectangle(
                            blockX,
                            blockY,
                            blockWidth,
                            currentBlockHeight);

            drawReportBackground(
                    graphics,
                    reportBounds);

            int lineY =
                    reportBounds.y
                            + REPORT_PADDING
                            + 12;

            /*
             * Report source header/title.
             *
             *     ⚠ WE DO RAIDS
             *     ⚠ RUNEWATCH          6 CASES
             *       ^ bold             ^ normal
             */
            final String sourceTitle =
                    warningSourceTitle(
                            graphics,
                            summary);

            final String countText =
                    summary.getCaseCount() > 1
                            ? summary.getCaseCount()
                            + " CASES"
                            : "";

            final Font reportTitleFont =
                    normalFont.deriveFont(
                            Font.BOLD);

            graphics.setFont(
                    reportTitleFont);

            graphics.setColor(
                    REPORT_TITLE);

            graphics.drawString(
                    ellipsize(
                            graphics,
                            sourceTitle,
                            Math.max(
                                    0,
                                    reportBounds.width
                                            - (REPORT_PADDING * 2)
                                            - (countText.isEmpty()
                                            ? 0
                                            : graphics
                                            .getFontMetrics(
                                                    normalFont)
                                            .stringWidth(
                                                    countText)
                                            + 10))),
                    reportBounds.x
                            + REPORT_PADDING,
                    lineY);

            /*
             * Keep the case count on the header row at normal weight.
             */
            graphics.setFont(
                    normalFont);

            if (!countText.isEmpty())
            {
                final int countWidth =
                        graphics.getFontMetrics()
                                .stringWidth(
                                        countText);

                graphics.drawString(
                        countText,
                        reportBounds.x
                                + reportBounds.width
                                - REPORT_PADDING
                                - countWidth,
                        lineY);
            }

            /*
             * Right-align the aggregate case count above the evidence rating.
             */
            if (!countText.isEmpty())
            {
                final int countWidth =
                        graphics.getFontMetrics()
                                .stringWidth(
                                        countText);

                graphics.drawString(
                        countText,
                        reportBounds.x
                                + reportBounds.width
                                - REPORT_PADDING
                                - countWidth,
                        lineY);
            }

            lineY +=
                    LINE_HEIGHT;

            final String ratingText =
                    evidenceRatingText(
                            graphics,
                            summary.getEvidenceRating());

            final int ratingWidth =
                    ratingText.isEmpty()
                            ? 0
                            : graphics.getFontMetrics()
                            .stringWidth(
                                    ratingText);

            final int reasonWidth =
                    Math.max(
                            0,
                            reportBounds.width
                                    - (REPORT_PADDING * 2)
                                    - (ratingWidth > 0
                                    ? ratingWidth + 10
                                    : 0));

            final String reason =
                    summary.getReason() == null
                            || summary.getReason()
                            .trim()
                            .isEmpty()
                            ? "Case details unavailable"
                            : summary.getReason()
                            .trim();

            final String displayedReason =
                    ellipsize(
                            graphics,
                            reason,
                            reasonWidth);

            final FontMetrics reasonMetrics =
                    graphics.getFontMetrics();

            final boolean clickable =
                    summary.hasCaseLink()
                            && !displayedReason.isEmpty();

            final Rectangle currentCaseBounds =
                    clickable
                            ? new Rectangle(
                            reportBounds.x
                                    + REPORT_PADDING,
                            lineY
                                    - reasonMetrics.getAscent(),
                            reasonMetrics.stringWidth(
                                    displayedReason),
                            reasonMetrics.getHeight())
                            : null;

            final boolean caseHovered =
                    currentCaseBounds != null
                            && mouse != null
                            && currentCaseBounds.contains(
                            mouse);

            graphics.setColor(
                    caseHovered
                            ? HOVER_TEXT
                            : TEXT_PRIMARY);

            graphics.drawString(
                    displayedReason,
                    reportBounds.x
                            + REPORT_PADDING,
                    lineY);

            /*
             * Underline the case link while hovered.
             */
            if (caseHovered)
            {
                graphics.drawLine(
                        reportBounds.x
                                + REPORT_PADDING,
                        lineY + 1,
                        reportBounds.x
                                + REPORT_PADDING
                                + reasonMetrics.stringWidth(
                                displayedReason),
                        lineY + 1);
            }

            if (reportCaseLinkBounds == null
                    && currentCaseBounds != null)
            {
                reportCaseLinkBounds =
                        currentCaseBounds;
            }

            if (ratingWidth > 0)
            {
                graphics.setColor(
                        SECTION_TITLE);

                graphics.drawString(
                        ratingText,
                        reportBounds.x
                                + reportBounds.width
                                - REPORT_PADDING
                                - ratingWidth,
                        lineY);
            }

            /*
             * Undated WDR records intentionally end after the case/evidence row.
             */
            final String formattedDate =
                    summary.getFormattedDate();

            if (!formattedDate.isEmpty())
            {
                lineY +=
                        LINE_HEIGHT;

                graphics.setColor(
                        TEXT_SECONDARY);

                graphics.drawString(
                        ellipsize(
                                graphics,
                                "Reported: "
                                        + formattedDate,
                                reportBounds.width
                                        - (REPORT_PADDING * 2)),
                        reportBounds.x
                                + REPORT_PADDING,
                        lineY);
            }

            blockY +=
                    currentBlockHeight
                            + REPORT_BLOCK_GAP;
        }

        return reportCaseLinkBounds;
    }

    private void drawNoteBackground(
            Graphics2D graphics,
            Rectangle bounds)
    {
        final Color old =
                graphics.getColor();

        try
        {
            graphics.setColor(
                    noteBackgroundColor());

            graphics.fillRoundRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    NOTE_CORNER_RADIUS,
                    NOTE_CORNER_RADIUS);
        }
        finally
        {
            graphics.setColor(
                    old);
        }
    }

    private Color noteBackgroundColor()
    {
        /*
         * Give Notes a subtle contrast within RECORDS while
         * retaining the user's configured RuneLite Overlay Color.
         */
        return themedButtonBackground(
                sectionBackgroundColor(),
                false);
    }

    private void drawReportBackground(
            Graphics2D graphics,
            Rectangle bounds)
    {
        final Color old =
                graphics.getColor();

        try
        {
            graphics.setColor(
                    reportBackgroundColor());

            graphics.fillRoundRect(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    REPORT_CORNER_RADIUS,
                    REPORT_CORNER_RADIUS);
        }
        finally
        {
            graphics.setColor(
                    old);
        }
    }

    private Color reportBackgroundColor()
    {
        final Color base =
                sectionBackgroundColor();

        final float inverse =
                1f - REPORT_TINT_STRENGTH;

        return new Color(
                clampColor(
                        Math.round(
                                (base.getRed() * inverse)
                                        + (REPORT_TINT.getRed()
                                        * REPORT_TINT_STRENGTH))),
                clampColor(
                        Math.round(
                                (base.getGreen() * inverse)
                                        + (REPORT_TINT.getGreen()
                                        * REPORT_TINT_STRENGTH))),
                clampColor(
                        Math.round(
                                (base.getBlue() * inverse)
                                        + (REPORT_TINT.getBlue()
                                        * REPORT_TINT_STRENGTH))),
                base.getAlpha());
    }

    private static String warningSourceTitle(
            Graphics2D graphics,
            ReportSummary summary)
    {
        final String source =
                summary != null
                        ? summary.getSourceLabel()
                        : "";

        final Font font =
                graphics.getFont();

        return font != null
                && font.canDisplay('⚠')
                ? "⚠ " + source
                : source;
    }

    private static String evidenceRatingText(
            Graphics2D graphics,
            String rawRating)
    {
        if (rawRating == null
                || rawRating.trim().isEmpty())
        {
            return "";
        }

        final String rating =
                rawRating.trim();

        final double parsed;

        try
        {
            parsed =
                    Double.parseDouble(
                            rating);
        }
        catch (NumberFormatException exception)
        {
            return rating;
        }

        if (parsed < 0d
                || parsed > 5d)
        {
            return rating;
        }

        final int wholeStars =
                (int) Math.round(
                        parsed);

        final boolean integral =
                Math.abs(
                        parsed - wholeStars) < 0.0001d;

        final Font font =
                graphics.getFont();

        if (integral
                && font != null
                && font.canDisplay('★')
                && font.canDisplay('☆'))
        {
            final StringBuilder stars =
                    new StringBuilder(5);

            for (int i = 0; i < 5; i++)
            {
                stars.append(
                        i < wholeStars
                                ? '★'
                                : '☆');
            }

            return stars.toString();
        }

        return rating + "/5";
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
        final HiscoreEnrichmentState state =
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
        final HiscoreEnrichmentState state =
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

    private Color favoriteColor()
    {
        final Color configured =
                config.favoriteColor();

        return configured != null
                ? configured
                : SECTION_TITLE;
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
         * Distinguish buttons from the card while preserving
         * the configured overlay hue and alpha.
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

    private static Map<AccountType, BufferedImage> loadAccountIcons()
    {
        final Map<AccountType, BufferedImage> icons =
                new EnumMap<>(
                        AccountType.class);

        for (AccountType accountType
                : AccountType.values())
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
         * World visibility depends on Share Status as well as Share World. When world
         * sharing is unavailable, fall back to the normal online/offline status.
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
         * Location is independent from status/world and receives no
         * leading separator when it is the first visible identity value.
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
                == HiscoreEnrichmentState.LOADING)
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

        if (model.getEnrichmentState() == HiscoreEnrichmentState.LOADING)
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

    private static void drawTagButton(
            Graphics2D graphics,
            Rectangle bounds,
            boolean hovered)
    {
        final Color oldColor = graphics.getColor();
        final Font oldFont = graphics.getFont();

        try
        {
            final Font font = graphics.getFont();
            final String glyph = font != null && font.canDisplay('⌗') ? "⌗" : "#";
            graphics.setColor(hovered ? HOVER_TEXT : TEXT_SECONDARY);

            final FontMetrics metrics = graphics.getFontMetrics();
            final int textX = bounds.x
                    + Math.max(0, (bounds.width - metrics.stringWidth(glyph)) / 2)
                    - 2;
            final int textY = bounds.y
                    + ((bounds.height - metrics.getHeight()) / 2)
                    + metrics.getAscent()
                    + 2;

            graphics.drawString(glyph, textX, textY);
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }
    }

    private static void drawNoteButton(
            Graphics2D graphics,
            Rectangle bounds,
            boolean hovered)
    {
        final Color oldColor =
                graphics.getColor();

        final Font oldFont =
                graphics.getFont();

        try
        {
            final Font font =
                    graphics.getFont();

            final String glyph =
                    font != null
                            && font.canDisplay('✎')
                            ? "✎"
                            : "N";

            graphics.setColor(
                    hovered
                            ? HOVER_TEXT
                            : TEXT_SECONDARY);

            final FontMetrics metrics =
                    graphics.getFontMetrics();

            final int textX =
                    bounds.x
                            + Math.max(
                            0,
                            (bounds.width
                                    - metrics.stringWidth(
                                    glyph)) / 2) - 2;

            final int textY =
                    bounds.y
                            + ((bounds.height
                            - metrics.getHeight()) / 2)
                            + metrics.getAscent() + 2;

            graphics.drawString(
                    glyph,
                    textX,
                    textY);
        }
        finally
        {
            graphics.setFont(
                    oldFont);

            graphics.setColor(
                    oldColor);
        }
    }

    private void drawFavoriteButton(
            Graphics2D graphics,
            Rectangle bounds,
            boolean favorite,
            boolean hovered)
    {
        final Color oldColor =
                graphics.getColor();

        final Font oldFont =
                graphics.getFont();

        try
        {
            final String glyph =
                    favorite
                            ? "♛"
                            : "♕";

            graphics.setColor(
                    hovered
                            ? HOVER_TEXT
                            : favorite
                            ? favoriteColor()
                            : TEXT_SECONDARY);

            final FontMetrics metrics =
                    graphics.getFontMetrics();

            final int textX =
                    bounds.x
                            + Math.max(
                            0,
                            (bounds.width
                                    - metrics.stringWidth(glyph)) / 2) -2;

            final int textY =
                    bounds.y
                            + ((bounds.height
                            - metrics.getHeight()) / 2)
                            + metrics.getAscent() +2;

            graphics.drawString(
                    glyph,
                    textX,
                    textY);
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }
    }

    private static void drawCloseButton(
            Graphics2D graphics,
            Rectangle bounds,
            boolean hovered)
    {
        final Color oldColor =
                graphics.getColor();

        final Font oldFont =
                graphics.getFont();

        try
        {
            final String glyph =
                    "✖";

            graphics.setColor(
                    hovered
                            ? OFFLINE
                            : TEXT_SECONDARY);

            final FontMetrics metrics =
                    graphics.getFontMetrics();

            final int textX =
                    bounds.x
                            + Math.max(
                            0,
                            (bounds.width
                                    - metrics.stringWidth(
                                    glyph)) / 2) -2;

            final int textY =
                    bounds.y
                            + ((bounds.height
                            - metrics.getHeight()) / 2)
                            + metrics.getAscent() +2;

            graphics.drawString(
                    glyph,
                    textX,
                    textY);
        }
        finally
        {
            graphics.setFont(
                    oldFont);

            graphics.setColor(
                    oldColor);
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

    private static String efficiencyMetricsText(
            QuickProfileModel model,
            boolean showEhp,
            boolean showEhb)
    {
        final StringBuilder text = new StringBuilder();

        if (showEhp)
        {
            text.append("EHP: ").append(formatEfficiencyValue(model.getEfficientHoursPlayed()));
        }

        if (showEhb)
        {
            if (text.length() > 0)
            {
                text.append("  •  ");
            }

            text.append("EHB: ").append(formatEfficiencyValue(model.getEfficientHoursBossed()));
        }

        return text.toString();
    }

    private static String formatEfficiencyValue(Double value)
    {
        if (value == null || !Double.isFinite(value) || value < 0d)
        {
            return "—";
        }

        return Long.toString((long) Math.floor(value));
    }

    private static int preferredCardWidth(
            Graphics2D graphics,
            QuickProfileModel model,
            boolean showStatus,
            boolean showWorld,
            boolean showLocation,
            boolean showChannel,
            boolean showRank,
            boolean showTags,
            boolean showNote,
            boolean showReports,
            boolean showStats,
            boolean showEhp,
            boolean showEhb,
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

        final Font reportTitleFont =
                graphics.getFont()
                        .deriveFont(
                                Font.BOLD);

        final FontMetrics reportTitleMetrics =
                graphics.getFontMetrics(
                        reportTitleFont);

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

        if (showTags
                && model.getTags() != null
                && !model.getTags().isEmpty())
        {
            int tagsWidth = 0;

            for (String tag : model.getTags())
            {
                if (tag == null || tag.trim().isEmpty())
                {
                    continue;
                }

                final int pillWidth =
                        metrics.stringWidth(tag)
                                + (TAG_PILL_PAD_X * 2)
                                + TAG_REMOVE_GAP
                                + TAG_REMOVE_WIDTH;

                if (tagsWidth > 0)
                {
                    tagsWidth += TAG_PILL_GAP_X;
                }

                tagsWidth += pillWidth;
            }

            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            tagsWidth
                                    + TAG_BLOCK_PADDING_X_LEFT
                                    + TAG_BLOCK_PADDING_X_RIGHT);
        }

        if (showNote
                && model.getNote() != null
                && !model.getNote().trim().isEmpty())
        {
            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            Math.min(
                                    NOTE_PREFERRED_TEXT_WIDTH,
                                    NoteTextLayout.widestLogicalLineWidth(
                                            metrics,
                                            model.getNote())));
        }

        if (showReports
                && model.getReportSummaries() != null)
        {
            for (ReportSummary summary
                    : model.getReportSummaries())
            {
                if (summary == null)
                {
                    continue;
                }

                final String sourceTitle =
                        warningSourceTitle(
                                graphics,
                                summary);

                final String countText =
                        summary.getCaseCount() > 1
                                ? summary.getCaseCount() + " CASES"
                                : "";

                int headerWidth =
                        reportTitleMetrics.stringWidth(
                                sourceTitle);

                if (!countText.isEmpty())
                {
                    headerWidth +=
                            10
                                    + metrics.stringWidth(
                                    countText);
                }

                final String ratingText =
                        evidenceRatingText(
                                graphics,
                                summary.getEvidenceRating());

                final String reason =
                        summary.getReason() == null
                                || summary.getReason().trim().isEmpty()
                                ? "Case details unavailable"
                                : summary.getReason().trim();

                int reasonWidth =
                        metrics.stringWidth(
                                reason);

                if (!ratingText.isEmpty())
                {
                    reasonWidth +=
                            10
                                    + metrics.stringWidth(
                                    ratingText);
                }

                final String formattedDate =
                        summary.getFormattedDate();

                final int dateWidth =
                        formattedDate.isEmpty()
                                ? 0
                                : metrics.stringWidth(
                                "Reported: "
                                        + formattedDate);

                final int reportContentWidth =
                        Math.max(
                                headerWidth,
                                Math.max(
                                        reasonWidth,
                                        dateWidth));

                sectionContentWidth =
                        Math.max(
                                sectionContentWidth,
                                reportContentWidth
                                        + (REPORT_PADDING * 2));
            }
        }

        if (showStats)
        {
            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            metrics.stringWidth(
                                    statsText(model)));
        }

        if (showEhp || showEhb)
        {
            sectionContentWidth =
                    Math.max(
                            sectionContentWidth,
                            metrics.stringWidth(
                                    efficiencyMetricsText(model, showEhp, showEhb)));
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
                    final ProfileMetricValue metric =
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
