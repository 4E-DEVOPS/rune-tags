package com.runetags.target;

import com.runetags.Configurations;
import com.runetags.config.HideOthersMode;
import com.runetags.quickprofile.QuickProfileModel;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TargetControllerTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private Configurations config;
    private TargetController targetController;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        config =
                Mockito.mock(
                        Configurations.class);

        targetController =
                new TargetController(
                        client,
                        config);

        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        true);

        Mockito.when(
                        config.targetTimeout())
                .thenReturn(
                        0);

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.OFF);

        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGGED_IN);
    }

    /*
     * TESTS
     */

    @Test
    public void startsWithoutTarget()
    {
        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());

        Assert.assertFalse(
                targetController.isTargetingName(
                        "Santa"));
    }

    @Test
    public void invalidStringTargetLeavesStateUnchanged()
    {
        Assert.assertFalse(
                targetController.toggleTarget(
                        (String) null));

        Assert.assertFalse(
                targetController.toggleTarget(
                        ""));

        Assert.assertFalse(
                targetController.toggleTarget(
                        "   "));

        Assert.assertFalse(
                targetController.isTargeting());
    }

    @Test
    public void invalidPlayerTargetLeavesStateUnchanged()
    {
        final Player unnamed =
                player(
                        null);

        final Player blank =
                player(
                        "   ");

        Assert.assertFalse(
                targetController.toggleTarget(
                        (Player) null));

        Assert.assertFalse(
                targetController.toggleTarget(
                        unnamed));

        Assert.assertFalse(
                targetController.toggleTarget(
                        blank));

        Assert.assertFalse(
                targetController.isTargeting());
    }

    @Test
    public void invalidQuickProfileLeavesStateUnchanged()
    {
        final QuickProfileModel notNearby =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .nearby(
                                false)
                        .build();

        final QuickProfileModel missingName =
                QuickProfileModel.builder()
                        .displayName(
                                null)
                        .nearby(
                                true)
                        .build();

        final QuickProfileModel blankName =
                QuickProfileModel.builder()
                        .displayName(
                                "   ")
                        .nearby(
                                true)
                        .build();

        Assert.assertFalse(
                targetController.toggleTarget(
                        (QuickProfileModel) null));

        Assert.assertFalse(
                targetController.toggleTarget(
                        notNearby));

        Assert.assertFalse(
                targetController.toggleTarget(
                        missingName));

        Assert.assertFalse(
                targetController.toggleTarget(
                        blankName));

        Assert.assertFalse(
                targetController.isTargeting());
    }

    @Test
    public void stringTargetRequiresNearbyPlayer()
    {
        setWorldView();

        Assert.assertFalse(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void missingWorldViewPreventsTargeting()
    {
        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        null);

        Assert.assertFalse(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertFalse(
                targetController.isTargeting());
    }

    @Test
    public void targetsNearbyPlayerByCanonicalName()
    {
        final Player santa =
                player(
                        "Santa");

        setWorldView(
                santa);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertTrue(
                targetController.isTargeting());

        Assert.assertTrue(
                targetController.isTargetingName(
                        "Santa"));

        Assert.assertSame(
                santa,
                targetController.getTargetPlayer());
    }

    @Test
    public void targetLookupNormalizesSpacesUnderscoresHyphensAndCase()
    {
        final Player santaClause =
                player(
                        "Santa Clause");

        setWorldView(
                santaClause);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "  SANTA__CLAUSE  "));

        Assert.assertTrue(
                targetController.isTargetingName(
                        "santa-clause"));

        Assert.assertTrue(
                targetController.isTargetingName(
                        " Santa   Clause "));
    }

    @Test
    public void nearbyScanSkipsNullAndUnnamedPlayers()
    {
        final Player unnamed =
                player(
                        null);

        final Player santa =
                player(
                        "Santa");

        setWorldView(
                null,
                unnamed,
                santa);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertSame(
                santa,
                targetController.getTargetPlayer());
    }

    @Test
    public void playerTargetIsResolvedAgainstCurrentScene()
    {
        final Player staleMenuPlayer =
                player(
                        "Santa");

        final Player liveScenePlayer =
                player(
                        "Santa");

        setWorldView(
                liveScenePlayer);

        Assert.assertTrue(
                targetController.toggleTarget(
                        staleMenuPlayer));

        Assert.assertSame(
                liveScenePlayer,
                targetController.getTargetPlayer());

        Assert.assertNotSame(
                staleMenuPlayer,
                targetController.getTargetPlayer());
    }

    @Test
    public void quickProfileTargetUsesDisplayNameAndNearbyValidation()
    {
        final Player live =
                player(
                        "Santa");

        setWorldView(
                live);

        final QuickProfileModel model =
                QuickProfileModel.builder()
                        .displayName(
                                "Santa")
                        .nearby(
                                true)
                        .build();

        Assert.assertTrue(
                targetController.toggleTarget(
                        model));

        Assert.assertSame(
                live,
                targetController.getTargetPlayer());
    }

    @Test
    public void selectingCurrentTargetTogglesItOff()
    {
        final Player santa =
                player(
                        "Santa");

        setWorldView(
                santa);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertFalse(
                targetController.toggleTarget(
                        "santa"));

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void selectingDifferentNearbyPlayerReplacesTarget()
    {
        final Player santa =
                player(
                        "Santa");

        final Player zezima =
                player(
                        "Zezima");

        setWorldView(
                santa,
                zezima);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Zezima"));

        Assert.assertTrue(
                targetController.isTargetingName(
                        "Zezima"));

        Assert.assertFalse(
                targetController.isTargetingName(
                        "Santa"));

        Assert.assertSame(
                zezima,
                targetController.getTargetPlayer());
    }

    @Test
    public void failedReplacementKeepsExistingTarget()
    {
        final Player santa =
                player(
                        "Santa");

        setWorldView(
                santa);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Zezima"));

        Assert.assertTrue(
                targetController.isTargetingName(
                        "Santa"));

        Assert.assertSame(
                santa,
                targetController.getTargetPlayer());
    }

    @Test
    public void disablingTargetOptionClearsExistingTarget()
    {
        final Player santa =
                player(
                        "Santa");

        setWorldView(
                santa);

        Assert.assertTrue(
                targetController.toggleTarget(
                        "Santa"));

        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        false);

        Assert.assertFalse(
                targetController.toggleTarget(
                        "Zezima"));

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void disabledTargetOptionRejectsNewTarget()
    {
        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        false);

        final Player santa =
                player(
                        "Santa");

        setWorldView(
                santa);

        Assert.assertFalse(
                targetController.toggleTarget(
                        "Santa"));

        Assert.assertFalse(
                targetController.isTargeting());

        Mockito.verify(
                        client,
                        Mockito.never())
                .getTopLevelWorldView();
    }

    @Test
    public void refreshWithoutTargetClearsCachedPlayerReference()
            throws Exception
    {
        setField(
                "targetPlayer",
                player(
                        "Santa"));

        targetController.refresh();

        Assert.assertNull(
                targetController.getTargetPlayer());

        Assert.assertFalse(
                targetController.isTargeting());
    }

    @Test
    public void refreshClearsTargetWhenTargetingBecomesDisabled()
    {
        target(
                "Santa");

        Mockito.when(
                        config.targetPlayerOption())
                .thenReturn(
                        false);

        targetController.refresh();

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void refreshClearsTargetWhenLoggedOut()
    {
        target(
                "Santa");

        Mockito.when(
                        client.getGameState())
                .thenReturn(
                        GameState.LOGIN_SCREEN);

        targetController.refresh();

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void refreshClearsTargetWhenPlayerLeavesScene()
    {
        target(
                "Santa");

        setWorldView();

        targetController.refresh();

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void refreshRebindsTargetToCurrentLivePlayer()
    {
        final Player first =
                target(
                        "Santa");

        final Player replacement =
                player(
                        "Santa");

        setWorldView(
                replacement);

        targetController.refresh();

        Assert.assertSame(
                replacement,
                targetController.getTargetPlayer());

        Assert.assertNotSame(
                first,
                targetController.getTargetPlayer());

        Assert.assertTrue(
                targetController.isTargetingName(
                        "Santa"));
    }

    @Test
    public void zeroAndNegativeTimeoutsDoNotExpireTarget()
            throws Exception
    {
        final Player santa =
                target(
                        "Santa");

        setLongField(
                "targetStartedNanos",
                1L);

        Mockito.when(
                        config.targetTimeout())
                .thenReturn(
                        0);

        targetController.refresh();

        Assert.assertTrue(
                targetController.isTargeting());

        Mockito.when(
                        config.targetTimeout())
                .thenReturn(
                        -10);

        targetController.refresh();

        Assert.assertTrue(
                targetController.isTargeting());

        Assert.assertSame(
                santa,
                targetController.getTargetPlayer());
    }

    @Test
    public void expiredTargetTimeoutClearsTarget()
            throws Exception
    {
        target(
                "Santa");

        Mockito.when(
                        config.targetTimeout())
                .thenReturn(
                        10);

        setLongField(
                "targetStartedNanos",
                System.nanoTime()
                        - 11_000_000_000L);

        targetController.refresh();

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void unexpiredTargetTimeoutRetainsTarget()
            throws Exception
    {
        final Player santa =
                target(
                        "Santa");

        Mockito.when(
                        config.targetTimeout())
                .thenReturn(
                        10);

        setLongField(
                "targetStartedNanos",
                System.nanoTime()
                        - 1_000_000_000L);

        targetController.refresh();

        Assert.assertTrue(
                targetController.isTargeting());

        Assert.assertSame(
                santa,
                targetController.getTargetPlayer());
    }

    @Test
    public void clearRemovesAllTargetState()
            throws Exception
    {
        target(
                "Santa");

        Assert.assertTrue(
                targetController.isTargeting());

        targetController.clear();

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());

        Assert.assertEquals(
                0L,
                getLongField(
                        "targetStartedNanos"));

        Assert.assertEquals(
                0L,
                getLongField(
                        "hideOthersStartedNanos"));
    }

    @Test
    public void clearWithReasonAlsoRemovesAllTargetState()
    {
        target(
                "Santa");

        targetController.clear(
                "test");

        Assert.assertFalse(
                targetController.isTargeting());

        Assert.assertNull(
                targetController.getTargetPlayer());
    }

    @Test
    public void hideOthersIsFalseWithoutTarget()
    {
        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.ON);

        Assert.assertFalse(
                targetController.shouldHideOtherPlayers());
    }

    @Test
    public void hideOthersOffAndNullAreDisabled()
    {
        target(
                "Santa");

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.OFF);

        Assert.assertFalse(
                targetController.shouldHideOtherPlayers());

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        null);

        Assert.assertFalse(
                targetController.shouldHideOtherPlayers());
    }

    @Test
    public void persistentHideOthersRemainsEnabled()
            throws Exception
    {
        target(
                "Santa");

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.ON);

        setLongField(
                "hideOthersStartedNanos",
                1L);

        Assert.assertTrue(
                targetController.shouldHideOtherPlayers());
    }

    @Test
    public void timedHideOthersIsEnabledBeforeExpiry()
            throws Exception
    {
        target(
                "Santa");

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.TEN);

        setLongField(
                "hideOthersStartedNanos",
                System.nanoTime()
                        - 1_000_000_000L);

        Assert.assertTrue(
                targetController.shouldHideOtherPlayers());
    }

    @Test
    public void timedHideOthersExpiresAfterDuration()
            throws Exception
    {
        target(
                "Santa");

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.TEN);

        setLongField(
                "hideOthersStartedNanos",
                System.nanoTime()
                        - 11_000_000_000L);

        Assert.assertFalse(
                targetController.shouldHideOtherPlayers());
    }

    @Test
    public void timedHideOthersRequiresStartTimestamp()
            throws Exception
    {
        target(
                "Santa");

        Mockito.when(
                        config.hideAllOthers())
                .thenReturn(
                        HideOthersMode.TEN);

        setLongField(
                "hideOthersStartedNanos",
                0L);

        Assert.assertFalse(
                targetController.shouldHideOtherPlayers());
    }

    /*
     * HELPERS
     */

    private Player target(
            String name)
    {
        final Player player =
                player(
                        name);

        setWorldView(
                player);

        Assert.assertTrue(
                targetController.toggleTarget(
                        name));

        return player;
    }

    private void setWorldView(
            Player... players)
    {
        final WorldView view =
                worldView(
                        players);

        Mockito.when(
                        client.getTopLevelWorldView())
                .thenReturn(
                        view);
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

    private static Player performancePlayer(
            String name)
    {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]
                        {
                                Player.class
                        },
                (proxy, method, args) ->
                {
                    if ("getName".equals(
                            method.getName()))
                    {
                        return name;
                    }

                    if ("toString".equals(
                            method.getName()))
                    {
                        return "Player["
                                + name
                                + "]";
                    }

                    if ("hashCode".equals(
                            method.getName()))
                    {
                        return System.identityHashCode(
                                proxy);
                    }

                    if ("equals".equals(
                            method.getName()))
                    {
                        return proxy
                                == args[0];
                    }

                    final Class<?> returnType =
                            method.getReturnType();

                    if (!returnType.isPrimitive())
                    {
                        return null;
                    }

                    if (returnType == boolean.class)
                    {
                        return false;
                    }

                    if (returnType == byte.class)
                    {
                        return (byte) 0;
                    }

                    if (returnType == short.class)
                    {
                        return (short) 0;
                    }

                    if (returnType == int.class)
                    {
                        return 0;
                    }

                    if (returnType == long.class)
                    {
                        return 0L;
                    }

                    if (returnType == float.class)
                    {
                        return 0.0F;
                    }

                    if (returnType == double.class)
                    {
                        return 0.0D;
                    }

                    if (returnType == char.class)
                    {
                        return '\0';
                    }

                    return null;
                });
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

        final List<Player> playerList =
                players == null
                        ? Collections.emptyList()
                        : Arrays.asList(
                        players);

        Mockito.when(
                        playerSet.iterator())
                .thenAnswer(
                        invocation ->
                                playerList.iterator());

        Mockito.doReturn(
                        playerSet)
                .when(
                        worldView)
                .players();

        return worldView;
    }

    private void setField(
            String fieldName,
            Object value)
            throws Exception
    {
        final Field field =
                TargetController.class.getDeclaredField(
                        fieldName);

        field.setAccessible(
                true);

        field.set(
                targetController,
                value);
    }

    private void setLongField(
            String fieldName,
            long value)
            throws Exception
    {
        final Field field =
                TargetController.class.getDeclaredField(
                        fieldName);

        field.setAccessible(
                true);

        field.setLong(
                targetController,
                value);
    }

    private long getLongField(
            String fieldName)
            throws Exception
    {
        final Field field =
                TargetController.class.getDeclaredField(
                        fieldName);

        field.setAccessible(
                true);

        return field.getLong(
                targetController);
    }

    /*
     * PERFORMANCE
     */

    @Test
    public void performanceNearbyTargetResolution()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final Player[] players =
                new Player[100];

        for (int i = 0;
             i < players.length - 1;
             i++)
        {
            players[i] =
                    performancePlayer(
                            String.format(
                                    "Party Hat %02d",
                                    i));
        }

        players[players.length - 1] =
                performancePlayer(
                        "Santa");

        setWorldView(
                players);

        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            final boolean targeted =
                    targetController.toggleTarget(
                            "Santa");

            if (targeted)
            {
                checksum++;
            }

            targetController.clear();
        }

        final long started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            final boolean targeted =
                    targetController.toggleTarget(
                            "Santa");

            if (targeted)
            {
                checksum++;
            }

            targetController.clear();
        }

        final long elapsed =
                System.nanoTime()
                        - started;

        Assert.assertTrue(
                checksum > 0L);

        final double totalMs =
                elapsed
                        / 1_000_000.0;

        System.out.printf(
                "[RuneTags][TargetControllerTest] Performance= "
                        + "Resolve100Nearby: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                totalMs,
                totalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }
}