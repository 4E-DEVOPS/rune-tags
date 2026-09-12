package com.runetags.input;

import com.runetags.Configurations;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ReferenceHitbox;
import com.runetags.config.ChatInteractionMode;
import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;
import com.runetags.quickprofile.QuickProfileController;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;
import com.runetags.suggestion.SuggestionService;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class InputListenerTest
{
    /*
     * TESTS
     */

    @Test
    public void rightClickClosesOpenQuickProfile()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        final MouseEvent event =
                mousePressed(
                        MouseEvent.BUTTON3,
                        50,
                        50);

        harness.listener.mousePressed(
                event);

        Mockito.verify(
                        harness.controller,
                        Mockito.times(
                                1))
                .close();

        Assert.assertFalse(
                event.isConsumed());
    }

    @Test
    public void rightClickDoesNotCloseAlreadyClosedQuickProfile()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        false);

        harness.listener.mousePressed(
                mousePressed(
                        MouseEvent.BUTTON3,
                        50,
                        50));

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();
    }

    @Test
    public void middleClickDoesNotInteractWithQuickProfile()
    {
        final TestHarness harness =
                createHarness();

        harness.listener.mousePressed(
                mousePressed(
                        MouseEvent.BUTTON2,
                        50,
                        50));

        Mockito.verifyNoInteractions(
                harness.registry);
    }

    @Test
    public void nativeOpenMenuOwnsLeftClick()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.client.isMenuOpen())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        final MouseEvent event =
                mousePressed(
                        MouseEvent.BUTTON1,
                        50,
                        50);

        harness.listener.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();

        Mockito.verifyNoInteractions(
                harness.registry);
    }

    @Test
    public void tagRemovalHasHighestQuickProfilePriority()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.tagRemovalAt(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        "Trusted");

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .removeTag(
                        "Trusted");

        Mockito.verifyNoInteractions(
                harness.registry);
    }

    @Test
    public void closeButtonConsumesClickAndClosesProfile()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isCloseButton(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .close();

        Mockito.verifyNoInteractions(
                harness.registry);
    }

    @Test
    public void tagButtonConsumesClickAndOpensTagEditor()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isTagButton(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .editTags();
    }

    @Test
    public void noteButtonConsumesClickAndOpensNoteEditor()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isNoteButton(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .editNote();
    }

    @Test
    public void favoriteButtonConsumesClickAndTogglesFavorite()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isFavoriteButton(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .toggleFavorite();
    }

    @Test
    public void targetButtonConsumesClickAndTargetsPlayer()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isTargetButton(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .target();
    }

    @Test
    public void lookupButtonConsumesClickAndLooksUpPlayer()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isLookupButton(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .lookup();
    }

    @Test
    public void clanLinkConsumesClickAndLooksUpClan()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isClanLink(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .lookupClan();
    }

    @Test
    public void reportLinkConsumesClickAndOpensReportCase()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isReportCaseLink(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .openReportCase();
    }

    @Test
    public void clickInsideCardIsConsumedWithoutClosing()
    {
        final TestHarness harness =
                createOpenProfileHarness();

        Mockito.when(
                        harness.controller.isInsideCard(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        true);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();

        Mockito.verifyNoInteractions(
                harness.registry);
    }

    @Test
    public void leftClickModeOpensReferenceHitbox()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        final PlayerReference reference =
                reference(
                        ReferenceType.MENTION,
                        "Santa");

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference)));

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .open(
                        Mockito.same(
                                reference),
                        Mockito.any(
                                Point.class));
    }

    @Test
    public void bothModeOpensReferenceHitboxOnLeftClick()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.BOTH);

        final PlayerReference reference =
                reference(
                        ReferenceType.TAG,
                        "Santa");

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference)));

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .open(
                        Mockito.same(
                                reference),
                        Mockito.any(
                                Point.class));
    }

    @Test
    public void rightClickModeDoesNotOpenReferenceOnLeftClick()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verifyNoInteractions(
                harness.registry);

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .open(
                        Mockito.any(
                                PlayerReference.class),
                        Mockito.any(
                                Point.class));
    }

    @Test
    public void nullInteractionModeDoesNotOpenReferenceOnLeftClick()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        null);

        harness.listener.mousePressed(
                leftClick());

        Mockito.verifyNoInteractions(
                harness.registry);
    }

    @Test
    public void missingReferenceHitboxLeavesClickUntouched()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.empty());

        final MouseEvent event =
                leftClick();

        harness.listener.mousePressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .open(
                        Mockito.any(
                                PlayerReference.class),
                        Mockito.any(
                                Point.class));
    }

    @Test
    public void clickingElsewhereClosesOpenProfile()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        harness.listener.mousePressed(
                leftClick());

        Mockito.verify(
                        harness.controller)
                .close();
    }

    @Test
    public void consumedLeftPressAlsoConsumesRelease()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference(
                                                ReferenceType.MENTION,
                                                "Santa"))));

        harness.listener.mousePressed(
                leftClick());

        final MouseEvent release =
                mouseReleased(
                        MouseEvent.BUTTON1,
                        50,
                        50);

        harness.listener.mouseReleased(
                release);

        Assert.assertTrue(
                release.isConsumed());
    }

    @Test
    public void consumedLeftPressAlsoConsumesClick()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference(
                                                ReferenceType.MENTION,
                                                "Santa"))));

        harness.listener.mousePressed(
                leftClick());

        final MouseEvent click =
                mouseClicked(
                        MouseEvent.BUTTON1,
                        50,
                        50);

        harness.listener.mouseClicked(
                click);

        Assert.assertTrue(
                click.isConsumed());
    }

    @Test
    public void suppressionClearsAfterConsumedMouseClick()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference(
                                                ReferenceType.MENTION,
                                                "Santa"))));

        harness.listener.mousePressed(
                leftClick());

        harness.listener.mouseClicked(
                mouseClicked(
                        MouseEvent.BUTTON1,
                        50,
                        50));

        final MouseEvent secondClick =
                mouseClicked(
                        MouseEvent.BUTTON1,
                        50,
                        50);

        harness.listener.mouseClicked(
                secondClick);

        Assert.assertFalse(
                secondClick.isConsumed());
    }

    @Test
    public void escapeClosesOpenQuickProfile()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isEditingNote())
                .thenReturn(
                        false);

        Mockito.when(
                        harness.controller.isEditingTag())
                .thenReturn(
                        false);

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyPressed(
                event);

        Assert.assertTrue(
                event.isConsumed());

        Mockito.verify(
                        harness.controller)
                .close();
    }

    @Test
    public void escapeReleaseIsConsumedAfterProfileClose()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED));

        final KeyEvent release =
                released(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyReleased(
                release);

        Assert.assertTrue(
                release.isConsumed());
    }

    @Test
    public void escapeSuppressionClearsAfterRelease()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED));

        harness.listener.keyReleased(
                released(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED));

        final KeyEvent secondRelease =
                released(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyReleased(
                secondRelease);

        Assert.assertFalse(
                secondRelease.isConsumed());
    }

    @Test
    public void escapeDoesNotCloseProfileWhileEditingNote()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isEditingNote())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyPressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();
    }

    @Test
    public void escapeDoesNotCloseProfileWhileEditingTag()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isEditingTag())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyPressed(
                event);

        Assert.assertFalse(
                event.isConsumed());

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();
    }

    @Test
    public void nonEscapeKeyDoesNotCloseQuickProfile()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_A,
                        'a'));

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();
    }

    @Test
    public void handledSuggestionKeyIsNotProcessedAsProfileEscape()
    {
        final TestHarness harness =
                createHarness();

        activateSuggestion(
                harness);

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        final KeyEvent event =
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyPressed(
                event);

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();
    }

    @Test
    public void handledSuggestionEscapeConsumesRelease()
    {
        final TestHarness harness =
                createHarness();

        activateSuggestion(
                harness);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED));

        final KeyEvent release =
                released(
                        KeyEvent.VK_ESCAPE,
                        KeyEvent.CHAR_UNDEFINED);

        harness.listener.keyReleased(
                release);

        Assert.assertTrue(
                release.isConsumed());
    }

    @Test
    public void handledSuggestionTypedCharacterConsumesTypedEvent()
            throws Exception
    {
        final TestHarness harness =
                createHarness();

        setBooleanField(
                harness.listener,
                "suppressCurrentSuggestionTyped",
                true);

        final KeyEvent typed =
                typed(
                        'a');

        harness.listener.keyTyped(
                typed);

        Assert.assertTrue(
                typed.isConsumed());

        Assert.assertFalse(
                booleanField(
                        harness.listener,
                        "suppressCurrentSuggestionTyped"));
    }

    @Test
    public void suggestionTypedSuppressionClearsAfterTypedEvent()
    {
        final TestHarness harness =
                createHarness();

        activateSuggestion(
                harness);

        Mockito.when(
                        harness.config.completeSuggestionHotkey())
                .thenReturn(
                        new Keybind(
                                KeyEvent.VK_A,
                                0));

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_A,
                        'a'));

        harness.listener.keyTyped(
                typed(
                        'a'));

        final KeyEvent second =
                typed(
                        'b');

        harness.listener.keyTyped(
                second);

        Assert.assertFalse(
                second.isConsumed());
    }

    @Test
    public void suggestionServiceIsSkippedWhileEditingNote()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isEditingNote())
                .thenReturn(
                        true);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_A,
                        'a'));

        Mockito.verify(
                        harness.config,
                        Mockito.never())
                .suggestUsernames();
    }

    @Test
    public void suggestionServiceIsSkippedWhileEditingTag()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isEditingTag())
                .thenReturn(
                        true);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_A,
                        'a'));

        Mockito.verify(
                        harness.config,
                        Mockito.never())
                .suggestUsernames();
    }

    @Test
    public void nullSuggestionServiceIsSafe()
    {
        final TestHarness harness =
                createHarness(
                        null);

        harness.listener.keyPressed(
                pressed(
                        KeyEvent.VK_A,
                        'a'));

        Mockito.verify(
                        harness.controller,
                        Mockito.never())
                .close();
    }

    @Test
    public void nullKeyPressedEventIsSafe()
    {
        createHarness()
                .listener
                .keyPressed(
                        null);
    }

    @Test
    public void nullKeyReleasedEventIsSafe()
    {
        createHarness()
                .listener
                .keyReleased(
                        null);
    }

    @Test
    public void nullKeyTypedEventIsSafe()
    {
        createHarness()
                .listener
                .keyTyped(
                        null);
    }

    @Test
    public void menuOpenedAlwaysClosesExistingQuickProfile()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        final MenuOpened event =
                Mockito.mock(
                        MenuOpened.class);

        harness.listener.onMenuOpened(
                event);

        Mockito.verify(
                        harness.controller)
                .close();
    }

    @Test
    public void leftClickOnlyModeDoesNotAugmentMenu()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.LEFT_CLICK);

        harness.listener.onMenuOpened(
                Mockito.mock(
                        MenuOpened.class));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void nullInteractionModeDoesNotAugmentMenu()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        null);

        harness.listener.onMenuOpened(
                Mockito.mock(
                        MenuOpened.class));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void missingMouseCanvasPointDoesNotAugmentMenu()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        Mockito.when(
                        harness.client.getMouseCanvasPosition())
                .thenReturn(
                        null);

        harness.listener.onMenuOpened(
                Mockito.mock(
                        MenuOpened.class));

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void mentionRightClickCreatesProfileAndLookupEntries()
    {
        final TestHarness harness =
                createSemanticMenuHarness(
                        ReferenceType.MENTION,
                        false);

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -1);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -3);

        Mockito.verify(
                        harness.client,
                        Mockito.times(
                                2))
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void tagRightClickCreatesProfileAndLookupEntries()
    {
        final TestHarness harness =
                createSemanticMenuHarness(
                        ReferenceType.TAG,
                        false);

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        Mockito.verify(
                        harness.client,
                        Mockito.times(
                                2))
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void targetableMentionCreatesTargetEntryBetweenProfileAndLookup()
    {
        final TestHarness harness =
                createSemanticMenuHarness(
                        ReferenceType.MENTION,
                        true);

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -1);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -2);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -3);

        Mockito.verify(
                        harness.client,
                        Mockito.times(
                                3))
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void senderReferenceDoesNotCreateSyntheticReferenceMenu()
    {
        final TestHarness harness =
                createSemanticMenuHarness(
                        ReferenceType.SENDER,
                        false);

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void missingReferenceHitboxDoesNotCreateSyntheticMenu()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        stubMouseCanvasPoint(
                harness.client,
                50,
                50);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.empty());

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void nullReferenceDoesNotCreateSyntheticMenu()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        stubMouseCanvasPoint(
                harness.client,
                50,
                50);

        final ReferenceHitbox hitbox =
                new ReferenceHitbox(
                        1L,
                        new Rectangle(
                                0,
                                0,
                                100,
                                100),
                        null,
                        null);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox));

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        Mockito.verify(
                        harness.client,
                        Mockito.never())
                .createMenuEntry(
                        Mockito.anyInt());
    }

    @Test
    public void splitPrivateSenderMenuCreatesOpenProfileEntry()
    {
        final TestHarness harness =
                createSplitPrivateNativeMenuHarness(
                        false);

        final MenuEntry profileEntry =
                menuEntryBuilder();

        Mockito.when(
                        harness.client.createMenuEntry(
                                1))
                .thenReturn(
                        profileEntry);

        harness.listener.onMenuOpened(
                splitPrivateMenuEvent(
                        harness,
                        "Santa"));

        Mockito.verify(
                        profileEntry)
                .setOption(
                        "Open Profile");

        Mockito.verify(
                        profileEntry)
                .setTarget(
                        "<col=00ff00>Santa</col>");
    }

    @Test
    public void splitPrivateSenderMenuCreatesTargetWhenEnabled()
    {
        final TestHarness harness =
                createSplitPrivateNativeMenuHarness(
                        true);

        final MenuEntry profileEntry =
                menuEntryBuilder();

        final MenuEntry targetEntry =
                menuEntryBuilder();

        Mockito.when(
                        harness.client.createMenuEntry(
                                1))
                .thenReturn(
                        profileEntry);

        Mockito.when(
                        harness.client.createMenuEntry(
                                -1))
                .thenReturn(
                        targetEntry);

        harness.listener.onMenuOpened(
                splitPrivateMenuEvent(
                        harness,
                        "Santa"));

        Mockito.verify(
                        targetEntry)
                .setOption(
                        Mockito.contains(
                                "Target"));
    }

    @Test
    public void splitPrivateMentionSuppressesNativeSenderMenu()
    {
        assertSplitPrivateReferenceSuppressesNativeSenderMenu(
                ReferenceType.MENTION);
    }

    @Test
    public void splitPrivateTagSuppressesNativeSenderMenu()
    {
        assertSplitPrivateReferenceSuppressesNativeSenderMenu(
                ReferenceType.TAG);
    }

    @Test
    public void ownMessageMentionSuppressesWalkHereWithoutReport()
    {
        final TestHarness harness =
                createSemanticMenuHarness(
                        ReferenceType.MENTION,
                        false);

        final MenuEntry walkHere =
                nativeMenuEntry(
                        "Walk here",
                        "",
                        0);

        final MenuEntry[] nativeEntries =
                new MenuEntry[]
                        {
                                walkHere
                        };

        Mockito.when(
                        harness.client.getMenuEntries())
                .thenReturn(
                        nativeEntries);

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        final ArgumentCaptor<MenuEntry[]> entriesCaptor =
                ArgumentCaptor.forClass(
                        MenuEntry[].class);

        Mockito.verify(
                        harness.client)
                .setMenuEntries(
                        entriesCaptor.capture());

        final MenuEntry[] remaining =
                entriesCaptor.getValue();

        Assert.assertEquals(
                0,
                remaining.length);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -1);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -3);
    }

    @Test
    public void ownMessageTagSuppressesWalkHereWithoutReport()
    {
        final TestHarness harness =
                createSemanticMenuHarness(
                        ReferenceType.TAG,
                        false);

        final MenuEntry walkHere =
                nativeMenuEntry(
                        "Walk here",
                        "",
                        0);

        final MenuEntry[] nativeEntries =
                new MenuEntry[]
                        {
                                walkHere
                        };

        Mockito.when(
                        harness.client.getMenuEntries())
                .thenReturn(
                        nativeEntries);

        harness.listener.onMenuOpened(
                emptyMenuEvent());

        final ArgumentCaptor<MenuEntry[]> entriesCaptor =
                ArgumentCaptor.forClass(
                        MenuEntry[].class);

        Mockito.verify(
                        harness.client)
                .setMenuEntries(
                        entriesCaptor.capture());

        final MenuEntry[] remaining =
                entriesCaptor.getValue();

        Assert.assertEquals(
                0,
                remaining.length);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -1);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -3);
    }

    @Test
    public void cleanPlayerNameRemovesMarkup()
            throws Exception
    {
        Assert.assertEquals(
                "Santa",
                cleanPlayerName(
                        "<col=ff0000>Santa</col>"));
    }

    @Test
    public void cleanPlayerNameConvertsNonBreakingSpaces()
            throws Exception
    {
        Assert.assertEquals(
                "Santa Clause",
                cleanPlayerName(
                        " Santa\u00A0Clause "));
    }

    @Test
    public void cleanPlayerNameReturnsEmptyForNull()
            throws Exception
    {
        Assert.assertEquals(
                "",
                cleanPlayerName(
                        null));
    }

    @Test
    public void menuTargetPrefersLookupName()
            throws Exception
    {
        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "Raw Santa")
                        .lookupName(
                                "  Santa  ")
                        .type(
                                ReferenceType.MENTION)
                        .build();

        Assert.assertEquals(
                "Santa",
                menuTarget(
                        reference));
    }

    @Test
    public void menuTargetFallsBackToRawText()
            throws Exception
    {
        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                "  Santa Clause  ")
                        .lookupName(
                                "   ")
                        .type(
                                ReferenceType.TAG)
                        .build();

        Assert.assertEquals(
                "Santa Clause",
                menuTarget(
                        reference));
    }

    @Test
    public void menuTargetReturnsEmptyWhenReferenceHasNoNames()
            throws Exception
    {
        final PlayerReference reference =
                PlayerReference.builder()
                        .type(
                                ReferenceType.MENTION)
                        .build();

        Assert.assertEquals(
                "",
                menuTarget(
                        reference));
    }

    @Test
    public void menuTargetReturnsEmptyForNullReference()
            throws Exception
    {
        Assert.assertEquals(
                "",
                menuTarget(
                        null));
    }

    /*
     * HELPERS
     */

    private static TestHarness createHarness()
    {
        return createHarnessWithSuggestionService(
                true);
    }

    private static TestHarness createHarness(
            SuggestionService suggestionService)
    {
        final Client client =
                Mockito.mock(
                        Client.class);

        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        final ChatHitboxRegistry registry =
                Mockito.mock(
                        ChatHitboxRegistry.class);

        final QuickProfileController controller =
                Mockito.mock(
                        QuickProfileController.class);

        final ClientThread clientThread =
                Mockito.mock(
                        ClientThread.class);

        final PlayerDirectory playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        Mockito.when(
                        client.isMenuOpen())
                .thenReturn(
                        false);

        Mockito.when(
                        client.getMenuEntries())
                .thenReturn(
                        new MenuEntry[0]);

        Mockito.when(
                        controller.isOpen())
                .thenReturn(
                        false);

        Mockito.when(
                        controller.isEditingNote())
                .thenReturn(
                        false);

        Mockito.when(
                        controller.isEditingTag())
                .thenReturn(
                        false);

        return new TestHarness(
                client,
                config,
                registry,
                controller,
                clientThread,
                playerDirectory,
                suggestionService,
                new InputListener(
                        client,
                        config,
                        registry,
                        controller,
                        suggestionService));
    }

    private static TestHarness createHarnessWithSuggestionService(
            boolean includeSuggestionService)
    {
        final Client client =
                Mockito.mock(
                        Client.class);

        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        final ChatHitboxRegistry registry =
                Mockito.mock(
                        ChatHitboxRegistry.class);

        final QuickProfileController controller =
                Mockito.mock(
                        QuickProfileController.class);

        final ClientThread clientThread =
                Mockito.mock(
                        ClientThread.class);

        final PlayerDirectory playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        final SuggestionService suggestionService =
                includeSuggestionService
                        ? new SuggestionService(
                        client,
                        clientThread,
                        config,
                        playerDirectory)
                        : null;

        Mockito.when(
                        client.isMenuOpen())
                .thenReturn(
                        false);

        Mockito.when(
                        client.getMenuEntries())
                .thenReturn(
                        new MenuEntry[0]);

        Mockito.when(
                        controller.isOpen())
                .thenReturn(
                        false);

        Mockito.when(
                        controller.isEditingNote())
                .thenReturn(
                        false);

        Mockito.when(
                        controller.isEditingTag())
                .thenReturn(
                        false);

        return new TestHarness(
                client,
                config,
                registry,
                controller,
                clientThread,
                playerDirectory,
                suggestionService,
                new InputListener(
                        client,
                        config,
                        registry,
                        controller,
                        suggestionService));
    }

    private static void activateSuggestion(
            TestHarness harness)
    {
        Mockito.when(
                        harness.config.suggestUsernames())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.config.autocompleteFriends())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.config.completeSuggestionHotkey())
                .thenReturn(
                        new Keybind(
                                KeyEvent.VK_TAB,
                                0));

        Mockito.when(
                        harness.client.getVarcStrValue(
                                VarClientID.CHATINPUT))
                .thenReturn(
                        "@Sa");

        Mockito.when(
                        harness.playerDirectory.all())
                .thenReturn(
                        Collections.singletonList(
                                PlayerIdentity.builder()
                                        .canonicalName(
                                                "Santa")
                                        .sources(
                                                PlayerIdentity.sourceSet(
                                                        PlayerSource.FRIEND))
                                        .build()));
    }

    private static TestHarness createSplitPrivateNativeMenuHarness(
            boolean targetable)
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        Mockito.when(
                        harness.config.targetPlayerOption())
                .thenReturn(
                        targetable);

        Mockito.when(
                        harness.config.targetColor())
                .thenReturn(
                        Color.RED);

        Mockito.when(
                        harness.controller.canTarget(
                                Mockito.any(
                                        PlayerReference.class)))
                .thenReturn(
                        targetable);

        stubMouseCanvasPoint(
                harness.client,
                50,
                50);

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.empty());

        stubSplitPrivateWidgets(
                harness.client);

        return harness;
    }

    private static void assertSplitPrivateReferenceSuppressesNativeSenderMenu(
            ReferenceType type)
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        stubMouseCanvasPoint(
                harness.client,
                50,
                50);

        final PlayerReference reference =
                reference(
                        type,
                        "Tagged Player");

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference)));

        stubSplitPrivateWidgets(
                harness.client);

        final MenuEntry report =
                nativeMenuEntry(
                        "Report",
                        "<col=00ff00>Sender</col>",
                        InterfaceID.PmChat.PM1);

        final MenuEntry message =
                nativeMenuEntry(
                        "Message",
                        "<col=00ff00>Sender</col>",
                        InterfaceID.PmChat.PM1);

        final MenuEntry cancel =
                nativeMenuEntry(
                        "Cancel",
                        "",
                        0);

        final MenuEntry[] nativeEntries =
                new MenuEntry[]
                        {
                                cancel,
                                report,
                                message
                        };

        Mockito.when(
                        harness.client.getMenuEntries())
                .thenReturn(
                        nativeEntries);

        stubMenuEntryCreation(
                harness.client);

        final MenuOpened event =
                Mockito.mock(
                        MenuOpened.class);

        Mockito.when(
                        event.getMenuEntries())
                .thenReturn(
                        nativeEntries);

        harness.listener.onMenuOpened(
                event);

        final ArgumentCaptor<MenuEntry[]> entriesCaptor =
                ArgumentCaptor.forClass(
                        MenuEntry[].class);

        Mockito.verify(
                        harness.client)
                .setMenuEntries(
                        entriesCaptor.capture());

        final MenuEntry[] remaining =
                entriesCaptor.getValue();

        Assert.assertEquals(
                1,
                remaining.length);

        Assert.assertSame(
                cancel,
                remaining[0]);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -1);

        Mockito.verify(
                        harness.client)
                .createMenuEntry(
                        -3);
    }

    private static MenuOpened splitPrivateMenuEvent(
            TestHarness harness,
            String playerName)
    {
        final MenuEntry report =
                nativeMenuEntry(
                        "Report",
                        "<col=00ff00>"
                                + playerName
                                + "</col>",
                        InterfaceID.PmChat.PM1);

        final MenuEntry[] entries =
                new MenuEntry[]
                        {
                                report
                        };

        Mockito.when(
                        harness.client.getMenuEntries())
                .thenReturn(
                        entries);

        final MenuOpened event =
                Mockito.mock(
                        MenuOpened.class);

        Mockito.when(
                        event.getMenuEntries())
                .thenReturn(
                        entries);

        return event;
    }

    private static void stubSplitPrivateWidgets(
            Client client)
    {
        final Widget container =
                Mockito.mock(
                        Widget.class);

        final Widget message =
                Mockito.mock(
                        Widget.class);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PM_CHAT,
                                WidgetUtil.componentToId(
                                        InterfaceID.PmChat.PM1)))
                .thenReturn(
                        message);

        Mockito.when(
                        client.getWidget(
                                InterfaceID.PmChat.CONTAINER))
                .thenReturn(
                        container);

        Mockito.when(
                        message.getParent())
                .thenReturn(
                        container);
    }

    private static MenuEntry nativeMenuEntry(
            String option,
            String target,
            int param1)
    {
        final MenuEntry entry =
                Mockito.mock(
                        MenuEntry.class);

        Mockito.when(
                        entry.getOption())
                .thenReturn(
                        option);

        Mockito.when(
                        entry.getTarget())
                .thenReturn(
                        target);

        Mockito.when(
                        entry.getParam1())
                .thenReturn(
                        param1);

        return entry;
    }

    private static MenuEntry menuEntryBuilder()
    {
        final MenuEntry entry =
                Mockito.mock(
                        MenuEntry.class);

        Mockito.when(
                        entry.setOption(
                                Mockito.anyString()))
                .thenReturn(
                        entry);

        Mockito.when(
                        entry.setTarget(
                                Mockito.anyString()))
                .thenReturn(
                        entry);

        Mockito.when(
                        entry.setType(
                                Mockito.any(
                                        MenuAction.class)))
                .thenReturn(
                        entry);

        Mockito.when(
                        entry.onClick(
                                Mockito.any()))
                .thenReturn(
                        entry);

        return entry;
    }

    private static TestHarness createOpenProfileHarness()
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.controller.isOpen())
                .thenReturn(
                        true);

        Mockito.when(
                        harness.controller.tagRemovalAt(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        null);

        return harness;
    }

    private static TestHarness createSemanticMenuHarness(
            ReferenceType type,
            boolean targetable)
    {
        final TestHarness harness =
                createHarness();

        Mockito.when(
                        harness.config.chatInteractionMode())
                .thenReturn(
                        ChatInteractionMode.RIGHT_CLICK);

        Mockito.when(
                        harness.config.targetPlayerOption())
                .thenReturn(
                        targetable);

        Mockito.when(
                        harness.config.targetColor())
                .thenReturn(
                        Color.RED);

        stubMouseCanvasPoint(
                harness.client,
                50,
                50);

        final PlayerReference reference =
                reference(
                        type,
                        "Santa");

        Mockito.when(
                        harness.registry.find(
                                Mockito.any(
                                        Point.class)))
                .thenReturn(
                        Optional.of(
                                hitbox(
                                        reference)));

        Mockito.when(
                        harness.controller.canTarget(
                                Mockito.same(
                                        reference)))
                .thenReturn(
                        targetable);

        stubMenuEntryCreation(
                harness.client);

        return harness;
    }

    private static void stubMenuEntryCreation(
            Client client)
    {
        Mockito.when(
                        client.createMenuEntry(
                                Mockito.anyInt()))
                .thenAnswer(invocation ->
                {
                    final MenuEntry entry =
                            Mockito.mock(
                                    MenuEntry.class);

                    Mockito.when(
                                    entry.setOption(
                                            Mockito.anyString()))
                            .thenReturn(
                                    entry);

                    Mockito.when(
                                    entry.setTarget(
                                            Mockito.anyString()))
                            .thenReturn(
                                    entry);

                    Mockito.when(
                                    entry.setType(
                                            Mockito.any(
                                                    MenuAction.class)))
                            .thenReturn(
                                    entry);

                    Mockito.when(
                                    entry.onClick(
                                            Mockito.any()))
                            .thenReturn(
                                    entry);

                    return entry;
                });
    }

    private static void stubMouseCanvasPoint(
            Client client,
            int x,
            int y)
    {
        final net.runelite.api.Point point =
                new net.runelite.api.Point(
                        x,
                        y);

        Mockito.when(
                        client.getMouseCanvasPosition())
                .thenReturn(
                        point);
    }

    private static MenuOpened emptyMenuEvent()
    {
        final MenuOpened event =
                Mockito.mock(
                        MenuOpened.class);

        Mockito.when(
                        event.getMenuEntries())
                .thenReturn(
                        new MenuEntry[0]);

        return event;
    }

    private static void setBooleanField(
            InputListener listener,
            String fieldName,
            boolean value)
            throws Exception
    {
        final java.lang.reflect.Field field =
                InputListener.class
                        .getDeclaredField(
                                fieldName);

        field.setAccessible(
                true);

        field.setBoolean(
                listener,
                value);
    }

    private static boolean booleanField(
            InputListener listener,
            String fieldName)
            throws Exception
    {
        final java.lang.reflect.Field field =
                InputListener.class
                        .getDeclaredField(
                                fieldName);

        field.setAccessible(
                true);

        return field.getBoolean(
                listener);
    }

    private static PlayerReference reference(
            ReferenceType type,
            String name)
    {
        return PlayerReference.builder()
                .rawText(
                        name)
                .normalizedToken(
                        name)
                .lookupName(
                        name)
                .startOffset(
                        0)
                .endOffset(
                        name != null
                                ? name.length()
                                : 0)
                .type(
                        type)
                .locallyResolved(
                        false)
                .identity(
                        null)
                .chatType(
                        null)
                .build();
    }

    private static ReferenceHitbox hitbox(
            PlayerReference reference)
    {
        return new ReferenceHitbox(
                1L,
                new Rectangle(
                        0,
                        0,
                        100,
                        100),
                reference,
                null);
    }

    private static MouseEvent leftClick()
    {
        return mousePressed(
                MouseEvent.BUTTON1,
                50,
                50);
    }

    private static MouseEvent mousePressed(
            int button,
            int x,
            int y)
    {
        return new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                x,
                y,
                1,
                false,
                button);
    }

    private static MouseEvent mouseReleased(
            int button,
            int x,
            int y)
    {
        return new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(),
                0,
                x,
                y,
                1,
                false,
                button);
    }

    private static MouseEvent mouseClicked(
            int button,
            int x,
            int y)
    {
        return new MouseEvent(
                new Canvas(),
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                x,
                y,
                1,
                false,
                button);
    }

    private static KeyEvent pressed(
            int keyCode,
            char keyChar)
    {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                keyCode,
                keyChar);
    }

    private static KeyEvent released(
            int keyCode,
            char keyChar)
    {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                0,
                keyCode,
                keyChar);
    }

    private static KeyEvent typed(
            char keyChar)
    {
        return new KeyEvent(
                new Canvas(),
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_UNDEFINED,
                keyChar);
    }

    private static String cleanPlayerName(
            String value)
            throws Exception
    {
        final Method method =
                InputListener.class
                        .getDeclaredMethod(
                                "cleanPlayerName",
                                String.class);

        method.setAccessible(
                true);

        return (String) method.invoke(
                null,
                value);
    }

    private static String menuTarget(
            PlayerReference reference)
            throws Exception
    {
        final Method method =
                InputListener.class
                        .getDeclaredMethod(
                                "menuTarget",
                                PlayerReference.class);

        method.setAccessible(
                true);

        return (String) method.invoke(
                null,
                reference);
    }

    private static final class TestHarness
    {
        private final Client client;
        private final Configurations config;
        private final ChatHitboxRegistry registry;
        private final QuickProfileController controller;
        private final ClientThread clientThread;
        private final PlayerDirectory playerDirectory;
        private final SuggestionService suggestionService;
        private final InputListener listener;

        private TestHarness(
                Client client,
                Configurations config,
                ChatHitboxRegistry registry,
                QuickProfileController controller,
                ClientThread clientThread,
                PlayerDirectory playerDirectory,
                SuggestionService suggestionService,
                InputListener listener)
        {
            this.client =
                    client;

            this.config =
                    config;

            this.registry =
                    registry;

            this.controller =
                    controller;

            this.clientThread =
                    clientThread;

            this.playerDirectory =
                    playerDirectory;

            this.suggestionService =
                    suggestionService;

            this.listener =
                    listener;
        }
    }
}
