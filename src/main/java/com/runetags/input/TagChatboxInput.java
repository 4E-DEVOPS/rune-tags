package com.runetags.input;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.runetags.records.PlayerTagCatalog;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.util.Text;

/** Native chatbox selector for adding one RuneTags local player Tag. */
public final class TagChatboxInput extends ChatboxInput
        implements KeyListener, MouseListener
{
    private static final int LEFT = 16;
    private static final int RIGHT = 16;
    private static final int START_Y = 38;

    private static final int PILL_HEIGHT = 18;
    private static final int PILL_PAD_X = 5;
    private static final int PILL_GAP_X = 5;
    private static final int PILL_GAP_Y = 4;
    private static final int PILL_TEXT_Y_OFFSET = 2;
    private static final int PILL_EDGE_BUFFER = 5;

    private static final Color PILL_BACKGROUND = new Color(55, 55, 55, 140);
    private static final Color PILL_BACKGROUND_HOVER = new Color(30, 30, 30, 140);

    private final ChatboxPanelManager chatboxPanelManager;
    private final ClientThread clientThread;
    private final List<String> selected = new ArrayList<>();
    private final List<TagHitbox> hitboxes = new ArrayList<>();

    private TagHitbox hoveredHitbox;
    private String prompt;
    private Consumer<List<String>> onDone;
    private Runnable onClose;
    private boolean built;

    public TagChatboxInput(
            ChatboxPanelManager chatboxPanelManager,
            ClientThread clientThread)
    {
        this.chatboxPanelManager = chatboxPanelManager;
        this.clientThread = clientThread;
    }

    public TagChatboxInput prompt(String prompt)
    {
        this.prompt = prompt;
        requestUpdate();
        return this;
    }

    public TagChatboxInput value(List<String> tags)
    {
        selected.clear();

        if (tags != null)
        {
            for (String value : tags)
            {
                final String tag = PlayerTagCatalog.canonical(value);

                if (tag != null
                        && !selected.contains(tag)
                        && selected.size() < PlayerTagCatalog.MAX_TAGS_PER_PLAYER)
                {
                    selected.add(tag);
                }
            }
        }

        requestUpdate();
        return this;
    }

    public TagChatboxInput onDone(Consumer<List<String>> onDone)
    {
        this.onDone = onDone;
        return this;
    }

    public TagChatboxInput onClose(Runnable onClose)
    {
        this.onClose = onClose;
        return this;
    }

    public TagChatboxInput build()
    {
        if (prompt == null)
        {
            throw new IllegalStateException("prompt must be non-null");
        }

        chatboxPanelManager.openInput(this);
        return this;
    }

    @Override
    protected void open()
    {
        built = true;
        update();
    }

    @Override
    protected void close()
    {
        built = false;
        hoveredHitbox = null;
        hitboxes.clear();

        if (onClose != null)
        {
            onClose.run();
        }
    }

    private void requestUpdate()
    {
        if (built)
        {
            clientThread.invoke(this::update);
        }
    }

    private static void setPillColor(Widget background, Color color)
    {
        if (background == null || color == null)
        {
            return;
        }

        background.setTextColor(color.getRGB() & 0xFFFFFF);
        background.setOpacity(255 - color.getAlpha());
    }

    private void update()
    {
        final Widget container = chatboxPanelManager.getContainerWidget();

        if (container == null)
        {
            return;
        }

        container.deleteAllChildren();
        hitboxes.clear();

        final Widget promptWidget = container.createChild(-1, WidgetType.TEXT);
        promptWidget.setText(prompt);
        promptWidget.setTextColor(0x800000);
        promptWidget.setFontId(FontID.QUILL_8);
        promptWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        promptWidget.setOriginalX(0);
        promptWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
        promptWidget.setOriginalY(6);
        promptWidget.setOriginalHeight(24);
        promptWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
        promptWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
        promptWidget.setWidthMode(WidgetSizeMode.MINUS);
        promptWidget.revalidate();

        final Widget probe = container.createChild(-1, WidgetType.RECTANGLE);
        probe.setFontId(FontID.PLAIN_12);

        final FontTypeFace font = probe.getFont();
        probe.setHidden(true);

        if (font == null)
        {
            return;
        }

        if (selected.size() >= PlayerTagCatalog.MAX_TAGS_PER_PLAYER)
        {
            final Widget message = container.createChild(-1, WidgetType.TEXT);
            message.setFontId(FontID.BOLD_12);
            message.setText(
                    "A maximum of (" + PlayerTagCatalog.MAX_TAGS_PER_PLAYER + ") Tags have already been selected.");
            message.setTextColor(0x282828);
            message.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
            message.setXTextAlignment(WidgetTextAlignment.CENTER);
            message.setOriginalX(0);
            message.setOriginalY(START_Y + PILL_TEXT_Y_OFFSET);
            message.setOriginalWidth(Math.max(1, container.getWidth() - LEFT - RIGHT));
            message.setOriginalHeight(PILL_HEIGHT);
            message.revalidate();
            return;
        }

        final List<String> available = new ArrayList<>();

        for (String tag : PlayerTagCatalog.ALL)
        {
            if (!selected.contains(tag))
            {
                available.add(tag);
            }
        }

        drawAvailable(container, font, available, START_Y);
    }

    private void drawAvailable(
            Widget container,
            FontTypeFace font,
            List<String> tags,
            int y)
    {
        int x = LEFT;

        final int maxX = Math.max(
                LEFT,
                container.getWidth()
                        - RIGHT
                        - PILL_EDGE_BUFFER);

        for (String tag : tags)
        {
            final int width = font.getTextWidth(tag) + (PILL_PAD_X * 2);

            if (x > LEFT
                    && x
                    + width
                    + PILL_GAP_X
                    > maxX)
            {
                x = LEFT;
                y += PILL_HEIGHT + PILL_GAP_Y;
            }

            final Widget background = container.createChild(-1, WidgetType.RECTANGLE);
            background.setFilled(true);
            setPillColor(background, PILL_BACKGROUND);
            background.setOriginalX(x);
            background.setOriginalY(y);
            background.setOriginalWidth(width);
            background.setOriginalHeight(PILL_HEIGHT);
            background.revalidate();

            final Widget text = container.createChild(-1, WidgetType.TEXT);
            text.setFontId(FontID.PLAIN_12);
            text.setText(Text.escapeJagex(tag));
            text.setTextColor(0xFFFFFF);
            text.setOriginalX(x + PILL_PAD_X);
            text.setOriginalY(y + PILL_TEXT_Y_OFFSET);
            text.setOriginalWidth(width - (PILL_PAD_X * 2));
            text.setOriginalHeight(PILL_HEIGHT);
            text.revalidate();

            hitboxes.add(
                    new TagHitbox(
                            new Rectangle(x, y, width, PILL_HEIGHT),
                            tag,
                            background));

            x += width + PILL_GAP_X;
        }
    }

    @Override
    public void keyPressed(KeyEvent event)
    {
        if (!chatboxPanelManager.shouldTakeInput())
        {
            return;
        }

        if (event.getKeyCode() == KeyEvent.VK_ESCAPE
                || event.getKeyCode() == KeyEvent.VK_ENTER)
        {
            event.consume();
            chatboxPanelManager.close();
        }
    }

    @Override
    public void keyTyped(KeyEvent event)
    {
    }

    @Override
    public void keyReleased(KeyEvent event)
    {
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        if (event.getButton() != MouseEvent.BUTTON1)
        {
            return event;
        }

        final Widget container = chatboxPanelManager.getContainerWidget();

        if (container == null)
        {
            return event;
        }

        final net.runelite.api.Point canvas = container.getCanvasLocation();

        final Point local = new Point(
                event.getX() - canvas.getX(),
                event.getY() - canvas.getY());

        for (TagHitbox hitbox : new ArrayList<>(hitboxes))
        {
            if (!hitbox.bounds.contains(local))
            {
                continue;
            }

            if (selected.size() >= PlayerTagCatalog.MAX_TAGS_PER_PLAYER)
            {
                return event;
            }

            selected.add(hitbox.tag);

            if (onDone != null)
            {
                onDone.accept(
                        Collections.unmodifiableList(
                                new ArrayList<>(selected)));
            }

            event.consume();
            chatboxPanelManager.close();
            return event;
        }

        return event;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent event)
    {
        if (hoveredHitbox != null)
        {
            setPillColor(hoveredHitbox.background, PILL_BACKGROUND);
            hoveredHitbox = null;
        }

        return event;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent event)
    {
        return event;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent event)
    {
        final Widget container = chatboxPanelManager.getContainerWidget();

        if (container == null)
        {
            return event;
        }

        final net.runelite.api.Point canvas = container.getCanvasLocation();
        final Point local = new Point(
                event.getX() - canvas.getX(),
                event.getY() - canvas.getY());

        TagHitbox nextHoveredHitbox = null;

        for (TagHitbox hitbox : hitboxes)
        {
            if (hitbox.bounds.contains(local))
            {
                nextHoveredHitbox = hitbox;
                break;
            }
        }

        if (hoveredHitbox == nextHoveredHitbox)
        {
            return event;
        }

        if (hoveredHitbox != null)
        {
            setPillColor(hoveredHitbox.background, PILL_BACKGROUND);
        }

        hoveredHitbox = nextHoveredHitbox;

        if (hoveredHitbox != null)
        {
            setPillColor(hoveredHitbox.background, PILL_BACKGROUND_HOVER);
        }

        return event;
    }

    private static final class TagHitbox
    {
        private final Rectangle bounds;
        private final String tag;
        private final Widget background;

        private TagHitbox(Rectangle bounds, String tag, Widget background)
        {
            this.bounds = bounds;
            this.tag = tag;
            this.background = background;
        }
    }
}