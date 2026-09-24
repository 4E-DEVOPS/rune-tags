package com.runetags;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.runetags.chat.ChatHitboxRegistry;
import com.runetags.chat.ChatProcessor;
import com.runetags.chat.ChatText;
import com.runetags.chat.FontLayoutService;
import com.runetags.chat.MessageFormatter;
import com.runetags.chat.NativeBootstrapService;
import com.runetags.chat.ReferenceLayoutService;
import com.runetags.chat.TaggedMessage;
import com.runetags.chat.TaggedMessageRepository;
import com.runetags.context.PartyContextService;
import com.runetags.context.PlayerContextService;
import com.runetags.context.ProfileMetricResolver;
import com.runetags.hiscores.EfficiencyMetricService;
import com.runetags.hiscores.HiscoreEnrichmentCache;
import com.runetags.hiscores.HiscoreEnrichmentService;
import com.runetags.hiscores.PlayerLookupService;
import com.runetags.history.MentionHistoryPanel;
import com.runetags.history.MentionHistoryService;
import com.runetags.input.InputListener;
import com.runetags.location.LocationIndex;
import com.runetags.location.PlayerLocationService;
import com.runetags.mention.KnownPlayerMentionParser;
import com.runetags.mention.LocalMentionMatcher;
import com.runetags.mention.NameNormalizer;
import com.runetags.mention.TagParser;
import com.runetags.notification.MentionNotificationService;
import com.runetags.overlay.ChatReferenceOverlay;
import com.runetags.overlay.FavoriteOverlay;
import com.runetags.overlay.QuickProfileOverlay;
import com.runetags.overlay.TargetMinimapOverlay;
import com.runetags.overlay.TargetOverlay;
import com.runetags.player.PlayerDirectory;
import com.runetags.player.PlayerSource;
import com.runetags.quickprofile.ClanLookupService;
import com.runetags.quickprofile.QuickProfileController;
import com.runetags.records.LocalPlayerRecordService;
import com.runetags.reports.ReportCaseService;
import com.runetags.suggestion.SuggestionOverlay;
import com.runetags.suggestion.SuggestionService;
import com.runetags.target.PlayerVisibilityService;
import com.runetags.target.TargetController;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Nameable;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NameableNameChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.RemovedFriend;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.party.messages.LocationUpdate;
import net.runelite.client.plugins.party.messages.StatusUpdate;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
		name = "RuneTags",
		description = "Quick-card profiles for player mentions and tags.",
		tags = {"1877", "card", "chat", "clan", "mentions", "player", "players", "profile", "quick", "tagging", "tags", "rune-tags", "runetags"},
		enabledByDefault = true
)
public class RuneTags extends Plugin {
	private static final int MESSAGE_REPOSITORY_CAPACITY = 500;
	private static final String PLAYER_MENU_OPEN_PROFILE = "Open Profile";

	/*
	 * Periodically reconcile PlayerDirectory state not covered by incremental RuneLite events.
	 */
	private static final int DIRECTORY_RECONCILE_GAME_TICKS = 25;
	private static final int DIRECTORY_TARGETED_REFRESH_LIMIT = 2;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ChatboxPanelManager chatboxPanelManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Configurations config;
	@Inject
	private Gson gson;
	@Inject
	private HiscoreClient hiscoreClient;
	@Inject
	private Hooks hooks;
	@Inject
	private KeyManager keyManager;
	@Inject
	private LocationIndex locationIndex;
	@Inject
	private MentionHistoryService mentionHistoryService;
	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;
	@Inject
	private MouseManager mouseManager;
	@Inject
	private Notifier notifier;
	@Inject
	private OkHttpClient okHttpClient;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private PartyService partyService;
	@Inject
	private Provider<MenuManager> menuManager;
	@Inject
	private RuneLiteConfig runeLiteConfig;
	@Inject
	private TooltipManager tooltipManager;
	@Inject
	private UpdateMessages updateMessages;
	@Inject
	private WorldService worldService;

	/*
	 * Identity / mention parsing.
	 */
	private NameNormalizer nameNormalizer;
	private LocalPlayerRecordService localPlayerRecordService;
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

	private MentionHistoryPanel mentionHistoryPanel;
	private NavigationButton mentionHistoryNavigation;
	private boolean mentionHistoryNavigationAdded;

	/*
	 * Chat storage / rendering / interaction.
	 */
	private TaggedMessageRepository messageRepository;
	private NativeBootstrapService nativeBootstrapService;

	private ChatHitboxRegistry chatHitboxRegistry;
	private ReferenceLayoutService referenceLayoutService;
	private FontLayoutService fontLayoutService;

	private ChatReferenceOverlay chatReferenceOverlay;
	private SuggestionService suggestionService;
	private SuggestionOverlay suggestionOverlay;
	private InputListener inputListener;

	/*
	 * Context / profile enrichment.
	 */
	private PlayerLocationService playerLocationService;
	private ProfileMetricResolver profileMetricResolver;

	private PlayerContextService playerContextService;
	private PartyContextService partyContextService;

	private HiscoreEnrichmentCache hiscoreEnrichmentCache;
	private HiscoreEnrichmentService hiscoreEnrichmentService;
	private EfficiencyMetricService efficiencyMetricService;

	private PlayerLookupService playerLookupService;
	private ClanLookupService clanLookupService;
	private ReportCaseService reportCaseService;

	/*
	 * Quick Profile.
	 */
	private QuickProfileController quickProfileController;
	private QuickProfileOverlay quickProfileOverlay;

	private FavoriteOverlay favoriteOverlay;

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

	private final Set<String> pendingDirectoryRefreshes = new LinkedHashSet<>();
	private boolean fullDirectoryRefreshPending;
	private int directoryReconcileTicks;

	private long lastFavoriteUiRevision = Long.MIN_VALUE;

	/*
	 * Retain RUNELITE_PLAYER identifiers until the corresponding menu click.
	 * Native menus can outlive the Player object that created them.
	 */
	private final Map<Integer, String> playerProfileIndexNames = new HashMap<>();

	/**
	 * ================================================================
	 * START-UP & SHUT-DOWN
	 * ================================================================
	 */
	@Override
	protected void startUp() {
		// 1. Identity / mention parsing
		nameNormalizer = new NameNormalizer();
		localPlayerRecordService = new LocalPlayerRecordService(gson, nameNormalizer, configManager);
		playerDirectory = new PlayerDirectory(client, partyService, worldService, nameNormalizer);
		tagParser = new TagParser(nameNormalizer, playerDirectory);
		knownPlayerMentionParser = new KnownPlayerMentionParser(playerDirectory, nameNormalizer);
		localMentionMatcher = new LocalMentionMatcher(config, nameNormalizer);
		messageFormatter = new MessageFormatter(config, localMentionMatcher);
		chatProcessor = new ChatProcessor(tagParser, knownPlayerMentionParser, localMentionMatcher);

		// 2. Context / profile enrichment
		hiscoreEnrichmentCache = new HiscoreEnrichmentCache();
		hiscoreEnrichmentService = new HiscoreEnrichmentService(hiscoreClient, hiscoreEnrichmentCache);
		efficiencyMetricService = new EfficiencyMetricService(okHttpClient, gson);
		playerLocationService = new PlayerLocationService(client, locationIndex);
		profileMetricResolver = new ProfileMetricResolver();
		playerContextService = new PlayerContextService(client, playerLocationService, profileMetricResolver);
		partyContextService = new PartyContextService(partyService, locationIndex, nameNormalizer);

		// 3. Targeting / lookup / Quick Profile
		targetController = new TargetController(client, config);
		playerVisibilityService = new PlayerVisibilityService(hooks, targetController);
		playerLookupService = new PlayerLookupService(config);
		clanLookupService = new ClanLookupService();
		reportCaseService = new ReportCaseService(okHttpClient, gson, clientThread, config);
		quickProfileController = new QuickProfileController(
				client,
				clientThread,
				playerDirectory,
				hiscoreEnrichmentService,
				efficiencyMetricService,
				config,
				playerContextService,
				partyContextService,
				profileMetricResolver,
				targetController,
				playerLookupService,
				clanLookupService,
				reportCaseService,
				localPlayerRecordService,
				chatboxPanelManager);
		quickProfileOverlay = new QuickProfileOverlay(
				client,
				config,
				runeLiteConfig,
				quickProfileController,
				tooltipManager);
		favoriteOverlay = new FavoriteOverlay(client, config, localPlayerRecordService, modelOutlineRenderer);
		targetOverlay = new TargetOverlay(client, config, targetController, modelOutlineRenderer);
		targetMinimapOverlay = new TargetMinimapOverlay(client, config, targetController);

		// 4. Mention notifications / persistent history
		mentionNotificationService = new MentionNotificationService(client, config, notifier);
		mentionHistoryService.reload();
		mentionHistoryPanel = new MentionHistoryPanel(
				client,
				config,
				mentionHistoryService,
				quickProfileController,
				localPlayerRecordService);
		final BufferedImage historyIcon = ImageUtil.loadImageResource(RuneTags.class, "sidebar_icon.png");
		mentionHistoryNavigation = NavigationButton.builder()
				.tooltip("RuneTags")
				.icon(historyIcon)
				.priority(8)
				.panel(mentionHistoryPanel)
				.build();

		// 5. Structured chat storage / geometry / interaction
		messageRepository = new TaggedMessageRepository(MESSAGE_REPOSITORY_CAPACITY);
		nativeBootstrapService = new NativeBootstrapService(client, playerDirectory, chatProcessor, messageRepository);
		chatHitboxRegistry = new ChatHitboxRegistry();
		referenceLayoutService = new ReferenceLayoutService(
				client,
				config,
				messageRepository,
				localPlayerRecordService);
		// Font resolution depends on finalized message ownership and layout state.
		fontLayoutService = new FontLayoutService(client, config, messageRepository, referenceLayoutService);
		chatReferenceOverlay = new ChatReferenceOverlay(
				referenceLayoutService,
				chatHitboxRegistry,
				client,
				config,
				localMentionMatcher);
		suggestionService = new SuggestionService(client, clientThread, config, playerDirectory);
		suggestionOverlay = new SuggestionOverlay(client, suggestionService);
		inputListener = new InputListener(
				client,
				config,
				chatHitboxRegistry,
				quickProfileController,
				suggestionService);

		// 6. Runtime state
		nextMessageId = 0;
		nativeChatBootstrapPending = true;
		pendingDirectoryRefreshes.clear();
		fullDirectoryRefreshPending = false;
		directoryReconcileTicks = 0;
		playerProfileIndexNames.clear();

		/*
		 * Initial client-state synchronization must run on the client thread when
		 * RuneTags is enabled from RuneLite's Swing configuration UI.
		 */
		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN) {
				return;
			}

			updateMessages.onLoggedIn();

			if (playerLocationService != null) {
				playerLocationService.refresh();
			}

			if (playerContextService != null) {
				playerContextService.refresh();
			}

			if (playerDirectory != null) {
				playerDirectory.rebuild();
				markDirectoryFullySynchronized();
			}

			captureKnownNameHistory();

			if (reportCaseService != null) {
				reportCaseService.refreshInitialIfMissing();
			}

			bootstrapNativeChatIfPending();

			if (targetController != null) {
				targetController.refresh();
			}
		});

		lastFavoriteUiRevision = localPlayerRecordService != null
				? localPlayerRecordService.getFavoriteRevision()
				: Long.MIN_VALUE;

		// 7. Register external RuneLite hooks LAST
		mentionHistoryNavigationAdded = false;
		if (config.mentionHistory() && mentionHistoryNavigation != null) {
			clientToolbar.addNavigation(mentionHistoryNavigation);
			mentionHistoryNavigationAdded = true;
		}
		overlayManager.add(chatReferenceOverlay);
		overlayManager.add(favoriteOverlay);
		overlayManager.add(targetOverlay);
		overlayManager.add(targetMinimapOverlay);
		overlayManager.add(quickProfileOverlay);
		overlayManager.add(suggestionOverlay);
		playerVisibilityService.start();
		menuManager.get().addPlayerMenuItem(PLAYER_MENU_OPEN_PROFILE);
		mouseManager.registerMouseListener(inputListener);
		keyManager.registerKeyListener(inputListener);
		log.debug("[RuneTags] Plugin Initiated!");
	}

	@Override
	protected void shutDown() {
		final boolean uninstalling = updateMessages.prepareShutdown();
		// 1. Stop external interaction first
		if (inputListener != null) {
			keyManager.unregisterKeyListener(inputListener);

			mouseManager.unregisterMouseListener(inputListener);
		}

		if (reportCaseService != null) {
			reportCaseService.shutdown();
		}

		if (efficiencyMetricService != null) {
			efficiencyMetricService.shutdown();
		}

		if (localPlayerRecordService != null) {
			localPlayerRecordService.shutdown();
		}

		menuManager.get().removePlayerMenuItem(PLAYER_MENU_OPEN_PROFILE);

		playerProfileIndexNames.clear();

		if (playerVisibilityService != null) {
			playerVisibilityService.stop();
		}

		if (mentionHistoryNavigation != null && mentionHistoryNavigationAdded) {
			clientToolbar.removeNavigation(mentionHistoryNavigation);

			mentionHistoryNavigationAdded = false;
		}

		// 2. Remove overlays
		if (suggestionOverlay != null) {
			overlayManager.remove(suggestionOverlay);
		}

		if (quickProfileOverlay != null) {
			overlayManager.remove(quickProfileOverlay);
		}

		if (targetMinimapOverlay != null) {
			overlayManager.remove(targetMinimapOverlay);
		}

		if (targetOverlay != null) {
			overlayManager.remove(targetOverlay);
		}

		if (favoriteOverlay != null) {
			overlayManager.remove(favoriteOverlay);
		}

		if (chatReferenceOverlay != null) {
			overlayManager.remove(chatReferenceOverlay);
		}

		// 3. Invalidate active runtime controllers
		if (quickProfileController != null) {
			quickProfileController.close();
		}

		if (targetController != null) {
			targetController.clear("plugin stopped");
		}

		// 4. Clear runtime / cached state
		if (referenceLayoutService != null) {
			referenceLayoutService.restoreFavoriteSenderColors();
			referenceLayoutService.restoreMentionFonts();
			referenceLayoutService.clearChatboxBodyXState();
		}

		if (chatHitboxRegistry != null) {
			chatHitboxRegistry.clear();
		}

		if (messageRepository != null) {
			messageRepository.clear();
		}

		if (playerDirectory != null) {
			playerDirectory.clear();
		}

		if (playerContextService != null) {
			playerContextService.clear();
		}

		if (partyContextService != null) {
			partyContextService.clear();
		}

		if (hiscoreEnrichmentCache != null) {
			hiscoreEnrichmentCache.clear();
		}

		// 5. Release RuneTags objects in reverse dependency order
		// Input and chat presentation
		inputListener = null;
		suggestionOverlay = null;
		suggestionService = null;

		chatReferenceOverlay = null;

		fontLayoutService = null;
		referenceLayoutService = null;
		chatHitboxRegistry = null;

		// Persistent history UI
		mentionHistoryNavigation = null;
		mentionHistoryNavigationAdded = false;
		mentionHistoryPanel = null;

		// Structured chat services
		nativeBootstrapService = null;
		messageRepository = null;

		mentionNotificationService = null;

		chatProcessor = null;
		messageFormatter = null;
		localMentionMatcher = null;

		knownPlayerMentionParser = null;
		tagParser = null;

		// Quick Profile and targeting
		quickProfileOverlay = null;

		targetMinimapOverlay = null;
		targetOverlay = null;
		favoriteOverlay = null;

		quickProfileController = null;

		playerVisibilityService = null;
		targetController = null;

		playerLookupService = null;
		clanLookupService = null;
		reportCaseService = null;

		// Enrichment and context
		hiscoreEnrichmentService = null;
		hiscoreEnrichmentCache = null;

		partyContextService = null;
		playerContextService = null;

		profileMetricResolver = null;

		// Identity root
		playerDirectory = null;
		localPlayerRecordService = null;
		nameNormalizer = null;

		// Runtime counters
		nextMessageId = 0;
		nativeChatBootstrapPending = false;

		clearPendingDirectorySynchronization();
		directoryReconcileTicks = 0;
		lastFavoriteUiRevision = Long.MIN_VALUE;

		updateMessages.finishShutdown(uninstalling);

		log.debug("[RuneTags] Plugin Terminated!");
	}

	/**
	 * ================================================================
	 * GAME STATE
	 * ================================================================
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event == null) {
			return;
		}

		final GameState gameState = event.getGameState();
		if (gameState == GameState.LOGGED_IN) {
			updateMessages.onLoggedIn();
		}

		if (playerDirectory == null) {
			return;
		}

		if (gameState == GameState.LOGGED_IN) {
			if (playerLocationService != null) {
				playerLocationService.refresh();
			}

			if (playerContextService != null) {
				playerContextService.refresh();
			}

			playerDirectory.rebuild();
			markDirectoryFullySynchronized();

			captureKnownNameHistory();

			if (reportCaseService != null) {
				reportCaseService.refreshInitialIfMissing();
			}

			bootstrapNativeChatIfPending();
			return;
		}

		if (gameState == GameState.HOPPING) {
			/*
			 * Preserve retained chat semantics, hitboxes, and durable account observations.
			 * Only live world/session profile state becomes invalid.
			 */
			clearPendingDirectorySynchronization();
			directoryReconcileTicks = 0;
			playerDirectory.clearLiveState();

			if (quickProfileController != null) {
				quickProfileController.close();
			}

			if (targetController != null) {
				targetController.clear("world change");
			}

			if (playerContextService != null) {
				playerContextService.clear();
			}

			if (partyContextService != null) {
				partyContextService.clear();
			}

			return;
		}

		if (gameState == GameState.LOGIN_SCREEN) {
			/*
			 * Preserve retained chat semantics, hitboxes, and durable account observations.
			 * Only live world/session player state becomes invalid.
			 */
			clearPendingDirectorySynchronization();
			directoryReconcileTicks = 0;
			playerDirectory.clearLiveState();

			if (quickProfileController != null) {
				quickProfileController.close();
			}

			if (targetController != null) {
				targetController.clear("logged out");
			}

			if (playerContextService != null) {
				playerContextService.clear();
			}

			if (partyContextService != null) {
				partyContextService.clear();
			}

			if (reportCaseService != null) {
				reportCaseService.clear();
			}
		}
	}

	/**
	 * ================================================================
	 * CHAT COMMANDS
	 * ================================================================
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event) {
		updateMessages.onCommandExecuted(event);
	}

	/**
	 * ================================================================
	 * CHAT CONSTRUCTION
	 * ================================================================
	 */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		if (fontLayoutService != null) {
			fontLayoutService.onScriptPreFired(event);
		}
	}

	/**
	 * ================================================================
	 * TICK PROCESSING
	 * ================================================================
	 */
	@Subscribe
	public void onGameTick(GameTick event) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		/*
		 * Dedicated events handle normal directory changes; periodic reconciliation covers
		 * durable or offline social-list changes without dedicated events.
		 */
		directoryReconcileTicks++;

		if (directoryReconcileTicks >= DIRECTORY_RECONCILE_GAME_TICKS) {
			queueFullDirectoryRefresh();
		}

		if (playerLocationService != null) {
			playerLocationService.refresh();
		}

		if (playerContextService != null) {
			playerContextService.refresh();
		}

		/*
		 * Refresh only the open Quick-Card here. Full PlayerDirectory discovery remains
		 * event-driven or periodic.
		 */
		if (quickProfileController != null && quickProfileController.isOpen()) {
			quickProfileController.refreshContext();
		}

		if (targetController != null && targetController.isTargeting()) {
			targetController.refresh();
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event) {
		// Apply coalesced PlayerDirectory changes before next-tick consumers run.
		flushPendingDirectorySynchronization();

		if (fontLayoutService != null) {
			fontLayoutService.onPostClientTick();
		}

		if (referenceLayoutService != null) {
			referenceLayoutService.syncFavoriteSenderColorsIfNeeded();
		}

		if (localPlayerRecordService != null) {
			final long favoriteUiRevision = localPlayerRecordService.getFavoriteRevision();
			if (favoriteUiRevision != lastFavoriteUiRevision) {
				lastFavoriteUiRevision = favoriteUiRevision;

				if (mentionHistoryPanel != null) {
					mentionHistoryPanel.refreshFavoriteAppearance();
				}
			}
		}
	}

	/**
	 * ================================================================
	 * NAME HISTORY
	 * ================================================================
	 */
	@Subscribe
	public void onNameableNameChanged(NameableNameChanged event) {
		if (localPlayerRecordService == null || event == null) {
			return;
		}

		final Nameable nameable = event.getNameable();
		if (nameable == null
				|| nameable.getName() == null
				|| nameable.getName().trim().isEmpty()
				|| nameable.getPrevName() == null
				|| nameable.getPrevName().trim().isEmpty()) {
			return;
		}

		localPlayerRecordService.observeNameChange(nameable.getName(), nameable.getPrevName());

		/*
		 * Queue both names so end-of-tick synchronization rebuilds the affected identity
		 * without forcing a full directory refresh.
		 */
		queueDirectoryPlayerRefresh(nameable.getPrevName());

		queueDirectoryPlayerRefresh(nameable.getName());
	}

	private void captureKnownNameHistory() {
		if (localPlayerRecordService == null) {
			return;
		}

		final Map<String, String> observations = new LinkedHashMap<>();
		final FriendContainer friendContainer = client.getFriendContainer();
		if (friendContainer != null && friendContainer.getMembers() != null) {
			for (Friend friend : friendContainer.getMembers()) {
				addNameHistoryObservation(observations, friend);
			}
		}

		final ClanChannel clanChannel = client.getClanChannel();
		if (clanChannel != null && clanChannel.getMembers() != null) {
			for (ClanChannelMember member : clanChannel.getMembers()) {
				addNameHistoryObservation(observations, member);
			}
		}

		final ClanChannel guestClanChannel = client.getGuestClanChannel();
		if (guestClanChannel != null && guestClanChannel.getMembers() != null) {
			for (ClanChannelMember member : guestClanChannel.getMembers()) {
				addNameHistoryObservation(observations, member);
			}
		}

		final FriendsChatManager friendsChatManager = client.getFriendsChatManager();
		if (friendsChatManager != null && friendsChatManager.getMembers() != null) {
			for (FriendsChatMember member : friendsChatManager.getMembers()) {
				addNameHistoryObservation(observations, member);
			}
		}

		localPlayerRecordService.observeNameChanges(observations);
	}

	private static void addNameHistoryObservation(Map<String, String> observations, Nameable nameable) {
		if (observations == null
				|| nameable == null
				|| nameable.getName() == null
				|| nameable.getName().trim().isEmpty()
				|| nameable.getPrevName() == null
				|| nameable.getPrevName().trim().isEmpty()) {
			return;
		}

		observations.put(nameable.getName(), nameable.getPrevName());
	}

	/**
	 * ================================================================
	 * PLAYER DIRECTORY
	 * ================================================================
	 */
	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event) {
		// Clan channel replacement is a bulk directory change.
		queueFullDirectoryRefresh();
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined event) {
		if (event != null && event.getClanMember() != null) {
			queueDirectoryPlayerRefresh(event.getClanMember().getName());
		}
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft event) {
		if (event != null && event.getClanMember() != null) {
			queueDirectoryPlayerRefresh(event.getClanMember().getName());
		}
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event) {
		// Rebuild once for initial Friends Chat population instead of processing each event.
		queueFullDirectoryRefresh();
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event) {
		if (event != null && event.getMember() != null) {
			queueDirectoryPlayerRefresh(event.getMember().getName());
		}
	}

	@Subscribe
	public void onFriendsChatMemberLeft(FriendsChatMemberLeft event) {
		if (event != null && event.getMember() != null) {
			queueDirectoryPlayerRefresh(event.getMember().getName());
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event) {
		if (event == null
				|| event.getPlayer() == null
				|| playerDirectory == null
				|| client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		playerDirectory.addNearbyPlayer(event.getPlayer());

		refreshOpenQuickProfile();
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event) {
		if (event == null
				|| event.getPlayer() == null
				|| playerDirectory == null
				|| client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		playerDirectory.removeNearbyPlayer(event.getPlayer());

		refreshOpenQuickProfile();
	}

	@Subscribe
	public void onRemovedFriend(RemovedFriend event) {
		if (event != null && event.getNameable() instanceof Friend) {
			queueDirectoryPlayerRefresh(event.getNameable().getName());
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event) {
		queueFullDirectoryRefresh();
	}

	@Subscribe
	public void onUserJoin(UserJoin event) {
		/*
		 * UserJoin can arrive before the character name. Clear stale member-ID context
		 * and wait for StatusUpdate to provide a usable name.
		 */
		if (event != null && partyContextService != null) {
			partyContextService.removeMember(event.getMemberId());
		}
	}

	@Subscribe
	public void onUserPart(UserPart event) {
		if (event != null && partyContextService != null) {
			partyContextService.removeMember(event.getMemberId());
		}

		queueFullDirectoryRefresh();
	}

	@Subscribe
	public void onStatusUpdate(StatusUpdate event) {
		if (event == null) {
			return;
		}

		String previousPlayerName = null;
		if (partyContextService != null) {
			final PartyContextService.PartyContext previousContext =
					partyContextService.findByMemberId(event.getMemberId());

			previousPlayerName = previousContext != null
					? previousContext.getPlayerName()
					: null;

			partyContextService.onStatusUpdate(event);
		}

		/*
		 * StatusUpdate supplies PartyMember display-name and login state. Queue both the
		 * previous and current names for end-of-tick directory synchronization.
		 */
		queueDirectoryPlayerRefresh(previousPlayerName);

		String playerName = event.getCharacterName();
		if (playerName == null || playerName.trim().isEmpty()) {
			final PartyMember member = partyService != null
					? partyService.getMemberById(event.getMemberId())
					: null;

			playerName = member != null
					? member.getDisplayName()
					: null;
		}

		queueDirectoryPlayerRefresh(playerName);
	}

	private void queueDirectoryPlayerRefresh(String playerName) {
		if (playerDirectory == null
				|| fullDirectoryRefreshPending
				|| playerName == null
				|| playerName.trim().isEmpty()) {
			return;
		}

		pendingDirectoryRefreshes.add(playerName);

		// Escalate bursts of targeted updates to one full directory refresh.
		if (pendingDirectoryRefreshes.size() > DIRECTORY_TARGETED_REFRESH_LIMIT) {
			queueFullDirectoryRefresh();
		}
	}

	private void queueFullDirectoryRefresh() {
		if (playerDirectory == null) {
			return;
		}

		fullDirectoryRefreshPending = true;
		pendingDirectoryRefreshes.clear();
	}

	private void flushPendingDirectorySynchronization() {
		if (playerDirectory == null) {
			clearPendingDirectorySynchronization();
			return;
		}

		if (!fullDirectoryRefreshPending && pendingDirectoryRefreshes.isEmpty()) {
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN) {
			clearPendingDirectorySynchronization();
			return;
		}

		final boolean fullRefresh = fullDirectoryRefreshPending;

		/*
		 * Clear pending requests before synchronization so events raised during the pass
		 * become requests for the next client tick.
		 */
		fullDirectoryRefreshPending = false;

		final Set<String> targetedPlayers = new LinkedHashSet<>(pendingDirectoryRefreshes);

		pendingDirectoryRefreshes.clear();

		if (fullRefresh) {
			playerDirectory.rebuild();
			directoryReconcileTicks = 0;
		} else {
			for (String playerName : targetedPlayers) {
				playerDirectory.refreshPlayer(playerName);
			}
		}

		refreshOpenQuickProfile();
	}

	private void refreshOpenQuickProfile() {
		if (quickProfileController != null && quickProfileController.isOpen()) {
			quickProfileController.refreshContext();
		}
	}

	private void clearPendingDirectorySynchronization() {
		pendingDirectoryRefreshes.clear();
		fullDirectoryRefreshPending = false;
	}

	private void markDirectoryFullySynchronized() {
		clearPendingDirectorySynchronization();
		directoryReconcileTicks = 0;
	}

	/**
	 * ================================================================
	 * CHAT MESSAGES
	 * ================================================================
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (chatProcessor == null || !isSupportedChatType(event.getType()) || event.getMessageNode() == null) {
			return;
		}

		/*
		 * Apply queued membership or presence changes before parsing chat so semantic
		 * resolution sees current directory state.
		 */
		flushPendingDirectorySynchronization();

		final Player localPlayer = client.getLocalPlayer();
		final String localName = localPlayer != null
				? localPlayer.getName()
				: null;

		// Preserve native markup for rendering and derive a separate semantic body.
		final String rawMessage = event.getMessageNode().getValue();
		final String semanticMessage = ChatText.toSemanticPlain(rawMessage);
		if (playerDirectory != null && event.getName() != null && !event.getName().trim().isEmpty()) {
			/*
			 * Incoming player-authored chat is authoritative for native account icons.
			 * PRIVATECHATOUT names the recipient and must not overwrite their account type.
			 */
			if (event.getType() != ChatMessageType.PRIVATECHATOUT) {
				playerDirectory.observeAccountType(event.getName());
			}
		}

		final TaggedMessage taggedMessage = chatProcessor.process(
				++nextMessageId,
				event.getType(),
				Text.removeTags(event.getName()),
				semanticMessage,
				localName);

		// TaggedMessage remains the semantic source of truth
		messageRepository.add(taggedMessage);

		/*
		 * New supported messages can recycle retained chat rows. Mark fonts dirty and
		 * synchronize all reconstructed rows once at PostClientTick.
		 */
		if (fontLayoutService != null) {
			fontLayoutService.markFontsDirty();
		}

		if (mentionHistoryService != null && taggedMessage.getLocalMentionMatch().isMatchesLocalPlayer()) {
			final QuickProfileController.ProfileContextSnapshot historyContext = quickProfileController != null
					? quickProfileController.resolveHistoryContext(taggedMessage.getCanonicalSender())
					: QuickProfileController.ProfileContextSnapshot.empty();

			mentionHistoryService.add(
					taggedMessage,
					historyContext.getWorld(),
					historyContext.getLocationName(),
					historyChannelName(taggedMessage, historyContext));

			// Reload the history panel after persistent history is updated.
			if (mentionHistoryPanel != null) {
				mentionHistoryPanel.reload();
			}
		}

		/*
		 * Reuse ChatProcessor semantic match state for formatting, notifications,
		 * highlighting, and history.
		 */
		if (mentionNotificationService != null
				&& taggedMessage.getLocalMentionMatch().isMatchesLocalPlayer()
				&& !isLocalSender(taggedMessage, localName)) {
			mentionNotificationService.notifyMention(taggedMessage);
		}

		final String formattedMessage = messageFormatter.format(taggedMessage, rawMessage, localName);
		if (!formattedMessage.equals(rawMessage)) {
			event.getMessageNode().setValue(formattedMessage);
			event.getMessageNode().setRuneLiteFormatMessage(formattedMessage);
		}
	}

	private boolean isLocalSender(TaggedMessage taggedMessage, String localPlayerName) {
		if (taggedMessage == null
				|| localPlayerName == null
				|| localPlayerName.isEmpty()
				|| taggedMessage.getCanonicalSender() == null
				|| taggedMessage.getCanonicalSender().isEmpty()) {
			return false;
		}

		final String senderKey = nameNormalizer.comparisonKey(taggedMessage.getCanonicalSender());
		final String localKey = nameNormalizer.comparisonKey(localPlayerName);

		return !senderKey.isEmpty() && senderKey.equals(localKey);
	}

	private static boolean isSupportedChatType(ChatMessageType type) {
		if (type == null) {
			return false;
		}

		switch (type) {
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
			QuickProfileController.ProfileContextSnapshot context) {
		if (taggedMessage == null || taggedMessage.getType() == null) {
			return null;
		}

		/*
		 * Preserve a shared channel name only when its PlayerSource matches the channel
		 * that produced the historical message.
		 */
		switch (taggedMessage.getType()) {
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

	/**
	 * ================================================================
	 * CHAT INTERACTION
	 * ================================================================
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event) {
		/*
		 * Cache RUNELITE_PLAYER profile entries because the native menu can outlive the
		 * Player object that created them.
		 */
		playerProfileIndexNames.clear();

		if (event != null && event.getMenuEntries() != null) {
			for (MenuEntry entry : event.getMenuEntries()) {
				if (entry == null
						|| entry.getType() != MenuAction.RUNELITE_PLAYER
						|| !PLAYER_MENU_OPEN_PROFILE.equals(entry.getOption())) {
					continue;
				}

				final Player player = entry.getPlayer();
				if (player == null || player.getName() == null || player.getName().trim().isEmpty()) {
					continue;
				}

				playerProfileIndexNames.put(entry.getIdentifier(), player.getName());
			}
		}

		if (inputListener != null) {
			inputListener.onMenuOpened(event);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (event == null
				|| event.getMenuAction() != MenuAction.RUNELITE_PLAYER
				|| !PLAYER_MENU_OPEN_PROFILE.equals(event.getMenuOption())
				|| quickProfileController == null) {
			return;
		}

		final Player player = event.getMenuEntry() != null
				? event.getMenuEntry().getPlayer()
				: null;

		String playerName = player != null
				? player.getName()
				: playerProfileIndexNames.get(event.getId());

		if (playerName != null && !playerName.trim().isEmpty()) {
			final net.runelite.api.Point canvasPoint = client.getMouseCanvasPosition();
			final Point anchorPoint = canvasPoint != null
					? new Point(canvasPoint.getX(), canvasPoint.getY())
					: null;

			quickProfileController.openPlayer(playerName, anchorPoint);
		}

		playerProfileIndexNames.clear();
	}

	/**
	 * ================================================================
	 * CONFIGURATION
	 * ================================================================
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event == null || !Constants.CONFIG_GROUP.equals(event.getGroup())) {
			return;
		}

		switch (event.getKey()) {
			case "mentionHistory":
				updateMentionHistoryNavigation();
				break;

			case "maximumHistory":
				if (mentionHistoryService != null) {
					mentionHistoryService.enforceLimit();
				}

				if (mentionHistoryPanel != null) {
					mentionHistoryPanel.reload();
				}
				break;

			case "showFavorites":
			case "favoriteColor":
				if (referenceLayoutService != null) {
					referenceLayoutService.markFavoriteSenderRowsDirty();
				}

				if (mentionHistoryPanel != null) {
					mentionHistoryPanel.refreshFavoriteAppearance();
				}
				break;

			case "showNotes":
				if (!config.showNotes() && quickProfileController != null) {
					quickProfileController.cancelNoteEdit();
				}
				break;

			case "wiseOldManMetrics":
			case "showEhp":
			case "showEhb":
				if (quickProfileController != null && quickProfileController.isOpen()) {
					if (config.wiseOldManMetrics() && (config.showEhp() || config.showEhb())) {
						quickProfileController.refreshEfficiencyMetrics();
					} else {
						quickProfileController.clearEfficiencyMetrics();
					}
				}
				break;

			case "showReports":
				if (reportCaseService != null) {
					if (config.showReports()) {
						if (client.getGameState() == GameState.LOGGED_IN) {
							reportCaseService.refreshInitialIfMissing();
						}

						if (quickProfileController != null && quickProfileController.isOpen()) {
							quickProfileController.refreshReports();
						}
					} else {
						reportCaseService.clear();

						if (quickProfileController != null) {
							quickProfileController.clearReports();
						}
					}
				}
				break;

			case "targetPlayerOption":
				if (!config.targetPlayerOption() && targetController != null) {
					targetController.clear("targeting disabled");
				}
				break;

			case "fontMentions":
				clientThread.invokeLater(() -> {
					client.refreshChat();

					// Switching to NORMAL also requires a final restoration pass.
					if (fontLayoutService != null) {
						fontLayoutService.markFontsDirty();
					}
				});
				break;

			default:
				break;
		}
	}

	private void updateMentionHistoryNavigation() {
		if (mentionHistoryNavigation == null) {
			return;
		}

		if (config.mentionHistory()) {
			if (!mentionHistoryNavigationAdded) {
				clientToolbar.addNavigation(mentionHistoryNavigation);

				mentionHistoryNavigationAdded = true;
			}
		} else if (mentionHistoryNavigationAdded) {
			clientToolbar.removeNavigation(mentionHistoryNavigation);

			mentionHistoryNavigationAdded = false;
		}
	}

	/**
	 * ================================================================
	 * CONFIGURATION PROVIDER
	 * ================================================================
	 */
	@Provides
	Configurations provideConfig(ConfigManager configManager) {
		return configManager.getConfig(Configurations.class);
	}

	/**
	 * ================================================================
	 * PARTY CONTEXT
	 * ================================================================
	 */
	@Subscribe
	public void onLocationUpdate(LocationUpdate event) {
		if (partyContextService == null) {
			return;
		}

		partyContextService.onLocationUpdate(event);

		final PartyContextService.PartyContext context = partyContextService.findByMemberId(event.getMemberId());
		if (context != null) {
			refreshOpenQuickProfile();
		}
	}

	/**
	 * ================================================================
	 * NATIVE CHAT BOOTSTRAP
	 * ================================================================
	 */
	private void bootstrapNativeChatIfPending() {
		if (!nativeChatBootstrapPending
				|| nativeBootstrapService == null
				|| messageRepository == null
				|| chatProcessor == null
				|| client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		final Player localPlayer = client.getLocalPlayer();
		final String localPlayerName = localPlayer != null
				? localPlayer.getName()
				: null;

		// Reset semantic state before rebuilding from the native chat buffer.
		messageRepository.clear();

		nextMessageId = 0;

		nextMessageId = nativeBootstrapService.bootstrap(nextMessageId, localPlayerName, RuneTags::isSupportedChatType);

		nativeChatBootstrapPending = false;

		/*
		 * Existing chat may already be visible when RuneTags starts. Request one final
		 * semantic/font synchronization without waiting for another incoming message.
		 */
		if (fontLayoutService != null) {
			fontLayoutService.markFontsDirty();
		}
	}
}

