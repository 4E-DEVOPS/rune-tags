package com.runetags.input;

import com.runetags.records.PlayerTagCatalog;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TagChatboxInputTest
{
    /*
     * TESTS
     */

    @Test
    public void nullInitialTagsBecomeEmptySelection()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                null);

        Assert.assertTrue(
                selected(
                        input)
                        .isEmpty());
    }

    @Test
    public void emptyInitialTagsRemainEmpty()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Collections.emptyList());

        Assert.assertTrue(
                selected(
                        input)
                        .isEmpty());
    }

    @Test
    public void validInitialTagsArePreserved()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "Alt",
                        "Trusted",
                        "Raider"));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Trusted",
                        "Raider"),
                selected(
                        input));
    }

    @Test
    public void initialTagsAreCanonicalized()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "alt",
                        "TRUSTED",
                        "  raider  "));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Trusted",
                        "Raider"),
                selected(
                        input));
    }

    @Test
    public void unknownInitialTagsAreIgnored()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "Alt",
                        "DefinitelyNotATag",
                        "Trusted"));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Trusted"),
                selected(
                        input));
    }

    @Test
    public void nullInitialTagEntriesAreIgnored()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "Alt",
                        null,
                        "Trusted"));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Trusted"),
                selected(
                        input));
    }

    @Test
    public void duplicateInitialTagsAreRemovedAfterCanonicalization()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "Alt",
                        "alt",
                        " ALT ",
                        "Trusted"));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Trusted"),
                selected(
                        input));
    }

    @Test
    public void initialTagsStopAtMaximumSelectionCount()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "Alt",
                        "Avoid",
                        "BiS",
                        "Carrier",
                        "Chill",
                        "Drama"));

        Assert.assertEquals(
                PlayerTagCatalog.MAX_TAGS_PER_PLAYER,
                selected(
                        input)
                        .size());

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Avoid",
                        "BiS",
                        "Carrier",
                        "Chill"),
                selected(
                        input));
    }

    @Test
    public void replacingValueClearsPreviousSelection()
            throws Exception
    {
        final TagChatboxInput input =
                createInput();

        input.value(
                Arrays.asList(
                        "Alt",
                        "Trusted"));

        input.value(
                Collections.singletonList(
                        "Raider"));

        Assert.assertEquals(
                Collections.singletonList(
                        "Raider"),
                selected(
                        input));
    }

    @Test(expected = IllegalStateException.class)
    public void buildRequiresPrompt()
    {
        createInput()
                .build();
    }

    @Test
    public void buildOpensThroughChatboxPanelManager()
    {
        final TestHarness harness =
                createHarness();

        Assert.assertSame(
                harness.input,
                harness.input
                        .prompt(
                                "Select Tag")
                        .build());

        Mockito.verify(
                        harness.manager,
                        Mockito.times(
                                1))
                .openInput(
                        harness.input);
    }

    @Test
    public void fluentConfigurationReturnsSameInstance()
    {
        final TagChatboxInput input =
                createInput();

        Assert.assertSame(
                input,
                input.prompt(
                        "Select Tag"));

        Assert.assertSame(
                input,
                input.value(
                        Collections.singletonList(
                                "Alt")));

        Assert.assertSame(
                input,
                input.onDone(
                        tags ->
                        {
                        }));

        Assert.assertSame(
                input,
                input.onClose(
                        () ->
                        {
                        }));
    }

    @Test
    public void escapeClosesEditorWhenInputIsOwned()
    {
        final TestHarness harness =
                createHarness();

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE);

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
    public void enterClosesEditorWhenInputIsOwned()
    {
        final TestHarness harness =
                createHarness();

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ENTER);

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
    public void otherKeyDoesNotCloseEditor()
    {
        final TestHarness harness =
                createHarness();

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_A);

        harness.input.keyPressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void keyPressIsIgnoredWhenChatboxDoesNotOwnInput()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.manager.shouldTakeInput())
                .thenReturn(
                        false);

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE);

        harness.input.keyPressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void closeRunsCloseCallback()
            throws Exception
    {
        final TagChatboxInput input =
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
        final TagChatboxInput input =
                createInput();

        invokeClose(
                input);

        Assert.assertTrue(
                selected(
                        input)
                        .isEmpty());
    }

    @Test
    public void leftClickOnSeededTagAddsSelection()
            throws Exception
    {
        final TestHarness harness =
                createHarnessWithContainer();

        seedHitbox(
                harness.input,
                "Trusted",
                new Rectangle(
                        -1000,
                        -1000,
                        2000,
                        2000));

        final MouseEvent event =
                mouse(
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.BUTTON1,
                        20,
                        15);

        harness.input.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Assert.assertEquals(
                Collections.singletonList(
                        "Trusted"),
                selected(
                        harness.input));

        Mockito.verify(
                        harness.manager,
                        Mockito.times(
                                1))
                .close();
    }

    @Test
    public void leftClickPublishesImmutableSelectionSnapshot()
            throws Exception
    {
        final TestHarness harness =
                createHarnessWithContainer();

        harness.input.value(
                Collections.singletonList(
                        "Alt"));

        final AtomicReference<List<String>> captured =
                new AtomicReference<>();

        harness.input.onDone(
                captured::set);

        seedHitbox(
                harness.input,
                "Trusted",
                new Rectangle(
                        -1000,
                        -1000,
                        2000,
                        2000));

        harness.input.mousePressed(
                mouse(
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.BUTTON1,
                        20,
                        15));

        Assert.assertEquals(
                Arrays.asList(
                        "Alt",
                        "Trusted"),
                captured.get());

        try
        {
            captured.get()
                    .add(
                            "Raider");

            Assert.fail(
                    "Expected immutable callback selection.");
        }
        catch (UnsupportedOperationException expected)
        {
            // Expected.
        }
    }

    @Test
    public void leftClickOutsideTagDoesNothing()
            throws Exception
    {
        final TestHarness harness =
                createHarnessWithContainer();

        seedHitbox(
                harness.input,
                "Trusted",
                new Rectangle(
                        10,
                        10,
                        60,
                        18));

        final MouseEvent event =
                mouse(
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.BUTTON1,
                        100,
                        100);

        harness.input.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Assert.assertTrue(
                selected(
                        harness.input)
                        .isEmpty());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void nonLeftClickDoesNotSelectTag()
            throws Exception
    {
        final TestHarness harness =
                createHarnessWithContainer();

        seedHitbox(
                harness.input,
                "Trusted",
                new Rectangle(
                        10,
                        10,
                        60,
                        18));

        final MouseEvent event =
                mouse(
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.BUTTON3,
                        20,
                        15);

        harness.input.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Assert.assertTrue(
                selected(
                        harness.input)
                        .isEmpty());
    }

    @Test
    public void selectionCannotExceedMaximumTagCount()
            throws Exception
    {
        final TestHarness harness =
                createHarnessWithContainer();

        harness.input.value(
                Arrays.asList(
                        "Alt",
                        "Avoid",
                        "BiS",
                        "Carrier",
                        "Chill"));

        final AtomicBoolean callbackCalled =
                new AtomicBoolean();

        harness.input.onDone(
                tags -> callbackCalled.set(
                        true));

        seedHitbox(
                harness.input,
                "Drama",
                new Rectangle(
                        10,
                        10,
                        60,
                        18));

        final MouseEvent event =
                mouse(
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.BUTTON1,
                        20,
                        15);

        harness.input.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Assert.assertEquals(
                PlayerTagCatalog.MAX_TAGS_PER_PLAYER,
                selected(
                        harness.input)
                        .size());

        Assert.assertFalse(
                callbackCalled.get());

        Mockito.verify(
                        harness.manager,
                        Mockito.never())
                .close();
    }

    @Test
    public void nullContainerPreventsMouseSelection()
            throws Exception
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.manager.getContainerWidget())
                .thenReturn(
                        null);

        seedHitbox(
                harness.input,
                "Trusted",
                new Rectangle(
                        10,
                        10,
                        60,
                        18));

        final MouseEvent event =
                mouse(
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.BUTTON1,
                        20,
                        15);

        harness.input.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Assert.assertTrue(
                selected(
                        harness.input)
                        .isEmpty());
    }

    /*
     * HELPERS
     */

    private static TagChatboxInput createInput()
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
                new TagChatboxInput(
                        manager,
                        clientThread));
    }

    private static TestHarness createHarnessWithContainer()
    {
        final TestHarness harness =
                createHarness();

        final Widget container =
                Mockito.mock(
                        Widget.class);

        final net.runelite.api.Point canvasPoint =
                new net.runelite.api.Point(
                        0,
                        0);

        Mockito.when(
                        container.getCanvasLocation())
                .thenReturn(
                        canvasPoint);

        Mockito.when(
                        harness.manager.getContainerWidget())
                .thenReturn(
                        container);

        return harness;
    }

    @SuppressWarnings("unchecked")
    private static List<String> selected(
            TagChatboxInput input)
            throws Exception
    {
        final Field field =
                TagChatboxInput.class
                        .getDeclaredField(
                                "selected");

        field.setAccessible(
                true);

        return new ArrayList<>(
                (List<String>) field.get(
                        input));
    }

    @SuppressWarnings("unchecked")
    private static void seedHitbox(
            TagChatboxInput input,
            String tag,
            Rectangle bounds)
            throws Exception
    {
        final Class<?> hitboxClass =
                Class.forName(
                        "com.runetags.input.TagChatboxInput$TagHitbox");

        final Constructor<?> constructor =
                hitboxClass.getDeclaredConstructor(
                        Rectangle.class,
                        String.class,
                        Widget.class);

        constructor.setAccessible(
                true);

        final Object hitbox =
                constructor.newInstance(
                        bounds,
                        tag,
                        Mockito.mock(
                                Widget.class));

        final Field field =
                TagChatboxInput.class
                        .getDeclaredField(
                                "hitboxes");

        field.setAccessible(
                true);

        ((List<Object>) field.get(
                input))
                .add(
                        hitbox);
    }

    private static void invokeClose(
            TagChatboxInput input)
            throws Exception
    {
        final Method method =
                TagChatboxInput.class
                        .getDeclaredMethod(
                                "close");

        method.setAccessible(
                true);

        method.invoke(
                input);
    }

    private static KeyEvent pressed(
            int keyCode)
    {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                keyCode,
                KeyEvent.CHAR_UNDEFINED);
    }

    private static MouseEvent mouse(
            int id,
            int button,
            int x,
            int y)
    {
        return new MouseEvent(
                new Canvas(),
                id,
                System.currentTimeMillis(),
                0,
                x,
                y,
                1,
                false,
                button);
    }

    private static final class TestHarness
    {
        private final ChatboxPanelManager manager;
        private final ClientThread clientThread;
        private final TagChatboxInput input;

        private TestHarness(
                ChatboxPanelManager manager,
                ClientThread clientThread,
                TagChatboxInput input)
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