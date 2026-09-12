package com.runetags.quickprofile;

import com.runetags.Configurations;
import com.runetags.context.*;
import com.runetags.context.ProfileMetric;
import com.runetags.hiscores.*;
import com.runetags.target.TargetController;
import com.runetags.input.NoteChatboxInput;
import com.runetags.input.NoteTextLayout;
import com.runetags.location.PlayerLocation;
import com.runetags.player.OnlineState;
import com.runetags.player.AccountType;
import com.runetags.player.PlayerIdentity;
import com.runetags.reference.PlayerReference;
import com.runetags.player.PlayerSource;
import com.runetags.player.PlayerDirectory;
import com.runetags.reports.ReportCaseService;
import com.runetags.reports.ReportSummary;
import com.runetags.records.LocalPlayerRecordService;
import com.runetags.input.TagChatboxInput;

import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.LinkBrowser;

@Slf4j
public class QuickProfileController
{
    private final Client client;
    private final ClientThread clientThread;
    private final PlayerDirectory playerDirectory;
    private final HiscoreEnrichmentService hiscoreEnrichmentService;
    private final EfficiencyMetricService efficiencyMetricService;
    private final Configurations config;
    private final PlayerContextService playerContextService;
    private final PartyContextService partyContextService;
    private final ProfileMetricResolver profileMetricResolver;
    private final PlayerLookupService playerLookupService;
    private final ClanLookupService clanLookupService;
    private final TargetController targetController;
    private final ReportCaseService reportCaseService;
    private final LocalPlayerRecordService localPlayerRecordService;
    private final ChatboxPanelManager chatboxPanelManager;

    private volatile QuickProfileModel model;
    private Point anchorPoint;

    /*
     * Context resolved for the player currently displayed by this profile card.
     *
     * Local scene context is only shared with players whose relationship to the
     * local client is authoritative. Remote players require their own resolved
     * context and must not inherit local state.
     */
    private PlayerContext profileContext;
    private volatile HiscoreProfileData enrichmentData;

    private Rectangle cardBounds;
    private Rectangle closeButtonBounds;
    private Rectangle tagButtonBounds;
    private Rectangle noteButtonBounds;
    private Rectangle favoriteButtonBounds;
    private Rectangle targetButtonBounds;
    private Rectangle lookupButtonBounds;
    private Rectangle clanLinkBounds;
    private Rectangle reportCaseLinkBounds;
    private final Map<String, Rectangle> tagRemoveBounds = new LinkedHashMap<>();

    private long openGeneration;
    private volatile boolean noteEditorOpen;
    private volatile boolean reopenProfileAfterNoteEdit;
    private volatile boolean tagEditorOpen;
    private volatile boolean reopenProfileAfterTagEdit;

    private volatile FontMetrics noteDisplayFontMetrics;
    private volatile int noteDisplayMaxWidth;

    public QuickProfileController(
            Client client,
            ClientThread clientThread,
            PlayerDirectory playerDirectory,
            HiscoreEnrichmentService hiscoreEnrichmentService,
            EfficiencyMetricService efficiencyMetricService,
            Configurations config,
            PlayerContextService playerContextService,
            PartyContextService partyContextService,
            ProfileMetricResolver profileMetricResolver,
            TargetController targetController,
            PlayerLookupService playerLookupService,
            ClanLookupService clanLookupService,
            ReportCaseService reportCaseService,
            LocalPlayerRecordService localPlayerRecordService,
            ChatboxPanelManager chatboxPanelManager)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.playerDirectory = playerDirectory;
        this.hiscoreEnrichmentService = hiscoreEnrichmentService;
        this.efficiencyMetricService = efficiencyMetricService;
        this.config = config;
        this.playerContextService = playerContextService;
        this.partyContextService = partyContextService;
        this.profileMetricResolver = profileMetricResolver;
        this.targetController = targetController;
        this.playerLookupService = playerLookupService;
        this.clanLookupService = clanLookupService;
        this.reportCaseService = reportCaseService;
        this.localPlayerRecordService = localPlayerRecordService;
        this.chatboxPanelManager = chatboxPanelManager;
    }

    public void open(
            PlayerReference reference,
            Point clickPoint)
    {
        if (reference == null)
        {
            return;
        }

        final Point requestedPoint =
                clickPoint != null
                        ? new Point(clickPoint)
                        : null;

        clientThread.invokeLater(() ->
                openResolved(
                        reference,
                        requestedPoint));
    }

    /*
     * Profile context is only assigned when the player relationship provides a
     * valid source of context. Unresolved chat references cannot use local
     * player context.
     */
    private void openResolved(
            PlayerReference reference,
            Point clickPoint)
    {
        if (reference == null)
        {
            return;
        }

        cancelNoteEdit();

        final long generation = ++openGeneration;

        enrichmentData = null;

        final PlayerIdentity historicalIdentity =
                reference.getIdentity();

        PlayerIdentity liveIdentity = null;

        /*
         * PlayerReference stores the information available when the chat message
         * was processed. Live presence data must come from PlayerDirectory so the
         * profile reflects the player's current state.
         */
        if (reference.getLookupName() != null
                && !reference.getLookupName().trim().isEmpty())
        {
            liveIdentity = playerDirectory
                    .find(reference.getLookupName())
                    .orElse(null);
        }

        /*
         * Identity fields may fall back to the original reference when live data
         * is unavailable. Live presence fields are intentionally not restored from
         * stale references.
         */
        final PlayerIdentity semanticIdentity =
                liveIdentity != null
                        ? liveIdentity
                        : historicalIdentity;

        profileContext =
                resolveProfileContext(liveIdentity);

        final ChatMessageType originatingChatType =
                reference.getChatType();

        final String lookupName;

        if (semanticIdentity != null)
        {
            lookupName = semanticIdentity.getCanonicalName();

            model = QuickProfileModel.builder()
                    .displayName(semanticIdentity.getCanonicalName())
                    .resolved(true)
                    .accountType(
                            semanticIdentity.getAccountType() != null
                                    ? semanticIdentity.getAccountType()
                                    : AccountType.UNKNOWN)
                    .combatLevel(
                            liveIdentity != null
                                    && liveIdentity.getCombatLevel() != null
                                    ? liveIdentity.getCombatLevel()
                                    : semanticIdentity.getCombatLevel())
                    .world(
                            liveIdentity != null
                                    ? liveIdentity.getWorld()
                                    : null)
                    .onlineState(
                            liveIdentity != null
                                    && liveIdentity.getOnlineState() != null
                                    ? liveIdentity.getOnlineState()
                                    : OnlineState.UNKNOWN)
                    .locationName(
                            displayLocation(
                                    semanticIdentity.getCanonicalName(),
                                    liveIdentity,
                                    profileContext))
                    .channelName(
                            displayChannelName(
                                    liveIdentity,
                                    originatingChatType))
                    .channelRank(
                            displayChannelRank(
                                    liveIdentity))
                    .channelSource(
                            displayChannelSource(
                                    liveIdentity))
                    .originatingChatType(originatingChatType)
                    .nearby(
                            liveIdentity != null
                                    && liveIdentity.isNearby())
                    .identity(liveIdentity)
                    .enrichmentState(HiscoreEnrichmentState.LOCAL)
                    .build();
        }
        else
        {
            lookupName =
                    reference.getLookupName() != null
                            && !reference.getLookupName().isEmpty()
                            ? reference.getLookupName()
                            : reference.getRawText();

            model =
                    QuickProfileModel.unresolved(
                                    lookupName)
                            .toBuilder()
                            .locationName(
                                    displayLocation(
                                            lookupName,
                                            null,
                                            null))
                            .channelName(
                                    displayMessageChannel(
                                            originatingChatType))
                            .originatingChatType(
                                    originatingChatType)
                            .build();
        }

        if (model != null)
        {
            model = model.toBuilder()
                    .previousRsns(
                            localPlayerRecordService != null
                                    ? localPlayerRecordService.getPreviousRsns(
                                    model.getDisplayName())
                                    : Collections.emptyList())
                    .tags(
                            localPlayerRecordService != null
                                    ? localPlayerRecordService.getTags(
                                    model.getDisplayName())
                                    : Collections.emptyList())
                    .favorite(
                            localPlayerRecordService != null
                                    && localPlayerRecordService.isFavorite(
                                    model.getDisplayName()))
                    .note(
                            localPlayerRecordService != null
                                    ? localPlayerRecordService.getNote(
                                    model.getDisplayName())
                                    : null)
                    .build();
        }

        anchorPoint =
                clickPoint != null
                        ? new Point(clickPoint)
                        : new Point(20, 20);

        clearLayoutBounds();

        final String resolutionSources =
                liveIdentity != null
                        && liveIdentity.getSources() != null
                        && !liveIdentity.getSources().isEmpty()
                        ? liveIdentity.getSources().toString()
                        : semanticIdentity != null
                        ? "[HISTORICAL_ONLY]"
                        : "[UNRESOLVED]";

        startAutomaticEnrichment(
                lookupName,
                generation);

        startEfficiencyLookup(
                lookupName,
                generation);

        startReportLookup(
                lookupName,
                generation);
    }

    public void close()
    {
        cancelNoteEdit();
        cancelTagEdit();

        ++openGeneration;
        model = null;
        anchorPoint = null;
        profileContext = null;
        enrichmentData = null;
        clearLayoutBounds();
    }

    public boolean isOpen()
    {
        return model != null;
    }

    public void refreshContext()
    {
        final QuickProfileModel current =
                model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty())
        {
            return;
        }

        /*
         * Refreshes use the current PlayerDirectory state rather than rebuilding
         * identity data from every source during normal client updates.
         */
        final PlayerIdentity identity =
                playerDirectory
                        .find(current.getDisplayName())
                        .orElse(null);

        if (identity == null)
        {
            profileContext = null;

            model = current.toBuilder()
                    .world(null)
                    .onlineState(OnlineState.UNKNOWN)
                    .locationName(
                            displayLocation(
                                    current.getDisplayName(),
                                    null,
                                    null))
                    .contextMetrics(
                            Collections.emptyList())
                    .channelName(
                            displayMessageChannel(
                                    current.getOriginatingChatType()))
                    .channelRank(null)
                    .channelSource(null)
                    .nearby(false)
                    .identity(null)
                    .build();

            return;
        }

        final PlayerContext refreshedContext =
                resolveProfileContext(identity);

        profileContext =
                refreshedContext;

        final List<ProfileMetricValue> refreshedMetrics =
                buildContextMetrics(
                        refreshedContext,
                        enrichmentData);

        model = current.toBuilder()
                .accountType(
                        resolveAccountType(
                                identity.getAccountType(),
                                enrichmentData))
                .world(identity.getWorld())
                .onlineState(
                        identity.getOnlineState() != null
                                ? identity.getOnlineState()
                                : OnlineState.UNKNOWN)
                .locationName(
                        displayLocation(
                                identity.getCanonicalName(),
                                identity,
                                refreshedContext))
                .contextMetrics(refreshedMetrics)
                .channelName(displayChannelName(identity, current.getOriginatingChatType()))
                .channelRank(displayChannelRank(identity))
                .channelSource(displayChannelSource(identity))
                .nearby(identity.isNearby())
                .identity(identity)
                .build();
    }

    public QuickProfileModel getModel()
    {
        return model;
    }

    public Point getAnchorPoint()
    {
        return anchorPoint == null
                ? null
                : new Point(anchorPoint);
    }

    public ProfileContextSnapshot resolveHistoryContext(
            String playerName)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            return ProfileContextSnapshot.empty();
        }

        final PlayerIdentity identity =
                playerDirectory
                        .find(playerName)
                        .orElse(null);

        if (identity == null)
        {
            return ProfileContextSnapshot.empty();
        }

        final PlayerContext context =
                resolveProfileContext(identity);

        return new ProfileContextSnapshot(
                identity.getWorld(),
                context != null
                        ? context.getLocationName()
                        : null,
                identity.getChannelName(),
                identity.getChannelSource());
    }

    /**
     * Opens a Quick-Card from a stored player reference.
     *
     * The displayed profile is resolved against current directory data before
     * showing live information.
     */
    public void openPlayer(
            String playerName)
    {
        openPlayer(
                playerName,
                null,
                null);
    }

    public void openPlayer(
            String playerName,
            Point clickPoint)
    {
        openPlayer(
                playerName,
                null,
                clickPoint);
    }

    public void openPlayer(
            String playerName,
            ChatMessageType chatType,
            Point clickPoint)
    {
        if (playerName == null
                || playerName.trim().isEmpty())
        {
            return;
        }

        final String requestedName =
                playerName.trim();

        final Point requestedPoint =
                clickPoint != null
                        ? new Point(clickPoint)
                        : null;

        clientThread.invokeLater(() ->
        {
            final PlayerIdentity identity =
                    playerDirectory
                            .find(requestedName)
                            .orElse(null);

            final PlayerReference reference =
                    PlayerReference.builder()
                            .rawText(requestedName)
                            .lookupName(
                                    identity != null
                                            ? identity.getCanonicalName()
                                            : requestedName)
                            .locallyResolved(identity != null)
                            .identity(identity)
                            .chatType(chatType)
                            .build();

            openResolved(
                    reference,
                    requestedPoint);
        });
    }

    public Rectangle getCardBounds()
    {
        return copy(cardBounds);
    }

    public Rectangle getCloseButtonBounds()
    {
        return copy(closeButtonBounds);
    }

    public Rectangle getTagButtonBounds()
    {
        return copy(tagButtonBounds);
    }

    public Rectangle getNoteButtonBounds()
    {
        return copy(noteButtonBounds);
    }

    public Rectangle getFavoriteButtonBounds()
    {
        return copy(favoriteButtonBounds);
    }

    public Rectangle getTargetButtonBounds()
    {
        return copy(targetButtonBounds);
    }

    public Rectangle getLookupButtonBounds()
    {
        return copy(lookupButtonBounds);
    }

    public void updateLayoutBounds(
            Rectangle cardBounds,
            Rectangle closeButtonBounds,
            Rectangle tagButtonBounds,
            Rectangle noteButtonBounds,
            Rectangle favoriteButtonBounds,
            Rectangle targetButtonBounds,
            Rectangle lookupButtonBounds,
            Rectangle clanLinkBounds,
            Rectangle reportCaseLinkBounds,
            Map<String, Rectangle> tagRemoveBounds)
    {
        this.cardBounds = copy(cardBounds);
        this.closeButtonBounds = copy(closeButtonBounds);
        this.tagButtonBounds = copy(tagButtonBounds);
        this.noteButtonBounds = copy(noteButtonBounds);
        this.favoriteButtonBounds = copy(favoriteButtonBounds);
        this.targetButtonBounds = copy(targetButtonBounds);
        this.lookupButtonBounds = copy(lookupButtonBounds);
        this.clanLinkBounds = copy(clanLinkBounds);
        this.reportCaseLinkBounds =
                copy(reportCaseLinkBounds);

        this.tagRemoveBounds.clear();
        if (tagRemoveBounds != null)
        {
            for (Map.Entry<String, Rectangle> entry : tagRemoveBounds.entrySet())
            {
                if (entry.getKey() != null
                        && entry.getValue() != null)
                {
                    this.tagRemoveBounds.put(
                            entry.getKey(),
                            copy(entry.getValue()));
                }
            }
        }
    }

    public void updateNoteLayoutMetrics(
            FontMetrics metrics,
            int maxWidth)
    {
        noteDisplayFontMetrics = metrics;
        noteDisplayMaxWidth =
                Math.max(
                        0,
                        maxWidth);
    }

    public boolean isInsideCard(Point point)
    {
        return cardBounds != null
                && point != null
                && cardBounds.contains(point);
    }

    public boolean isCloseButton(Point point)
    {
        return closeButtonBounds != null
                && point != null
                && closeButtonBounds.contains(point);
    }

    public boolean isTagButton(Point point)
    {
        return tagButtonBounds != null
                && point != null
                && tagButtonBounds.contains(point);
    }

    public String tagRemovalAt(Point point)
    {
        if (point == null)
        {
            return null;
        }

        for (Map.Entry<String, Rectangle> entry : tagRemoveBounds.entrySet())
        {
            if (entry.getValue() != null
                    && entry.getValue().contains(point))
            {
                return entry.getKey();
            }
        }

        return null;
    }

    public void removeTag(String tag)
    {
        final QuickProfileModel current = model;

        if (current == null
                || tag == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty()
                || localPlayerRecordService == null)
        {
            return;
        }

        final List<String> updated =
                new ArrayList<>(
                        localPlayerRecordService.getTags(
                                current.getDisplayName()));

        if (!updated.remove(tag))
        {
            return;
        }

        localPlayerRecordService.setTags(
                current.getDisplayName(),
                updated);

        model = current.toBuilder()
                .tags(
                        localPlayerRecordService.getTags(
                                current.getDisplayName()))
                .build();
    }

    public boolean isNoteButton(Point point)
    {
        return noteButtonBounds != null
                && point != null
                && noteButtonBounds.contains(point);
    }

    public boolean isFavoriteButton(Point point)
    {
        return favoriteButtonBounds != null
                && point != null
                && favoriteButtonBounds.contains(point);
    }

    public boolean isReportCaseLink(
            Point point)
    {
        return reportCaseLinkBounds != null
                && point != null
                && reportCaseLinkBounds.contains(
                point);
    }

    public boolean isClanLink(Point point)
    {
        return clanLinkBounds != null
                && point != null
                && clanLinkBounds.contains(point);
    }

    public boolean isTargetButton(Point point)
    {
        return targetButtonBounds != null
                && point != null
                && targetButtonBounds.contains(point);
    }

    public boolean isLookupButton(Point point)
    {
        return lookupButtonBounds != null
                && point != null
                && lookupButtonBounds.contains(point);
    }

    public boolean isEditingTag()
    {
        return tagEditorOpen;
    }

    public void editTags()
    {
        final QuickProfileModel current = model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty()
                || localPlayerRecordService == null
                || chatboxPanelManager == null
                || tagEditorOpen
                || noteEditorOpen)
        {
            return;
        }

        final String playerName = current.getDisplayName().trim();
        final ChatMessageType originatingChatType = current.getOriginatingChatType();
        final Point reopenAnchor = anchorPoint != null
                ? new Point(anchorPoint)
                : new Point(20, 20);

        final TagChatboxInput input =
                new TagChatboxInput(chatboxPanelManager, clientThread)
                        .prompt("RuneTags Tags: " + playerName)
                        .value(localPlayerRecordService.getTags(playerName));

        tagEditorOpen = true;
        reopenProfileAfterTagEdit = true;

        ++openGeneration;
        model = null;
        anchorPoint = null;
        profileContext = null;
        enrichmentData = null;
        clearLayoutBounds();

        input.onDone(tags ->
                        localPlayerRecordService.setTags(playerName, tags))
                .onClose(() ->
                {
                    tagEditorOpen = false;
                    final boolean shouldReopen = reopenProfileAfterTagEdit;
                    reopenProfileAfterTagEdit = false;

                    if (shouldReopen)
                    {
                        openPlayer(playerName, originatingChatType, reopenAnchor);
                    }
                })
                .build();
    }

    public void cancelTagEdit()
    {
        reopenProfileAfterTagEdit = false;

        if (tagEditorOpen
                && chatboxPanelManager != null)
        {
            chatboxPanelManager.close();
        }
    }

    public void toggleFavorite()
    {
        final QuickProfileModel current =
                model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty()
                || localPlayerRecordService == null)
        {
            return;
        }

        final boolean favorite =
                localPlayerRecordService.toggleFavorite(
                        current.getDisplayName());

        model = current.toBuilder()
                .favorite(favorite)
                .build();
    }

    public boolean isEditingNote()
    {
        return noteEditorOpen;
    }

    /**
     * Open RuneLite's native chatbox Note editor for the current player's local
     * Note. The first logical line always starts with a bullet, Shift+Enter adds
     * another bullet line, Enter validates/saves, and ESC cancels.
     */
    public void editNote()
    {
        final QuickProfileModel current =
                model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty()
                || localPlayerRecordService == null
                || chatboxPanelManager == null
                || noteEditorOpen
                || tagEditorOpen)
        {
            return;
        }

        final String playerName =
                current.getDisplayName().trim();

        final ChatMessageType originatingChatType =
                current.getOriginatingChatType();

        final Point reopenAnchor =
                anchorPoint != null
                        ? new Point(anchorPoint)
                        : new Point(20, 20);

        final String existingNote =
                localPlayerRecordService.getNote(
                        playerName);

        final NoteChatboxInput noteInput =
                new NoteChatboxInput(
                        chatboxPanelManager,
                        clientThread)
                        .prompt(
                                "RuneTags Note: "
                                        + playerName)
                        .lines(4)
                        .maxLength(
                                LocalPlayerRecordService.MAX_NOTE_LENGTH)
                        .maxLogicalLines(
                                NoteTextLayout.MAX_LOGICAL_LINES)
                        .value(existingNote);

        noteEditorOpen = true;
        reopenProfileAfterNoteEdit = true;

        /*
         * Temporarily hide the Quick-Card while adding/editing a Note.
         */
        ++openGeneration;
        model = null;
        anchorPoint = null;
        profileContext = null;
        enrichmentData = null;
        clearLayoutBounds();

        noteInput.onDone(value ->
                {
                    final String candidate =
                            NoteTextLayout.normalizeForStorage(
                                    value);

                    if (candidate == null)
                    {
                        localPlayerRecordService.setNote(
                                playerName,
                                null);

                        return true;
                    }

                    if (candidate.length()
                            > LocalPlayerRecordService.MAX_NOTE_LENGTH)
                    {
                        noteInput.prompt(
                                "RuneTags Note: max "
                                        + LocalPlayerRecordService.MAX_NOTE_LENGTH
                                        + " characters");

                        return false;
                    }

                    if (NoteTextLayout.logicalLineCount(
                            candidate)
                            > NoteTextLayout.MAX_LOGICAL_LINES)
                    {
                        noteInput.prompt(
                                "RuneTags Note: max "
                                        + NoteTextLayout.MAX_LOGICAL_LINES
                                        + " bullets");

                        return false;
                    }

                    final FontMetrics metrics =
                            noteDisplayFontMetrics;

                    final int maxWidth =
                            noteDisplayMaxWidth;

                    if (metrics != null
                            && maxWidth > 0)
                    {
                        final NoteTextLayout.Result layout =
                                NoteTextLayout.layout(
                                        metrics,
                                        candidate,
                                        maxWidth,
                                        NoteTextLayout.MAX_RENDERED_ROWS);

                        if (layout.isOverflow())
                        {
                            noteInput.prompt(
                                    "RuneTags Note: max "
                                            + NoteTextLayout.MAX_RENDERED_ROWS
                                            + " display lines");

                            return false;
                        }
                    }

                    localPlayerRecordService.setNote(
                            playerName,
                            candidate);

                    return true;
                })
                .onClose(() ->
                {
                    noteEditorOpen = false;

                    final boolean shouldReopen =
                            reopenProfileAfterNoteEdit;

                    reopenProfileAfterNoteEdit = false;

                    if (shouldReopen)
                    {
                        openPlayer(
                                playerName,
                                originatingChatType,
                                reopenAnchor);
                    }
                })
                .build();
    }

    public void cancelNoteEdit()
    {
        reopenProfileAfterNoteEdit = false;

        if (noteEditorOpen
                && chatboxPanelManager != null)
        {
            chatboxPanelManager.close();
        }
    }

    public void target()
    {
        final QuickProfileModel current = model;

        if (current == null || !current.isNearby())
        {
            return;
        }

        targetController.toggleTarget(current);
    }

    public boolean isCurrentProfileTargeted()
    {
        final QuickProfileModel current = model;

        return current != null
                && targetController.isTargetingName(
                current.getDisplayName());
    }

    /**
     * Returns whether a chat reference currently resolves to a nearby player.
     *
     * The live directory is checked instead of the reference snapshot.
     */
    public boolean canTarget(
            PlayerReference reference)
    {
        final PlayerIdentity identity =
                resolveLiveIdentity(
                        reference);

        return identity != null
                && identity.isNearby();
    }

    /**
     * Targets a player selected from a chat reference menu.
     *
     * The player is resolved at activation time to ensure the target is still
     * valid in the current scene.
     */
    public void target(
            PlayerReference reference)
    {
        final PlayerIdentity identity =
                resolveLiveIdentity(
                        reference);

        if (identity == null
                || !identity.isNearby()
                || identity.getCanonicalName() == null
                || identity.getCanonicalName().trim().isEmpty())
        {
            return;
        }

        targetController.toggleTarget(
                identity.getCanonicalName());
    }

    public void lookup()
    {
        final QuickProfileModel current = model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty())
        {
            return;
        }

        playerLookupService.lookup(
                current.getDisplayName());
    }

    public void lookup(
            PlayerReference reference)
    {
        if (reference == null)
        {
            return;
        }

        PlayerIdentity identity =
                reference.getIdentity();

        /*
         * Resolve the current directory identity when available rather than using
         * the original chat-processing snapshot.
         */
        if (reference.getLookupName() != null
                && !reference.getLookupName().trim().isEmpty())
        {
            final PlayerIdentity currentIdentity =
                    playerDirectory
                            .find(reference.getLookupName())
                            .orElse(null);

            if (currentIdentity != null)
            {
                identity = currentIdentity;
            }
        }

        final String lookupName;

        if (identity != null
                && identity.getCanonicalName() != null
                && !identity.getCanonicalName().trim().isEmpty())
        {
            lookupName =
                    identity.getCanonicalName();
        }
        else if (reference.getLookupName() != null
                && !reference.getLookupName().trim().isEmpty())
        {
            lookupName =
                    reference.getLookupName();
        }
        else
        {
            lookupName =
                    reference.getRawText();
        }

        if (lookupName == null
                || lookupName.trim().isEmpty())
        {
            return;
        }

        playerLookupService.lookup(
                lookupName.trim());
    }

    public void lookupClan()
    {
        final QuickProfileModel current =
                model;

        if (current == null
                || current.getChannelName() == null
                || current.getChannelName().trim().isEmpty())
        {
            return;
        }

        final PlayerSource channelSource =
                current.getChannelSource();

        if (channelSource != PlayerSource.CLAN
                && channelSource != PlayerSource.GUEST_CLAN)
        {
            return;
        }

        clanLookupService.search(
                current.getChannelName());
    }

    public void openReportCase()
    {
        final QuickProfileModel current =
                model;

        if (current == null
                || current.getReportSummaries() == null
                || current.getReportSummaries().isEmpty())
        {
            return;
        }

        /*
         * Iterate through available summaries defensively in case the stored report
         * structure changes in the future.
         */
        for (ReportSummary summary
                : current.getReportSummaries())
        {
            if (summary == null
                    || !summary.hasCaseLink())
            {
                continue;
            }

            final String caseUrl =
                    summary.getCaseUrl();

            if (caseUrl == null
                    || caseUrl.trim().isEmpty())
            {
                return;
            }

            LinkBrowser.browse(
                    caseUrl);

            return;
        }
    }

    private PlayerIdentity resolveLiveIdentity(
            PlayerReference reference)
    {
        if (reference == null)
        {
            return null;
        }

        if (reference.getLookupName() != null
                && !reference.getLookupName().trim().isEmpty())
        {
            final PlayerIdentity identity =
                    playerDirectory
                            .find(reference.getLookupName())
                            .orElse(null);

            if (identity != null)
            {
                return identity;
            }
        }

        if (reference.getIdentity() != null
                && reference.getIdentity().getCanonicalName() != null
                && !reference.getIdentity().getCanonicalName().trim().isEmpty())
        {
            final PlayerIdentity identity =
                    playerDirectory
                            .find(reference.getIdentity().getCanonicalName())
                            .orElse(null);

            if (identity != null)
            {
                return identity;
            }
        }

        if (reference.getRawText() != null
                && !reference.getRawText().trim().isEmpty())
        {
            return playerDirectory
                    .find(reference.getRawText())
                    .orElse(null);
        }

        return null;
    }

    public void refreshEfficiencyMetrics()
    {
        final QuickProfileModel current = model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty())
        {
            return;
        }

        if (!config.wiseOldManMetrics()
                || (!config.showEhp()
                && !config.showEhb()))
        {
            clearEfficiencyMetrics();
            return;
        }

        startEfficiencyLookup(
                current.getDisplayName(),
                openGeneration);
    }

    public void clearEfficiencyMetrics()
    {
        final QuickProfileModel current = model;

        if (current == null)
        {
            return;
        }

        model = current.toBuilder()
                .efficientHoursPlayed(null)
                .efficientHoursBossed(null)
                .build();
    }

    /**
     * Refresh report summaries for the currently open Quick-Card.
     *
     * This is used when Show Reports is enabled while a card is already open.
     * The ReportCaseService performs all network staleness checks and keeps
     * parsing off the client thread.
     */
    public void refreshReports()
    {
        final QuickProfileModel current =
                model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty())
        {
            return;
        }

        startReportLookup(
                current.getDisplayName(),
                openGeneration);
    }

    public void clearReports()
    {
        final QuickProfileModel current =
                model;

        if (current == null)
        {
            return;
        }

        model = current.toBuilder()
                .reportSummaries(
                        Collections.emptyList())
                .build();
    }

    private void startReportLookup(
            String playerName,
            long generation)
    {
        if (reportCaseService == null
                || playerName == null
                || playerName.trim().isEmpty())
        {
            return;
        }

        reportCaseService.requestReports(
                playerName,
                reports -> applyReportSummaries(
                        generation,
                        playerName,
                        reports));
    }

    private void applyReportSummaries(
            long generation,
            String playerName,
            List<ReportSummary> reports)
    {
        if (generation != openGeneration)
        {
            return;
        }

        final QuickProfileModel current =
                model;

        if (current == null
                || current.getDisplayName() == null
                || !samePlayer(
                current.getDisplayName(),
                playerName))
        {
            return;
        }

        model = current.toBuilder()
                .reportSummaries(
                        reports != null
                                ? reports
                                : Collections.emptyList())
                .build();
    }

    private void startEfficiencyLookup(
            String playerName,
            long generation)
    {
        if (efficiencyMetricService == null
                || !config.wiseOldManMetrics()
                || (!config.showEhp()
                && !config.showEhb())
                || playerName == null
                || playerName.trim().isEmpty())
        {
            return;
        }

        efficiencyMetricService.lookup(playerName)
                .thenAccept(data ->
                        applyEfficiencyMetrics(
                                generation,
                                playerName,
                                data));
    }

    private void applyEfficiencyMetrics(
            long generation,
            String playerName,
            EfficiencyProfileData data)
    {
        if (generation != openGeneration
                || !config.wiseOldManMetrics())
        {
            return;
        }

        final QuickProfileModel current = model;

        if (current == null
                || current.getDisplayName() == null
                || !samePlayer(
                current.getDisplayName(),
                playerName))
        {
            return;
        }

        model = current.toBuilder()
                .efficientHoursPlayed(
                        data != null
                                ? data.getEhp()
                                : null)
                .efficientHoursBossed(
                        data != null
                                ? data.getEhb()
                                : null)
                .build();
    }

    private void startAutomaticEnrichment(
            String playerName,
            long generation)
    {
        if (playerName == null || playerName.trim().isEmpty())
        {
            return;
        }

        final HiscoreEnrichmentService.EnrichmentRequest request =
                hiscoreEnrichmentService.enrich(playerName);

        applyEnrichment(
                generation,
                playerName,
                request.getInitialState(),
                request.getInitialData());

        final CompletableFuture<HiscoreEnrichmentCache.CachedProfileEnrichment> future =
                request.getFuture();

        if (future == null)
        {
            return;
        }

        future.thenAccept(result ->
                applyEnrichment(
                        generation,
                        playerName,
                        result.getState(),
                        result.getData()));
    }

    private void applyEnrichment(
            long generation,
            String playerName,
            HiscoreEnrichmentState state,
            HiscoreProfileData data)
    {
        if (generation != openGeneration)
        {
            return;
        }

        final QuickProfileModel current = model;

        if (current == null
                || current.getDisplayName() == null
                || !samePlayer(current.getDisplayName(), playerName))
        {
            return;
        }

        /*
         * Use the context captured when the profile opened. Asynchronous enrichment
         * must not recalculate context from the local player's current location.
         */
        enrichmentData = data;

        final PlayerContext playerContext =
                profileContext;

        final List<ProfileMetricValue> contextMetrics =
                buildContextMetrics(
                        playerContext,
                        enrichmentData);

        model = current.toBuilder()
                .accountType(
                        resolveAccountType(
                                current.getAccountType(),
                                data))
                .combatLevel(
                        data != null
                                && data.getCombatLevel() != null
                                ? data.getCombatLevel()
                                : current.getCombatLevel())
                .totalLevel(
                        data != null
                                ? data.getTotalLevel()
                                : current.getTotalLevel())
                .contextMetrics(contextMetrics)
                .enrichmentState(state)
                .build();
    }

    private static AccountType resolveAccountType(
            AccountType current,
            HiscoreProfileData data)
    {
        final AccountType enriched =
                data != null
                        ? data.getAccountType()
                        : AccountType.UNKNOWN;

        return AccountType.prefer(
                current,
                enriched);
    }

    private PlayerContext resolveProfileContext(
            PlayerIdentity identity)
    {
        if (identity == null)
        {
            return null;
        }

        /*
         * Nearby context takes priority over cached Party context because local
         * scene information is more current.
         */
        if (identity.isNearby())
        {
            return playerContextService != null
                    ? playerContextService.getCurrent()
                    : null;
        }

        /*
         * Remote Party members may use their own shared Party context. Other remote
         * players must not inherit local context.
         */
        if (identity.getSources() == null
                || !identity.getSources().contains(PlayerSource.PARTY)
                || partyContextService == null)
        {
            return null;
        }

        final PartyContextService.PartyContext partyContext =
                partyContextService.find(
                        identity.getCanonicalName());

        if (partyContext == null)
        {
            return null;
        }

        return profileMetricResolver.resolveLocation(
                new PlayerLocation(
                        partyContext.getLocationName(),
                        partyContext.getRegionId()));
    }

    private String displayLocation(
            String playerName,
            PlayerIdentity identity,
            PlayerContext context)
    {
        /*
         * A resolved location is preferred whenever one is available.
         */
        if (context != null
                && context.getLocationName() != null
                && !context.getLocationName().trim().isEmpty())
        {
            return context.getLocationName();
        }

        /*
         * Region-grid fallback is only valid for the local player. Nearby players
         * without a mapped location should not expose the local region as their own.
         */
        if (isLocalPlayerName(playerName))
        {
            final PlayerContext localContext =
                    context != null
                            ? context
                            : playerContextService != null
                            ? playerContextService.getCurrent()
                            : null;

            if (localContext != null)
            {
                if (localContext.getLocationName() != null
                        && !localContext.getLocationName().trim().isEmpty())
                {
                    return localContext.getLocationName();
                }

                if (localContext.getRegionId() >= 0)
                {
                    return formatRegionGrid(
                            localContext.getRegionId());
                }
            }
        }

        /*
         * Nearby players without a resolved location only expose their nearby state.
         */
        if (identity != null
                && identity.isNearby())
        {
            return "Nearby";
        }

        /*
         * Remote players without a resolved location have no location data available.
         */
        return "Location: Unknown";
    }

    private boolean isLocalPlayerName(
            String playerName)
    {
        if (client == null
                || playerName == null
                || playerName.trim().isEmpty())
        {
            return false;
        }

        final Player localPlayer =
                client.getLocalPlayer();

        return localPlayer != null
                && localPlayer.getName() != null
                && samePlayer(
                playerName,
                localPlayer.getName());
    }

    /**
     * Locations.json stores region-grid coordinates as [regionX, regionY].
     * WorldPoint#getRegionID packs that pair as (regionX << 8) | regionY.
     */
    private static String formatRegionGrid(
            int regionId)
    {
        final int regionX =
                (regionId >>> 8) & 0xFF;

        final int regionY =
                regionId & 0xFF;

        return "Region: ["
                + regionX
                + ", "
                + regionY
                + "]";
    }

    private static String displayChannelName(
            PlayerIdentity identity,
            ChatMessageType originatingChatType)
    {
        /*
         * Prefer the resolved shared channel when available.
         */
        if (identity != null
                && identity.getChannelName() != null
                && !identity.getChannelName().trim().isEmpty())
        {
            return identity.getChannelName();
        }

        return displayMessageChannel(
                originatingChatType);
    }

    private static String displayMessageChannel(
            ChatMessageType chatType)
    {
        if (chatType == null)
        {
            return null;
        }

        switch (chatType)
        {
            case PUBLICCHAT:
            case MODCHAT:
                return "Public";

            case PRIVATECHAT:
            case MODPRIVATECHAT:
            case PRIVATECHATOUT:
                return "Private";

            case FRIENDSCHAT:
                return "Friends Chat";

            case CLAN_CHAT:
            case CLAN_GIM_CHAT:
                return "Clan";

            case CLAN_GUEST_CHAT:
                return "Guest Clan";

            default:
                return null;
        }
    }

    private static String displayChannelRank(
            PlayerIdentity identity)
    {
        /*
         * Message-derived channels do not provide a channel rank.
         */
        if (identity == null
                || identity.getChannelName() == null
                || identity.getChannelName().trim().isEmpty())
        {
            return null;
        }

        return identity.getChannelRank();
    }

    private static PlayerSource displayChannelSource(
            PlayerIdentity identity)
    {
        /*
         * Message-derived channels do not map to a PlayerSource.
         */
        if (identity == null
                || identity.getChannelName() == null
                || identity.getChannelName().trim().isEmpty())
        {
            return null;
        }

        return identity.getChannelSource();
    }

    private static List<ProfileMetricValue> buildContextMetrics(
            PlayerContext playerContext,
            HiscoreProfileData data)
    {
        if (playerContext == null
                || data == null
                || !playerContext.hasMetrics())
        {
            return Collections.emptyList();
        }

        final List<ProfileMetricValue> values =
                new ArrayList<>();

        for (ProfileMetric metric : playerContext.getMetrics())
        {
            final Integer value =
                    data.getContextValue(
                            metric.getHiscoreSkillName());

            if (value == null)
            {
                continue;
            }

            values.add(
                    new ProfileMetricValue(
                            metric.getLabel(),
                            value));
        }

        return Collections.unmodifiableList(values);
    }

    private static boolean samePlayer(
            String left,
            String right)
    {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value)
    {
        return value == null
                ? ""
                : value.trim()
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("[ _-]+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    @lombok.Value
    public static class ProfileContextSnapshot
    {
        Integer world;
        String locationName;
        String channelName;
        PlayerSource channelSource;

        public static ProfileContextSnapshot empty()
        {
            return new ProfileContextSnapshot(
                    null,
                    null,
                    null,
                    null);
        }
    }

    private void clearLayoutBounds()
    {
        cardBounds = null;
        closeButtonBounds = null;
        tagButtonBounds = null;
        noteButtonBounds = null;
        favoriteButtonBounds = null;
        targetButtonBounds = null;
        lookupButtonBounds = null;
        clanLinkBounds = null;
        reportCaseLinkBounds = null;
        tagRemoveBounds.clear();
    }

    private static Rectangle copy(Rectangle rectangle)
    {
        return rectangle == null
                ? null
                : new Rectangle(rectangle);
    }
}
