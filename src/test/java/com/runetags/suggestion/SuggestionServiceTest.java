package com.runetags.suggestion;

import com.runetags.Configurations;
import com.runetags.player.PlayerIdentity;
import com.runetags.player.PlayerSource;
import com.runetags.player.PlayerDirectory;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/*
 * Suggestion parsing bug found by O_D_S_T;
 * The fix was implemented and proved successful after further testing.
 */
public class SuggestionServiceTest
{
    private static final boolean PERFORMANCE_TEST = false;
    private static final int PERFORMANCE_WARMUP_ITERATIONS = 1_000;
    private static final int PERFORMANCE_ITERATIONS = 10_000;

    private Client client;
    private ClientThread clientThread;
    private Configurations config;
    private PlayerDirectory playerDirectory;
    private SuggestionService suggestionService;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        clientThread =
                Mockito.mock(
                        ClientThread.class);

        config =
                Mockito.mock(
                        Configurations.class);

        playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        suggestionService =
                new SuggestionService(
                        client,
                        clientThread,
                        config,
                        playerDirectory);

        Mockito.when(
                        config.suggestUsernames())
                .thenReturn(
                        true);

        Mockito.when(
                        config.autocompleteFriends())
                .thenReturn(
                        true);

        Mockito.when(
                        config.completeSuggestionHotkey())
                .thenReturn(
                        new Keybind(
                                KeyEvent.VK_TAB,
                                0));

        final PlayerIdentity odst =
                PlayerIdentity.builder()
                        .canonicalName(
                                "O D S T")
                        .sources(
                                Collections.singleton(
                                        PlayerSource.FRIEND))
                        .build();

        Mockito.when(
                        playerDirectory.all())
                .thenReturn(
                        Collections.singletonList(
                                odst));
    }

    /*
     * TESTS
     */

    @Test
    public void singleCharacterDoesNotOfferSuggestion()
    {
        setInput(
                "@O");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    @Test
    public void underscoreCountsAsSecondCharacterAndMatchesSpace()
    {
        setInput(
                "@O_");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void hyphenCountsAsSecondCharacterAndMatchesSpace()
    {
        setInput(
                "@O-");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void literalSpaceCountsAsSecondCharacterAndMatchesSpace()
    {
        setInput(
                "@O ");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void underscoreSeparatedPrefixMatchesSpacedName()
    {
        setInput(
                "@O_D");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void hyphenSeparatedPrefixMatchesSpacedName()
    {
        setInput(
                "@O-D");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void spaceSeparatedPrefixMatchesSpacedName()
    {
        setInput(
                "@O D");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void suggestionsDisabledClearsExistingState()
    {
        setInput(
                "@O_");

        suggestionService.refresh();

        Assert.assertFalse(
                suggestionService.getSuggestions()
                        .isEmpty());

        Mockito.when(
                        config.suggestUsernames())
                .thenReturn(
                        false);

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());

        Assert.assertFalse(
                suggestionService.isActive());

        Assert.assertEquals(
                0,
                suggestionService.getSelectedIndex());
    }

    @Test
    public void inputWithoutMentionDoesNotOfferSuggestions()
    {
        setInput(
                "O D");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());

        Assert.assertFalse(
                suggestionService.isActive());
    }

    @Test
    public void embeddedAtSignDoesNotStartSuggestion()
    {
        setInput(
                "hello@O_");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    @Test
    public void whitespaceBeforeAtSignAllowsSuggestion()
    {
        setInput(
                "hello @O_");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");

        Assert.assertTrue(
                suggestionService.isActive());
    }

    @Test
    public void newlineAfterAtSignRejectsSuggestion()
    {
        setInput(
                "@O_\nnext");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    @Test
    public void latestTrailingMentionIsUsed()
    {
        setDirectory(
                identity(
                        "Santa",
                        PlayerSource.FRIEND),
                identity(
                        "Party Hat",
                        PlayerSource.FRIEND));

        setInput(
                "@Pa hello @Sa");

        suggestionService.refresh();

        assertOnlySuggestion(
                "Santa");
    }

    @Test
    public void sourceConfigurationFiltersCandidates()
    {
        setDirectory(
                identity(
                        "Santa",
                        PlayerSource.FRIEND),
                identity(
                        "Santa Clan",
                        PlayerSource.CLAN),
                identity(
                        "Santa Guest",
                        PlayerSource.GUEST_CLAN),
                identity(
                        "Santa Chat",
                        PlayerSource.FRIENDS_CHAT),
                identity(
                        "Santa Party",
                        PlayerSource.PARTY),
                identity(
                        "Santa Nearby",
                        PlayerSource.NEARBY));

        Mockito.when(
                        config.autocompleteFriends())
                .thenReturn(
                        false);

        Mockito.when(
                        config.autocompleteClan())
                .thenReturn(
                        true);

        Mockito.when(
                        config.autocompleteFriendsChat())
                .thenReturn(
                        false);

        Mockito.when(
                        config.autocompleteParty())
                .thenReturn(
                        true);

        Mockito.when(
                        config.autocompleteNearby())
                .thenReturn(
                        false);

        setInput(
                "@Sa");

        suggestionService.refresh();

        Assert.assertEquals(
                Arrays.asList(
                        "Santa Clan",
                        "Santa Guest",
                        "Santa Party"),
                suggestionService.getSuggestions());
    }

    @Test
    public void nullEmptyAndNamelessCandidatesAreIgnored()
    {
        final PlayerIdentity nullName =
                PlayerIdentity.builder()
                        .canonicalName(
                                null)
                        .sources(
                                Collections.singleton(
                                        PlayerSource.FRIEND))
                        .build();

        final PlayerIdentity emptyName =
                PlayerIdentity.builder()
                        .canonicalName(
                                "   ")
                        .sources(
                                Collections.singleton(
                                        PlayerSource.FRIEND))
                        .build();

        final PlayerIdentity noSources =
                PlayerIdentity.builder()
                        .canonicalName(
                                "Santa")
                        .sources(
                                Collections.emptySet())
                        .build();

        setDirectory(
                null,
                nullName,
                emptyName,
                noSources);

        setInput(
                "@Sa");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    @Test
    public void prefixMatchesSortBeforeSubstringMatches()
    {
        setDirectory(
                identity(
                        "Big Santa",
                        PlayerSource.FRIEND),
                identity(
                        "Santa",
                        PlayerSource.FRIEND),
                identity(
                        "Santa Clause",
                        PlayerSource.FRIEND),
                identity(
                        "The Santa",
                        PlayerSource.FRIEND));

        setInput(
                "@Sa");

        suggestionService.refresh();

        Assert.assertEquals(
                Arrays.asList(
                        "Santa",
                        "Santa Clause",
                        "Big Santa",
                        "The Santa"),
                suggestionService.getSuggestions());
    }

    @Test
    public void equalMatchTypesSortCaseInsensitivelyByName()
    {
        setDirectory(
                identity(
                        "Santa Zoo",
                        PlayerSource.FRIEND),
                identity(
                        "Santa Apple",
                        PlayerSource.FRIEND),
                identity(
                        "Santa Bear",
                        PlayerSource.FRIEND));

        setInput(
                "@Sa");

        suggestionService.refresh();

        Assert.assertEquals(
                Arrays.asList(
                        "Santa Apple",
                        "Santa Bear",
                        "Santa Zoo"),
                suggestionService.getSuggestions());
    }

    @Test
    public void suggestionsAreLimitedToSix()
    {
        setDirectory(
                identity(
                        "Santa A",
                        PlayerSource.FRIEND),
                identity(
                        "Santa B",
                        PlayerSource.FRIEND),
                identity(
                        "Santa C",
                        PlayerSource.FRIEND),
                identity(
                        "Santa D",
                        PlayerSource.FRIEND),
                identity(
                        "Santa E",
                        PlayerSource.FRIEND),
                identity(
                        "Santa F",
                        PlayerSource.FRIEND),
                identity(
                        "Santa G",
                        PlayerSource.FRIEND),
                identity(
                        "Santa H",
                        PlayerSource.FRIEND));

        setInput(
                "@Sa");

        suggestionService.refresh();

        Assert.assertEquals(
                6,
                suggestionService.getSuggestions()
                        .size());

        Assert.assertEquals(
                Arrays.asList(
                        "Santa A",
                        "Santa B",
                        "Santa C",
                        "Santa D",
                        "Santa E",
                        "Santa F"),
                suggestionService.getSuggestions());
    }

    @Test
    public void returnedSuggestionsAreDefensiveCopies()
    {
        setInput(
                "@O_");

        suggestionService.refresh();

        final List<String> first =
                suggestionService.getSuggestions();

        first.clear();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void downAndUpKeysWrapSelection()
    {
        setDirectory(
                identity(
                        "Santa",
                        PlayerSource.FRIEND),
                identity(
                        "Santa Clause",
                        PlayerSource.FRIEND),
                identity(
                        "Santa Hat",
                        PlayerSource.FRIEND));

        setInput(
                "@Sa");

        suggestionService.refresh();

        Assert.assertEquals(
                0,
                suggestionService.getSelectedIndex());

        final KeyEvent up =
                keyEvent(
                        KeyEvent.VK_UP);

        Assert.assertTrue(
                suggestionService.handleKeyPressed(
                        up));

        Assert.assertTrue(
                up.isConsumed());

        Assert.assertEquals(
                2,
                suggestionService.getSelectedIndex());

        final KeyEvent down =
                keyEvent(
                        KeyEvent.VK_DOWN);

        Assert.assertTrue(
                suggestionService.handleKeyPressed(
                        down));

        Assert.assertTrue(
                down.isConsumed());

        Assert.assertEquals(
                0,
                suggestionService.getSelectedIndex());
    }

    @Test
    public void escapeClearsSuggestionsAndConsumesEvent()
    {
        setInput(
                "@O_");

        suggestionService.refresh();

        final KeyEvent escape =
                keyEvent(
                        KeyEvent.VK_ESCAPE);

        Assert.assertTrue(
                suggestionService.handleKeyPressed(
                        escape));

        Assert.assertTrue(
                escape.isConsumed());

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());

        Assert.assertFalse(
                suggestionService.isActive());
    }

    @Test
    public void unrelatedKeyIsNotConsumed()
    {
        setInput(
                "@O_");

        final KeyEvent event =
                keyEvent(
                        KeyEvent.VK_A);

        Assert.assertFalse(
                suggestionService.handleKeyPressed(
                        event));

        Assert.assertFalse(
                event.isConsumed());
    }

    @Test
    public void nullKeyEventDoesNothing()
    {
        Assert.assertFalse(
                suggestionService.handleKeyPressed(
                        null));
    }

    @Test
    public void completionReplacesMentionWithUnderscoredCanonicalName()
    {
        Mockito.doAnswer(invocation ->
                {
                    final Runnable runnable =
                            invocation.getArgument(
                                    0);

                    runnable.run();

                    return null;
                })
                .when(
                        clientThread)
                .invokeLater(
                        Mockito.any(
                                Runnable.class));

        setDirectory(
                identity(
                        "Santa Clause",
                        PlayerSource.FRIEND));

        setInput(
                "hello @Sa");

        final KeyEvent tab =
                hotkeyEvent(
                        KeyEvent.VK_TAB);

        Assert.assertTrue(
                suggestionService.handleKeyPressed(
                        tab));

        Assert.assertTrue(
                tab.isConsumed());

        Mockito.verify(
                        client)
                .setVarcStrValue(
                        VarClientID.CHATINPUT,
                        "hello @Santa_Clause ");

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    @Test
    public void completionDoesNotModifyInputIfMentionPositionChanged()
    {
        final Runnable[] pending =
                new Runnable[1];

        Mockito.doAnswer(invocation ->
                {
                    pending[0] =
                            invocation.getArgument(
                                    0);

                    return null;
                })
                .when(
                        clientThread)
                .invokeLater(
                        Mockito.any(
                                Runnable.class));

        setDirectory(
                identity(
                        "Santa Clause",
                        PlayerSource.FRIEND));

        setInput(
                "hello @Sa");

        final KeyEvent tab =
                hotkeyEvent(
                        KeyEvent.VK_TAB);

        Assert.assertTrue(
                suggestionService.handleKeyPressed(
                        tab));

        Assert.assertNotNull(
                pending[0]);

        setInput(
                "different text");

        pending[0].run();

        Mockito.verify(
                        client,
                        Mockito.never())
                .setVarcStrValue(
                        Mockito.eq(
                                VarClientID.CHATINPUT),
                        Mockito.anyString());
    }

    @Test
    public void completedInputIsSuppressedOnImmediateRefresh()
    {
        Mockito.doAnswer(invocation ->
                {
                    final Runnable runnable =
                            invocation.getArgument(
                                    0);

                    runnable.run();

                    return null;
                })
                .when(
                        clientThread)
                .invokeLater(
                        Mockito.any(
                                Runnable.class));

        setDirectory(
                identity(
                        "Santa Clause",
                        PlayerSource.FRIEND));

        setInput(
                "@Sa");

        suggestionService.handleKeyPressed(
                hotkeyEvent(
                        KeyEvent.VK_TAB));

        setInput(
                "@Santa_Clause ");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());

        Assert.assertFalse(
                suggestionService.isActive());
    }

    @Test
    public void clearRestoresInactiveInitialState()
    {
        setInput(
                "@O_");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.isActive());

        suggestionService.clear();

        Assert.assertFalse(
                suggestionService.isActive());

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());

        Assert.assertEquals(
                0,
                suggestionService.getSelectedIndex());
    }

    @Test
    public void friendsChatShortcutAllowsSuggestionImmediatelyAfterSlash()
    {
        setInput(
                "/@O_");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void clanChatShortcutAllowsSuggestionImmediatelyAfterDoubleSlash()
    {
        setInput(
                "//@O_");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void guestClanChatShortcutAllowsSuggestionImmediatelyAfterTripleSlash()
    {
        setInput(
                "///@O_");

        suggestionService.refresh();

        assertOnlySuggestion(
                "O D S T");
    }

    @Test
    public void embeddedSlashDoesNotCreateMentionBoundary()
    {
        setInput(
                "hello/@O_");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    @Test
    public void fourLeadingSlashesDoNotCreateSupportedChannelShortcut()
    {
        setInput(
                "////@O_");

        suggestionService.refresh();

        Assert.assertTrue(
                suggestionService.getSuggestions()
                        .isEmpty());
    }

    /*
     * HELPERS
     */

    private void setInput(
            String input)
    {
        Mockito.when(
                        client.getVarcStrValue(
                                VarClientID.CHATINPUT))
                .thenReturn(
                        input);
    }

    private void setDirectory(
            PlayerIdentity... identities)
    {
        Mockito.when(
                        playerDirectory.all())
                .thenReturn(
                        Arrays.asList(
                                identities));
    }

    private static PlayerIdentity identity(
            String name,
            PlayerSource... sources)
    {
        return PlayerIdentity.builder()
                .canonicalName(
                        name)
                .sources(
                        sources == null
                                || sources.length == 0
                                ? Collections.emptySet()
                                : EnumSet.copyOf(
                                Arrays.asList(
                                        sources)))
                .build();
    }

    private static KeyEvent keyEvent(
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

    private static KeyEvent hotkeyEvent(
            int keyCode)
    {
        final KeyEvent event =
                Mockito.spy(
                        new KeyEvent(
                                new Canvas(),
                                KeyEvent.KEY_PRESSED,
                                System.currentTimeMillis(),
                                0,
                                keyCode,
                                KeyEvent.CHAR_UNDEFINED));

        Mockito.doReturn(
                        keyCode)
                .when(
                        event)
                .getExtendedKeyCode();

        return event;
    }

    private void assertOnlySuggestion(
            String expected)
    {
        final List<String> suggestions =
                suggestionService.getSuggestions();

        Assert.assertEquals(
                1,
                suggestions.size());

        Assert.assertEquals(
                expected,
                suggestions.get(
                        0));
    }

    /*
     * PERFORMANCE
     */

    @Test
    public void performanceSuggestionCandidateRefresh()
    {
        if (!PERFORMANCE_TEST)
        {
            return;
        }

        final PlayerIdentity[] identities =
                new PlayerIdentity[100];

        for (int i = 0;
             i < identities.length;
             i++)
        {
            identities[i] =
                    identity(
                            String.format(
                                    "Santa %02d",
                                    i),
                            PlayerSource.FRIEND);
        }

        setDirectory(
                identities);

        setInput(
                "@Sa");

        long checksum =
                0L;

        for (int i = 0;
             i < PERFORMANCE_WARMUP_ITERATIONS;
             i++)
        {
            suggestionService.refresh();

            checksum +=
                    suggestionService.getSuggestions()
                            .size();
        }

        final long started =
                System.nanoTime();

        for (int i = 0;
             i < PERFORMANCE_ITERATIONS;
             i++)
        {
            suggestionService.refresh();

            checksum +=
                    suggestionService.getSuggestions()
                            .size();
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
                "[RuneTags][SuggestionServiceTest] Performance= "
                        + "Refresh100Candidates: %.3fms (%.6fms) | "
                        + "Iterations: %d%n",
                totalMs,
                totalMs
                        / PERFORMANCE_ITERATIONS,
                PERFORMANCE_ITERATIONS);
    }
}