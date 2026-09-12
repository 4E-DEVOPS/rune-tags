package com.runetags.input;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
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

/**
 * RuneTags-specific native chatbox editor for local player Notes.
 *
 * Unlike the stock ChatboxTextInput, Notes preserve hard line breaks:
 * Shift+Enter inserts a new bullet line, Enter submits, and explicit logical
 * lines are limited independently from soft-wrapped rows.
 */
@Slf4j
public final class NoteChatboxInput
        extends ChatboxInput
        implements KeyListener, MouseListener
{
    private static final int CURSOR_FLASH_RATE_MILLIS = 1000;
    private static final int TEXT_Y = 50;

    private static final int EDITOR_LEFT_PADDING = 16;
    private static final int EDITOR_RIGHT_PADDING = 16;
    private static final int BULLET_GUTTER_WIDTH = 10;
    private static final int BULLET_SIZE = 3;
    private static final int BULLET_Y_OFFSET = 3;

    private final ChatboxPanelManager chatboxPanelManager;
    private final ClientThread clientThread;

    private String prompt;
    private int visibleLines = 4;
    private int maxLength = 256;
    private int maxLogicalLines = NoteTextLayout.MAX_LOGICAL_LINES;

    private final StringBuilder value =
            new StringBuilder();

    private int cursor;
    private Predicate<String> onDone;
    private Runnable onClose;
    private boolean built;

    private volatile List<VisibleRow> visibleRows =
            Collections.emptyList();

    public NoteChatboxInput(
            ChatboxPanelManager chatboxPanelManager,
            ClientThread clientThread)
    {
        this.chatboxPanelManager =
                chatboxPanelManager;
        this.clientThread =
                clientThread;
    }

    public NoteChatboxInput prompt(
            String prompt)
    {
        this.prompt = prompt;

        requestUpdate();
        return this;
    }

    public NoteChatboxInput lines(
            int lines)
    {
        this.visibleLines =
                Math.max(
                        1,
                        lines);

        requestUpdate();
        return this;
    }

    public NoteChatboxInput maxLength(
            int maxLength)
    {
        this.maxLength =
                Math.max(
                        1,
                        maxLength);

        return this;
    }

    public NoteChatboxInput maxLogicalLines(
            int maxLogicalLines)
    {
        this.maxLogicalLines =
                Math.max(
                        1,
                        maxLogicalLines);

        return this;
    }

    public NoteChatboxInput value(
            String initialValue)
    {
        value.setLength(0);

        final String editorValue =
                NoteTextLayout.normalizeForEditor(
                        initialValue);

        appendWithinLimit(
                editorValue);

        if (value.length() == 0)
        {
            value.append(
                    NoteTextLayout.BULLET_PREFIX);
        }

        cursor =
                value.length();

        requestUpdate();
        return this;
    }

    public NoteChatboxInput onDone(
            Predicate<String> onDone)
    {
        this.onDone = onDone;
        return this;
    }

    public NoteChatboxInput onClose(
            Runnable onClose)
    {
        this.onClose = onClose;
        return this;
    }

    public String getValue()
    {
        return value.toString();
    }

    public NoteChatboxInput build()
    {
        if (prompt == null)
        {
            throw new IllegalStateException(
                    "prompt must be non-null");
        }

        chatboxPanelManager.openInput(
                this);

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
        visibleRows =
                Collections.emptyList();

        if (onClose != null)
        {
            onClose.run();
        }
    }

    private void requestUpdate()
    {
        if (built)
        {
            clientThread.invoke(
                    this::update);
        }
    }

    private void update()
    {
        final Widget container =
                chatboxPanelManager.getContainerWidget();

        if (container == null)
        {
            return;
        }

        container.deleteAllChildren();

        final Widget promptWidget =
                container.createChild(
                        -1,
                        WidgetType.TEXT);

        promptWidget.setText(prompt);
        promptWidget.setTextColor(0x800000);
        promptWidget.setFontId(FontID.BARBARIAN);
        promptWidget.setXPositionMode(
                WidgetPositionMode.ABSOLUTE_CENTER);
        promptWidget.setOriginalX(0);
        promptWidget.setYPositionMode(
                WidgetPositionMode.ABSOLUTE_TOP);
        promptWidget.setOriginalY(8);
        promptWidget.setOriginalHeight(24);
        promptWidget.setXTextAlignment(
                WidgetTextAlignment.CENTER);
        promptWidget.setYTextAlignment(
                WidgetTextAlignment.CENTER);
        promptWidget.setWidthMode(
                WidgetSizeMode.MINUS);
        promptWidget.revalidate();

        drawEditor(
                container);
    }

    private void drawEditor(
            Widget container)
    {
        final Widget cursorWidget =
                container.createChild(
                        -1,
                        WidgetType.RECTANGLE);

        final long start =
                System.currentTimeMillis();

        cursorWidget.setOnTimerListener(
                (JavaScriptCallback) event ->
                {
                    final boolean on =
                            (System.currentTimeMillis()
                                    - start)
                                    % CURSOR_FLASH_RATE_MILLIS
                                    > CURSOR_FLASH_RATE_MILLIS / 2;

                    cursorWidget.setOpacity(
                            on
                                    ? 255
                                    : 0);
                });

        cursorWidget.setTextColor(0xFFFFFF);
        cursorWidget.setHasListener(true);
        cursorWidget.setFilled(true);
        cursorWidget.setFontId(FontID.PLAIN_12);

        final FontTypeFace font =
                cursorWidget.getFont();

        if (font == null)
        {
            visibleRows =
                    Collections.emptyList();
            return;
        }

        final int lineHeight =
                Math.max(
                        1,
                        font.getBaseline());

        final int width =
                Math.max(
                        1,
                        container.getWidth());

        /*
         * Use a stable left-aligned text area with a dedicated bullet gutter. Logical
         * rows draw a bullet there; soft-wrapped continuation rows share the text X
         * without drawing another bullet.
         */
        final int textX =
                EDITOR_LEFT_PADDING
                        + BULLET_GUTTER_WIDTH;

        final int textAreaWidth =
                Math.max(
                        1,
                        width
                                - textX
                                - EDITOR_RIGHT_PADDING);

        final List<InputRow> allRows =
                buildRows(
                        font,
                        textAreaWidth);

        final int cursorRowIndex =
                findCursorRow(
                        allRows);

        final int viewportStart =
                viewportStart(
                        allRows.size(),
                        cursorRowIndex);

        final int viewportEnd =
                Math.min(
                        allRows.size(),
                        viewportStart
                                + visibleLines);

        final List<VisibleRow> rendered =
                new ArrayList<>();

        int y =
                TEXT_Y;

        for (int index = viewportStart;
             index < viewportEnd;
             ++index)
        {
            final InputRow row =
                    allRows.get(index);

            final String displayText =
                    row.text;

            final int textWidth =
                    font.getTextWidth(
                            displayText);

            /*
             * QUILL_8 does not reliably expose the Unicode bullet glyph, so draw the
             * marker as a small native rectangle in the bullet gutter instead.
             */
            if (row.bullet)
            {
                final Widget bulletWidget =
                        container.createChild(
                                -1,
                                WidgetType.RECTANGLE);

                bulletWidget.setTextColor(
                        0x000000);
                bulletWidget.setFilled(true);

                bulletWidget.setOriginalX(
                        EDITOR_LEFT_PADDING
                                + 2);

                bulletWidget.setOriginalY(
                        y
                                + Math.max(
                                1,
                                (lineHeight
                                        - BULLET_SIZE) / 2)
                                + BULLET_Y_OFFSET);

                bulletWidget.setOriginalWidth(
                        BULLET_SIZE);

                bulletWidget.setOriginalHeight(
                        BULLET_SIZE);

                bulletWidget.revalidate();
            }

            final Widget textWidget =
                    container.createChild(
                            -1,
                            WidgetType.TEXT);

            textWidget.setFontId(
                    FontID.PLAIN_12);

            textWidget.setText(
                    Text.escapeJagex(
                            displayText));

            textWidget.setOriginalX(
                    textX);

            textWidget.setOriginalY(
                    y);

            textWidget.setOriginalWidth(
                    textAreaWidth);

            textWidget.setOriginalHeight(
                    lineHeight);

            textWidget.revalidate();

            if (index == cursorRowIndex)
            {
                final int relativeCursor =
                        Math.max(
                                0,
                                Math.min(
                                        displayText.length(),
                                        cursor
                                                - row.start));

                final int cursorX =
                        textX
                                + font.getTextWidth(
                                displayText.substring(
                                        0,
                                        relativeCursor));

                cursorWidget.setOriginalX(
                        cursorX - 1);

                cursorWidget.setOriginalY(
                        y);

                cursorWidget.setOriginalWidth(
                        2);

                cursorWidget.setOriginalHeight(
                        lineHeight);

                cursorWidget.revalidate();
            }

            /*
             * Use the full text area for mouse targeting so clicking after visible text
             * places the cursor at that row's end.
             */
            rendered.add(
                    new VisibleRow(
                            row,
                            textX,
                            y,
                            textAreaWidth,
                            lineHeight));

            y +=
                    lineHeight;
        }

        visibleRows =
                Collections.unmodifiableList(
                        rendered);
    }

    private List<InputRow> buildRows(
            FontTypeFace font,
            int maxWidth)
    {
        final List<InputRow> rows =
                new ArrayList<>();

        int logicalStart =
                0;

        while (logicalStart
                <= value.length())
        {
            final int newlineIndex =
                    indexOfNewline(
                            logicalStart);

            final int logicalEnd =
                    newlineIndex >= 0
                            ? newlineIndex
                            : value.length();

            final int prefixLength =
                    NoteTextLayout.BULLET_PREFIX.length();

            final boolean hasBullet =
                    logicalEnd
                            - logicalStart
                            >= prefixLength
                            && value.substring(
                                    logicalStart,
                                    logicalStart
                                            + prefixLength)
                            .equals(
                                    NoteTextLayout.BULLET_PREFIX);

            /*
             * Stored text retains "• ", while QUILL_8 renders only the editable text and
             * drawEditor() renders the bullet separately.
             */
            final int contentStart =
                    hasBullet
                            ? logicalStart
                            + prefixLength
                            : logicalStart;

            wrapLogicalLine(
                    rows,
                    font,
                    maxWidth,
                    contentStart,
                    logicalEnd,
                    hasBullet);

            if (newlineIndex < 0)
            {
                break;
            }

            logicalStart =
                    newlineIndex + 1;
        }

        if (rows.isEmpty())
        {
            rows.add(
                    new InputRow(
                            0,
                            0,
                            "",
                            false));
        }

        return rows;
    }

    private void wrapLogicalLine(
            List<InputRow> output,
            FontTypeFace font,
            int maxWidth,
            int start,
            int end,
            boolean bullet)
    {
        if (start >= end)
        {
            output.add(
                    new InputRow(
                            start,
                            end,
                            "",
                            bullet));

            return;
        }

        int rowStart =
                start;

        boolean firstRow =
                true;

        while (rowStart < end)
        {
            int fittingEnd =
                    rowStart;

            int lastSpace =
                    -1;

            for (int index = rowStart;
                 index < end;
                 ++index)
            {
                final char character =
                        value.charAt(index);

                final String candidate =
                        value.substring(
                                rowStart,
                                index + 1);

                if (font.getTextWidth(
                        candidate)
                        <= maxWidth)
                {
                    fittingEnd =
                            index + 1;

                    if (character == ' ')
                    {
                        lastSpace =
                                index;
                    }

                    continue;
                }

                break;
            }

            if (fittingEnd <= rowStart)
            {
                fittingEnd =
                        Math.min(
                                end,
                                rowStart + 1);
            }
            else if (fittingEnd < end
                    && lastSpace >= rowStart)
            {
                /*
                 * Keep the wrapping space in the previous row's source span so every source
                 * position remains owned by rendered geometry.
                 */
                fittingEnd =
                        lastSpace + 1;
            }

            final int rowEnd =
                    fittingEnd;

            /*
             * Do not trim or skip boundary spaces; every source
             * character must belong to exactly one InputRow.
             */
            output.add(
                    new InputRow(
                            rowStart,
                            rowEnd,
                            value.substring(
                                    rowStart,
                                    rowEnd),
                            firstRow
                                    && bullet));

            rowStart =
                    rowEnd;

            firstRow =
                    false;
        }
    }

    private int findCursorRow(
            List<InputRow> rows)
    {
        for (int index = 0;
             index < rows.size();
             ++index)
        {
            final InputRow row =
                    rows.get(index);

            if (row.start == row.end
                    && cursor == row.start)
            {
                return index;
            }

            if (cursor >= row.start
                    && cursor < row.end)
            {
                return index;
            }

            if (cursor == row.end)
            {
                final boolean nextRowStartsHere =
                        index + 1
                                < rows.size()
                                && rows.get(
                                index + 1).start
                                == cursor;

                /*
                 * At a soft-wrap boundary, assign the cursor to the next row;
                 * at the logical line end, keep it on the current row.
                 */
                if (!nextRowStartsHere)
                {
                    return index;
                }
            }
        }

        return Math.max(
                0,
                rows.size() - 1);
    }

    private int viewportStart(
            int rowCount,
            int cursorRowIndex)
    {
        if (rowCount <= visibleLines)
        {
            return 0;
        }

        return Math.max(
                0,
                Math.min(
                        cursorRowIndex
                                - visibleLines
                                + 1,
                        rowCount
                                - visibleLines));
    }

    private int indexOfNewline(
            int fromIndex)
    {
        for (int index = fromIndex;
             index < value.length();
             ++index)
        {
            if (value.charAt(index) == '\n')
            {
                return index;
            }
        }

        return -1;
    }

    @Override
    public void keyTyped(
            KeyEvent event)
    {
        if (!chatboxPanelManager.shouldTakeInput())
        {
            return;
        }

        final char character =
                event.getKeyChar();

        if (character < 32
                || character >= 127)
        {
            return;
        }

        if (value.length()
                >= maxLength)
        {
            setTemporaryPrompt(
                    "RuneTags Note: max "
                            + maxLength
                            + " characters");
            return;
        }

        value.insert(
                cursor,
                character);

        ++cursor;
        requestUpdate();
    }

    @Override
    public void keyPressed(
            KeyEvent event)
    {
        if (!chatboxPanelManager.shouldTakeInput())
        {
            return;
        }

        final int code =
                event.getKeyCode();

        if (event.isControlDown())
        {
            if (code == KeyEvent.VK_V)
            {
                event.consume();
                pasteClipboard();
            }

            return;
        }

        switch (code)
        {
            case KeyEvent.VK_ENTER:
                event.consume();

                if (event.isShiftDown())
                {
                    insertBulletLine();
                    return;
                }

                if (onDone != null
                        && !onDone.test(
                        getValue()))
                {
                    return;
                }

                chatboxPanelManager.close();
                return;

            case KeyEvent.VK_ESCAPE:
                event.consume();
                chatboxPanelManager.close();
                return;

            case KeyEvent.VK_BACK_SPACE:
                event.consume();
                backspace();
                return;

            case KeyEvent.VK_DELETE:
                event.consume();
                delete();
                return;

            case KeyEvent.VK_LEFT:
                event.consume();

                final int editableStart =
                        editableLineStart(
                                cursor);

                if (cursor > editableStart)
                {
                    --cursor;
                }
                else
                {
                    final int logicalStart =
                            logicalLineStart(
                                    cursor);

                    if (logicalStart > 0)
                    {
                        /*
                         * Cross "\n• " as one structural boundary to the previous logical line.
                         */
                        cursor =
                                logicalLineEnd(
                                        logicalStart - 1);
                    }
                }

                requestUpdate();
                return;

            case KeyEvent.VK_RIGHT:
                event.consume();

                final int currentLineEnd =
                        logicalLineEnd(
                                cursor);

                if (cursor < currentLineEnd)
                {
                    ++cursor;
                }
                else if (currentLineEnd
                        < value.length())
                {
                    /*
                     * Skip the complete newline + bullet prefix as one structural boundary.
                     */
                    cursor =
                            editableLineStart(
                                    currentLineEnd + 1);
                }

                requestUpdate();
                return;

            case KeyEvent.VK_HOME:
                event.consume();

                cursor =
                        editableLineStart(
                                cursor);

                requestUpdate();
                return;

            case KeyEvent.VK_END:
                event.consume();
                cursor =
                        logicalLineEnd(
                                cursor);
                requestUpdate();
                return;

            case KeyEvent.VK_UP:
                event.consume();
                moveVertical(-1);
                return;

            case KeyEvent.VK_DOWN:
                event.consume();
                moveVertical(1);
                return;

            default:
                return;
        }
    }

    @Override
    public void keyReleased(
            KeyEvent event)
    {
    }

    private void insertBulletLine()
    {
        if (NoteTextLayout.logicalLineCount(
                getValue()) >= maxLogicalLines)
        {
            setTemporaryPrompt(
                    "RuneTags Note: max "
                            + maxLogicalLines
                            + " bullets");
            return;
        }

        final String insertion =
                "\n"
                        + NoteTextLayout.BULLET_PREFIX;

        if (value.length()
                + insertion.length()
                > maxLength)
        {
            setTemporaryPrompt(
                    "RuneTags Note: max "
                            + maxLength
                            + " characters");
            return;
        }

        value.insert(
                cursor,
                insertion);

        cursor +=
                insertion.length();

        requestUpdate();
    }

    private void backspace()
    {
        if (cursor <= 0)
        {
            return;
        }

        final int lineStart =
                logicalLineStart(
                        cursor);

        final int editableStart =
                editableLineStart(
                        cursor);

        final int lineEnd =
                logicalLineEnd(
                        cursor);

        /*
         * On an empty secondary bullet, Backspace removes the complete "\n• "
         * structure and returns to the previous logical line. The first bullet is
         * permanent while the editor is open.
         */
        if (cursor == editableStart
                && lineStart > 0
                && lineEnd == editableStart)
        {
            value.delete(
                    lineStart - 1,
                    editableStart);

            cursor =
                    lineStart - 1;

            requestUpdate();
            return;
        }

        /*
         * Prevent Backspace from entering or deleting the structural bullet prefix.
         */
        if (cursor <= editableStart)
        {
            return;
        }

        value.deleteCharAt(
                cursor - 1);

        --cursor;

        requestUpdate();
    }

    private void delete()
    {
        if (cursor >= value.length())
        {
            return;
        }

        final int lineEnd =
                logicalLineEnd(
                        cursor);

        /*
         * Prevent Delete from crossing a logical-line boundary
         * and removing its newline or bullet prefix.
         */
        if (cursor >= lineEnd)
        {
            return;
        }

        value.deleteCharAt(
                cursor);

        requestUpdate();
    }

    private int logicalLineStart(
            int position)
    {
        int index =
                Math.max(
                        0,
                        Math.min(
                                position,
                                value.length()));

        while (index > 0
                && value.charAt(
                index - 1) != '\n')
        {
            --index;
        }

        return index;
    }

    private int editableLineStart(
            int position)
    {
        final int lineStart =
                logicalLineStart(
                        position);

        final int lineEnd =
                logicalLineEnd(
                        lineStart);

        final int prefixLength =
                NoteTextLayout.BULLET_PREFIX.length();

        if (lineEnd
                - lineStart
                >= prefixLength
                && value.substring(
                        lineStart,
                        lineStart
                                + prefixLength)
                .equals(
                        NoteTextLayout.BULLET_PREFIX))
        {
            return lineStart
                    + prefixLength;
        }

        return lineStart;
    }

    private int logicalLineEnd(
            int position)
    {
        int index =
                Math.max(
                        0,
                        Math.min(
                                position,
                                value.length()));

        while (index < value.length()
                && value.charAt(index) != '\n')
        {
            ++index;
        }

        return index;
    }

    private void moveVertical(
            int direction)
    {
        final List<VisibleRow> rows =
                visibleRows;

        if (rows.isEmpty())
        {
            return;
        }

        int currentIndex =
                -1;

        for (int index = 0;
             index < rows.size();
             ++index)
        {
            final InputRow row =
                    rows.get(index).row;

            if (cursor >= row.start
                    && cursor <= row.end)
            {
                currentIndex = index;
                break;
            }
        }

        if (currentIndex < 0)
        {
            return;
        }

        final int targetIndex =
                currentIndex
                        + direction;

        if (targetIndex < 0
                || targetIndex >= rows.size())
        {
            return;
        }

        final InputRow currentRow =
                rows.get(currentIndex).row;

        final InputRow targetRow =
                rows.get(targetIndex).row;

        final int column =
                Math.max(
                        0,
                        cursor
                                - currentRow.start);

        cursor =
                Math.min(
                        targetRow.end,
                        targetRow.start
                                + column);

        requestUpdate();
    }

    private void pasteClipboard()
    {
        try
        {
            final Object clipboardValue =
                    Toolkit.getDefaultToolkit()
                            .getSystemClipboard()
                            .getData(
                                    DataFlavor.stringFlavor);

            if (clipboardValue == null)
            {
                return;
            }

            String pasted =
                    clipboardValue.toString()
                            .replace("\r\n", " ")
                            .replace('\r', ' ')
                            .replace('\n', ' ')
                            .replace(
                                    NoteTextLayout.BULLET,
                                    "-");

            final StringBuilder clean =
                    new StringBuilder();

            for (int index = 0;
                 index < pasted.length();
                 ++index)
            {
                final char character =
                        pasted.charAt(index);

                if (character >= 32
                        && character < 127)
                {
                    clean.append(character);
                }
            }

            final int available =
                    Math.max(
                            0,
                            maxLength
                                    - value.length());

            if (clean.length()
                    > available)
            {
                clean.setLength(
                        available);
            }

            if (clean.length() == 0)
            {
                return;
            }

            value.insert(
                    cursor,
                    clean);

            cursor +=
                    clean.length();

            requestUpdate();
        }
        catch (IOException | UnsupportedFlavorException exception)
        {
            log.warn("Unable to Read Clipboard for RuneTags Note", exception);
        }
    }

    private void appendWithinLimit(
            String text)
    {
        if (text == null
                || text.isEmpty())
        {
            return;
        }

        final int available =
                Math.max(
                        0,
                        maxLength
                                - value.length());

        value.append(
                text,
                0,
                Math.min(
                        text.length(),
                        available));
    }

    private void setTemporaryPrompt(
            String message)
    {
        prompt = message;
        requestUpdate();
    }

    @Override
    public MouseEvent mouseClicked(
            MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mousePressed(
            MouseEvent mouseEvent)
    {
        if (mouseEvent.getButton()
                != MouseEvent.BUTTON1)
        {
            return mouseEvent;
        }

        final Widget container =
                chatboxPanelManager.getContainerWidget();

        if (container == null)
        {
            return mouseEvent;
        }

        final net.runelite.api.Point canvasLocation =
                container.getCanvasLocation();

        final int localX =
                mouseEvent.getX()
                        - canvasLocation.getX();

        final int localY =
                mouseEvent.getY()
                        - canvasLocation.getY();

        for (VisibleRow visibleRow
                : visibleRows)
        {
            final Rectangle bounds =
                    new Rectangle(
                            visibleRow.x,
                            visibleRow.y,
                            Math.max(
                                    1,
                                    visibleRow.width),
                            visibleRow.height);

            if (!bounds.contains(
                    new Point(
                            localX,
                            localY)))
            {
                continue;
            }

            final Widget probe =
                    container.createChild(
                            -1,
                            WidgetType.RECTANGLE);

            probe.setFontId(
                    FontID.PLAIN_12);

            final FontTypeFace font =
                    probe.getFont();

            probe.setHidden(true);

            if (font == null)
            {
                break;
            }

            final int relativeX =
                    Math.max(
                            0,
                            localX
                                    - visibleRow.x);

            int bestOffset = 0;
            int bestDistance =
                    Integer.MAX_VALUE;

            for (int offset = 0;
                 offset <= visibleRow.row.text.length();
                 ++offset)
            {
                final int x =
                        font.getTextWidth(
                                visibleRow.row.text.substring(
                                        0,
                                        offset));

                final int distance =
                        Math.abs(
                                x
                                        - relativeX);

                if (distance < bestDistance)
                {
                    bestDistance =
                            distance;
                    bestOffset =
                            offset;
                }
            }

            cursor =
                    Math.min(
                            visibleRow.row.end,
                            visibleRow.row.start
                                    + bestOffset);

            requestUpdate();
            break;
        }

        return mouseEvent;
    }

    @Override
    public MouseEvent mouseReleased(
            MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseEntered(
            MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseExited(
            MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseDragged(
            MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseMoved(
            MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    private static final class InputRow
    {
        private final int start;
        private final int end;
        private final String text;
        private final boolean bullet;

        private InputRow(
                int start,
                int end,
                String text,
                boolean bullet)
        {
            this.start = start;
            this.end = end;
            this.text =
                    text != null
                            ? text
                            : "";
            this.bullet = bullet;
        }
    }

    private static final class VisibleRow
    {
        private final InputRow row;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private VisibleRow(
                InputRow row,
                int x,
                int y,
                int width,
                int height)
        {
            this.row = row;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
