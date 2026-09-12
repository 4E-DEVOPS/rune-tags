package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.config.MentionFont;
import com.runetags.mention.LocalMentionMatch;
import com.runetags.mention.MatchReason;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;
import com.runetags.records.LocalPlayerRecordService;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

/**
 * Maps semantic PlayerReference spans onto the actual rendered chat widgets.
 *
 * RuneScape can render chat references on two physically separate surfaces:
 *
 * CHATBOX
 * - normal chat
 * - private chat when Split Private Chat is disabled
 * - private-chat history while the Private chat tab is selected
 *
 * SPLIT_PRIVATE
 * - the floating private-chat rows rendered through the separate PmChat
 *   interface when Split Private Chat is enabled
 *
 * A single TaggedMessage may legitimately be rendered on both surfaces at
 * the same time. RuneTags therefore treats each surface independently and
 * creates hitboxes for every currently rendered physical representation.
 *
 * The service uses each widget's own Jagex FontTypeFace for width
 * measurements so markup and chat-font widths remain aligned with the
 * game renderer.
 */
public class ReferenceLayoutService
{
    /**
     * Physical surface on which RuneScape rendered a chat reference.
     *
     * This is deliberately separate from ChatMessageType.
     *
     * For example, PRIVATECHAT may render:
     *
     * - on CHATBOX,
     * - on SPLIT_PRIVATE,
     * - or on both simultaneously.
     */
    public enum Surface
    {
        CHATBOX,
        SPLIT_PRIVATE
    }

    /**
     * Complete presentation geometry produced by one shared chat-layout pass.
     *
     * Clickable reference/sender hitboxes and non-clickable local-highlight
     * rectangles deliberately share the same semantic -> physical Widget
     * association.
     */
    public static final class LayoutResult
    {
        private final List<ReferenceHitbox> hitboxes;
        private final List<LocalHighlight> localHighlights;

        private LayoutResult(
                List<ReferenceHitbox> hitboxes,
                List<LocalHighlight> localHighlights)
        {
            this.hitboxes = hitboxes;
            this.localHighlights = localHighlights;
        }

        public List<ReferenceHitbox> getHitboxes()
        {
            return hitboxes;
        }

        public List<LocalHighlight> getLocalHighlights()
        {
            return localHighlights;
        }
    }

    /**
     * One non-clickable physical background rectangle belonging to a local alias
     * or normalized-self match.
     */
    public static final class LocalHighlight
    {
        private final long messageId;
        private final Rectangle bounds;
        private final Surface surface;

        private LocalHighlight(
                long messageId,
                Rectangle bounds,
                Surface surface)
        {
            this.messageId = messageId;
            this.bounds = bounds;
            this.surface = surface;
        }

        public long getMessageId()
        {
            return messageId;
        }

        public Rectangle getBounds()
        {
            return bounds;
        }

        public Surface getSurface()
        {
            return surface;
        }
    }

    private static final int MAX_WIDGET_DEPTH = 4;

    /**
     * One physical line produced by RuneScape's wrapped text widget.
     *
     * start/end are semantic plain-text offsets into the complete
     * widget text. Width is the rendered width of this individual
     * visual line.
     */
    private static final class WrappedLine
    {
        private final int start;
        private final int end;
        private final int width;

        private WrappedLine(
                int start,
                int end,
                int width)
        {
            this.start = start;
            this.end = end;
            this.width = width;
        }
    }

    /**
     * Pass-local semantic view of one rendered text Widget.
     *
     * RuneTags captures only the text state which is expensive and unsafe to
     * repeatedly normalize while RuneScape is reconstructing chat rows. Bounds
     * and FontTypeFace deliberately remain live reads during geometry so this
     * optimization cannot freeze transient SPLIT_PRIVATE coordinates.
     */
    private static final class RenderedTextWidget
    {
        private final Widget widget;
        private final String rawText;
        private final String semanticText;

        private RenderedTextWidget(
                Widget widget,
                String rawText,
                String semanticText)
        {
            this.widget = widget;
            this.rawText = rawText;
            this.semanticText = semanticText;
        }
    }

    /**
     * Exact physical CHATBOX sender widget and the sender's semantic start
     * offset inside that widget.
     */
    private static final class RenderedSenderMatch
    {
        private final RenderedTextWidget rendered;
        private final int semanticStart;

        private RenderedSenderMatch(
                RenderedTextWidget rendered,
                int semanticStart)
        {
            this.rendered = rendered;
            this.semanticStart = semanticStart;
        }
    }

    /**
     * Native sender text ownership for one Widget currently colored by
     * RuneTags. Both original and applied strings are retained so a recycled
     * Widget is never restored over newer RuneScape content.
     */
    private static final class FavoriteSenderTextState
    {
        private final String originalText;
        private final String appliedText;

        private FavoriteSenderTextState(
                String originalText,
                String appliedText)
        {
            this.originalText = originalText;
            this.appliedText = appliedText;
        }
    }

    /**
     * This deliberately indexes semantic text only. Widget geometry, hidden state,
     * and FontTypeFace remain live reads so transient RuneScape reconstruction
     * coordinates cannot become authoritative cached state.
     *
     * Candidate lists preserve the collector's native order. The existing
     * sender-confirmed-first/body-fallback ownership rule therefore remains
     * deterministic for duplicate and short messages.
     */
    private static final class RenderedBodyIndex
    {
        private final Map<String, List<RenderedTextWidget>> bodyIndex =
                new HashMap<>();

        private final Map<String, List<RenderedTextWidget>> caseFoldedBodyIndex =
                new HashMap<>();

        private final List<RenderedTextWidget> whitespaceBodies =
                new ArrayList<>();

        private RenderedBodyIndex(
                List<RenderedTextWidget> widgets)
        {
            if (widgets == null)
            {
                return;
            }

            for (RenderedTextWidget rendered : widgets)
            {
                if (rendered == null)
                {
                    continue;
                }

                final String semantic =
                        rendered.semanticText;

                if (semantic == null
                        || semantic.isEmpty())
                {
                    continue;
                }

                if (semantic.trim().isEmpty())
                {
                    whitespaceBodies.add(
                            rendered);

                    continue;
                }

                bodyIndex.computeIfAbsent(
                                semantic,
                                ignored -> new ArrayList<>())
                        .add(
                                rendered);

                caseFoldedBodyIndex.computeIfAbsent(
                                semantic.toLowerCase(
                                        Locale.ROOT),
                                ignored -> new ArrayList<>())
                        .add(
                                rendered);
            }
        }

        private List<RenderedTextWidget> candidates(
                String semanticBody)
        {
            if (semanticBody == null
                    || semanticBody.isEmpty())
            {
                return Collections.emptyList();
            }

            if (semanticBody.trim().isEmpty())
            {
                return whitespaceBodies;
            }

            final List<RenderedTextWidget> candidates =
                    bodyIndex.get(
                            semanticBody);

            return candidates != null
                    ? candidates
                    : Collections.emptyList();
        }

        private List<RenderedTextWidget> candidatesIgnoreCase(
                String semanticBody)
        {
            if (semanticBody == null
                    || semanticBody.isEmpty()
                    || semanticBody.trim().isEmpty())
            {
                return Collections.emptyList();
            }

            final List<RenderedTextWidget> candidates =
                    caseFoldedBodyIndex.get(
                            semanticBody.toLowerCase(
                                    Locale.ROOT));

            return candidates != null
                    ? candidates
                    : Collections.emptyList();
        }
    }

    /**
     * Every indexed candidate is revalidated against live bounds before it can confirm message ownership.
     */
    private static final class RenderedRowIndex
    {
        private final Map<Integer, List<RenderedTextWidget>> rowIndex =
                new HashMap<>();

        private RenderedRowIndex(
                List<RenderedTextWidget> widgets)
        {
            if (widgets == null)
            {
                return;
            }

            for (RenderedTextWidget rendered : widgets)
            {
                if (rendered == null
                        || rendered.widget == null
                        || rendered.widget.isHidden())
                {
                    continue;
                }

                final Rectangle bounds =
                        rendered.widget.getBounds();

                if (bounds == null
                        || bounds.width <= 0
                        || bounds.height <= 0)
                {
                    continue;
                }

                rowIndex.computeIfAbsent(
                                bounds.y,
                                ignored -> new ArrayList<>())
                        .add(
                                rendered);
            }
        }

        private List<RenderedTextWidget> candidates(
                int y)
        {
            final List<RenderedTextWidget> candidates =
                    rowIndex.get(
                            y);

            return candidates != null
                    ? candidates
                    : Collections.emptyList();
        }
    }

    private final Client client;
    private final Configurations config;
    private final TaggedMessageRepository repository;
    private final LocalPlayerRecordService localPlayerRecordService;

    private final Map<Widget, Integer> originalFontIds = new IdentityHashMap<>();

    private final Map<Widget, FavoriteSenderTextState> favoriteSenderTextStates =
            new IdentityHashMap<>();

    /**
     * A reconstructed CHATBOX body can briefly expose a recycled horizontal
     * position before RuneScape finishes positioning the row.
     *
     * Require the same X coordinate twice before accepting a new horizontal
     * position. Vertical movement remains live once that X is accepted.
     */
    private final Map<Long, Integer> acceptedChatboxBodyX =
            new HashMap<>();

    private final Map<Long, Integer> pendingChatboxBodyX =
            new HashMap<>();

    private boolean favoriteSenderRowsDirty = true;
    private long lastFavoriteRevision = Long.MIN_VALUE;
    private boolean lastFavoritesEnabled;
    private int lastFavoriteColorRgb = Integer.MIN_VALUE;

    public ReferenceLayoutService(
            Client client,
            Configurations config,
            TaggedMessageRepository repository,
            LocalPlayerRecordService localPlayerRecordService)
    {
        this.client = client;
        this.config = config;
        this.repository = repository;
        this.localPlayerRecordService =
                localPlayerRecordService;
    }

    /**
     * Synchronize RuneTags mention fonts against the final physical chat widgets
     * after RuneScape has completed its clientscript reconstruction for the
     * current client tick.
     *
     * Font mutation deliberately does not occur from the render overlays.
     *
     * One synchronization pass:
     *
     * - restores any physical Widget RuneTags previously modified;
     * - resolves current semantic-message -> physical-widget ownership;
     * - reapplies the configured font only to messages which currently own those
     *   final rendered Widgets.
     *
     * Because restoration and reapplication occur synchronously in one
     * PostClientTick callback, RuneScape cannot render the temporary native-font
     * state between those operations.
     */
    public void syncMentionFonts()
    {
        /*
         * Physical Widget instances are recycled by RuneScape. Always restore
         * native ownership before resolving the current live rows.
         */
        restoreAllOriginalFonts();

        /*
         * Always resolve the final physical rows on a dirty synchronization.
         *
         * Even when the configured font is NORMAL, RuneScape may have expanded a
         * recognized player-name macro after MessageFormatter ran. Those expanded
         * spans carry native foreground/underline markup which must be reconciled
         * with RuneTags' independent appearance controls.
         */
        final List<TaggedMessage> messages =
                new ArrayList<>(
                        repository.snapshot());

        Collections.reverse(
                messages);

        syncSurfaceFonts(
                client.getWidget(
                        InterfaceID.PmChat.CONTAINER),
                Surface.SPLIT_PRIVATE,
                messages);

        syncSurfaceFonts(
                client.getWidget(
                        InterfaceID.Chatbox.SCROLLAREA),
                Surface.CHATBOX,
                messages);
    }

    /**
     * Mark normal-chatbox sender presentation dirty after native row
     * reconstruction. FontLayoutService calls this alongside font dirtiness
     * so Favorite color ownership follows the same settled PostClientTick cadence.
     */
    public void markFavoriteSenderRowsDirty()
    {
        favoriteSenderRowsDirty = true;
    }

    /**
     * Synchronize Favorite sender-name colors only when either native rows,
     * Favorite state, or the Favorites config changed.
     *
     * This cheap gate is safe to call every PostClientTick. The expensive
     * semantic/widget association runs only when presentation is actually dirty.
     */
    public void syncFavoriteSenderColorsIfNeeded()
    {
        final long favoriteRevision =
                localPlayerRecordService != null
                        ? localPlayerRecordService.getFavoriteRevision()
                        : 0L;

        final boolean favoritesEnabled =
                config.showFavorites();

        final Color favoriteColor =
                configuredFavoriteColor();

        final int favoriteColorRgb =
                favoriteColor.getRGB();

        if (!favoriteSenderRowsDirty
                && favoriteRevision == lastFavoriteRevision
                && favoritesEnabled == lastFavoritesEnabled
                && favoriteColorRgb == lastFavoriteColorRgb)
        {
            return;
        }

        syncFavoriteSenderColors(
                favoritesEnabled);

        favoriteSenderRowsDirty = false;
        lastFavoriteRevision = favoriteRevision;
        lastFavoritesEnabled = favoritesEnabled;
        lastFavoriteColorRgb = favoriteColorRgb;
    }

    /**
     * Restore every native sender Widget currently owned by Favorite styling.
     *
     * The applied text is compared before restoration. RuneScape can recycle a
     * Widget for a newer row at any time; if that has already happened, RuneTags
     * must never write the stale original text back over the new message.
     */
    public void restoreFavoriteSenderColors()
    {
        restoreOriginalFavoriteSenderTexts();

        favoriteSenderRowsDirty = true;
        lastFavoriteRevision = Long.MIN_VALUE;
        lastFavoritesEnabled = false;
        lastFavoriteColorRgb = Integer.MIN_VALUE;
    }

    private void syncFavoriteSenderColors(
            boolean favoritesEnabled)
    {
        restoreOriginalFavoriteSenderTexts();

        if (!favoritesEnabled
                || localPlayerRecordService == null)
        {
            return;
        }

        final List<TaggedMessage> messages =
                new ArrayList<>(
                        repository.snapshot());

        if (messages.isEmpty())
        {
            return;
        }

        Collections.reverse(
                messages);

        final Widget chatbox =
                client.getWidget(
                        InterfaceID.Chatbox.SCROLLAREA);

        if (chatbox == null
                || chatbox.isHidden())
        {
            return;
        }

        final List<RenderedTextWidget> textWidgets =
                new ArrayList<>();

        final Set<Widget> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        /*
         * Use a null viewport just like font synchronization. Retained off-screen
         * rows can later scroll into view without native reconstruction, so their
         * Favorite sender color must already be correct.
         */
        collectRenderedTextWidgets(
                chatbox,
                Surface.CHATBOX,
                null,
                textWidgets,
                visited,
                0);

        if (textWidgets.isEmpty())
        {
            return;
        }

        final RenderedBodyIndex bodyIndex =
                new RenderedBodyIndex(
                        textWidgets);

        final RenderedRowIndex rowIndex =
                new RenderedRowIndex(
                        textWidgets);

        final Set<Widget> usedBodyWidgets =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        for (TaggedMessage message : messages)
        {
            if (message == null
                    || message.getCanonicalSender() == null
                    || message.getCanonicalSender().trim().isEmpty())
            {
                continue;
            }

            /*
             * Resolve and reserve body ownership for EVERY retained message, not
             * only Favorites. Duplicate/short message bodies can otherwise let a
             * Favorite fall back onto a row that semantically belongs to an
             * earlier non-Favorite message. This keeps Favorite presentation on
             * exactly the same deterministic ownership path as normal chat
             * reference geometry.
             */
            final RenderedTextWidget messageWidget =
                    findRenderedWidgetForMessageIndexedForFont(
                            message,
                            textWidgets,
                            bodyIndex,
                            rowIndex,
                            usedBodyWidgets);

            if (messageWidget == null
                    || messageWidget.widget == null)
            {
                continue;
            }

            usedBodyWidgets.add(
                    messageWidget.widget);

            if (!localPlayerRecordService.isFavorite(
                    message.getCanonicalSender()))
            {
                continue;
            }

            final RenderedSenderMatch senderMatch =
                    findChatboxSender(
                            messageWidget,
                            message.getCanonicalSender(),
                            textWidgets,
                            rowIndex);

            if (senderMatch == null)
            {
                continue;
            }

            applyFavoriteSenderColor(
                    senderMatch,
                    message.getCanonicalSender());
        }
    }

    private void applyFavoriteSenderColor(
            RenderedSenderMatch senderMatch,
            String sender)
    {
        if (senderMatch == null
                || senderMatch.rendered == null
                || senderMatch.rendered.widget == null
                || sender == null
                || sender.isEmpty())
        {
            return;
        }

        final String rawText =
                senderMatch.rendered.rawText;

        final String semanticText =
                senderMatch.rendered.semanticText;

        if (rawText == null
                || semanticText == null)
        {
            return;
        }

        final int semanticEnd =
                senderMatch.semanticStart
                        + sender.length();

        if (semanticEnd > semanticText.length())
        {
            return;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawText);

        if (!map.matchesPlain(
                semanticText))
        {
            return;
        }

        final int rawStart =
                map.rawBoundary(
                        senderMatch.semanticStart);

        final int rawEnd =
                map.rawBoundary(
                        semanticEnd);

        if (rawStart < 0
                || rawEnd <= rawStart
                || rawEnd > rawText.length())
        {
            return;
        }

        final String appliedText =
                rawText.substring(
                        0,
                        rawStart)
                        + "<col="
                        + colorHex(
                        configuredFavoriteColor())
                        + ">"
                        + rawText.substring(
                        rawStart,
                        rawEnd)
                        + "</col>"
                        + rawText.substring(
                        rawEnd);

        if (appliedText.equals(
                rawText))
        {
            return;
        }

        senderMatch.rendered.widget.setText(
                appliedText);

        favoriteSenderTextStates.put(
                senderMatch.rendered.widget,
                new FavoriteSenderTextState(
                        rawText,
                        appliedText));
    }

    private Color configuredFavoriteColor()
    {
        final Color configured =
                config.favoriteColor();

        return configured != null
                ? configured
                : new Color(255, 205, 70);
    }

    private void restoreOriginalFavoriteSenderTexts()
    {
        if (favoriteSenderTextStates.isEmpty())
        {
            return;
        }

        for (Map.Entry<Widget, FavoriteSenderTextState> entry
                : favoriteSenderTextStates.entrySet())
        {
            final Widget widget =
                    entry.getKey();

            final FavoriteSenderTextState state =
                    entry.getValue();

            if (widget == null
                    || state == null
                    || state.appliedText == null)
            {
                continue;
            }

            final String currentText =
                    widget.getText();

            if (state.appliedText.equals(
                    currentText))
            {
                widget.setText(
                        state.originalText);
            }
        }

        favoriteSenderTextStates.clear();
    }

    /**
     * Reconcile RuneScape's post-construction player-name macro styling with
     * RuneTags' independent appearance controls.
     *
     * MessageFormatter runs on the ChatMessage/MessageNode before native chat-row
     * reconstruction. RuneScape can subsequently canonicalize a recognized player
     * name and inject an inner span such as:
     *
     *     <col=ffffff><u>Santa</u><col=ff0000>
     *
     * That inner native color wins over RuneTags' outer Self/Others Mention color.
     * It can also reintroduce an underline even when Underline Mentions is disabled.
     *
     * This repair runs only after native construction has settled at PostClientTick.
     * Visible characters are never changed; only the native macro's effective color
     * and underline wrapper are reconciled.
     */
    private void repairExpandedReferenceStyles(
            Widget messageWidget,
            TaggedMessage message)
    {
        if (messageWidget == null
                || message == null
                || message.getReferences() == null
                || message.getReferences().isEmpty())
        {
            return;
        }

        final String rawWidgetText =
                messageWidget.getText();

        if (rawWidgetText == null
                || rawWidgetText.isEmpty())
        {
            return;
        }

        final String semanticWidgetText =
                ChatText.toSemanticPlain(
                        rawWidgetText);

        final String originalMessage =
                message.getOriginalMessage();

        if (semanticWidgetText == null
                || originalMessage == null
                || originalMessage.isEmpty())
        {
            return;
        }

        int messageStart =
                semanticWidgetText.indexOf(
                        originalMessage);

        if (messageStart < 0
                && isPrivateMessage(
                message))
        {
            messageStart =
                    indexOfIgnoreCase(
                            semanticWidgetText,
                            originalMessage);
        }

        if (messageStart < 0)
        {
            return;
        }

        final String localPlayerName =
                client.getLocalPlayer() != null
                        ? client.getLocalPlayer().getName()
                        : null;

        String repaired =
                rawWidgetText;

        for (PlayerReference reference
                : message.getReferences())
        {
            if (reference == null)
            {
                continue;
            }

            final int semanticStart =
                    messageStart
                            + reference.getStartOffset();

            final int semanticEnd =
                    messageStart
                            + reference.getEndOffset();

            if (semanticStart < 0
                    || semanticEnd <= semanticStart
                    || semanticEnd > semanticWidgetText.length())
            {
                continue;
            }

            String renderedToken =
                    semanticWidgetText.substring(
                            semanticStart,
                            semanticEnd);

            if (renderedToken.startsWith("@"))
            {
                renderedToken =
                        renderedToken.substring(1);
            }

            if (renderedToken.isEmpty())
            {
                continue;
            }

            final boolean isSelf =
                    samePlayerName(
                            reference.getLookupName(),
                            localPlayerName)
                            || samePlayerName(
                            renderedToken,
                            localPlayerName);

            final boolean shouldColor =
                    isSelf
                            ? config.mentionSelf()
                            : config.mentionOthers();

            final Color desiredColor =
                    isSelf
                            ? config.selfMentionColor()
                            : config.otherMentionColor();

            /*
             * RuneScape's expanded player-name wrapper is deliberately targeted
             * rather than stripping arbitrary markup from the complete message.
             *
             * The third color is the native restore color and must be preserved.
             */
            final Pattern expandedNamePattern =
                    Pattern.compile(
                            "<col=([0-9a-fA-F]{6})><u>("
                                    + Pattern.quote(
                                    renderedToken)
                                    + ")</u><col=([0-9a-fA-F]{6})>",
                            Pattern.CASE_INSENSITIVE);

            final Matcher matcher =
                    expandedNamePattern.matcher(
                            repaired);

            if (!matcher.find())
            {
                continue;
            }

            final String openingColor =
                    shouldColor
                            && desiredColor != null
                            ? colorHex(
                            desiredColor)
                            : matcher.group(1);

            final String displayedName =
                    matcher.group(2);

            final String restoreColor =
                    matcher.group(3);

            final String replacement =
                    "<col="
                            + openingColor
                            + ">"
                            + (config.underlineMentions()
                            ? "<u>"
                            : "")
                            + displayedName
                            + (config.underlineMentions()
                            ? "</u>"
                            : "")
                            + "<col="
                            + restoreColor
                            + ">";

            repaired =
                    matcher.replaceFirst(
                            Matcher.quoteReplacement(
                                    replacement));
        }

        if (!repaired.equals(
                rawWidgetText))
        {
            messageWidget.setText(
                    repaired);
        }
    }

    private static String colorHex(
            Color color)
    {
        return String.format(
                Locale.ROOT,
                "%06x",
                color.getRGB()
                        & 0xFFFFFF);
    }

    private static boolean samePlayerName(
            String left,
            String right)
    {
        if (left == null
                || right == null)
        {
            return false;
        }

        return playerNameKey(
                left).equals(
                playerNameKey(
                        right));
    }

    private static String playerNameKey(
            String value)
    {
        return value
                .replace('\u00A0', ' ')
                .replace('_', ' ')
                .trim()
                .toLowerCase(
                        Locale.ROOT);
    }

    /**
     * Restore every physical chat font currently owned by RuneTags.
     *
     * Used when the plugin is shutting down so RuneScape is never left displaying
     * a RuneTags FontId after the plugin has been disabled.
     */
    public void restoreMentionFonts()
    {
        restoreAllOriginalFonts();
    }

    /**
     * Resolve semantic message ownership for one physical surface and synchronize
     * post-reconstruction presentation.
     *
     * RuneScape may expand recognized player names after MessageFormatter runs.
     * Reconcile that native expansion first, then apply the configured Widget font.
     * This intentionally does not calculate reference hitboxes or sender geometry;
     * those remain render-time responsibilities.
     */
    private void syncSurfaceFonts(
            Widget surfaceWidget,
            Surface surface,
            List<TaggedMessage> messages)
    {
        if (surfaceWidget == null
                || surfaceWidget.isHidden()
                || messages == null
                || messages.isEmpty())
        {
            return;
        }

        final List<RenderedTextWidget> textWidgets =
                new ArrayList<>();

        final Set<Widget> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        collectRenderedTextWidgets(
                surfaceWidget,
                surface,
                null,
                textWidgets,
                visited,
                0);

        final RenderedBodyIndex bodyIndex =
                new RenderedBodyIndex(
                        textWidgets);

        final RenderedRowIndex rowIndex =
                new RenderedRowIndex(
                        textWidgets);

        if (textWidgets.isEmpty())
        {
            return;
        }

        final Set<Widget> usedWidgets =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        for (TaggedMessage message : messages)
        {
            if (message == null)
            {
                continue;
            }

            if (surface == Surface.SPLIT_PRIVATE
                    && !isPrivateMessage(
                    message))
            {
                continue;
            }

            final RenderedTextWidget messageWidget =
                    findRenderedWidgetForMessageIndexedForFont(
                            message,
                            textWidgets,
                            bodyIndex,
                            rowIndex,
                            usedWidgets);

            if (messageWidget == null
                    || messageWidget.widget == null)
            {
                continue;
            }

            usedWidgets.add(
                    messageWidget.widget);

            repairExpandedReferenceStyles(
                    messageWidget.widget,
                    message);

            applyMentionFont(
                    messageWidget.widget,
                    message);
        }
    }

    /**
     * Resolve font ownership from the pass-local semantic body index.
     *
     * Sender confirmation uses the advisory row index. Exact whole-body matching
     * remains primary; private chat receives a case-insensitive whole-body retry
     * only when exact ownership produces no candidate.
     *
     * Candidate geometry is always re-read live, and failed indexed sender
     * confirmation falls back to the complete semantic Widget list.
     */
    private RenderedTextWidget findRenderedWidgetForMessageIndexedForFont(
            TaggedMessage message,
            List<RenderedTextWidget> widgets,
            RenderedBodyIndex bodyIndex,
            RenderedRowIndex rowIndex,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || widgets == null
                || bodyIndex == null
                || rowIndex == null
                || usedWidgets == null)
        {
            return null;
        }

        final String needle =
                message.getOriginalMessage();

        if (needle == null
                || needle.isEmpty())
        {
            return null;
        }

        final RenderedTextWidget exactMatch =
                selectRenderedFontBodyCandidate(
                        message,
                        bodyIndex.candidates(
                                needle),
                        rowIndex,
                        widgets,
                        usedWidgets);

        if (exactMatch != null
                || !isPrivateMessage(
                message))
        {
            return exactMatch;
        }

        return selectRenderedFontBodyCandidate(
                message,
                bodyIndex.candidatesIgnoreCase(
                        needle),
                rowIndex,
                widgets,
                usedWidgets);
    }

    /**
     * Select one unused indexed body candidate using sender-confirmed-first,
     * body-fallback ownership.
     *
     * Candidate lists preserve native collector order so duplicate bodies remain
     * deterministic. The advisory row index narrows sender confirmation while
     * live geometry and the complete Widget list remain the correctness fallback.
     */
    private RenderedTextWidget selectRenderedFontBodyCandidate(
            TaggedMessage message,
            List<RenderedTextWidget> candidates,
            RenderedRowIndex rowIndex,
            List<RenderedTextWidget> allWidgets,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || candidates == null
                || candidates.isEmpty()
                || rowIndex == null
                || allWidgets == null
                || usedWidgets == null)
        {
            return null;
        }

        RenderedTextWidget bodyFallback =
                null;

        for (RenderedTextWidget rendered : candidates)
        {
            if (rendered == null
                    || rendered.widget == null
                    || usedWidgets.contains(
                    rendered.widget)
                    || rendered.widget.isHidden())
            {
                continue;
            }

            if (bodyFallback == null)
            {
                bodyFallback =
                        rendered;
            }

            if (hasRenderedSenderOnRow(
                    rendered,
                    message,
                    rowIndex,
                    allWidgets))
            {
                return rendered;
            }
        }

        return bodyFallback;
    }

    /**
     * Rebuild all currently rendered reference hitboxes.
     *
     * RuneTags does not need to query RuneScape's Split Private Chat setting
     * directly. Instead, the actual populated widget surfaces are the source
     * of truth.
     *
     * SPLIT_PRIVATE is checked first because it represents the immediately
     * visible floating private-chat presentation. CHATBOX is then processed
     * independently so the same private message may also remain interactive
     * in the selected Private chat tab.
     */
    public LayoutResult layout()
    {
        /*
         * Font ownership is synchronized once after native chat reconstruction.
         *
         * Render-time layout is read-only with respect to FontId.
         */
        final List<TaggedMessage> messages =
                new ArrayList<>(
                        repository.snapshot());

        pruneChatboxBodyXState(messages);

        final List<ReferenceHitbox> hitboxes =
                new ArrayList<>();

        final List<LocalHighlight> localHighlights =
                new ArrayList<>();

        if (messages.isEmpty())
        {
            return new LayoutResult(
                    hitboxes,
                    localHighlights);
        }

        /*
         * Visible RuneScape chat is newest-first, so search semantic messages
         * in the same direction.
         */
        Collections.reverse(
                messages);

        /*
         * Local alias/normalized-self geometry is calculated only when it can
         * actually be painted.
         */
        final boolean includeLocalHighlights =
                shouldLayoutLocalHighlights();

        /*
         * Split Private and normal CHATBOX remain independent physical surfaces.
         *
         * One TaggedMessage may legitimately resolve on both.
         */
        layoutSurface(
                client.getWidget(
                        InterfaceID.PmChat.CONTAINER),
                Surface.SPLIT_PRIVATE,
                messages,
                hitboxes,
                localHighlights,
                includeLocalHighlights);

        layoutSurface(
                client.getWidget(
                        InterfaceID.Chatbox.SCROLLAREA),
                Surface.CHATBOX,
                messages,
                hitboxes,
                localHighlights,
                includeLocalHighlights);

        return new LayoutResult(
                hitboxes,
                localHighlights);
    }

    /**
     * Layout semantic messages against one physical RuneScape chat surface.
     *
     * Widget ownership is unique only within this surface pass. The same
     * TaggedMessage may therefore resolve independently against another
     * physical surface.
     */
    private void layoutSurface(
            Widget surfaceWidget,
            Surface surface,
            List<TaggedMessage> messages,
            List<ReferenceHitbox> output,
            List<LocalHighlight> localHighlights,
            boolean includeLocalHighlights)
    {
        if (surfaceWidget == null
                || surfaceWidget.isHidden())
        {
            return;
        }

        final List<RenderedTextWidget> textWidgets =
                new ArrayList<>();

        Rectangle visibleBounds =
                null;

        /*
         * Render-time CHATBOX layout only needs physical rows which
         * intersect CHATBOX_MESSAGE_LINES. Cull fully off-screen history rows
         * before semantic normalization/indexing.
         *
         * SPLIT_PRIVATE is intentionally left unculled here because RuneScape
         * already exposes only the populated floating PM rows. Font synchronization
         * also remains unchanged and continues to inspect the complete CHATBOX
         * surface so scrolling can reveal retained rows without reconstruction.
         */
        if (surface == Surface.CHATBOX)
        {
            visibleBounds =
                    surfaceWidget.getBounds();

            if (visibleBounds == null
                    || visibleBounds.width <= 0
                    || visibleBounds.height <= 0)
            {
                return;
            }
        }

        final Set<Widget> visited =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        collectRenderedTextWidgets(
                surfaceWidget,
                surface,
                visibleBounds,
                textWidgets,
                visited,
                0);

        if (textWidgets.isEmpty())
        {
            return;
        }

        final RenderedBodyIndex bodyIndex =
                new RenderedBodyIndex(
                        textWidgets);

        /*
         * Reuse the advisory Y-row index for both:
         * - sender confirmation during body ownership; and
         * - actual sender hitbox candidate selection.
         *
         * Geometry remains live and every indexed lookup retains the complete
         * semantic Widget list as a correctness fallback.
         */
        final RenderedRowIndex rowIndex =
                new RenderedRowIndex(
                        textWidgets);

        /*
         * One physical text widget may represent only one TaggedMessage
         * during this surface pass.
         *
         * This is intentionally surface-local. A semantic message may have a
         * separate physical widget on another surface.
         */
        final Set<Widget> usedWidgets =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        for (TaggedMessage message : messages)
        {
            if (message == null)
            {
                continue;
            }

            /*
             * PmChat is specifically the floating split-private interface.
             *
             * Restrict semantic matching on this surface to actual private
             * message types so identical public/clan/etc. text cannot claim a
             * PmChat widget.
             */
            if (surface == Surface.SPLIT_PRIVATE
                    && !isPrivateMessage(message))
            {
                continue;
            }

            final RenderedTextWidget messageWidget =
                    findRenderedWidgetForMessage(
                            message,
                            textWidgets,
                            bodyIndex,
                            rowIndex,
                            usedWidgets);

            if (messageWidget == null)
            {
                continue;
            }

            usedWidgets.add(
                    messageWidget.widget);

            /*
             * RuneScape may recycle a CHATBOX body Widget and update its text before
             * completing that row's horizontal positioning.
             *
             * Do not publish geometry from a new X position until RuneScape exposes
             * that same X on two consecutive layout passes.
             */
            if (surface == Surface.CHATBOX
                    && !isChatboxBodyGeometryStable(
                    message,
                    messageWidget))
            {
                continue;
            }

            /*
             * Body references and sender interaction are deliberately laid
             * out independently.
             *
             * A message may contain no @tag or recognized mention and still
             * need a clickable SENDER when Clickable Players = ALL.
             */
            layoutMessage(
                    messageWidget,
                    message,
                    surface,
                    output);

            layoutSender(
                    messageWidget,
                    message,
                    surface,
                    textWidgets,
                    rowIndex,
                    output);

            /*
             * Reuse the same semantic -> physical Widget ownership for non-clickable
             * Unique Highlight / normalized-self backgrounds.
             */
            if (includeLocalHighlights)
            {
                layoutLocalHighlight(
                        messageWidget,
                        message,
                        surface,
                        localHighlights);
            }
        }
    }

    /**
     * Apply the configured font to a physical message body containing a RuneTags
     * mention.
     *
     * Jagex font selection belongs to the complete Widget, unlike color,
     * underline, and shadow markup which can be applied to individual spans.
     *
     * The original font ID is retained before RuneTags changes it so NORMAL can
     * restore the widget exactly instead of assuming every chat surface uses the
     * same default font.
     */
    private void applyMentionFont(
            Widget messageWidget,
            TaggedMessage message)
    {
        if (messageWidget == null
                || message == null)
        {
            return;
        }

        /*
         * Font treatment belongs only to messages RuneTags actually recognizes
         * as containing a mention/highlight. Sender-only interaction must not
         * change an otherwise ordinary chat message.
         */
        final boolean hasPlayerReference =
                message.getReferences() != null
                        && !message.getReferences().isEmpty();

        final boolean hasLocalMention =
                message.getLocalMentionMatch() != null
                        && message.getLocalMentionMatch()
                        .isMatchesLocalPlayer();

        if (!hasPlayerReference
                && !hasLocalMention)
        {
            restoreOriginalFont(
                    messageWidget);

            return;
        }

        final MentionFont mentionFont =
                config.fontMentions();

        if (mentionFont == null
                || mentionFont == MentionFont.NORMAL)
        {
            restoreOriginalFont(
                    messageWidget);

            return;
        }

        /*
         * Remember RuneScape's actual font before RuneTags mutates this widget.
         * IdentityHashMap ensures the physical Widget instance itself is the key.
         */
        originalFontIds.putIfAbsent(
                messageWidget,
                messageWidget.getFontId());

        final int fontId;

        switch (mentionFont)
        {
            case BOLD:
                fontId = FontID.BOLD_12;
                break;

            case VERDANA:
                fontId = FontID.VERDANA_13_BOLD;
                break;

            case NORMAL:
            default:
                restoreOriginalFont(
                        messageWidget);

                return;
        }

        if (messageWidget.getFontId() != fontId)
        {
            messageWidget.setFontId(
                    fontId);
        }
    }

    /**
     * Restore the font a physical RuneScape widget had before RuneTags changed
     * it.
     */
    private void restoreOriginalFont(
            Widget widget)
    {
        if (widget == null)
        {
            return;
        }

        final Integer originalFontId =
                originalFontIds.remove(
                        widget);

        if (originalFontId == null)
        {
            return;
        }

        if (widget.getFontId()
                != originalFontId)
        {
            widget.setFontId(
                    originalFontId);
        }
    }

    /**
     * Restore every physical Widget previously modified by RuneTags.
     *
     * RuneScape recycles chat widgets as messages shift and chat surfaces are
     * reconstructed. A Widget which represented a mention during the previous
     * pass may represent an unrelated message during the current pass.
     *
     * Resetting all tracked widgets before current semantic ownership is resolved
     * prevents mention fonts from following the physical row instead of the
     * TaggedMessage.
     */
    private void restoreAllOriginalFonts()
    {
        if (originalFontIds.isEmpty())
        {
            return;
        }

        final Map<Widget, Integer> fontsToRestore =
                new IdentityHashMap<>(
                        originalFontIds);

        originalFontIds.clear();

        for (Map.Entry<Widget, Integer> entry
                : fontsToRestore.entrySet())
        {
            final Widget widget =
                    entry.getKey();

            final Integer originalFontId =
                    entry.getValue();

            if (widget == null
                    || originalFontId == null)
            {
                continue;
            }

            if (widget.getFontId()
                    != originalFontId)
            {
                widget.setFontId(
                        originalFontId);
            }
        }
    }

    private RenderedTextWidget findRenderedWidgetForMessage(
            TaggedMessage message,
            List<RenderedTextWidget> widgets,
            RenderedBodyIndex bodyIndex,
            RenderedRowIndex rowIndex,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || widgets == null
                || bodyIndex == null
                || rowIndex == null
                || usedWidgets == null)
        {
            return null;
        }

        final String needle =
                message.getOriginalMessage();

        if (needle == null
                || needle.isEmpty())
        {
            return null;
        }

        final RenderedTextWidget exactMatch =
                selectRenderedBodyCandidate(
                        message,
                        bodyIndex.candidates(
                                needle),
                        rowIndex,
                        widgets,
                        usedWidgets);

        if (exactMatch != null
                || !isPrivateMessage(message)
                || needle.trim().isEmpty())
        {
            return exactMatch;
        }

        /*
         * Private-only compatibility fallback.
         *
         * The case-folded index narrows lookup to Widgets whose complete semantic
         * body differs only by character case. Candidate order remains native
         * collection order, and normal sender-confirmed/body-fallback selection is
         * reused unchanged.
         */
        return selectRenderedBodyCandidate(
                message,
                bodyIndex.candidatesIgnoreCase(
                        needle),
                rowIndex,
                widgets,
                usedWidgets);
    }

    /**
     * Select one unused exact-body candidate while preserving RuneTags'
     * sender-confirmed-first and deterministic body-only fallback behavior.
     */
    private RenderedTextWidget selectRenderedBodyCandidate(
            TaggedMessage message,
            List<RenderedTextWidget> candidates,
            RenderedRowIndex rowIndex,
            List<RenderedTextWidget> allWidgets,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || candidates == null
                || candidates.isEmpty()
                || rowIndex == null
                || allWidgets == null
                || usedWidgets == null)
        {
            return null;
        }

        RenderedTextWidget bodyFallback =
                null;

        for (RenderedTextWidget rendered : candidates)
        {
            if (rendered == null
                    || rendered.widget == null
                    || usedWidgets.contains(
                    rendered.widget)
                    || rendered.widget.isHidden())
            {
                continue;
            }

            if (bodyFallback == null)
            {
                bodyFallback =
                        rendered;
            }

            if (hasRenderedSenderOnRow(
                    rendered,
                    message,
                    rowIndex,
                    allWidgets))
            {
                return rendered;
            }
        }

        return bodyFallback;
    }

    /**
     * Confirm sender ownership using same-row candidates when possible.
     *
     * Candidate bounds are re-read live. If the advisory row index cannot confirm
     * the sender, fall back to the complete semantic Widget list so transient
     * reconstruction cannot invalidate ownership.
     */
    private boolean hasRenderedSenderOnRow(
            RenderedTextWidget messageWidget,
            TaggedMessage message,
            RenderedRowIndex rowIndex,
            List<RenderedTextWidget> widgets)
    {
        if (messageWidget == null
                || messageWidget.widget == null
                || message == null
                || rowIndex == null
                || widgets == null)
        {
            return false;
        }

        final Rectangle messageBounds =
                messageWidget.widget.getBounds();

        final String sender =
                message.getCanonicalSender();

        if (messageBounds == null
                || messageBounds.width <= 0
                || messageBounds.height <= 0
                || sender == null
                || sender.isEmpty())
        {
            return false;
        }

        final List<RenderedTextWidget> indexedCandidates =
                rowIndex.candidates(
                        messageBounds.y);

        if (hasRenderedSenderInCandidates(
                messageWidget,
                messageBounds,
                sender,
                indexedCandidates))
        {
            return true;
        }

        /*
         * The row index is advisory only. If RuneScape moved a sender Widget after
         * index construction, fall back to the complete semantic Widget list.
         */
        return hasRenderedSenderInCandidates(
                messageWidget,
                messageBounds,
                sender,
                widgets);
    }

    private boolean hasRenderedSenderInCandidates(
            RenderedTextWidget messageWidget,
            Rectangle messageBounds,
            String sender,
            List<RenderedTextWidget> candidates)
    {
        if (messageWidget == null
                || messageWidget.widget == null
                || messageBounds == null
                || sender == null
                || sender.isEmpty()
                || candidates == null
                || candidates.isEmpty())
        {
            return false;
        }

        for (RenderedTextWidget candidate : candidates)
        {
            if (candidate == null
                    || candidate.widget == null
                    || candidate.widget == messageWidget.widget
                    || candidate.widget.isHidden())
            {
                continue;
            }

            final Rectangle candidateBounds =
                    candidate.widget.getBounds();

            if (candidateBounds == null
                    || candidateBounds.width <= 0
                    || candidateBounds.height <= 0)
            {
                continue;
            }

            /*
             * Revalidate the row and left-of-body ownership rule against live
             * geometry. The advisory index never overrides current coordinates.
             */
            if (candidateBounds.y != messageBounds.y
                    || candidateBounds.x > messageBounds.x)
            {
                continue;
            }

            if (indexOfName(
                    candidate.semanticText,
                    sender) >= 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolve the physical message-body widget for one semantic TaggedMessage.
     *
     * This exposes the same sender-aware association used by clickable reference
     * layout so other RuneTags rendering layers do not maintain a second,
     * potentially divergent widget-matching implementation.
     *
     * Widget ownership remains local to the caller's physical surface pass through
     * the supplied usedWidgets set.
     */
    public Widget findRenderedMessageWidget(
            TaggedMessage message,
            List<Widget> widgets,
            Set<Widget> usedWidgets)
    {
        if (message == null
                || widgets == null
                || usedWidgets == null)
        {
            return null;
        }

        return findWidgetForMessage(
                message,
                widgets,
                usedWidgets);
    }

    /**
     * Find the rendered widget containing the semantic message body.
     *
     * The known-good live matcher remains authoritative. Private chat gets one
     * narrow compatibility retry because RuneScape/RuneLite may canonicalize the
     * local account-name casing after the ChatMessage was recorded
     * (for example, "@santa" -> "@Santa").
     */
    private Widget findWidgetForMessage(
            TaggedMessage message,
            List<Widget> widgets,
            Set<Widget> usedWidgets)
    {
        final Widget exactMatch =
                findWidgetForMessage(
                        message,
                        widgets,
                        usedWidgets,
                        false);

        if (exactMatch != null
                || !isPrivateMessage(message))
        {
            return exactMatch;
        }

        /*
         * Private-only fallback. This remains an exact whole-body comparison;
         * character case is the only allowed difference.
         */
        return findWidgetForMessage(
                message,
                widgets,
                usedWidgets,
                true);
    }

    private Widget findWidgetForMessage(
            TaggedMessage message,
            List<Widget> widgets,
            Set<Widget> usedWidgets,
            boolean ignoreCase)
    {
        if (message == null
                || widgets == null
                || usedWidgets == null)
        {
            return null;
        }

        final String needle =
                message.getOriginalMessage();

        if (needle == null
                || needle.isEmpty())
        {
            return null;
        }

        final boolean whitespaceOnly =
                needle.trim().isEmpty();

        Widget bodyFallback =
                null;

        for (Widget widget : widgets)
        {
            if (widget == null
                    || usedWidgets.contains(widget)
                    || widget.isHidden())
            {
                continue;
            }

            final String raw =
                    widget.getText();

            if (raw == null
                    || raw.isEmpty())
            {
                continue;
            }

            final String semantic =
                    ChatText.toSemanticPlain(
                            raw);

            final boolean bodyMatches;

            if (whitespaceOnly)
            {
                bodyMatches =
                        semantic != null
                                && !semantic.isEmpty()
                                && semantic.trim().isEmpty();
            }
            else if (ignoreCase)
            {
                bodyMatches =
                        semantic != null
                                && semantic.equalsIgnoreCase(
                                needle);
            }
            else
            {
                bodyMatches =
                        semantic != null
                                && semantic.equals(
                                needle);
            }

            if (!bodyMatches)
            {
                continue;
            }

            if (bodyFallback == null)
            {
                bodyFallback =
                        widget;
            }

            if (hasSenderOnRow(
                    widget,
                    message,
                    widgets))
            {
                return widget;
            }
        }

        return bodyFallback;
    }

    /**
     * Verify that a candidate message body shares its rendered row with the
     * expected TaggedMessage sender.
     *
     * This disambiguates whitespace-only message bodies without treating every
     * ordinary widget containing a space as a match.
     */
    private boolean hasSenderOnRow(
            Widget messageWidget,
            TaggedMessage message,
            List<Widget> widgets)
    {
        if (messageWidget == null
                || message == null
                || widgets == null)
        {
            return false;
        }

        final Rectangle messageBounds =
                messageWidget.getBounds();

        final String sender =
                message.getCanonicalSender();

        if (messageBounds == null
                || sender == null
                || sender.isEmpty())
        {
            return false;
        }

        for (Widget candidate : widgets)
        {
            if (candidate == null
                    || candidate == messageWidget
                    || candidate.isHidden())
            {
                continue;
            }

            final Rectangle candidateBounds =
                    candidate.getBounds();

            if (candidateBounds == null
                    || candidateBounds.width <= 0
                    || candidateBounds.height <= 0)
            {
                continue;
            }

            /*
             * Sender and body must occupy the same rendered chat row.
             */
            if (candidateBounds.y
                    != messageBounds.y)
            {
                continue;
            }

            /*
             * The sender begins at or to the left of the message body.
             */
            if (candidateBounds.x
                    > messageBounds.x)
            {
                continue;
            }

            final String rawCandidateText =
                    candidate.getText();

            if (rawCandidateText == null
                    || rawCandidateText.isEmpty())
            {
                continue;
            }

            final String semanticCandidateText =
                    ChatText.toSemanticPlain(
                            rawCandidateText);

            if (indexOfName(
                    semanticCandidateText,
                    sender) >= 0)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Layout explicit @tags and recognized ordinary-name mentions inside the
     * semantic message body.
     *
     * A RuneScape chat message may occupy multiple visual rows while remaining
     * one Widget. Reference geometry must therefore be resolved against the
     * widget's actual wrapping rather than treating the complete text as one
     * horizontal line.
     *
     * One PlayerReference may legitimately produce more than one physical
     * ReferenceHitbox if the reference itself crosses a visual line break.
     */
    private void layoutMessage(
            RenderedTextWidget renderedWidget,
            TaggedMessage message,
            Surface surface,
            List<ReferenceHitbox> output)
    {
        final Widget widget =
                renderedWidget != null
                        ? renderedWidget.widget
                        : null;

        if (widget == null
                || message == null
                || message.getReferences() == null
                || message.getReferences().isEmpty())
        {
            return;
        }

        final FontTypeFace font =
                widget.getFont();

        final Rectangle widgetBounds =
                widget.getBounds();

        final String rawWidgetText =
                renderedWidget.rawText;

        final String semanticWidgetText =
                renderedWidget.semanticText;

        if (font == null
                || widgetBounds == null
                || widgetBounds.width <= 0
                || widgetBounds.height <= 0
                || rawWidgetText == null
                || semanticWidgetText == null)
        {
            return;
        }

        final String originalMessage =
                message.getOriginalMessage();

        if (originalMessage == null
                || originalMessage.isEmpty())
        {
            return;
        }

        int messageStart =
                semanticWidgetText.indexOf(
                        originalMessage);

        if (messageStart < 0
                && isPrivateMessage(message))
        {
            messageStart =
                    indexOfIgnoreCase(
                            semanticWidgetText,
                            originalMessage);
        }

        if (messageStart < 0)
        {
            return;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return;
        }

        final List<WrappedLine> wrappedLines =
                wrapSemanticLines(
                        widget,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        widgetBounds);

        if (wrappedLines.isEmpty())
        {
            return;
        }

        for (PlayerReference reference
                : message.getReferences())
        {
            if (reference == null
                    || !ChatInteractionPolicy.isClickable(
                    reference,
                    config))
            {
                continue;
            }

            final int semanticStart =
                    messageStart
                            + reference.getStartOffset();

            final int semanticEnd =
                    messageStart
                            + reference.getEndOffset();

            if (semanticStart < 0
                    || semanticEnd <= semanticStart
                    || semanticEnd > semanticWidgetText.length())
            {
                continue;
            }

            addWrappedSemanticHitboxes(
                    widget,
                    rawWidgetText,
                    semanticWidgetText,
                    map,
                    font,
                    widgetBounds,
                    wrappedLines,
                    semanticStart,
                    semanticEnd,
                    message.getId(),
                    reference,
                    surface,
                    output);
        }
    }

    /**
     * Layout the author/player shown beside a rendered chat message.
     *
     * The synthesized reference deliberately uses ReferenceType.SENDER.
     *
     * Existing ChatInteractionPolicy behavior then gives us:
     *
     * ALL
     * - sender clickable
     *
     * MENTIONS
     * - sender not clickable
     *
     * TAGGED_ONLY
     * - sender not clickable
     *
     * Sender references are synthesized only for interaction and are not
     * inserted into TaggedMessage.references, preventing them from affecting
     * mention matching, notifications, or history.
     */
    private void layoutSender(
            RenderedTextWidget messageWidget,
            TaggedMessage message,
            Surface surface,
            List<RenderedTextWidget> textWidgets,
            RenderedRowIndex rowIndex,
            List<ReferenceHitbox> output)
    {
        final String sender =
                message.getCanonicalSender();

        if (sender == null
                || sender.isEmpty())
        {
            return;
        }

        final PlayerReference senderReference =
                PlayerReference.builder()
                        .rawText(sender)
                        .normalizedToken(sender)
                        .lookupName(sender)
                        .startOffset(0)
                        .endOffset(sender.length())
                        .type(ReferenceType.SENDER)
                        .locallyResolved(false)
                        .identity(null)
                        .chatType(message.getType())
                        .build();

        if (!ChatInteractionPolicy.isClickable(
                senderReference,
                config))
        {
            return;
        }

        switch (surface)
        {
            case CHATBOX:
                /*
                 * The normal chatbox renders the sender and message body as
                 * separate text widgets.
                 *
                 * Find the sender dynamically from the other text widgets on
                 * the same rendered chat row.
                 */
                layoutChatboxSender(
                        messageWidget,
                        message,
                        senderReference,
                        surface,
                        textWidgets,
                        rowIndex,
                        output);
                break;

            case SPLIT_PRIVATE:
                /*
                 * Split private chat renders sender/prefix and message body as
                 * separate PmChat dynamic children.
                 */
                layoutSplitPrivateSender(
                        messageWidget,
                        message,
                        senderReference,
                        surface,
                        textWidgets,
                        rowIndex,
                        output);
                break;

            default:
                break;
        }
    }

    /**
     * The normal chatbox renders the sender and message body as separate
     * text widgets.
     *
     * Locate the sender from another visible text widget occupying the
     * same rendered chat row as the body.
     *
     * The sender widget is selected dynamically rather than relying on:
     *
     * - a fixed child index,
     * - a fixed X offset,
     * - sender length,
     * - channel-specific prefixes,
     * - or the sender being embedded inside the message body widget.
     *
     * Only the actual account-name span is converted into a hitbox.
     */
    private void layoutChatboxSender(
            RenderedTextWidget messageWidget,
            TaggedMessage message,
            PlayerReference senderReference,
            Surface surface,
            List<RenderedTextWidget> textWidgets,
            RenderedRowIndex rowIndex,
            List<ReferenceHitbox> output)
    {
        if (messageWidget == null
                || messageWidget.widget == null
                || rowIndex == null)
        {
            return;
        }

        final String sender =
                senderReference.getLookupName();

        final RenderedSenderMatch senderMatch =
                findChatboxSender(
                        messageWidget,
                        sender,
                        textWidgets,
                        rowIndex);

        if (senderMatch == null
                || senderMatch.rendered == null
                || senderMatch.rendered.widget == null)
        {
            return;
        }

        final int senderEnd =
                senderMatch.semanticStart
                        + sender.length();

        addSemanticHitbox(
                senderMatch.rendered.widget,
                senderMatch.rendered.rawText,
                senderMatch.rendered.semanticText,
                senderMatch.semanticStart,
                senderEnd,
                message.getId(),
                senderReference,
                surface,
                output);
    }

    /**
     * Resolve the exact physical sender Widget for one normal CHATBOX body.
     *
     * This is shared by clickable SENDER geometry and Favorite sender-name
     * coloring so both features use the same nearest-left, same-row ownership
     * rule. Mention/tag body styling is intentionally unrelated.
     */
    private RenderedSenderMatch findChatboxSender(
            RenderedTextWidget messageWidget,
            String sender,
            List<RenderedTextWidget> textWidgets,
            RenderedRowIndex rowIndex)
    {
        if (messageWidget == null
                || messageWidget.widget == null
                || sender == null
                || sender.isEmpty()
                || textWidgets == null
                || rowIndex == null)
        {
            return null;
        }

        final Rectangle messageBounds =
                messageWidget.widget.getBounds();

        if (messageBounds == null)
        {
            return null;
        }

        /*
         * Prefer only Widgets observed on the body's current Y row. The row
         * index is advisory: every candidate is revalidated against live bounds.
         *
         * If no sender is found, retry against the complete semantic Widget list
         * so transient native reconstruction cannot create an ownership failure.
         */
        RenderedTextWidget senderWidget =
                null;

        int senderStart =
                -1;

        final List<RenderedTextWidget> indexedCandidates =
                rowIndex.candidates(
                        messageBounds.y);

        for (int pass = 0;
             pass < 2 && senderWidget == null;
             pass++)
        {
            final List<RenderedTextWidget> candidates =
                    pass == 0
                            ? indexedCandidates
                            : textWidgets;

            if (candidates == null
                    || candidates.isEmpty())
            {
                continue;
            }

            int bestCandidateX =
                    Integer.MIN_VALUE;

            for (RenderedTextWidget candidate : candidates)
            {
                if (candidate == null
                        || candidate.widget == null
                        || candidate.widget == messageWidget.widget
                        || candidate.widget.isHidden())
                {
                    continue;
                }

                final Rectangle candidateBounds =
                        candidate.widget.getBounds();

                if (candidateBounds == null
                        || candidateBounds.width <= 0
                        || candidateBounds.height <= 0)
                {
                    continue;
                }

                if (candidateBounds.y != messageBounds.y
                        || candidateBounds.x > messageBounds.x)
                {
                    continue;
                }

                final int candidateSenderStart =
                        indexOfName(
                                candidate.semanticText,
                                sender);

                if (candidateSenderStart < 0)
                {
                    continue;
                }

                if (senderWidget == null
                        || candidateBounds.x > bestCandidateX)
                {
                    senderWidget =
                            candidate;

                    senderStart =
                            candidateSenderStart;

                    bestCandidateX =
                            candidateBounds.x;
                }
            }
        }

        return senderWidget != null
                && senderStart >= 0
                ? new RenderedSenderMatch(
                senderWidget,
                senderStart)
                : null;
    }

    /**
     * Split private chat renders the sender/prefix and message body as
     * separate dynamic children beneath PmChat.CONTAINER.
     *
     * Widget Inspector testing established that the sender and body widgets
     * share the same rendered Y coordinate while the message body's X offset
     * varies with the sender/prefix width.
     *
     * We therefore locate the sender dynamically instead of depending on:
     *
     * - child indices such as [0]/[1], [4]/[5], ...
     * - PmChat.PM1 through PM5,
     * - a fixed message X position,
     * - a fixed account-name length,
     * - a fixed "To " / "From " prefix width.
     */
    private void layoutSplitPrivateSender(
            RenderedTextWidget messageWidget,
            TaggedMessage message,
            PlayerReference senderReference,
            Surface surface,
            List<RenderedTextWidget> textWidgets,
            RenderedRowIndex rowIndex,
            List<ReferenceHitbox> output)
    {
        if (messageWidget == null
                || messageWidget.widget == null
                || rowIndex == null)
        {
            return;
        }

        final Rectangle messageBounds =
                messageWidget.widget.getBounds();

        if (messageBounds == null)
        {
            return;
        }

        final String sender =
                senderReference.getLookupName();

        if (sender == null
                || sender.isEmpty())
        {
            return;
        }

        /*
         * Prefer advisory same-row candidates while preserving SPLIT_PRIVATE's
         * native-order first-match behavior. Every candidate is checked against live
         * geometry, with the complete semantic list as a fallback.
         */
        RenderedTextWidget senderWidget =
                null;

        int senderStart =
                -1;

        final List<RenderedTextWidget> indexedCandidates =
                rowIndex.candidates(
                        messageBounds.y);

        for (int pass = 0;
             pass < 2 && senderWidget == null;
             pass++)
        {
            final List<RenderedTextWidget> candidates =
                    pass == 0
                            ? indexedCandidates
                            : textWidgets;

            if (candidates == null
                    || candidates.isEmpty())
            {
                continue;
            }

            for (RenderedTextWidget candidate : candidates)
            {
                if (candidate == null
                        || candidate.widget == null
                        || candidate.widget == messageWidget.widget
                        || candidate.widget.isHidden())
                {
                    continue;
                }

                final Rectangle candidateBounds =
                        candidate.widget.getBounds();

                if (candidateBounds == null
                        || candidateBounds.width <= 0
                        || candidateBounds.height <= 0)
                {
                    continue;
                }

                /*
                 * Revalidate the row and left-of-body rule against current
                 * geometry instead of trusting the advisory index.
                 */
                if (candidateBounds.y != messageBounds.y
                        || candidateBounds.x > messageBounds.x)
                {
                    continue;
                }

                final int candidateSenderStart =
                        indexOfName(
                                candidate.semanticText,
                                sender);

                if (candidateSenderStart < 0)
                {
                    continue;
                }

                senderWidget =
                        candidate;

                senderStart =
                        candidateSenderStart;

                break;
            }
        }

        if (senderWidget == null
                || senderWidget.widget == null
                || senderStart < 0)
        {
            return;
        }

        final int senderEnd =
                senderStart
                        + sender.length();

        addSemanticHitbox(
                senderWidget.widget,
                senderWidget.rawText,
                senderWidget.semanticText,
                senderStart,
                senderEnd,
                message.getId(),
                senderReference,
                surface,
                output);
    }

    private boolean isChatboxBodyGeometryStable(
            TaggedMessage message,
            RenderedTextWidget renderedWidget)
    {
        if (message == null
                || renderedWidget == null
                || renderedWidget.widget == null)
        {
            return false;
        }

        final Rectangle bounds =
                renderedWidget.widget.getBounds();

        if (bounds == null
                || bounds.width <= 0
                || bounds.height <= 0)
        {
            return false;
        }

        final long messageId =
                message.getId();

        final int currentX =
                bounds.x;

        final Integer acceptedX =
                acceptedChatboxBodyX.get(
                        messageId);

        /*
         * Once this horizontal body position has been accepted, ordinary
         * vertical movement remains live.
         */
        if (acceptedX != null
                && acceptedX == currentX)
        {
            pendingChatboxBodyX.remove(
                    messageId);

            return true;
        }

        final Integer pendingX =
                pendingChatboxBodyX.get(
                        messageId);

        /*
         * A new horizontal position becomes authoritative only after RuneScape
         * exposes the same X coordinate on two consecutive layout passes.
         */
        if (pendingX != null
                && pendingX == currentX)
        {
            pendingChatboxBodyX.remove(
                    messageId);

            acceptedChatboxBodyX.put(
                    messageId,
                    currentX);

            return true;
        }

        pendingChatboxBodyX.put(
                messageId,
                currentX);

        return false;
    }

    /**
     * Remove horizontal stabilization state only for semantic messages which are
     * no longer retained by RuneTags.
     *
     * Physical visibility is deliberately irrelevant here. A retained message may
     * be above or below the current CHATBOX viewport and must remain immediately
     * usable if the player scrolls back to it.
     */
    private void pruneChatboxBodyXState(
            List<TaggedMessage> messages)
    {
        if (acceptedChatboxBodyX.isEmpty()
                && pendingChatboxBodyX.isEmpty())
        {
            return;
        }

        if (messages == null
                || messages.isEmpty())
        {
            clearChatboxBodyXState();
            return;
        }

        final Set<Long> retainedMessageIds =
                new HashSet<>(
                        messages.size());

        for (TaggedMessage message : messages)
        {
            if (message != null)
            {
                retainedMessageIds.add(
                        message.getId());
            }
        }

        acceptedChatboxBodyX.keySet()
                .removeIf(
                        messageId ->
                                !retainedMessageIds.contains(
                                        messageId));

        pendingChatboxBodyX.keySet()
                .removeIf(
                        messageId ->
                                !retainedMessageIds.contains(
                                        messageId));
    }

    /*
     * Clear all transient CHATBOX horizontal stabilization ownership.
     */
    public void clearChatboxBodyXState()
    {
        acceptedChatboxBodyX.clear();
        pendingChatboxBodyX.clear();
    }

    /**
     * Convert a semantic plain-text span inside one rendered widget into one
     * or more exact physical hitboxes.
     *
     * Most sender widgets are one line, but using the same wrapping engine here
     * keeps sender and body geometry consistent and prevents a future multiline
     * widget from creating an oversized interaction target.
     */
    private void addSemanticHitbox(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            int semanticStart,
            int semanticEnd,
            long messageId,
            PlayerReference reference,
            Surface surface,
            List<ReferenceHitbox> output)
    {
        if (widget == null
                || rawWidgetText == null
                || semanticWidgetText == null
                || semanticStart < 0
                || semanticEnd <= semanticStart
                || semanticEnd > semanticWidgetText.length())
        {
            return;
        }

        final Rectangle widgetBounds =
                widget.getBounds();

        final FontTypeFace font =
                widget.getFont();

        if (widgetBounds == null
                || widgetBounds.width <= 0
                || widgetBounds.height <= 0
                || font == null)
        {
            return;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return;
        }

        final List<WrappedLine> wrappedLines =
                wrapSemanticLines(
                        widget,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        widgetBounds);

        if (wrappedLines.isEmpty())
        {
            return;
        }

        addWrappedSemanticHitboxes(
                widget,
                rawWidgetText,
                semanticWidgetText,
                map,
                font,
                widgetBounds,
                wrappedLines,
                semanticStart,
                semanticEnd,
                messageId,
                reference,
                surface,
                output);
    }

    /**
     * Measure how many visual rows a raw RuneScape text body will occupy when
     * rendered with the supplied Jagex font and available body width.
     *
     * FontLayoutService uses this before RuneScape creates a body widget so native
     * row allocation and the configured FontId use the same wrapping model as
     * clickable references and background highlights.
     */
    public int measureWrappedLineCount(
            String rawWidgetText,
            FontTypeFace font,
            int availableWidth)
    {
        if (rawWidgetText == null
                || rawWidgetText.isEmpty()
                || font == null
                || availableWidth <= 0)
        {
            return 1;
        }

        final String semanticWidgetText =
                ChatText.toSemanticPlain(
                        rawWidgetText);

        if (semanticWidgetText == null
                || semanticWidgetText.isEmpty())
        {
            return 1;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return 1;
        }

        /*
         * wrapSemanticLines only needs the available width from the Rectangle.
         *
         * No physical Widget exists yet during PRE construction, so null is
         * deliberately supplied for the unused Widget parameter.
         */
        final Rectangle measurementBounds =
                new Rectangle(
                        0,
                        0,
                        availableWidth,
                        1);

        final List<WrappedLine> lines =
                wrapSemanticLines(
                        null,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        measurementBounds);

        return Math.max(
                1,
                lines.size());
    }

    /**
     * Whether non-clickable self-token backgrounds can contribute anything to the
     * current frame.
     */
    private boolean shouldLayoutLocalHighlights()
    {
        if (!config.highlightBackground())
        {
            return false;
        }

        return config.selfBackgroundColor() != null
                && config.selfBackgroundColor()
                .getAlpha() > 0;
    }

    /**
     * Layout Unique Highlight / normalized-account-name local matches which are
     * not already represented by normal PlayerReference objects.
     *
     * The physical message Widget has already been resolved by layoutSurface().
     */
    private void layoutLocalHighlight(
            RenderedTextWidget renderedWidget,
            TaggedMessage message,
            Surface surface,
            List<LocalHighlight> output)
    {
        final Widget widget =
                renderedWidget != null
                        ? renderedWidget.widget
                        : null;

        if (widget == null
                || message == null
                || output == null)
        {
            return;
        }

        final LocalMentionMatch localMatch =
                message.getLocalMentionMatch();

        if (!shouldDrawLocalToken(
                localMatch))
        {
            return;
        }

        final String rawWidgetText =
                renderedWidget.rawText;

        if (rawWidgetText == null
                || rawWidgetText.isEmpty())
        {
            return;
        }

        final String semanticWidgetText =
                renderedWidget.semanticText;

        final String originalMessage =
                message.getOriginalMessage();

        if (semanticWidgetText == null
                || originalMessage == null
                || originalMessage.isEmpty())
        {
            return;
        }

        int messageStart =
                semanticWidgetText.indexOf(
                        originalMessage);

        if (messageStart < 0
                && isPrivateMessage(message))
        {
            messageStart =
                    indexOfIgnoreCase(
                            semanticWidgetText,
                            originalMessage);
        }

        if (messageStart < 0)
        {
            return;
        }

        final String token =
                localMatch.getMatchedToken();

        if (token == null
                || token.trim().isEmpty())
        {
            return;
        }

        /*
         * Mirror MessageFormatter's token semantics exactly.
         */
        final String loweredMessage =
                originalMessage.toLowerCase(
                        Locale.ROOT);

        final String loweredToken =
                token.toLowerCase(
                        Locale.ROOT);

        int from = 0;

        while (from
                <= loweredMessage.length()
                - loweredToken.length())
        {
            final int start =
                    loweredMessage.indexOf(
                            loweredToken,
                            from);

            if (start < 0)
            {
                break;
            }

            final int end =
                    start
                            + loweredToken.length();

            if (hasBoundaries(
                    loweredMessage,
                    start,
                    end)
                    && !overlapsPlayerReference(
                    start,
                    end,
                    message))
            {
                final List<Rectangle> rectangles =
                        layoutSemanticSpan(
                                widget,
                                rawWidgetText,
                                semanticWidgetText,
                                messageStart + start,
                                messageStart + end);

                for (Rectangle bounds : rectangles)
                {
                    if (bounds == null
                            || bounds.width <= 0
                            || bounds.height <= 0)
                    {
                        continue;
                    }

                    output.add(
                            new LocalHighlight(
                                    message.getId(),
                                    bounds,
                                    surface));
                }
            }

            /*
             * Preserve MessageFormatter's overlapping-search behavior.
             */
            from =
                    start + 1;
        }
    }

    /**
     * Only local matches without a normal PlayerReference require this additional
     * background geometry.
     */
    private static boolean shouldDrawLocalToken(
            LocalMentionMatch localMatch)
    {
        if (localMatch == null
                || !localMatch.isMatchesLocalPlayer()
                || localMatch.getReason() == null)
        {
            return false;
        }

        return localMatch.getReason()
                == MatchReason.UNIQUE_HIGHLIGHT
                || localMatch.getReason()
                == MatchReason.NORMALIZED_ACCOUNT_NAME;
    }

    private static boolean overlapsPlayerReference(
            int start,
            int end,
            TaggedMessage message)
    {
        if (message == null
                || message.getReferences() == null)
        {
            return false;
        }

        for (PlayerReference reference
                : message.getReferences())
        {
            if (reference == null)
            {
                continue;
            }

            if (start
                    < reference.getEndOffset()
                    && end
                    > reference.getStartOffset())
            {
                return true;
            }
        }

        return false;
    }

    private static boolean hasBoundaries(
            String text,
            int start,
            int end)
    {
        final boolean leftBoundary =
                start == 0
                        || !isNameChar(
                        text.charAt(
                                start - 1));

        final boolean rightBoundary =
                end == text.length()
                        || !isNameChar(
                        text.charAt(
                                end));

        return leftBoundary
                && rightBoundary;
    }

    private static boolean isNameChar(
            char c)
    {
        return Character.isLetterOrDigit(
                c)
                || c == '_'
                || c == '-';
    }

    /**
     * Resolve one arbitrary semantic span inside a rendered text widget into its
     * physical multiline rectangles.
     *
     * This is the general geometry form of the PlayerReference layout used by
     * RuneTags. It allows non-clickable presentation features, such as Unique
     * Highlight backgrounds, to use exactly the same wrapping calculations as
     * clickable mention/tag hitboxes.
     */
    public List<Rectangle> layoutSemanticSpan(
            Widget widget,
            int semanticStart,
            int semanticEnd)
    {
        final List<Rectangle> output =
                new ArrayList<>();

        if (widget == null
                || semanticStart < 0
                || semanticEnd <= semanticStart)
        {
            return output;
        }

        final String rawWidgetText =
                widget.getText();

        if (rawWidgetText == null)
        {
            return output;
        }

        final String semanticWidgetText =
                ChatText.toSemanticPlain(
                        rawWidgetText);

        return layoutSemanticSpan(
                widget,
                rawWidgetText,
                semanticWidgetText,
                semanticStart,
                semanticEnd);
    }

    /**
     * Cached-text form used by the render pass. Bounds and FontTypeFace remain
     * live reads so no reconstruction geometry is frozen in the semantic cache.
     */
    private List<Rectangle> layoutSemanticSpan(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            int semanticStart,
            int semanticEnd)
    {
        final List<Rectangle> output =
                new ArrayList<>();

        if (widget == null
                || rawWidgetText == null
                || semanticWidgetText == null
                || semanticStart < 0
                || semanticEnd <= semanticStart)
        {
            return output;
        }

        final Rectangle widgetBounds =
                widget.getBounds();

        final FontTypeFace font =
                widget.getFont();

        if (widgetBounds == null
                || widgetBounds.width <= 0
                || widgetBounds.height <= 0
                || font == null)
        {
            return output;
        }

        if (semanticEnd > semanticWidgetText.length())
        {
            return output;
        }

        final MessageMarkupMap map =
                MessageMarkupMap.create(
                        rawWidgetText);

        if (!map.matchesPlain(
                semanticWidgetText))
        {
            return output;
        }

        final List<WrappedLine> wrappedLines =
                wrapSemanticLines(
                        widget,
                        rawWidgetText,
                        semanticWidgetText,
                        map,
                        font,
                        widgetBounds);

        if (wrappedLines.isEmpty())
        {
            return output;
        }

        final int physicalLineHeight =
                Math.max(
                        1,
                        widgetBounds.height
                                / wrappedLines.size());

        for (int lineIndex = 0;
             lineIndex < wrappedLines.size();
             lineIndex++)
        {
            final WrappedLine line =
                    wrappedLines.get(
                            lineIndex);

            final int segmentStart =
                    Math.max(
                            semanticStart,
                            line.start);

            final int segmentEnd =
                    Math.min(
                            semanticEnd,
                            line.end);

            if (segmentStart >= segmentEnd)
            {
                continue;
            }

            /*
             * Preserve rendered leading markup on the first physical line.
             *
             * In particular, <img=...> tags occupy horizontal space even though they
             * do not exist in semantic plain text.
             */
            final int rawLineStart =
                    line.start == 0
                            ? 0
                            : map.rawBoundary(
                            line.start);

            final int rawSegmentStart =
                    map.rawBoundary(
                            segmentStart);

            final int rawSegmentEnd =
                    map.rawBoundary(
                            segmentEnd);

            if (rawLineStart < 0
                    || rawSegmentStart < rawLineStart
                    || rawSegmentEnd < rawSegmentStart
                    || rawSegmentEnd > rawWidgetText.length())
            {
                continue;
            }

            final int prefixWidth =
                    font.getTextWidth(
                            rawWidgetText.substring(
                                    rawLineStart,
                                    rawSegmentStart));

            final int segmentWidth =
                    Math.max(
                            1,
                            font.getTextWidth(
                                    rawWidgetText.substring(
                                            rawSegmentStart,
                                            rawSegmentEnd)));

            final int lineOriginX =
                    alignedLineX(
                            widget,
                            widgetBounds,
                            line.width);

            final int lineY =
                    widgetBounds.y
                            + (lineIndex
                            * physicalLineHeight);

            output.add(
                    new Rectangle(
                            lineOriginX + prefixWidth,
                            lineY,
                            segmentWidth,
                            physicalLineHeight));
        }

        return output;
    }

    /**
     * Add the physical pieces of one semantic span.
     *
     * The span is intersected independently with every visual line occupied by
     * the widget. If a name crosses a wrap boundary, each visible piece receives
     * its own ReferenceHitbox pointing to the same PlayerReference.
     */
    private void addWrappedSemanticHitboxes(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            MessageMarkupMap map,
            FontTypeFace font,
            Rectangle widgetBounds,
            List<WrappedLine> wrappedLines,
            int semanticStart,
            int semanticEnd,
            long messageId,
            PlayerReference reference,
            Surface surface,
            List<ReferenceHitbox> output)
    {
        if (wrappedLines == null
                || wrappedLines.isEmpty())
        {
            return;
        }

        /*
         * RuneScape expands the physical widget as lines are added.
         *
         * Widget Inspector testing showed:
         *
         * 1 line  -> approximately 14 px
         * 2 lines -> approximately 28 px
         *
         * Deriving the row height from the actual widget height gives us the
         * physical space RuneScape allocated rather than assuming Font baseline
         * or hardcoding 14/16 px.
         */
        final int physicalLineHeight =
                Math.max(
                        1,
                        widgetBounds.height
                                / wrappedLines.size());

        for (int lineIndex = 0;
             lineIndex < wrappedLines.size();
             lineIndex++)
        {
            final WrappedLine line =
                    wrappedLines.get(
                            lineIndex);

            final int segmentStart =
                    Math.max(
                            semanticStart,
                            line.start);

            final int segmentEnd =
                    Math.min(
                            semanticEnd,
                            line.end);

            if (segmentStart >= segmentEnd)
            {
                continue;
            }

            /*
             * The first semantic character may be preceded by rendered markup such as:
             *
             *     <img=...>
             *
             * MessageMarkupMap correctly maps semantic offset 0 to the first visible
             * text character, which is appropriate for formatting insertion.
             *
             * Geometry is different: leading image markup occupies real horizontal
             * space inside the Widget. For the first visual line, measure from the
             * beginning of the raw Widget text so FontTypeFace includes that rendered
             * prefix width.
             *
             * Wrapped continuation lines still begin at their semantic raw boundary.
             */
            final int rawLineStart =
                    line.start == 0
                            ? 0
                            : map.rawBoundary(
                            line.start);

            final int rawSegmentStart =
                    map.rawBoundary(
                            segmentStart);

            final int rawSegmentEnd =
                    map.rawBoundary(
                            segmentEnd);

            if (rawLineStart < 0
                    || rawSegmentStart < rawLineStart
                    || rawSegmentEnd < rawSegmentStart
                    || rawSegmentEnd > rawWidgetText.length())
            {
                continue;
            }

            final int prefixWidth =
                    font.getTextWidth(
                            rawWidgetText.substring(
                                    rawLineStart,
                                    rawSegmentStart));

            final int segmentWidth =
                    Math.max(
                            1,
                            font.getTextWidth(
                                    rawWidgetText.substring(
                                            rawSegmentStart,
                                            rawSegmentEnd)));

            /*
             * Alignment is resolved independently for every visual line.
             *
             * CHATBOX is normally LEFT aligned, but handling CENTER/RIGHT here
             * costs very little and keeps this routine correct for any future
             * RuneScape presentation using the same service.
             */
            final int lineOriginX =
                    alignedLineX(
                            widget,
                            widgetBounds,
                            line.width);

            final int lineY =
                    widgetBounds.y
                            + (lineIndex
                            * physicalLineHeight);

            final Rectangle bounds =
                    new Rectangle(
                            lineOriginX + prefixWidth,
                            lineY,
                            segmentWidth,
                            physicalLineHeight);

            output.add(
                    new ReferenceHitbox(
                            messageId,
                            bounds,
                            reference,
                            surface));
        }
    }

    /**
     * Reproduce the visual line ranges used by a RuneScape text widget.
     *
     * RuneScape exposes the final widget width and total expanded height but does
     * not expose an API containing the semantic start/end offset of each rendered
     * line. Reconstruct those ranges using the same Jagex FontTypeFace used by the
     * widget.
     *
     * Wrapping prefers whitespace. If one unbroken token is wider than the
     * widget, it falls back to a hard character boundary so layout can still
     * progress.
     */
    private List<WrappedLine> wrapSemanticLines(
            Widget widget,
            String rawWidgetText,
            String semanticWidgetText,
            MessageMarkupMap map,
            FontTypeFace font,
            Rectangle widgetBounds)
    {
        final List<WrappedLine> lines =
                new ArrayList<>();

        if (semanticWidgetText == null
                || semanticWidgetText.isEmpty()
                || widgetBounds.width <= 0)
        {
            return lines;
        }

        final int textLength =
                semanticWidgetText.length();

        int lineStart =
                0;

        while (lineStart < textLength)
        {
            if (isExplicitLineBreak(
                    semanticWidgetText.charAt(
                            lineStart)))
            {
                lineStart++;
                continue;
            }

            int cursor =
                    lineStart;

            int lastWhitespace =
                    -1;

            int acceptedEnd =
                    lineStart;

            while (cursor < textLength)
            {
                final char current =
                        semanticWidgetText.charAt(
                                cursor);

                if (isExplicitLineBreak(
                        current))
                {
                    acceptedEnd =
                            cursor;

                    break;
                }

                if (Character.isWhitespace(
                        current))
                {
                    lastWhitespace =
                            cursor;
                }

                final int candidateEnd =
                        cursor + 1;

                final int candidateWidth =
                        semanticWidth(
                                rawWidgetText,
                                map,
                                font,
                                lineStart,
                                candidateEnd);

                if (candidateWidth
                        <= widgetBounds.width)
                {
                    acceptedEnd =
                            candidateEnd;

                    cursor++;
                    continue;
                }

                /*
                 * The candidate no longer fits.
                 *
                 * Prefer the last whitespace belonging to this line. The space
                 * itself remains semantically between the two words but is not
                 * treated as visible leading content on the following line.
                 */
                if (lastWhitespace
                        >= lineStart)
                {
                    acceptedEnd =
                            lastWhitespace;
                }
                else if (acceptedEnd
                        <= lineStart)
                {
                    /*
                     * One token/character itself is wider than the widget.
                     * Force progress by allowing one semantic character.
                     */
                    acceptedEnd =
                            candidateEnd;
                }

                break;
            }

            if (cursor >= textLength)
            {
                acceptedEnd =
                        textLength;
            }

            if (acceptedEnd < lineStart)
            {
                acceptedEnd =
                        lineStart;
            }

            /*
             * Avoid zero-length visual lines caused by unusual whitespace.
             */
            if (acceptedEnd == lineStart)
            {
                acceptedEnd =
                        Math.min(
                                textLength,
                                lineStart + 1);
            }

            final int lineWidth =
                    semanticWidth(
                            rawWidgetText,
                            map,
                            font,
                            lineStart,
                            acceptedEnd);

            lines.add(
                    new WrappedLine(
                            lineStart,
                            acceptedEnd,
                            Math.max(
                                    0,
                                    lineWidth)));

            int nextStart =
                    acceptedEnd;

            while (nextStart < textLength)
            {
                final char next =
                        semanticWidgetText.charAt(
                                nextStart);

                if (isExplicitLineBreak(
                        next))
                {
                    nextStart++;
                    break;
                }

                /*
                 * RuneScape does not visually preserve the wrapping space at the
                 * start of the following line.
                 */
                if (Character.isWhitespace(
                        next))
                {
                    nextStart++;
                    continue;
                }

                break;
            }

            if (nextStart <= lineStart)
            {
                nextStart =
                        Math.min(
                                textLength,
                                lineStart + 1);
            }

            lineStart =
                    nextStart;
        }

        /*
         * Normally the number of reconstructed lines will agree with the widget
         * height. If the widget reports one physical row, preserving a single
         * line avoids introducing artificial vertical geometry.
         */
        if (lines.isEmpty())
        {
            lines.add(
                    new WrappedLine(
                            0,
                            textLength,
                            semanticWidth(
                                    rawWidgetText,
                                    map,
                                    font,
                                    0,
                                    textLength)));
        }

        return lines;
    }

    /**
     * Measure one semantic range using the actual raw RuneScape markup and the
     * widget's own font.
     */
    private static int semanticWidth(
            String rawWidgetText,
            MessageMarkupMap map,
            FontTypeFace font,
            int semanticStart,
            int semanticEnd)
    {
        if (semanticStart < 0
                || semanticEnd < semanticStart)
        {
            return 0;
        }

        final int rawStart =
                map.rawBoundary(
                        semanticStart);

        final int rawEnd =
                map.rawBoundary(
                        semanticEnd);

        if (rawStart < 0
                || rawEnd < rawStart
                || rawEnd > rawWidgetText.length())
        {
            return 0;
        }

        return font.getTextWidth(
                rawWidgetText.substring(
                        rawStart,
                        rawEnd));
    }

    /**
     * Resolve horizontal alignment for one visual line rather than the complete
     * multiline widget.
     */
    private static int alignedLineX(
            Widget widget,
            Rectangle bounds,
            int lineWidth)
    {
        switch (widget.getXTextAlignment())
        {
            case WidgetTextAlignment.CENTER:
                return bounds.x
                        + Math.max(
                        0,
                        (bounds.width
                                - lineWidth) / 2);

            case WidgetTextAlignment.RIGHT:
                return bounds.x
                        + Math.max(
                        0,
                        bounds.width
                                - lineWidth);

            case WidgetTextAlignment.LEFT:
            default:
                return bounds.x;
        }
    }

    /**
     * Semantic line-break characters which should terminate the current visual
     * row immediately.
     */
    private static boolean isExplicitLineBreak(
            char value)
    {
        return value == '\n'
                || value == '\r';
    }

    /**
     * Find an account name without depending on capitalization or
     * RuneScape's alternate space characters.
     *
     * NBSP and narrow-NBSP replacement is length-preserving, so the returned
     * offset remains valid against the original semantic text and
     * MessageMarkupMap.
     */
    private static int indexOfIgnoreCase(
            String text,
            String needle)
    {
        if (text == null
                || needle == null
                || needle.isEmpty()
                || needle.length() > text.length())
        {
            return -1;
        }

        final int lastStart =
                text.length()
                        - needle.length();

        for (int i = 0; i <= lastStart; i++)
        {
            if (text.regionMatches(
                    true,
                    i,
                    needle,
                    0,
                    needle.length()))
            {
                return i;
            }
        }

        return -1;
    }

    private static int indexOfName(
            String text,
            String name)
    {
        if (text == null
                || text.isEmpty()
                || name == null
                || name.isEmpty())
        {
            return -1;
        }

        final String comparableText =
                comparableNameText(
                        text);

        final String comparableName =
                comparableNameText(
                        name);

        if (comparableName.isEmpty()
                || comparableName.length()
                > comparableText.length())
        {
            return -1;
        }

        final int maxStart =
                comparableText.length()
                        - comparableName.length();

        for (int start = 0;
             start <= maxStart;
             start++)
        {
            if (comparableText.regionMatches(
                    true,
                    start,
                    comparableName,
                    0,
                    comparableName.length()))
            {
                return start;
            }
        }

        return -1;
    }

    private static String comparableNameText(
            String value)
    {
        return value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');
    }

    /**
     * The split-private PmChat surface must only match private-message
     * semantic records.
     */
    private static boolean isPrivateMessage(
            TaggedMessage message)
    {
        if (message == null
                || message.getType() == null)
        {
            return false;
        }

        final ChatMessageType type =
                message.getType();

        return type == ChatMessageType.PRIVATECHAT
                || type == ChatMessageType.MODPRIVATECHAT
                || type == ChatMessageType.PRIVATECHATOUT;
    }

    /**
     * Render collector.
     *
     * Read/normalize each eligible Widget's text only once for this layout
     * invocation. Applies the CHATBOX viewport only to candidate
     * inclusion; child traversal itself is never pruned. This preserves native
     * Widget order while ensuring partially visible rows remain eligible.
     * SPLIT_PRIVATE passes a null viewport and keeps its complete populated
     * surface. Bounds and fonts are checked live and are deliberately not stored.
     */
    private void collectRenderedTextWidgets(
            Widget widget,
            Surface surface,
            Rectangle visibleBounds,
            List<RenderedTextWidget> output,
            Set<Widget> visited,
            int depth)
    {
        if (widget == null
                || depth > MAX_WIDGET_DEPTH
                || !visited.add(widget))
        {
            return;
        }

        /*
         * Snapshot only the state needed for semantic ownership. FontTypeFace remains
         * a live read and is resolved later when physical geometry is required.
         *
         * getFontId() is sufficient for font eligibility here and avoids resolving a
         * FontTypeFace for every candidate in the recursive surface walk.
         */
        if (!widget.isHidden())
        {
            final String rawText =
                    widget.getText();

            if (rawText != null
                    && !rawText.isEmpty())
            {
                final Rectangle bounds =
                        widget.getBounds();

                if (bounds != null
                        && bounds.width > 0
                        && bounds.height > 0)
                {
                    final boolean visibleForLayout =
                            surface != Surface.CHATBOX
                                    || visibleBounds == null
                                    || visibleBounds.intersects(
                                    bounds);

                    if (visibleForLayout
                            && widget.getFontId() != -1)
                    {
                        final String semanticText =
                                ChatText.toSemanticPlain(
                                        rawText);

                        output.add(
                                new RenderedTextWidget(
                                        widget,
                                        rawText,
                                        semanticText));
                    }
                }
            }
        }

        collectRenderedTextChildren(
                widget.getChildren(),
                surface,
                visibleBounds,
                output,
                visited,
                depth + 1);

        collectRenderedTextChildren(
                widget.getStaticChildren(),
                surface,
                visibleBounds,
                output,
                visited,
                depth + 1);

        collectRenderedTextChildren(
                widget.getNestedChildren(),
                surface,
                visibleBounds,
                output,
                visited,
                depth + 1);
    }

    private void collectRenderedTextChildren(
            Widget[] children,
            Surface surface,
            Rectangle visibleBounds,
            List<RenderedTextWidget> output,
            Set<Widget> visited,
            int depth)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collectRenderedTextWidgets(
                    child,
                    surface,
                    visibleBounds,
                    output,
                    visited,
                    depth);
        }
    }

    /**
     * Recursively collect actual rendered text widgets beneath a physical chat
     * surface.
     *
     * Hidden/empty PmChat slots naturally fail these checks, so RuneTags does
     * not need to know that RuneScape currently exposes only five floating PM
     * rows or that populated dynamic children commonly follow a
     * [0]/[1], [4]/[5], ... pattern.
     */
    private void collectTextWidgets(
            Widget widget,
            List<Widget> output,
            Set<Widget> visited,
            int depth)
    {
        if (widget == null
                || depth > MAX_WIDGET_DEPTH
                || !visited.add(widget))
        {
            return;
        }

        final String text =
                widget.getText();

        if (!widget.isHidden()
                && text != null
                && !text.isEmpty()
                && widget.getFont() != null)
        {
            final Rectangle bounds =
                    widget.getBounds();

            /*
             * Do not trim rendered text here. RuneScape can represent a real chat message
             * body as markup containing only whitespace, and the Widget is still required
             * as the physical row anchor used to locate its sender.
             */
            if (bounds != null
                    && bounds.width > 0
                    && bounds.height > 0)
            {
                output.add(
                        widget);
            }
        }

        collectChildren(
                widget.getChildren(),
                output,
                visited,
                depth + 1);

        collectChildren(
                widget.getStaticChildren(),
                output,
                visited,
                depth + 1);

        collectChildren(
                widget.getNestedChildren(),
                output,
                visited,
                depth + 1);
    }

    private void collectChildren(
            Widget[] children,
            List<Widget> output,
            Set<Widget> visited,
            int depth)
    {
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            collectTextWidgets(
                    child,
                    output,
                    visited,
                    depth);
        }
    }
}
