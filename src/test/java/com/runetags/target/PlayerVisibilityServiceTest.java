package com.runetags.target;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.callback.Hooks;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class PlayerVisibilityServiceTest
{
    private Hooks hooks;
    private TargetController targetController;
    private PlayerVisibilityService visibilityService;

    @Before
    public void setUp()
    {
        hooks =
                Mockito.mock(
                        Hooks.class);

        targetController =
                Mockito.mock(
                        TargetController.class);

        visibilityService =
                new PlayerVisibilityService(
                        hooks,
                        targetController);
    }

    /*
     * TESTS
     */

    @Test
    public void startRegistersListenerExactlyOnce()
    {
        visibilityService.start();
        visibilityService.start();

        Mockito.verify(
                        hooks,
                        Mockito.times(
                                1))
                .registerRenderableDrawListener(
                        Mockito.any(
                                Hooks.RenderableDrawListener.class));
    }

    @Test
    public void stopBeforeStartDoesNothing()
    {
        visibilityService.stop();

        Mockito.verify(
                        hooks,
                        Mockito.never())
                .unregisterRenderableDrawListener(
                        Mockito.any(
                                Hooks.RenderableDrawListener.class));
    }

    @Test
    public void stopUnregistersSameListenerExactlyOnce()
    {
        visibilityService.start();

        final ArgumentCaptor<Hooks.RenderableDrawListener> registered =
                ArgumentCaptor.forClass(
                        Hooks.RenderableDrawListener.class);

        Mockito.verify(
                        hooks)
                .registerRenderableDrawListener(
                        registered.capture());

        visibilityService.stop();
        visibilityService.stop();

        Mockito.verify(
                        hooks,
                        Mockito.times(
                                1))
                .unregisterRenderableDrawListener(
                        registered.getValue());
    }

    @Test
    public void serviceCanRestartAfterStop()
    {
        visibilityService.start();
        visibilityService.stop();
        visibilityService.start();

        Mockito.verify(
                        hooks,
                        Mockito.times(
                                2))
                .registerRenderableDrawListener(
                        Mockito.any(
                                Hooks.RenderableDrawListener.class));

        Mockito.verify(
                        hooks,
                        Mockito.times(
                                1))
                .unregisterRenderableDrawListener(
                        Mockito.any(
                                Hooks.RenderableDrawListener.class));
    }

    @Test
    public void nonPlayerRenderableIsAlwaysVisible()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final NPC npc =
                Mockito.mock(
                        NPC.class);

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        true);

        Assert.assertTrue(
                listener.draw(
                        npc,
                        false));

        Assert.assertTrue(
                listener.draw(
                        npc,
                        true));

        Mockito.verify(
                        targetController,
                        Mockito.never())
                .isTargetingName(
                        Mockito.anyString());
    }

    @Test
    public void playersRemainVisibleWhenHideOthersIsInactive()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final Player santa =
                player(
                        "Santa");

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        false);

        Assert.assertTrue(
                listener.draw(
                        santa,
                        false));

        Assert.assertTrue(
                listener.draw(
                        santa,
                        true));

        Mockito.verify(
                        targetController,
                        Mockito.never())
                .isTargetingName(
                        Mockito.anyString());
    }

    @Test
    public void targetPlayerRemainsVisibleWhenHideOthersIsActive()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final Player santa =
                player(
                        "Santa");

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        true);

        Mockito.when(
                        targetController.isTargetingName(
                                "Santa"))
                .thenReturn(
                        true);

        Assert.assertTrue(
                listener.draw(
                        santa,
                        false));

        Assert.assertTrue(
                listener.draw(
                        santa,
                        true));
    }

    @Test
    public void nonTargetPlayerIsHiddenWhenHideOthersIsActive()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final Player zezima =
                player(
                        "Zezima");

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        true);

        Mockito.when(
                        targetController.isTargetingName(
                                "Zezima"))
                .thenReturn(
                        false);

        Assert.assertFalse(
                listener.draw(
                        zezima,
                        false));

        Assert.assertFalse(
                listener.draw(
                        zezima,
                        true));
    }

    @Test
    public void nullNamedPlayerRemainsVisible()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final Player player =
                player(
                        null);

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        true);

        Assert.assertTrue(
                listener.draw(
                        player,
                        false));

        Assert.assertTrue(
                listener.draw(
                        player,
                        true));

        Mockito.verify(
                        targetController,
                        Mockito.never())
                .isTargetingName(
                        Mockito.anyString());
    }

    @Test
    public void emptyNamedPlayerRemainsVisible()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final Player player =
                player(
                        "");

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        true);

        Assert.assertTrue(
                listener.draw(
                        player,
                        false));

        Assert.assertTrue(
                listener.draw(
                        player,
                        true));

        Mockito.verify(
                        targetController,
                        Mockito.never())
                .isTargetingName(
                        Mockito.anyString());
    }

    @Test
    public void drawingUiUsesSameVisibilityRuleAsModelDrawing()
    {
        final Hooks.RenderableDrawListener listener =
                startAndCaptureListener();

        final Player santa =
                player(
                        "Santa");

        Mockito.when(
                        targetController.shouldHideOtherPlayers())
                .thenReturn(
                        true);

        Mockito.when(
                        targetController.isTargetingName(
                                "Santa"))
                .thenReturn(
                        false);

        final boolean modelVisible =
                listener.draw(
                        santa,
                        false);

        final boolean uiVisible =
                listener.draw(
                        santa,
                        true);

        Assert.assertEquals(
                modelVisible,
                uiVisible);

        Assert.assertFalse(
                modelVisible);
    }

    /*
     * HELPERS
     */

    private Hooks.RenderableDrawListener startAndCaptureListener()
    {
        visibilityService.start();

        final ArgumentCaptor<Hooks.RenderableDrawListener> captor =
                ArgumentCaptor.forClass(
                        Hooks.RenderableDrawListener.class);

        Mockito.verify(
                        hooks)
                .registerRenderableDrawListener(
                        captor.capture());

        return captor.getValue();
    }

    private static Player player(
            String name)
    {
        final Player player =
                Mockito.mock(
                        Player.class);

        Mockito.when(
                        player.getName())
                .thenReturn(
                        name);

        return player;
    }
}