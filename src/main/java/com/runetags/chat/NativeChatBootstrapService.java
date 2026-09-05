package com.runetags.chat;

import com.runetags.model.TaggedMessage;
import com.runetags.player.PlayerDirectory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.client.util.Text;

/**
 * Reconstructs RuneTags' runtime semantic chat state from RuneScape's existing
 * native chat buffers.
 *
 * This is used when RuneTags is enabled or re-enabled while RuneScape already
 * has chat history in memory.
 *
 * Bootstrap deliberately does NOT:
 *
 * - replay ChatMessage events;
 * - send mention notifications;
 * - add duplicate persistent Mention History entries;
 * - rewrite native chat formatting;
 * - reconstruct physical hitboxes directly.
 *
 * It restores only the semantic TaggedMessage repository and authoritative
 * native account observations. Existing overlays then derive physical
 * rendering/hitboxes from the restored semantic state normally.
 */
public class NativeChatBootstrapService
{
    private final Client client;
    private final PlayerDirectory playerDirectory;
    private final ChatProcessor chatProcessor;
    private final TaggedMessageRepository repository;

    public NativeChatBootstrapService(
            Client client,
            PlayerDirectory playerDirectory,
            ChatProcessor chatProcessor,
            TaggedMessageRepository repository)
    {
        this.client = client;
        this.playerDirectory = playerDirectory;
        this.chatProcessor = chatProcessor;
        this.repository = repository;
    }

    /**
     * Rebuild RuneTags semantic chat state from the current native buffers.
     *
     * @param startingMessageId current RuneTags message ID
     * @param localPlayerName current local player's display name
     * @param supportedTypePredicate RuneTags supported-chat-type predicate
     *
     * @return the newest RuneTags message ID assigned by this bootstrap
     */
    public long bootstrap(
            long startingMessageId,
            String localPlayerName,
            Predicate<ChatMessageType> supportedTypePredicate)
    {
        if (supportedTypePredicate == null)
        {
            return startingMessageId;
        }

        final Map<Integer, ChatLineBuffer> chatLineMap =
                client.getChatLineMap();

        if (chatLineMap == null
                || chatLineMap.isEmpty())
        {
            return startingMessageId;
        }

        /*
         * A MessageNode may be reachable through more than one native
         * representation.
         *
         * MessageNode IDs are the client's native message identity, so retain
         * only one copy of each currently-live native message.
         */
        final Map<Integer, MessageNode> uniqueNodes =
                new LinkedHashMap<>();

        for (ChatLineBuffer buffer
                : chatLineMap.values())
        {
            if (buffer == null
                    || buffer.getLines() == null)
            {
                continue;
            }

            for (MessageNode node
                    : buffer.getLines())
            {
                if (node == null
                        || node.getType() == null
                        || !supportedTypePredicate.test(
                        node.getType()))
                {
                    continue;
                }

                uniqueNodes.putIfAbsent(
                        node.getId(),
                        node);
            }
        }

        if (uniqueNodes.isEmpty())
        {
            return startingMessageId;
        }

        final List<MessageNode> nodes =
                new ArrayList<>(
                        uniqueNodes.values());

        /*
         * ChatLineBuffer arrays are newest-first.
         *
         * RuneTags' repository is chronological oldest -> newest, so recover
         * native chronology before processing.
         *
         * Native timestamps have second-level precision. MessageNode ID gives
         * us a deterministic tie-breaker for messages created in the same
         * second.
         */
        nodes.sort(
                Comparator
                        .comparingInt(
                                MessageNode::getTimestamp)
                        .thenComparingInt(
                                MessageNode::getId));

        long nextMessageId =
                startingMessageId;

        for (MessageNode node : nodes)
        {
            final ChatMessageType type =
                    node.getType();

            final String rawName =
                    node.getName();

            /*
             * Match the live ChatMessage path:
             *
             * PRIVATECHATOUT names the recipient rather than an authoritative
             * sender observation. Absence of an icon there must not overwrite
             * account knowledge learned from that player's own messages.
             */
            if (playerDirectory != null
                    && rawName != null
                    && !rawName.trim().isEmpty()
                    && type
                    != ChatMessageType.PRIVATECHATOUT)
            {
                playerDirectory.observeAccountType(
                        rawName);
            }

            /*
             * getValue() is the native/current body represented by this node.
             *
             * If a node lacks a value, RuneLite's separately retained formatted
             * representation is still usable as a semantic fallback because
             * ChatText removes markup before parsing.
             */
            String rawMessage =
                    node.getValue();

            if (rawMessage == null)
            {
                rawMessage =
                        node.getRuneLiteFormatMessage();
            }

            if (rawMessage == null)
            {
                rawMessage = "";
            }

            final String semanticMessage =
                    ChatText.toSemanticPlain(
                            rawMessage);

            final String canonicalSender =
                    rawName != null
                            ? Text.removeTags(
                            rawName)
                            : null;

            final TaggedMessage taggedMessage =
                    chatProcessor.process(
                            ++nextMessageId,
                            type,
                            canonicalSender,
                            semanticMessage,
                            localPlayerName);

            repository.add(
                    taggedMessage);
        }

        return nextMessageId;
    }
}