package com.runetags.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import net.runelite.api.ChatMessageType;

/**
 * Runtime semantic storage for RuneTags chat messages.
 *
 * Retention is enforced independently per ChatMessageType because RuneScape
 * retains separate histories for different message types. Traffic in one type
 * must not evict a still-retained record from another.
 *
 * The master deque remains globally chronological so snapshot() exposes one
 * ordered semantic stream.
 */
public class TaggedMessageRepository {
	private final int capacityPerType;

	private final Deque<TaggedMessage> messages = new ArrayDeque<>();
	private final Map<ChatMessageType, Integer> countsByType = new EnumMap<>(ChatMessageType.class);
	private final Map<String, List<TaggedMessage>> messagesByBody = new HashMap<>();
	private final Map<String, List<TaggedMessage>> messagesByFoldedBody = new HashMap<>();
	private final Map<TaggedMessage, Long> insertionOrder = new IdentityHashMap<>();

	private long nextInsertionOrder;
	private long revision;

	public TaggedMessageRepository(int capacityPerType) {
		if (capacityPerType < 1) {
			throw new IllegalArgumentException("capacityPerType must be >= 1");
		}
		this.capacityPerType = capacityPerType;
	}

	public synchronized void add(TaggedMessage message) {
		if (message == null) {
			return;
		}

		final ChatMessageType retentionType = retentionType(message);
		messages.addLast(message);
		countsByType.put(retentionType, countsByType.getOrDefault(retentionType, 0) + 1);
		indexMessage(message);
		insertionOrder.put(message, nextInsertionOrder++);
		revision++;

		/*
		 * Evict only the oldest semantic record belonging to this same chat
		 * type.
		 *
		 * Traffic in another channel must never consume this type's retention
		 * allowance.
		 */
		while (countsByType.getOrDefault(retentionType, 0) > capacityPerType) {
			if (!removeOldestOfType(retentionType)) {
				/*
				 * Defensive consistency fallback.
				 *
				 * This should never occur because the count was incremented
				 * together with insertion.
				 */
				countsByType.remove(retentionType);
				break;
			}
		}
	}

	public synchronized Optional<TaggedMessage> get(long id) {
		return messages.stream().filter(message -> message.getId() == id).findFirst();
	}

	public synchronized List<TaggedMessage> snapshot() {
		return Collections.unmodifiableList(new ArrayList<>(messages));
	}

	/*
	 * Returns only retained semantic messages which can match one of the
	 * currently rendered physical bodies. Results remain newest-first.
	 *
	 * Exact bodies apply to every chat type. Case-folded candidates are private
	 * only, matching ReferenceLayoutService's existing compatibility fallback.
	 */
	public synchronized List<TaggedMessage> snapshotMatchingBodies(
			Set<String> exactBodies, Set<String> foldedBodies, boolean privateOnly) {
		if ((exactBodies == null || exactBodies.isEmpty())
				&& (foldedBodies == null || foldedBodies.isEmpty())) {
			return Collections.emptyList();
		}

		final Map<Long, TaggedMessage> orderedCandidates = new TreeMap<>(Collections.reverseOrder());
		final Set<TaggedMessage> selected = Collections.newSetFromMap(new IdentityHashMap<>());

		if (exactBodies != null) {
			for (String body : exactBodies) {
				addIndexedCandidates(messagesByBody.get(body), privateOnly, false, selected, orderedCandidates);
			}
		}

		if (foldedBodies != null) {
			for (String body : foldedBodies) {
				if (body == null || body.isEmpty()) {
					continue;
				}

				addIndexedCandidates(messagesByFoldedBody.get(body.toLowerCase(Locale.ROOT)), privateOnly, true,
						selected, orderedCandidates);
			}
		}

		return Collections.unmodifiableList(new ArrayList<>(orderedCandidates.values()));
	}

	public synchronized Set<Long> snapshotRetainedIds() {
		final Set<Long> ids = new HashSet<>(messages.size());
		for (TaggedMessage message : messages) {
			if (message != null) {
				ids.add(message.getId());
			}
		}
		return ids;
	}

	public synchronized long getRevision() {
		return revision;
	}

	public synchronized int size() {
		return messages.size();
	}

	public synchronized void clear() {
		if (messages.isEmpty() && countsByType.isEmpty()) {
			return;
		}

		messages.clear();
		countsByType.clear();
		messagesByBody.clear();
		messagesByFoldedBody.clear();
		insertionOrder.clear();
		revision++;
	}

	private void addIndexedCandidates(
			List<TaggedMessage> candidates,
			boolean privateOnly,
			boolean privateFallbackOnly,
			Set<TaggedMessage> selected,
			Map<Long, TaggedMessage> orderedCandidates) {
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		for (TaggedMessage candidate : candidates) {
			if (candidate == null || !selected.add(candidate)) {
				continue;
			}
			if ((privateOnly || privateFallbackOnly) && !isPrivateMessage(candidate)) {
				continue;
			}

			final Long order = insertionOrder.get(candidate);
			if (order != null) {
				orderedCandidates.put(order, candidate);
			}
		}
	}

	private void indexMessage(TaggedMessage message) {
		final String body = message != null ? message.getOriginalMessage() : null;
		if (body == null || body.isEmpty()) {
			return;
		}

		messagesByBody.computeIfAbsent(body, ignored -> new ArrayList<>()).add(message);
		messagesByFoldedBody.computeIfAbsent(
				body.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(message);
	}

	private void unindexMessage(TaggedMessage message) {
		final String body = message != null ? message.getOriginalMessage() : null;
		if (body == null || body.isEmpty()) {
			return;
		}

		removeIndexedMessage(messagesByBody, body, message);
		removeIndexedMessage(messagesByFoldedBody, body.toLowerCase(Locale.ROOT), message);
	}

	private static void removeIndexedMessage(
			Map<String, List<TaggedMessage>> index, String key, TaggedMessage message) {
		final List<TaggedMessage> indexed = index.get(key);
		if (indexed == null) {
			return;
		}

		for (Iterator<TaggedMessage> iterator = indexed.iterator(); iterator.hasNext();) {
			if (iterator.next() == message) {
				iterator.remove();
				break;
			}
		}

		if (indexed.isEmpty()) {
			index.remove(key);
		}
	}

	private boolean removeOldestOfType(ChatMessageType type) {
		final Iterator<TaggedMessage> iterator = messages.iterator();
		while (iterator.hasNext()) {
			final TaggedMessage candidate = iterator.next();
			if (retentionType(candidate) != type) {
				continue;
			}
			iterator.remove();
			unindexMessage(candidate);
			insertionOrder.remove(candidate);

			final int remaining = countsByType.getOrDefault(type, 0) - 1;
			if (remaining > 0) {
				countsByType.put(type, remaining);
			} else {
				countsByType.remove(type);
			}
			return true;
		}
		return false;
	}

	private static boolean isPrivateMessage(TaggedMessage message) {
		if (message == null || message.getType() == null) {
			return false;
		}

		final ChatMessageType type = message.getType();
		return type == ChatMessageType.PRIVATECHAT
				|| type == ChatMessageType.MODPRIVATECHAT
				|| type == ChatMessageType.PRIVATECHATOUT;
	}

	private static ChatMessageType retentionType(TaggedMessage message) {
		if (message == null || message.getType() == null) {
			return ChatMessageType.UNKNOWN;
		}
		return message.getType();
	}
}