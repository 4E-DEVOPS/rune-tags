package com.runetags.overlay;

import com.runetags.Configurations;
import com.runetags.config.TargetMode;
import com.runetags.records.LocalPlayerRecordService;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Collections;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class FavoriteOverlayTest
{
    private Client client;
    private Configurations config;
    private LocalPlayerRecordService recordService;
    private ModelOutlineRenderer outlineRenderer;

    private BufferedImage image;
    private Graphics2D graphics;

    private FavoriteOverlay overlay;

    /*
     * TESTS
     */

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        config =
                Mockito.mock(
                        Configurations.class);

        recordService =
                Mockito.mock(
                        LocalPlayerRecordService.class);

        outlineRenderer =
                Mockito.mock(
                        ModelOutlineRenderer.class);

        image =
                new BufferedImage(
                        300,
                        300,
                        BufferedImage.TYPE_INT_ARGB);

        graphics =
                image.createGraphics();

        overlay =
                new FavoriteOverlay(
                        client,
                        config,
                        recordService,
                        outlineRenderer);

        Mockito.when(
                        config.showFavorites())
                .thenReturn(
                        true);

        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.OFF);

        Mockito.when(
                        config.favoriteColor())
                .thenReturn(
                        new Color(
                                255,
                                205,
                                70));
    }

    @After
    public void tearDown()
    {
        graphics.dispose();
    }

    @Test
    public void disabledFavoritesDoNotInspectWorld()
    {
        Mockito.when(
                        config.showFavorites())
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        Mockito.verify(
                        client,
                        Mockito.never())
                .getTopLevelWorldView();

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void loggedOutClientDoesNotInspectWorld()
    {
        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGIN_SCREEN);

        overlay.render(
                graphics);

        Mockito.verify(
                        client,
                        Mockito.never())
                .getTopLevelWorldView();

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void missingWorldViewDoesNothing()
    {
        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        null);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void nullTargetModeFallsBackToOff()
    {
        final Player player =
                player(
                        "Santa");

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        null);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        true);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);

        Mockito.verify(
                        player,
                        Mockito.never())
                .getCanvasTilePoly();
    }

    @Test
    public void nonFavoritePlayerIsIgnored()
    {
        final Player player =
                player(
                        "Santa");

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        false);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);

        Mockito.verify(
                        player,
                        Mockito.never())
                .getCanvasTilePoly();

        Mockito.verify(
                        player,
                        Mockito.never())
                .getCanvasTextLocation(
                        Mockito.any(
                                Graphics2D.class),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void nullPlayerIsIgnored()
    {
        final WorldView worldView =
                worldView(
                        (Player) null);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);

        Mockito.verifyNoInteractions(
                recordService);
    }

    @Test
    public void nullPlayerNameIsIgnored()
    {
        final Player player =
                player(
                        null);

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);

        Mockito.verifyNoInteractions(
                recordService);
    }

    @Test
    public void blankPlayerNameIsIgnored()
    {
        final Player player =
                player(
                        "   ");

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        overlay.render(
                graphics);

        Mockito.verifyNoInteractions(
                outlineRenderer);

        Mockito.verifyNoInteractions(
                recordService);
    }

    @Test
    public void outlineModeDrawsFavoriteOutline()
    {
        final Player player =
                player(
                        "Santa");

        final Color favoriteColor =
                new Color(
                        100,
                        150,
                        200,
                        255);

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        true);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.OUTLINE);

        Mockito.when(
                        config.favoriteColor())
                .thenReturn(
                        favoriteColor);

        overlay.render(
                graphics);

        Mockito.verify(
                        outlineRenderer,
                        Mockito.times(
                                1))
                .drawOutline(
                        player,
                        2,
                        new Color(
                                100,
                                150,
                                200,
                                200),
                        4);
    }

    @Test
    public void tileModeRequestsFavoriteTile()
    {
        final Player player =
                player(
                        "Santa");

        final Polygon polygon =
                new Polygon(
                        new int[] {20, 40, 40, 20},
                        new int[] {20, 20, 40, 40},
                        4);

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        player.getCanvasTilePoly())
                .thenReturn(
                        polygon);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        true);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.TILE);

        overlay.render(
                graphics);

        Mockito.verify(
                        player,
                        Mockito.times(
                                1))
                .getCanvasTilePoly();

        Mockito.verifyNoInteractions(
                outlineRenderer);
    }

    @Test
    public void bothModeDrawsOutlineAndRequestsTile()
    {
        final Player player =
                player(
                        "Santa");

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        player.getCanvasTilePoly())
                .thenReturn(
                        new Polygon());

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        true);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.BOTH);

        overlay.render(
                graphics);

        Mockito.verify(
                        outlineRenderer,
                        Mockito.times(
                                1))
                .drawOutline(
                        Mockito.eq(
                                player),
                        Mockito.eq(
                                2),
                        Mockito.any(
                                Color.class),
                        Mockito.eq(
                                4));

        Mockito.verify(
                        player,
                        Mockito.times(
                                1))
                .getCanvasTilePoly();
    }

    @Test
    public void favoriteNameRequestsCanvasTextLocation()
    {
        final Player player =
                player(
                        "Santa");

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        player.getLogicalHeight())
                .thenReturn(
                        100);

        Mockito.when(
                        player.getCanvasTextLocation(
                                Mockito.any(
                                        Graphics2D.class),
                                Mockito.eq(
                                        "Santa"),
                                Mockito.eq(
                                        140)))
                .thenReturn(
                        new Point(
                                100,
                                100));

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        true);

        overlay.render(
                graphics);

        Mockito.verify(
                        player,
                        Mockito.times(
                                1))
                .getCanvasTextLocation(
                        Mockito.any(
                                Graphics2D.class),
                        Mockito.eq(
                                "Santa"),
                        Mockito.eq(
                                140));
    }

    @Test
    public void nullFavoriteColorUsesFallbackColor()
    {
        final Player player =
                player(
                        "Santa");

        final WorldView worldView =
                worldView(
                        player);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        worldView);

        Mockito.when(
                        recordService.isFavorite(
                                "Santa"))
                .thenReturn(
                        true);

        Mockito.when(
                        config.favoriteColor())
                .thenReturn(
                        null);

        Mockito.when(
                        config.targetMode())
                .thenReturn(
                        TargetMode.OUTLINE);

        overlay.render(
                graphics);

        Mockito.verify(
                        outlineRenderer,
                        Mockito.times(
                                1))
                .drawOutline(
                        player,
                        2,
                        new Color(
                                255,
                                205,
                                70,
                                200),
                        4);
    }

    /*
     * HELPERS
     */

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

    private static WorldView worldView(
            Player... players)
    {
        final WorldView worldView =
                Mockito.mock(
                        WorldView.class);

        @SuppressWarnings("unchecked")
        final IndexedObjectSet<Player> playerSet =
                Mockito.mock(
                        IndexedObjectSet.class);

        final java.util.List<Player> playerList =
                players == null
                        ? Collections.emptyList()
                        : java.util.Arrays.asList(
                        players);

        Mockito.when(
                        playerSet.iterator())
                .thenReturn(
                        playerList.iterator());

        Mockito.doReturn(
                        playerSet)
                .when(
                        worldView)
                .players();

        return worldView;
    }
}