package com.runetags.input;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class NoteChatboxInputTest
{
    /*
     * TESTS
     */

    @Test
    public void nullInitialValueBecomesSingleBullet()
    {
        final NoteChatboxInput input =
                createInput();

        input.value(
                null);

        Assert.assertEquals(
                NoteTextLayout.BULLET_PREFIX,
                input.getValue());
    }

    @Test
    public void blankInitialValueBecomesSingleBullet()
    {
        final NoteChatboxInput input =
                createInput();

        input.value(
                "   ");

        Assert.assertEquals(
                NoteTextLayout.BULLET_PREFIX,
                input.getValue());
    }

    @Test
    public void plainInitialValueIsNormalizedForEditor()
    {
        final NoteChatboxInput input =
                createInput();

        input.value(
                "Santa");

        Assert.assertEquals(
                "\u2022 Santa",
                input.getValue());
    }

    @Test
    public void multilineInitialValueIsNormalizedForEditor()
    {
        final NoteChatboxInput input =
                createInput();

        input.value(
                "Santa\nZezima");

        Assert.assertEquals(
                "\u2022 Santa\n\u2022 Zezima",
                input.getValue());
    }

    @Test
    public void valueRespectsConfiguredMaximumLength()
    {
        final NoteChatboxInput input =
                createInput();

        input.maxLength(
                        8)
                .value(
                        "Santa Clause");

        Assert.assertEquals(
                8,
                input.getValue()
                        .length());

        Assert.assertEquals(
                "\u2022 Santa ",
                input.getValue());
    }

    @Test
    public void maximumLengthCannotBeConfiguredBelowOne()
    {
        final NoteChatboxInput input =
                createInput();

        input.maxLength(
                        0)
                .value(
                        "Santa");

        Assert.assertEquals(
                1,
                input.getValue()
                        .length());
    }

    @Test(expected = IllegalStateException.class)
    public void buildRequiresPrompt()
    {
        final NoteChatboxInput input =
                createInput();

        input.build();
    }

    @Test
    public void buildOpensInputThroughChatboxPanelManager()
    {
        final ChatboxPanelManager manager =
                Mockito.mock(
                        ChatboxPanelManager.class);

        final NoteChatboxInput input =
                new NoteChatboxInput(
                        manager,
                        Mockito.mock(
                                ClientThread.class));

        Assert.assertSame(
                input,
                input.prompt(
                                "RuneTags Note")
                        .build());

        Mockito.verify(
                        manager,
                        Mockito.times(
                                1))
                .openInput(
                        input);
    }

    @Test
    public void typedPrintableCharacterIsInsertedAtCursor()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        final KeyEvent event =
                typed(
                        'X');

        harness.input.keyTyped(
                event);

        Assert.assertEquals(
                "\u2022 SantaX",
                harness.input.getValue());
    }

    @Test
    public void typedCharacterIsIgnoredWhenChatboxDoesNotTakeInput()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.manager.shouldTakeInput())
                .thenReturn(
                        false);

        harness.input.value(
                "Santa");

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 Santa",
                harness.input.getValue());
    }

    @Test
    public void controlCharacterIsIgnored()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyTyped(
                typed(
                        '\n'));

        Assert.assertEquals(
                "\u2022 Santa",
                harness.input.getValue());
    }

    @Test
    public void nonAsciiCharacterIsIgnored()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyTyped(
                typed(
                        '\u00E9'));

        Assert.assertEquals(
                "\u2022 Santa",
                harness.input.getValue());
    }

    @Test
    public void typedCharacterStopsAtMaximumLength()
    {
        final TestHarness harness =
                createHarness();

        harness.input
                .maxLength(
                        7)
                .value(
                        "Santa");

        Assert.assertEquals(
                7,
                harness.input.getValue()
                        .length());

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 Santa",
                harness.input.getValue());
    }

    @Test
    public void shiftEnterAddsNewBulletLine()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ENTER,
                        KeyEvent.SHIFT_DOWN_MASK);

        harness.input.keyPressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Assert.assertEquals(
                "\u2022 Santa\n\u2022 ",
                harness.input.getValue());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void shiftEnterStopsAtMaximumLogicalLines()
    {
        final TestHarness harness =
                createHarness();

        harness.input
                .maxLogicalLines(
                        2)
                .value(
                        "Santa\nZezima");

        final String before =
                harness.input.getValue();

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ENTER,
                        KeyEvent.SHIFT_DOWN_MASK));

        Assert.assertEquals(
                before,
                harness.input.getValue());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void logicalLineLimitCannotBeConfiguredBelowOne()
    {
        final TestHarness harness =
                createHarness();

        harness.input
                .maxLogicalLines(
                        0)
                .value(
                        "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ENTER,
                        KeyEvent.SHIFT_DOWN_MASK));

        Assert.assertEquals(
                "\u2022 Santa",
                harness.input.getValue());
    }

    @Test
    public void shiftEnterStopsWhenInsertionWouldExceedMaximumLength()
    {
        final TestHarness harness =
                createHarness();

        harness.input
                .maxLength(
                        7)
                .value(
                        "Santa");

        final String before =
                harness.input.getValue();

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ENTER,
                        KeyEvent.SHIFT_DOWN_MASK));

        Assert.assertEquals(
                before,
                harness.input.getValue());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void enterSubmitsCurrentEditorValue()
    {
        final TestHarness harness =
                createHarness();

        final AtomicReference<String> submitted =
                new AtomicReference<>();

        harness.input
                .value(
                        "Santa")
                .onDone(
                        value ->
                        {
                            submitted.set(
                                    value);

                            return true;
                        });

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ENTER,
                        0);

        harness.input.keyPressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Assert.assertEquals(
                "\u2022 Santa",
                submitted.get());

        Mockito.verify(
                        harness.manager,
                        Mockito.times(
                                1))
                .close();
    }

    @Test
    public void rejectedSubmissionKeepsEditorOpen()
    {
        final TestHarness harness =
                createHarness();

        harness.input
                .value(
                        "Santa")
                .onDone(
                        value -> false);

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ENTER,
                        0));

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void enterWithoutCallbackClosesEditor()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ENTER,
                        0));

        Mockito.verify(
                        harness.manager,
                        Mockito.times(
                                1))
                .close();
    }

    @Test
    public void escapeClosesEditor()
    {
        final TestHarness harness =
                createHarness();

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE,
                        0);

        harness.input.keyPressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.manager,
                        Mockito.times(
                                1))
                .close();
    }

    @Test
    public void keyPressIsIgnoredWhenChatboxDoesNotTakeInput()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.manager.shouldTakeInput())
                .thenReturn(
                        false);

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ESCAPE,
                        0));

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void backspaceDeletesLastEditableCharacter()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_BACK_SPACE,
                        0));

        Assert.assertEquals(
                "\u2022 Sant",
                harness.input.getValue());
    }

    @Test
    public void backspaceCannotDeleteFirstBulletPrefix()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                null);

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_BACK_SPACE,
                        0));

        Assert.assertEquals(
                NoteTextLayout.BULLET_PREFIX,
                harness.input.getValue());
    }

    @Test
    public void backspaceRemovesEmptySecondaryBullet()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_ENTER,
                        KeyEvent.SHIFT_DOWN_MASK));

        Assert.assertEquals(
                "\u2022 Santa\n\u2022 ",
                harness.input.getValue());

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_BACK_SPACE,
                        0));

        Assert.assertEquals(
                "\u2022 Santa",
                harness.input.getValue());
    }

    @Test
    public void leftArrowMovesWithinEditableText()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_LEFT,
                        0));

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 SantXa",
                harness.input.getValue());
    }

    @Test
    public void homeMovesToEditableStartNotBeforeBullet()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_HOME,
                        0));

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 XSanta",
                harness.input.getValue());
    }

    @Test
    public void endMovesToLogicalLineEnd()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_HOME,
                        0));

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_END,
                        0));

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 SantaX",
                harness.input.getValue());
    }

    @Test
    public void leftArrowCrossesBulletBoundaryToPreviousLineEnd()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa\nZezima");

        /*
         * HOME from line two stops immediately after its structural bullet prefix.
         */
        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_HOME,
                        0));

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_LEFT,
                        0));

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 SantaX\n\u2022 Zezima",
                harness.input.getValue());
    }

    @Test
    public void rightArrowCrossesLogicalBoundaryWithoutEnteringBulletPrefix()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa\nZezima");

        /*
         * LEFT from the start of line two crosses its structural boundary to line one.
         */
        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_HOME,
                        0));

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_LEFT,
                        0));

        /*
         * RIGHT crosses the newline + bullet prefix as one structural boundary.
         */
        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_RIGHT,
                        0));

        harness.input.keyTyped(
                typed(
                        'X'));

        Assert.assertEquals(
                "\u2022 Santa\n\u2022 XZezima",
                harness.input.getValue());
    }

    @Test
    public void deleteRemovesCharacterInsideLogicalLine()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_HOME,
                        0));

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_DELETE,
                        0));

        Assert.assertEquals(
                "\u2022 anta",
                harness.input.getValue());
    }

    @Test
    public void deleteCannotCrossLogicalLineBoundary()
    {
        final TestHarness harness =
                createHarness();

        harness.input.value(
                "Santa\nZezima");

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_HOME,
                        0));

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_LEFT,
                        0));

        final String before =
                harness.input.getValue();

        harness.input.keyPressed(
                pressed(
                        KeyEvent.VK_DELETE,
                        0));

        Assert.assertEquals(
                before,
                harness.input.getValue());
    }

    @Test
    public void closeCallbackCanBeConfigured()
            throws Exception
    {
        final NoteChatboxInput input =
                createInput();

        final AtomicBoolean closed =
                new AtomicBoolean();

        input.onClose(
                () -> closed.set(
                        true));

        invokeClose(
                input);

        Assert.assertTrue(
                closed.get());
    }

    @Test
    public void closeWithoutCallbackIsSafe()
            throws Exception
    {
        final NoteChatboxInput input =
                createInput();

        invokeClose(
                input);

        Assert.assertEquals(
                NoteTextLayout.BULLET_PREFIX,
                input.value(
                                null)
                        .getValue());
    }

    @Test
    public void fluentConfigurationReturnsSameInstance()
    {
        final NoteChatboxInput input =
                createInput();

        Assert.assertSame(
                input,
                input.prompt(
                        "RuneTags Note"));

        Assert.assertSame(
                input,
                input.lines(
                        4));

        Assert.assertSame(
                input,
                input.maxLength(
                        256));

        Assert.assertSame(
                input,
                input.maxLogicalLines(
                        3));

        Assert.assertSame(
                input,
                input.value(
                        "Santa"));

        Assert.assertSame(
                input,
                input.onDone(
                        value -> true));

        Assert.assertSame(
                input,
                input.onClose(
                        () ->
                        {
                        }));
    }

    /*
     * HELPERS
     */

    private static NoteChatboxInput createInput()
    {
        return createHarness()
                .input;
    }

    private static TestHarness createHarness()
    {
        final ChatboxPanelManager manager =
                Mockito.mock(
                        ChatboxPanelManager.class);

        Mockito.when(
                        manager.shouldTakeInput())
                .thenReturn(
                        true);

        final ClientThread clientThread =
                Mockito.mock(
                        ClientThread.class);

        return new TestHarness(
                manager,
                clientThread,
                new NoteChatboxInput(
                        manager,
                        clientThread));
    }

    private static KeyEvent typed(
            char character)
    {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_UNDEFINED,
                character);
    }

    private static KeyEvent pressed(
            int keyCode,
            int modifiers)
    {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                modifiers,
                keyCode,
                KeyEvent.CHAR_UNDEFINED);
    }

    private static void invokeClose(
            NoteChatboxInput input)
            throws Exception
    {
        final java.lang.reflect.Method method =
                NoteChatboxInput.class
                        .getDeclaredMethod(
                                "close");

        method.setAccessible(
                true);

        method.invoke(
                input);
    }

    private static final class TestHarness
    {
        private final ChatboxPanelManager manager;
        private final ClientThread clientThread;
        private final NoteChatboxInput input;

        private TestHarness(
                ChatboxPanelManager manager,
                ClientThread clientThread,
                NoteChatboxInput input)
        {
            this.manager =
                    manager;

            this.clientThread =
                    clientThread;

            this.input =
                    input;
        }
    }
}