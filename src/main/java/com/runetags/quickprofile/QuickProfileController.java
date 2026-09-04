package com.runetags.quickprofile;

import com.runetags.context.ContextMetric;
import com.runetags.context.ContextMetricResolver;
import com.runetags.context.ContextMetricValue;
import com.runetags.context.PartyContextService;
import com.runetags.context.PlayerContext;
import com.runetags.context.PlayerContextService;
import com.runetags.context.TargetController;
import com.runetags.model.OnlineState;
import com.runetags.model.PlayerIdentity;
import com.runetags.model.PlayerReference;
import com.runetags.model.PlayerSource;
import com.runetags.player.PlayerDirectory;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;

@Slf4j
public class QuickProfileController
{
    private final PlayerDirectory playerDirectory;
    private final HiscoreEnrichmentService hiscoreEnrichmentService;
    private final PlayerContextService playerContextService;
    private final PartyContextService partyContextService;
    private final ContextMetricResolver contextMetricResolver;
    private final LookupService lookupService;
    private final ClanLookupService clanLookupService;
    private final TargetController targetController;

    private volatile QuickProfileModel model;
    private Point anchorPoint;

    /*
     * Context associated with the player represented by the current card.
     *
     * Nearby players use the local shared scene context.
     * Remote Party members may use their own last reported Party context.
     * Other remote/unresolved players must never inherit our local context.
     */
    private PlayerContext profileContext;
    private volatile HiscoreProfileData enrichmentData;

    private Rectangle cardBounds;
    private Rectangle closeButtonBounds;
    private Rectangle targetButtonBounds;
    private Rectangle lookupButtonBounds;
    private Rectangle clanLinkBounds;

    private long openGeneration;

    public QuickProfileController(
            PlayerDirectory playerDirectory,
            HiscoreEnrichmentService hiscoreEnrichmentService,
            PlayerContextService playerContextService,
            PartyContextService partyContextService,
            ContextMetricResolver contextMetricResolver,
            TargetController targetController,
            LookupService lookupService,
            ClanLookupService clanLookupService)
    {
        this.playerDirectory = playerDirectory;
        this.hiscoreEnrichmentService = hiscoreEnrichmentService;
        this.playerContextService = playerContextService;
        this.partyContextService = partyContextService;
        this.contextMetricResolver = contextMetricResolver;
        this.targetController = targetController;
        this.lookupService = lookupService;
        this.clanLookupService = clanLookupService;
    }

    public void open(PlayerReference reference, Point clickPoint)
    {
        if (reference == null)
        {
            return;
        }

        final long generation = ++openGeneration;

        enrichmentData = null;

        PlayerIdentity identity = reference.getIdentity();

        if (identity == null && reference.getLookupName() != null)
        {
            identity = playerDirectory
                    .find(reference.getLookupName())
                    .orElse(null);
        }

        /*
         * Only a player physically present in our nearby-player directory
         * OR inside the same Party may share the local world context.
         *
         * An unresolved explicit @tag must not inherit our own location or contextual KC.
         */
        profileContext =
                resolveProfileContext(identity);

        final ChatMessageType originatingChatType =
                reference.getChatType();

        final String lookupName;

        if (identity != null)
        {
            lookupName = identity.getCanonicalName();

            model = QuickProfileModel.builder()
                    .displayName(identity.getCanonicalName())
                    .resolved(true)
                    .combatLevel(identity.getCombatLevel())
                    .world(identity.getWorld())
                    .onlineState(
                            identity.getOnlineState() != null
                                    ? identity.getOnlineState()
                                    : OnlineState.UNKNOWN)
                    .locationName(displayLocation(identity, profileContext))
                    .channelName(displayChannelName(identity, originatingChatType))
                    .channelRank(displayChannelRank(identity))
                    .channelSource(displayChannelSource(identity))
                    .originatingChatType(originatingChatType)
                    .nearby(identity.isNearby())
                    .identity(identity)
                    .enrichmentState(ProfileEnrichmentState.LOCAL)
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
                            .locationName("Location: Unknown")
                            .channelName(
                                    displayMessageChannel(
                                            originatingChatType))
                            .originatingChatType(
                                    originatingChatType)
                            .build();
        }

        anchorPoint =
                clickPoint != null
                        ? new Point(clickPoint)
                        : new Point(20, 20);

        clearLayoutBounds();

        final String resolutionSources =
                identity != null
                        && identity.getSources() != null
                        && !identity.getSources().isEmpty()
                        ? identity.getSources().toString()
                        : "[UNRESOLVED]";

        log.debug(
                "[RuneTags] Quick-Card opened for Player='{}' | Resolved={} Nearby={} Sources={} World={} Location='{}'",
                model.getDisplayName(),
                model.isResolved(),
                model.isNearby(),
                resolutionSources,
                model.getWorld(),
                model.getLocationName());

        startAutomaticEnrichment(
                lookupName,
                generation);
    }

    public void close()
    {
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
         * Re-resolve the identity because PlayerDirectory is rebuilt every tick.
         * The PlayerIdentity stored in the QuickProfileModel is only a snapshot
         * from when the card was opened.
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
                    .locationName("Location: Unknown")
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

        final List<ContextMetricValue> refreshedMetrics =
                buildContextMetrics(
                        refreshedContext,
                        enrichmentData);

        model = current.toBuilder()
                .world(identity.getWorld())
                .onlineState(
                        identity.getOnlineState() != null
                                ? identity.getOnlineState()
                                : OnlineState.UNKNOWN)
                .locationName(displayLocation(identity, refreshedContext))
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
     * Open a current Quick-Card for a player selected from persistent history.
     *
     * The history row itself remains a historical snapshot, but clicking it
     * deliberately re-resolves the sender against the current PlayerDirectory.
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

        final PlayerIdentity identity =
                playerDirectory
                        .find(playerName)
                        .orElse(null);

        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(playerName)
                        .lookupName(
                                identity != null
                                        ? identity.getCanonicalName()
                                        : playerName)
                        .locallyResolved(identity != null)
                        .identity(identity)
                        .chatType(chatType)
                        .build();

        open(
                reference,
                clickPoint);
    }

    public Rectangle getCardBounds()
    {
        return copy(cardBounds);
    }

    public Rectangle getCloseButtonBounds()
    {
        return copy(closeButtonBounds);
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
            Rectangle targetButtonBounds,
            Rectangle lookupButtonBounds,
            Rectangle clanLinkBounds)
    {
        this.cardBounds = copy(cardBounds);
        this.closeButtonBounds = copy(closeButtonBounds);
        this.targetButtonBounds = copy(targetButtonBounds);
        this.lookupButtonBounds = copy(lookupButtonBounds);
        this.clanLinkBounds = copy(clanLinkBounds);
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

    public boolean isClanLink(Point point)
    {
        return clanLinkBounds != null
                && point != null
                && clanLinkBounds.contains(point);
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

    public void lookup()
    {
        final QuickProfileModel current = model;

        if (current == null
                || current.getDisplayName() == null
                || current.getDisplayName().trim().isEmpty())
        {
            return;
        }

        lookupService.lookup(
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
         * PlayerReference identity is a snapshot from message-processing time.
         * Re-resolve against the current directory when possible.
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

        lookupService.lookup(
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

        final CompletableFuture<ProfileEnrichmentCache.CachedProfileEnrichment> future =
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
            ProfileEnrichmentState state,
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
         * Use the context captured when the card was opened.
         *
         * Do NOT call playerContextService.getCurrent() here. Hiscore enrichment is
         * asynchronous, and that would incorrectly associate the local player's
         * current location with remote/unresolved profiles.
         */
        enrichmentData = data;

        final PlayerContext playerContext =
                profileContext;

        final List<ContextMetricValue> contextMetrics =
                buildContextMetrics(
                        playerContext,
                        enrichmentData);

        model = current.toBuilder()
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

        log.debug(
                "[RuneTags] Profile Enrichment for Player='{}' | State={} Combat={} TotalLvl={} World={} Location='{}' Metrics={}",
                playerName,
                state,
                model.getCombatLevel(),
                model.getTotalLevel(),
                model.getWorld(),
                playerContext != null
                        ? playerContext.getLocationName()
                        : null,
                contextMetrics);
    }

    private PlayerContext resolveProfileContext(
            PlayerIdentity identity)
    {
        if (identity == null)
        {
            return null;
        }

        /*
         * Nearby always wins over Party context.
         *
         * If a remote Party member teleports into our scene, their local nearby
         * state immediately becomes authoritative even if a Party location cache
         * entry is still fresh.
         */
        if (identity.isNearby())
        {
            return playerContextService != null
                    ? playerContextService.getCurrent()
                    : null;
        }

        /*
         * Remote Party members may use their own last reported Party location.
         * Other remote identities must never inherit our local context.
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

        return contextMetricResolver.resolveLocation(
                partyContext.getLocationName(),
                partyContext.getRegionId());
    }

    private static String displayLocation(
            PlayerIdentity identity,
            PlayerContext context)
    {
        /*
         * A real mapped location always wins.
         */
        if (context != null
                && context.getLocationName() != null
                && !context.getLocationName().trim().isEmpty())
        {
            return context.getLocationName();
        }

        /*
         * The player is physically in our scene, but the current region is not
         * represented in Locations.json.
         */
        if (identity != null
                && identity.isNearby())
        {
            return "Nearby";
        }

        /*
         * Remote player with no exposed/resolved location.
         */
        return "Location: Unknown";
    }

    private static String displayChannelName(
            PlayerIdentity identity,
            ChatMessageType originatingChatType)
    {
        /*
         * A real shared channel always wins.
         *
         * PlayerDirectory already prioritizes:
         * Party > Clan > Guest Clan > Friends Chat.
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
            case PRIVATECHAT:
            case PRIVATECHATOUT:
                return "Private";

            case PUBLICCHAT:
            case MODCHAT:
                return "Public";

            default:
                return null;
        }
    }

    private static String displayChannelRank(
            PlayerIdentity identity)
    {
        /*
         * Public and Private are message fallbacks and have no rank.
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
         * Public and Private are not PlayerSource values.
         */
        if (identity == null
                || identity.getChannelName() == null
                || identity.getChannelName().trim().isEmpty())
        {
            return null;
        }

        return identity.getChannelSource();
    }

    private static List<ContextMetricValue> buildContextMetrics(
            PlayerContext playerContext,
            HiscoreProfileData data)
    {
        if (playerContext == null
                || data == null
                || !playerContext.hasMetrics())
        {
            return Collections.emptyList();
        }

        final List<ContextMetricValue> values =
                new ArrayList<>();

        for (ContextMetric metric : playerContext.getMetrics())
        {
            final Integer value =
                    data.getContextValue(
                            metric.getHiscoreSkillName());

            if (value == null)
            {
                continue;
            }

            values.add(
                    new ContextMetricValue(
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
        targetButtonBounds = null;
        lookupButtonBounds = null;
        clanLinkBounds = null;
    }

    private static Rectangle copy(Rectangle rectangle)
    {
        return rectangle == null
                ? null
                : new Rectangle(rectangle);
    }
}
