package com.runetags.chat;

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
 * Rebuilds RuneTags semantic chat state from RuneScape's existing native buffers.
 *
 * Bootstrap restores TaggedMessage state and authoritative account observations
 * without replaying events, sending notifications, rewriting chat, or creating hitboxes.
 */
public class NativeBootstrapService {
	private final Client client;
	private final PlayerDirectory playerDirectory;
	private final ChatProcessor chatProcessor;
	private final TaggedMessageRepository repository;

	public NativeBootstrapService(
			Client client,
			PlayerDirectory playerDirectory,
			ChatProcessor chatProcessor,
			TaggedMessageRepository repository) {
		this.client = client;
		this.playerDirectory = playerDirectory;
		this.chatProcessor = chatProcessor;
		this.repository = repository;
	}

	/**
	 * Rebuild RuneTags semantic chat state from the current native buffers.
	 *
	 * @param startingMessageId      current RuneTags message ID
	 * @param localPlayerName        current local player's display name
	 * @param supportedTypePredicate RuneTags supported-chat-type predicate
	 * @return the newest RuneTags message ID assigned by this bootstrap
	 */
	public long bootstrap(
			long startingMessageId, String localPlayerName, Predicate<ChatMessageType> supportedTypePredicate) {
		if (supportedTypePredicate == null) {
			return startingMessageId;
		}

		final Map<Integer, ChatLineBuffer> chatLineMap = client.getChatLineMap();
		if (chatLineMap == null || chatLineMap.isEmpty()) {
			return startingMessageId;
		}

		/*
		 * Deduplicate native messages by MessageNode ID before rebuilding semantic state.
		 */
		final Map<Integer, MessageNode> uniqueNodes = new LinkedHashMap<>();

		for (ChatLineBuffer buffer : chatLineMap.values()) {
			if (buffer == null || buffer.getLines() == null) {
				continue;
			}

			for (MessageNode node : buffer.getLines()) {
				if (node == null || node.getType() == null || !supportedTypePredicate.test(node.getType())) {
					continue;
				}

				uniqueNodes.putIfAbsent(node.getId(), node);
			}
		}

		if (uniqueNodes.isEmpty()) {
			return startingMessageId;
		}

		final List<MessageNode> nodes = new ArrayList<>(uniqueNodes.values());

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
		nodes.sort(Comparator.comparingInt(MessageNode::getTimestamp).thenComparingInt(MessageNode::getId));

		long nextMessageId = startingMessageId;

		for (MessageNode node : nodes) {
			final ChatMessageType type = node.getType();
			final String rawName = node.getName();

			/*
			 * PRIVATECHATOUT identifies the recipient and does not update authoritative
			 * sender-account observations.
			 */
			if (playerDirectory != null && rawName != null && !rawName.trim().isEmpty()
					&& type != ChatMessageType.PRIVATECHATOUT) {
				playerDirectory.observeAccountType(rawName);
			}

			/*
			 * Prefer the native message value and fall back to retained formatted text
			 * when no current value exists.
			 */
			String rawMessage = node.getValue();
			if (rawMessage == null) {
				rawMessage = node.getRuneLiteFormatMessage();
			}
			if (rawMessage == null) {
				rawMessage = "";
			}

			final String semanticMessage = ChatText.toSemanticPlain(rawMessage);
			final String canonicalSender = rawName != null
					? Text.removeTags(rawName)
					: null;
			final TaggedMessage taggedMessage = chatProcessor.process(
				++nextMessageId, type, canonicalSender, semanticMessage, localPlayerName);

			repository.add(taggedMessage);
		}

		return nextMessageId;
	}
}