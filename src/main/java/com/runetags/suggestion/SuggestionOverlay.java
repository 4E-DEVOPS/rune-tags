package com.runetags.suggestion;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** Passive suggestion list anchored immediately above RuneScape's chat input. */
public final class SuggestionOverlay extends Overlay
{
    private static final int WIDTH = 180;
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final int GAP = 3;

    private static final Color BACKGROUND = new Color(20, 20, 20, 210);
    private static final Color SELECTED_BACKGROUND = new Color(70, 60, 35, 220);
    private static final Color TEXT = new Color(220, 220, 220, 255);
    private static final Color SELECTED_TEXT = new Color(230, 185, 85, 255);

    private final Client client;
    private final SuggestionService suggestionService;

    public SuggestionOverlay(
            Client client,
            SuggestionService suggestionService)
    {
        this.client = client;
        this.suggestionService = suggestionService;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        suggestionService.refresh();

        if (!suggestionService.isActive())
        {
            return null;
        }

        final Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);

        if (input == null || input.isHidden())
        {
            return null;
        }

        final List<String> suggestions = suggestionService.getSuggestions();

        if (suggestions.isEmpty())
        {
            return null;
        }

        final Rectangle inputBounds = input.getBounds();
        final int height = (PADDING * 2) + (suggestions.size() * ROW_HEIGHT);
        final int x = Math.max(4, Math.min(inputBounds.x, client.getCanvasWidth() - WIDTH - 4));
        final int y = Math.max(4, inputBounds.y - height - GAP);
        final int selectedIndex = suggestionService.getSelectedIndex();
        final FontMetrics metrics = graphics.getFontMetrics();

        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(x, y, WIDTH, height, 6, 6);

        for (int i = 0; i < suggestions.size(); i++)
        {
            final int rowY = y + PADDING + (i * ROW_HEIGHT);
            final boolean selected = i == selectedIndex;

            if (selected)
            {
                graphics.setColor(SELECTED_BACKGROUND);
                graphics.fillRoundRect(x + 2, rowY, WIDTH - 4, ROW_HEIGHT, 4, 4);
            }

            graphics.setColor(selected ? SELECTED_TEXT : TEXT);
            graphics.drawString(
                    "@" + suggestions.get(i),
                    x + 7,
                    rowY + ((ROW_HEIGHT - metrics.getHeight()) / 2) + metrics.getAscent());
        }

        return null;
    }
}
