package com.runetags;

import com.google.inject.Provides;
import com.runetags.chat.ChatFontLayoutService;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ChatProcessor;
import com.runetags.chat.ChatReferenceLayoutService;
import com.runetags.chat.ChatText;
import com.runetags.chat.MessageFormatter;
import com.runetags.chat.NativeChatBootstrapService;
import com.runetags.chat.TaggedMessageRepository;
import com.runetags.context.ContextMetricResolver;
import com.runetags.context.LocationIndex;
import com.runetags.context.PartyContextService;
import com.runetags.context.PlayerContextService;
import com.runetags.context.TargetController;
import com.runetags.debug.ChatLayoutDiagnostic;
import com.runetags.history.MentionHistoryPanel;
import com.runetags.history.MentionHistoryService;
import com.runetags.input.ChatReferenceMouseListener;
import com.runetags.mention.KnownPlayerMentionParser;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.NameNormalizer;
import com.runetags.mention.TagParser;
import com.runetags.model.PlayerSource;
import com.runetags.model.TaggedMessage;
import com.runetags.notification.MentionNotificationService;
import com.runetags.overlay.ChatMessageHighlightOverlay;
import com.runetags.overlay.ChatReferenceOverlay;
import com.runetags.overlay.QuickProfileOverlay;
import com.runetags.overlay.TargetMinimapOverlay;
import com.runetags.overlay.TargetOverlay;
import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerVisibilityService;
import com.runetags.quickprofile.ClanLookupService;
import com.runetags.quickprofile.HiscoreEnrichmentService;
import com.runetags.quickprofile.LookupService;
import com.runetags.quickprofile.ProfileEnrichmentCache;
import com.runetags.quickprofile.QuickProfileController;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.input.MouseManager;
import net.runelite.client.Notifier;
import net.runelite.client.game.WorldService;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.party.messages.LocationUpdate;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
        name = "RuneTags",
        description = "Quick-card profiles for player mentions and tags.",
        tags = {"1877", "runetags", "rune-tags", "player", "players", "quick", "card", "profile", "tags", "tagging", "mentions", "chat", "clan"},
        enabledByDefault = true
)
public class RuneTagsPlugin extends Plugin {
    private static final boolean CHAT_DIAGNOSTICS_ENABLED = true;

    private static final int MESSAGE_REPOSITORY_CAPACITY = 500;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private HiscoreClient hiscoreClient;
    @Inject
    private Hooks hooks;
    @Inject
    private MouseManager mouseManager;
    @Inject
    private ModelOutlineRenderer modelOutlineRenderer;
    @Inject
    private Notifier notifier;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PartyService partyService;
    @Inject
    private WorldService worldService;
    @Inject
    private RuneTagsConfig config;
    @Inject
    private RuneLiteConfig runeLiteConfig;

    /*
     * Identity / mention parsing.
     */
    private NameNormalizer nameNormalizer;
    private PlayerDirectory playerDirectory;

    private TagParser tagParser;
    private KnownPlayerMentionParser knownPlayerMentionParser;
    private LocalMentionMatcher localMentionMatcher;

    private MessageFormatter messageFormatter;
    private ChatProcessor chatProcessor;

    /*
     * Notifications / history.
     */
    private MentionNotificationService mentionNotificationService;

    private MentionHistoryService mentionHistoryService;
    private MentionHistoryPanel mentionHistoryPanel;
    private NavigationButton mentionHistoryNavigation;
    private boolean mentionHistoryNavigationAdded;

    /*
     * Chat storage / rendering / interaction.
     */
    private TaggedMessageRepository messageRepository;
    private NativeChatBootstrapService nativeChatBootstrapService;

    private ChatHitboxRegistry chatHitboxRegistry;
    private ChatReferenceLayoutService chatReferenceLayoutService;
    private ChatFontLayoutService chatFontLayoutService;

    private ChatLayoutDiagnostic chatLayoutDiagnostic;
    private ChatMessageHighlightOverlay chatMessageHighlightOverlay;
    private ChatReferenceOverlay chatReferenceOverlay;
    private ChatReferenceMouseListener chatReferenceMouseListener;

    /*
     * Context / profile enrichment.
     */
    private LocationIndex locationIndex;
    private ContextMetricResolver contextMetricResolver;

    private PlayerContextService playerContextService;
    private PartyContextService partyContextService;

    private ProfileEnrichmentCache profileEnrichmentCache;
    private HiscoreEnrichmentService hiscoreEnrichmentService;

    private LookupService lookupService;
    private ClanLookupService clanLookupService;

    /*
     * Quick Profile.
     */
    private QuickProfileController quickProfileController;
    private QuickProfileOverlay quickProfileOverlay;

    /*
     * Targeting / player visibility.
     */
    private TargetController targetController;
    private PlayerVisibilityService playerVisibilityService;

    private TargetOverlay targetOverlay;
    private TargetMinimapOverlay targetMinimapOverlay;

    /*
     * Runtime state.
     */
    private long nextMessageId;
    private boolean nativeChatBootstrapPending;

    @Provides
    RuneTagsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RuneTagsConfig.class);
    }

    @Override
    protected void startUp()
    {
        /*
         * ================================================================
         * 1. Identity / mention parsing
         * ================================================================
         *
         * Everything downstream ultimately depends on normalized player
         * identities and semantic parsing.
         */
        nameNormalizer =
                new NameNormalizer();

        playerDirectory =
                new PlayerDirectory(
                        client,
                        partyService,
                        worldService,
                        nameNormalizer);

        tagParser =
                new TagParser(
                        nameNormalizer,
                        playerDirectory);

        knownPlayerMentionParser =
                new KnownPlayerMentionParser(
                        playerDirectory,
                        nameNormalizer);

        localMentionMatcher =
                new LocalMentionMatcher(
                        config,
                        nameNormalizer);

        messageFormatter =
                new MessageFormatter(
                        config,
                        localMentionMatcher);

        chatProcessor =
                new ChatProcessor(
                        tagParser,
                        knownPlayerMentionParser,
                        localMentionMatcher);

        /*
         * ================================================================
         * 2. Context / profile enrichment
         * ================================================================
         */
        profileEnrichmentCache =
                new ProfileEnrichmentCache();

        hiscoreEnrichmentService =
                new HiscoreEnrichmentService(
                        hiscoreClient,
                        profileEnrichmentCache);

        locationIndex =
                new LocationIndex();

        contextMetricResolver =
                new ContextMetricResolver();

        playerContextService =
                new PlayerContextService(
                        client,
                        locationIndex,
                        contextMetricResolver);

        partyContextService =
                new PartyContextService(
                        partyService,
                        locationIndex,
                        nameNormalizer);

        /*
         * ================================================================
         * 3. Targeting / lookup / Quick Profile
         * ================================================================
         */
        targetController =
                new TargetController(
                        client,
                        config);

        playerVisibilityService =
                new PlayerVisibilityService(
                        hooks,
                        targetController);

        lookupService =
                new LookupService(
                        config);

        clanLookupService =
                new ClanLookupService();

        quickProfileController =
                new QuickProfileController(
                        clientThread,
                        playerDirectory,
                        hiscoreEnrichmentService,
                        playerContextService,
                        partyContextService,
                        contextMetricResolver,
                        targetController,
                        lookupService,
                        clanLookupService);

        quickProfileOverlay =
                new QuickProfileOverlay(
                        client,
                        config,
                        runeLiteConfig,
                        quickProfileController);

        targetOverlay =
                new TargetOverlay(
                        client,
                        config,
                        targetController,
                        modelOutlineRenderer);

        targetMinimapOverlay =
                new TargetMinimapOverlay(
                        client,
                        config,
                        targetController);

        /*
         * ================================================================
         * 4. Mention notifications / persistent history
         * ================================================================
         */
        mentionNotificationService =
                new MentionNotificationService(
                        client,
                        config,
                        notifier);

        mentionHistoryService =
                new MentionHistoryService(
                        config);

        mentionHistoryPanel =
                new MentionHistoryPanel(
                        client,
                        mentionHistoryService,
                        quickProfileController);

        final BufferedImage historyIcon =
                ImageUtil.loadImageResource(
                        RuneTagsPlugin.class,
                        "runetags_history_icon.png");

        mentionHistoryNavigation =
                NavigationButton.builder()
                        .tooltip("RuneTags")
                        .icon(historyIcon)
                        .priority(8)
                        .panel(mentionHistoryPanel)
                        .build();

        /*
         * ================================================================
         * 5. Structured chat storage / geometry / interaction
         * ================================================================
         *
         * Dependency order is important:
         *
         * TaggedMessageRepository
         *      ↓
         * ChatReferenceLayoutService
         *      ↓
         * ChatMessageHighlightOverlay / ChatReferenceOverlay
         *      ↓
         * ChatReferenceMouseListener
         */
        messageRepository =
                new TaggedMessageRepository(
                        MESSAGE_REPOSITORY_CAPACITY);

        nativeChatBootstrapService =
                new NativeChatBootstrapService(
                        client,
                        playerDirectory,
                        chatProcessor,
                        messageRepository);

        chatHitboxRegistry =
                new ChatHitboxRegistry();

        chatReferenceLayoutService =
                new ChatReferenceLayoutService(
                        client,
                        config,
                        messageRepository);

        /*
         * Native chat-row allocation for configured mention fonts.
         *
         * This depends on:
         *
         * - TaggedMessageRepository for semantic message ownership;
         * - ChatReferenceLayoutService for the shared wrapping engine.
         *
         * It must therefore be constructed after both.
         */
        chatFontLayoutService =
                new ChatFontLayoutService(
                        client,
                        config,
                        messageRepository,
                        chatReferenceLayoutService);

        /*
         * Diagnostics
         */
        chatLayoutDiagnostic =
                CHAT_DIAGNOSTICS_ENABLED
                        ? new ChatLayoutDiagnostic(
                        client)
                        : null;

        /*
         * Non-clickable local alias / normalized-self background highlights.
         *
         * This reuses ChatReferenceLayoutService's multiline geometry and must
         * therefore be constructed after the layout service.
         */
        chatMessageHighlightOverlay =
                new ChatMessageHighlightOverlay(
                        client,
                        config,
                        messageRepository,
                        chatReferenceLayoutService,
                        chatLayoutDiagnostic);

        /*
         * Clickable PlayerReference highlights / hitboxes.
         */
        chatReferenceOverlay =
                new ChatReferenceOverlay(
                        chatReferenceLayoutService,
                        chatHitboxRegistry,
                        client,
                        config,
                        localMentionMatcher,
                        chatLayoutDiagnostic);

        chatReferenceMouseListener =
                new ChatReferenceMouseListener(
                        client,
                        config,
                        chatHitboxRegistry,
                        quickProfileController);

        /*
         * ================================================================
         * 6. Runtime state
         * ================================================================
         */
        nextMessageId = 0;
        nativeChatBootstrapPending = true;

        /*
         * RuneTags may be enabled from RuneLite's Swing configuration UI.
         *
         * PlayerDirectory and PlayerContextService access client-thread-only
         * game state, so initial synchronization must execute on the client
         * thread.
         */
        clientThread.invokeLater(() ->
        {
            if (client.getGameState()
                    != GameState.LOGGED_IN)
            {
                return;
            }

            if (playerContextService != null)
            {
                playerContextService.refresh();
            }

            if (playerDirectory != null)
            {
                playerDirectory.rebuild();
            }

            bootstrapNativeChatIfPending();

            if (targetController != null)
            {
                targetController.refresh();
            }
        });

        /*
         * ================================================================
         * 7. Register external RuneLite hooks LAST
         * ================================================================
         *
         * No overlay, mouse listener, toolbar entry, or render hook should be
         * able to observe partially initialized RuneTags state.
         */
        mentionHistoryNavigationAdded = false;

        if (config.mentionHistory()
                && mentionHistoryNavigation != null)
        {
            clientToolbar.addNavigation(
                    mentionHistoryNavigation);

            mentionHistoryNavigationAdded =
                    true;
        }

        overlayManager.add(
                chatMessageHighlightOverlay);

        overlayManager.add(
                chatReferenceOverlay);

        overlayManager.add(
                targetOverlay);

        overlayManager.add(
                targetMinimapOverlay);

        overlayManager.add(
                quickProfileOverlay);

        playerVisibilityService.start();

        mouseManager.registerMouseListener(
                chatReferenceMouseListener);

        log.debug(
                "[RuneTags] Plugin Initiated!");
    }

    @Override
    protected void shutDown()
    {
        /*
         * ================================================================
         * 1. Stop external interaction first
         * ================================================================
         *
         * Prevent new mouse events, render hooks, or toolbar actions from
         * reaching RuneTags while internal state is being dismantled.
         */
        if (chatReferenceMouseListener != null)
        {
            mouseManager.unregisterMouseListener(
                    chatReferenceMouseListener);
        }

        if (playerVisibilityService != null)
        {
            playerVisibilityService.stop();
        }

        if (mentionHistoryNavigation != null
                && mentionHistoryNavigationAdded)
        {
            clientToolbar.removeNavigation(
                    mentionHistoryNavigation);

            mentionHistoryNavigationAdded =
                    false;
        }

        /*
         * ================================================================
         * 2. Remove overlays
         * ================================================================
         *
         * Reverse their registration order.
         */
        if (quickProfileOverlay != null)
        {
            overlayManager.remove(
                    quickProfileOverlay);
        }

        if (targetMinimapOverlay != null)
        {
            overlayManager.remove(
                    targetMinimapOverlay);
        }

        if (targetOverlay != null)
        {
            overlayManager.remove(
                    targetOverlay);
        }

        if (chatReferenceOverlay != null)
        {
            overlayManager.remove(
                    chatReferenceOverlay);
        }

        if (chatMessageHighlightOverlay != null)
        {
            overlayManager.remove(
                    chatMessageHighlightOverlay);
        }

        /*
         * ================================================================
         * 3. Invalidate active runtime controllers
         * ================================================================
         */
        if (quickProfileController != null)
        {
            quickProfileController.close();
        }

        if (targetController != null)
        {
            targetController.clear(
                    "plugin stopped");
        }

        /*
         * ================================================================
         * 4. Clear runtime / cached state
         * ================================================================
         */

        /*
         * Return every physical chat widget still owned by RuneTags to its native
         * FontId before discarding semantic/font ownership state.
         */
        if (chatReferenceLayoutService != null)
        {
            chatReferenceLayoutService.restoreMentionFonts();
        }

        if (chatHitboxRegistry != null)
        {
            chatHitboxRegistry.clear();
        }

        if (messageRepository != null)
        {
            messageRepository.clear();
        }

        if (playerDirectory != null)
        {
            playerDirectory.clear();
        }

        if (playerContextService != null)
        {
            playerContextService.clear();
        }

        if (partyContextService != null)
        {
            partyContextService.clear();
        }

        if (profileEnrichmentCache != null)
        {
            profileEnrichmentCache.clear();
        }

        /*
         * ================================================================
         * 5. Release RuneTags objects in reverse dependency order
         * ================================================================
         */

        /*
         * Input / chat presentation.
         */
        chatReferenceMouseListener = null;

        chatReferenceOverlay = null;
        chatMessageHighlightOverlay = null;

        chatLayoutDiagnostic = null;

        chatFontLayoutService = null;
        chatReferenceLayoutService = null;
        chatHitboxRegistry = null;

        /*
         * Persistent/history UI.
         */
        mentionHistoryNavigation = null;
        mentionHistoryNavigationAdded = false;
        mentionHistoryPanel = null;

        /*
         * Structured chat services.
         */
        nativeChatBootstrapService = null;
        messageRepository = null;

        mentionHistoryService = null;
        mentionNotificationService = null;

        chatProcessor = null;
        messageFormatter = null;
        localMentionMatcher = null;

        knownPlayerMentionParser = null;
        tagParser = null;

        /*
         * Quick Profile / targeting.
         */
        quickProfileOverlay = null;

        targetMinimapOverlay = null;
        targetOverlay = null;

        quickProfileController = null;

        playerVisibilityService = null;
        targetController = null;

        lookupService = null;
        clanLookupService = null;

        /*
         * Enrichment / context.
         */
        hiscoreEnrichmentService = null;
        profileEnrichmentCache = null;

        partyContextService = null;
        playerContextService = null;

        contextMetricResolver = null;
        locationIndex = null;

        /*
         * Identity root.
         */
        playerDirectory = null;
        nameNormalizer = null;

        /*
         * Runtime counters.
         */
        nextMessageId = 0;
        nativeChatBootstrapPending = false;

        log.debug(
                "[RuneTags] Plugin Terminated!");
    }

    @Subscribe
    public void onScriptPreFired(
            ScriptPreFired event)
    {
        /*
         * Diagnostic observes the untouched native construction payload first.
         */
        if (chatLayoutDiagnostic != null)
        {
            chatLayoutDiagnostic.onScriptPreFired(
                    event);
        }

        if (chatFontLayoutService != null)
        {
            chatFontLayoutService.onScriptPreFired(
                    event);
        }
    }

    @Subscribe
    public void onPostClientTick(
            PostClientTick event)
    {
        final long fontTickStarted =
                chatLayoutDiagnostic != null
                        ? System.nanoTime()
                        : 0L;

        if (chatFontLayoutService != null)
        {
            chatFontLayoutService.onPostClientTick();
        }

        if (chatLayoutDiagnostic != null)
        {
            chatLayoutDiagnostic.recordFontPostClientTick(
                    System.nanoTime()
                            - fontTickStarted);

            chatLayoutDiagnostic.onPostClientTick();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (playerContextService != null) {
            playerContextService.refresh();
        }

        /*
         * An open QuickCard refreshes only the player it represents.
         *
         * Full PlayerDirectory discovery remains event-driven for chat parsing,
         * initial profile opening, login synchronization, and future consumers such
         * as player suggestions.
         */
        if (quickProfileController != null
                && quickProfileController.isOpen()) {

            quickProfileController.refreshContext();
        }

        if (targetController != null
                && targetController.isTargeting()) {
            targetController.refresh();
        }
    }

    @Subscribe
    public void onGameStateChanged(
            GameStateChanged event)
    {
        if (playerDirectory == null)
        {
            return;
        }

        final GameState gameState =
                event.getGameState();

        if (gameState == GameState.LOGGED_IN)
        {
            if (playerContextService != null)
            {
                playerContextService.refresh();
            }

            playerDirectory.rebuild();

            bootstrapNativeChatIfPending();

            return;
        }

        if (gameState == GameState.HOPPING)
        {
            /*
             * RuneScape preserves visible chat across a normal world hop.
             *
             * Preserve:
             * - TaggedMessageRepository;
             * - currently rendered reference hitboxes;
             * - durable account observations.
             *
             * Only live world/session-derived profile state becomes invalid.
             */
            playerDirectory.clearLiveState();

            if (quickProfileController != null)
            {
                quickProfileController.close();
            }

            if (targetController != null)
            {
                targetController.clear(
                        "world change");
            }

            if (playerContextService != null)
            {
                playerContextService.clear();
            }

            if (partyContextService != null)
            {
                partyContextService.clear();
            }

            return;
        }

        if (gameState == GameState.LOGIN_SCREEN)
        {
            /*
             * RuneScape retains chat history while the client remains open.
             *
             * Preserve:
             * - TaggedMessageRepository;
             * - currently derived reference hitboxes;
             * - durable account observations.
             *
             * Only live world/session-derived player state becomes invalid.
             *
             * ChatReferenceOverlay remains responsible for replacing the physical
             * hitbox geometry as RuneScape's rendered chat widgets change.
             */
            playerDirectory.clearLiveState();

            if (quickProfileController != null)
            {
                quickProfileController.close();
            }

            if (targetController != null)
            {
                targetController.clear(
                        "logged out");
            }

            if (playerContextService != null)
            {
                playerContextService.clear();
            }

            if (partyContextService != null)
            {
                partyContextService.clear();
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (chatProcessor == null
                || !isSupportedChatType(event.getType())
                || event.getMessageNode() == null) {
            return;
        }

        /*
         * Known-player mention parsing is event-driven.
         *
         * Refresh the directory immediately before processing a supported chat
         * message so KnownPlayerMentionParser sees the current Friends / Clan /
         * Guest Clan / Friends Chat / Party / Nearby snapshot.
         */
        if (playerDirectory != null)
        {
            final long rebuildStarted =
                    chatLayoutDiagnostic != null
                            ? System.nanoTime()
                            : 0L;

            playerDirectory.rebuild();

            if (chatLayoutDiagnostic != null)
            {
                chatLayoutDiagnostic.recordDirectoryRebuild(
                        System.nanoTime()
                                - rebuildStarted);
            }
        }

        final Player localPlayer = client.getLocalPlayer();

        final String localName =
                localPlayer != null
                        ? localPlayer.getName()
                        : null;

        /*
         * Keep the RuneLite/Jagex-marked version for rendering and build a
         * semantic version separately for RuneTags parsing.
         */

        final String rawMessage = event.getMessageNode().getValue();

        final String semanticMessage =
                ChatText.toSemanticPlain(rawMessage);

        if (playerDirectory != null
                && event.getName() != null
                && !event.getName().trim().isEmpty())
        {
            /*
             * Only incoming/player-authored chat is authoritative evidence of the
             * named player's native account icon.
             *
             * PRIVATECHATOUT names the recipient. Its lack of an account icon tells
             * us nothing about that recipient and must not overwrite an account type
             * previously learned from their own chat.
             */
            if (event.getType() != ChatMessageType.PRIVATECHATOUT)
            {
                playerDirectory.observeAccountType(
                        event.getName());
            }
        }

        final long processingStarted =
                chatLayoutDiagnostic != null
                        ? System.nanoTime()
                        : 0L;

        final TaggedMessage taggedMessage = chatProcessor.process(
                ++nextMessageId,
                event.getType(),
                Text.removeTags(event.getName()),
                semanticMessage,
                localName);

        if (chatLayoutDiagnostic != null)
        {
            chatLayoutDiagnostic.recordChatProcessing(
                    System.nanoTime()
                            - processingStarted);
        }

        /*
         * TaggedMessage remains the semantic source of truth.
         */
        messageRepository.add(taggedMessage);

        /*
         * A new supported message may cause RuneScape to reconstruct and recycle
         * retained physical chat rows.
         *
         * This is only a boolean request. All messages reconstructed during this
         * client tick are synchronized together once at PostClientTick.
         */
        if (chatFontLayoutService != null)
        {
            chatFontLayoutService.markFontsDirty();
        }

        if (mentionHistoryService != null
                && taggedMessage
                .getLocalMentionMatch()
                .isMatchesLocalPlayer())
        {
            final QuickProfileController.ProfileContextSnapshot historyContext =
                    quickProfileController != null
                            ? quickProfileController.resolveHistoryContext(
                            taggedMessage.getCanonicalSender())
                            : QuickProfileController.ProfileContextSnapshot.empty();

            mentionHistoryService.add(
                    taggedMessage,
                    historyContext.getWorld(),
                    historyContext.getLocationName(),
                    historyChannelName(
                            taggedMessage,
                            historyContext));

            /*
             * Refresh the sidebar after the persistent history has been updated.
             * MentionHistoryPanel.reload() moves itself onto Swing's EDT when needed.
             */
            if (mentionHistoryPanel != null)
            {
                mentionHistoryPanel.reload();
            }
        }

        /*
         * Do not rerun LocalMentionMatcher here. Whole-message foreground
         * coloring, notifications, mention highlighting, and mention history
         * should all consume the same semantic match produced by ChatProcessor.
         */
        if (mentionNotificationService != null
                && taggedMessage
                .getLocalMentionMatch()
                .isMatchesLocalPlayer()
                && !isLocalSender(
                taggedMessage,
                localName))
        {
            mentionNotificationService.notifyMention(
                    taggedMessage);
        }

        /*
         * Apply native chat styling while preserving the sender's
         * original text and existing RuneLite/Jagex markup.
         */
        final long formattingStarted =
                chatLayoutDiagnostic != null
                        ? System.nanoTime()
                        : 0L;

        final String formattedMessage = messageFormatter.format(
                taggedMessage,
                rawMessage,
                localName);

        if (chatLayoutDiagnostic != null)
        {
            chatLayoutDiagnostic.recordMessageFormatting(
                    System.nanoTime()
                            - formattingStarted);
        }

        if (!formattedMessage.equals(rawMessage)) {
            event.getMessageNode().setValue(formattedMessage);
            event.getMessageNode().setRuneLiteFormatMessage(formattedMessage);
        }

        /*
         * Diagnostic logging.
         */
        if (!taggedMessage.getReferences().isEmpty()
                || taggedMessage.getLocalMentionMatch().isMatchesLocalPlayer()) {
            log.debug(
                    "[RuneTags] TaggedMessageID={} Reference={} localMatch={} Sources={}",
                    taggedMessage.getId(),
                    taggedMessage.getReferences().size(),
                    taggedMessage.getLocalMentionMatch().getReason(),
                    referenceSources(taggedMessage));
        }
    }

    @Subscribe
    public void onMenuOpened(
            MenuOpened event)
    {
        if (chatReferenceMouseListener != null)
        {
            chatReferenceMouseListener.onMenuOpened(
                    event);
        }
    }

    @Subscribe
    public void onConfigChanged(
            ConfigChanged event)
    {
        if (event == null
                || !RuneTagsConstants.CONFIG_GROUP.equals(
                event.getGroup()))
        {
            return;
        }

        switch (event.getKey())
        {
            case "mentionHistory":
                updateMentionHistoryNavigation();
                break;

            case "maximumHistory":
                if (mentionHistoryService != null)
                {
                    mentionHistoryService.enforceLimit();
                }

                if (mentionHistoryPanel != null)
                {
                    mentionHistoryPanel.reload();
                }
                break;

            case "fontMentions":
                clientThread.invokeLater(() ->
                {
                    client.refreshChat();

                    /*
                     * Script construction normally marks this automatically, but keep the
                     * configuration lifecycle explicit so switching to NORMAL also forces
                     * final restoration.
                     */
                    if (chatFontLayoutService != null)
                    {
                        chatFontLayoutService.markFontsDirty();
                    }
                });
                break;

            default:
                break;
        }
    }

    private void updateMentionHistoryNavigation()
    {
        if (mentionHistoryNavigation == null)
        {
            return;
        }

        if (config.mentionHistory())
        {
            if (!mentionHistoryNavigationAdded)
            {
                clientToolbar.addNavigation(
                        mentionHistoryNavigation);

                mentionHistoryNavigationAdded =
                        true;
            }
        }
        else if (mentionHistoryNavigationAdded)
        {
            clientToolbar.removeNavigation(
                    mentionHistoryNavigation);

            mentionHistoryNavigationAdded =
                    false;
        }
    }

    private boolean isLocalSender(
            TaggedMessage taggedMessage,
            String localPlayerName)
    {
        if (taggedMessage == null
                || localPlayerName == null
                || localPlayerName.isEmpty()
                || taggedMessage.getCanonicalSender() == null
                || taggedMessage.getCanonicalSender().isEmpty())
        {
            return false;
        }

        final String senderKey =
                nameNormalizer.comparisonKey(
                        taggedMessage.getCanonicalSender());

        final String localKey =
                nameNormalizer.comparisonKey(
                        localPlayerName);

        return !senderKey.isEmpty()
                && senderKey.equals(localKey);
    }

    @Subscribe
    public void onLocationUpdate(LocationUpdate event) {
        if (partyContextService == null) {
            return;
        }

        partyContextService.onLocationUpdate(event);

        final PartyContextService.PartyContext context =
                partyContextService.findByMemberId(
                        event.getMemberId());

        if (context != null) {
            final int regionId =
                    context.getRegionId();

            final int regionX =
                    regionId >> 8;

            final int regionY =
                    regionId & 0xFF;

            log.debug(
                    "[RuneTags] Party LocationUpdate | Player='{}' MemberId={} RegionID={} Region=[{}, {}] Location='{}'",
                    context.getPlayerName(),
                    context.getMemberId(),
                    regionId,
                    regionX,
                    regionY,
                    context.getLocationName());
        }
    }

    private void bootstrapNativeChatIfPending()
    {
        if (!nativeChatBootstrapPending
                || nativeChatBootstrapService == null
                || messageRepository == null
                || chatProcessor == null
                || client.getGameState()
                != GameState.LOGGED_IN)
        {
            return;
        }

        final Player localPlayer =
                client.getLocalPlayer();

        final String localPlayerName =
                localPlayer != null
                        ? localPlayer.getName()
                        : null;

        /*
         * startUp() normally creates an empty repository.
         *
         * Clearing here also handles the narrow case where a live ChatMessage
         * reached RuneTags after plugin startup but before this client-thread
         * bootstrap callback executed. That message already exists in RuneScape's
         * native buffer and will therefore be reconstructed exactly once below.
         */
        messageRepository.clear();

        nextMessageId = 0;

        nextMessageId =
                nativeChatBootstrapService.bootstrap(
                        nextMessageId,
                        localPlayerName,
                        RuneTagsPlugin::isSupportedChatType);

        nativeChatBootstrapPending =
                false;

        /*
         * Existing native chat may already be visible when RuneTags is enabled.
         *
         * Request one final semantic/font synchronization so retained messages receive
         * their configured font without waiting for another incoming message.
         */
        if (chatFontLayoutService != null)
        {
            chatFontLayoutService.markFontsDirty();
        }

        log.debug(
                "[RuneTags] Native Chat Bootstrap Complete | TaggedMessages={}",
                messageRepository.size());
    }

    private static boolean isSupportedChatType(ChatMessageType type)
    {
        if (type == null)
        {
            return false;
        }

        switch (type)
        {
            case PUBLICCHAT:
            case MODCHAT:

            case PRIVATECHAT:
            case MODPRIVATECHAT:
            case PRIVATECHATOUT:

            case FRIENDSCHAT:

            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
            case CLAN_GIM_CHAT:
                return true;

            default:
                return false;
        }
    }

    private static String historyChannelName(
            TaggedMessage taggedMessage,
            QuickProfileController.ProfileContextSnapshot context)
    {
        if (taggedMessage == null
                || taggedMessage.getType() == null)
        {
            return null;
        }

        /*
         * Only preserve the shared channel name when it corresponds to the
         * channel in which the historical message was actually sent.
         *
         * Example:
         * A player may be both PARTY and CLAN. If they mentioned us in Clan Chat,
         * we must not record "Party" merely because Party is PlayerDirectory's
         * highest-priority current channel.
         */
        switch (taggedMessage.getType())
        {
            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
                return context.getChannelSource() == PlayerSource.CLAN
                        ? context.getChannelName()
                        : null;

            case CLAN_GUEST_CHAT:
                return context.getChannelSource() == PlayerSource.GUEST_CLAN
                        ? context.getChannelName()
                        : null;

            case FRIENDSCHAT:
                return context.getChannelSource() == PlayerSource.FRIENDS_CHAT
                        ? context.getChannelName()
                        : null;

            default:
                return null;
        }
    }

    private String referenceSources(
            com.runetags.model.TaggedMessage taggedMessage) {
        if (taggedMessage == null
                || taggedMessage.getReferences() == null
                || taggedMessage.getReferences().isEmpty()) {
            return "[]";
        }

        final java.util.LinkedHashSet<String> sources =
                new java.util.LinkedHashSet<>();

        for (com.runetags.model.PlayerReference reference
                : taggedMessage.getReferences()) {
            if (reference == null) {
                continue;
            }

            com.runetags.model.PlayerIdentity identity =
                    reference.getIdentity();

            /*
             * Mirror QuickProfileController's resolution behavior so the debug
             * log tells us how RuneTags can currently resolve this reference,
             * rather than only what was attached when the message was parsed.
             */
            if (identity == null
                    && playerDirectory != null
                    && reference.getLookupName() != null) {
                identity =
                        playerDirectory
                                .find(reference.getLookupName())
                                .orElse(null);
            }

            if (identity == null
                    || identity.getSources() == null
                    || identity.getSources().isEmpty()) {
                sources.add("UNRESOLVED");
                continue;
            }

            for (com.runetags.model.PlayerSource source
                    : identity.getSources()) {
                if (source != null) {
                    sources.add(source.name());
                }
            }
        }

        return sources.toString();
    }
}
