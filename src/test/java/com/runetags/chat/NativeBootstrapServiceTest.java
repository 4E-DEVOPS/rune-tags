package com.runetags.chat;

import com.runetags.player.PlayerDirectory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class NativeBootstrapServiceTest
{
    private Client client;
    private PlayerDirectory playerDirectory;
    private RecordingChatProcessor chatProcessor;
    private TaggedMessageRepository repository;
    private NativeBootstrapService service;

    @Before
    public void setUp()
    {
        client =
                Mockito.mock(
                        Client.class);

        playerDirectory =
                Mockito.mock(
                        PlayerDirectory.class);

        chatProcessor =
                new RecordingChatProcessor();

        repository =
                new TaggedMessageRepository(
                        100);

        service =
                new NativeBootstrapService(
                        client,
                        playerDirectory,
                        chatProcessor,
                        repository);
    }

    /*
     * TESTS
     */

    @Test
    public void nullSupportedPredicateReturnsStartingMessageId()
    {
        final long result =
                service.bootstrap(
                        50L,
                        "Santa",
                        null);

        Assert.assertEquals(
                50L,
                result);

        Assert.assertTrue(
                repository.snapshot()
                        .isEmpty());

        Assert.assertTrue(
                chatProcessor.calls
                        .isEmpty());

        Mockito.verifyNoInteractions(
                playerDirectory);
    }

    @Test
    public void nullChatLineMapReturnsStartingMessageId()
    {
        Mockito.when(
                        client.getChatLineMap())
                .thenReturn(
                        null);

        final long result =
                service.bootstrap(
                        50L,
                        "Santa",
                        supportedAll());

        Assert.assertEquals(
                50L,
                result);

        Assert.assertTrue(
                repository.snapshot()
                        .isEmpty());
    }

    @Test
    public void emptyChatLineMapReturnsStartingMessageId()
    {
        Mockito.when(
                        client.getChatLineMap())
                .thenReturn(
                        Collections.emptyMap());

        final long result =
                service.bootstrap(
                        50L,
                        "Santa",
                        supportedAll());

        Assert.assertEquals(
                50L,
                result);

        Assert.assertTrue(
                repository.snapshot()
                        .isEmpty());
    }

    @Test
    public void unsupportedMessagesAreIgnored()
    {
        final MessageNode publicNode =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "Hello");

        final MessageNode privateNode =
                node(
                        11,
                        101,
                        ChatMessageType.PRIVATECHAT,
                        "Santa",
                        "Private");

        setBuffers(
                buffer(
                        publicNode,
                        privateNode));

        final long result =
                service.bootstrap(
                        20L,
                        "Party Hat",
                        type ->
                                type == ChatMessageType.PUBLICCHAT);

        Assert.assertEquals(
                21L,
                result);

        Assert.assertEquals(
                1,
                chatProcessor.calls
                        .size());

        Assert.assertEquals(
                ChatMessageType.PUBLICCHAT,
                chatProcessor.calls
                        .get(0)
                        .type);

        Assert.assertEquals(
                1,
                repository.snapshot()
                        .size());
    }

    @Test
    public void duplicateNativeMessageIdsAreProcessedOnce()
    {
        final MessageNode first =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "Hello");

        final MessageNode duplicate =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "Hello");

        setBuffers(
                buffer(
                        first),
                buffer(
                        duplicate));

        final long result =
                service.bootstrap(
                        30L,
                        "Santa",
                        supportedAll());

        Assert.assertEquals(
                31L,
                result);

        Assert.assertEquals(
                1,
                chatProcessor.calls
                        .size());

        Assert.assertEquals(
                1,
                repository.snapshot()
                        .size());
    }

    @Test
    public void restoresMessagesInTimestampThenNativeIdOrder()
    {
        final MessageNode newest =
                node(
                        30,
                        300,
                        ChatMessageType.PUBLICCHAT,
                        "Party Hat",
                        "Third");

        final MessageNode sameTimestampHigherId =
                node(
                        20,
                        200,
                        ChatMessageType.PUBLICCHAT,
                        "Santa",
                        "Second");

        final MessageNode sameTimestampLowerId =
                node(
                        10,
                        200,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "First");

        setBuffers(
                buffer(
                        newest,
                        sameTimestampHigherId,
                        sameTimestampLowerId));

        final long result =
                service.bootstrap(
                        100L,
                        "Santa Clause",
                        supportedAll());

        Assert.assertEquals(
                103L,
                result);

        Assert.assertEquals(
                3,
                chatProcessor.calls
                        .size());

        Assert.assertEquals(
                "First",
                chatProcessor.calls
                        .get(0)
                        .message);

        Assert.assertEquals(
                101L,
                chatProcessor.calls
                        .get(0)
                        .id);

        Assert.assertEquals(
                "Second",
                chatProcessor.calls
                        .get(1)
                        .message);

        Assert.assertEquals(
                102L,
                chatProcessor.calls
                        .get(1)
                        .id);

        Assert.assertEquals(
                "Third",
                chatProcessor.calls
                        .get(2)
                        .message);

        Assert.assertEquals(
                103L,
                chatProcessor.calls
                        .get(2)
                        .id);
    }

    @Test
    public void canonicalizesFormattedSenderName()
    {
        final MessageNode node =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "<img=0><col=ff0000>Zezima</col>",
                        "Hello");

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Santa",
                supportedAll());

        Assert.assertEquals(
                1,
                chatProcessor.calls
                        .size());

        Assert.assertEquals(
                "Zezima",
                chatProcessor.calls
                        .get(0)
                        .sender);
    }

    @Test
    public void convertsNativeBodyToSemanticPlainText()
    {
        final MessageNode node =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "<col=ff0000><at>Santa</col>");

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Party Hat",
                supportedAll());

        Assert.assertEquals(
                "@Santa",
                chatProcessor.calls
                        .get(0)
                        .message);
    }

    @Test
    public void runeLiteFormattedMessageIsFallbackWhenNativeValueIsNull()
    {
        final MessageNode node =
                Mockito.mock(
                        MessageNode.class);

        Mockito.when(
                        node.getId())
                .thenReturn(
                        10);

        Mockito.when(
                        node.getTimestamp())
                .thenReturn(
                        100);

        Mockito.when(
                        node.getType())
                .thenReturn(
                        ChatMessageType.PUBLICCHAT);

        Mockito.when(
                        node.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        node.getValue())
                .thenReturn(
                        null);

        Mockito.when(
                        node.getRuneLiteFormatMessage())
                .thenReturn(
                        "<col=ff0000>Santa</col>");

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Party Hat",
                supportedAll());

        Assert.assertEquals(
                "Santa",
                chatProcessor.calls
                        .get(0)
                        .message);
    }

    @Test
    public void missingNativeAndRuneLiteBodyBecomesEmptyMessage()
    {
        final MessageNode node =
                Mockito.mock(
                        MessageNode.class);

        Mockito.when(
                        node.getId())
                .thenReturn(
                        10);

        Mockito.when(
                        node.getTimestamp())
                .thenReturn(
                        100);

        Mockito.when(
                        node.getType())
                .thenReturn(
                        ChatMessageType.PUBLICCHAT);

        Mockito.when(
                        node.getName())
                .thenReturn(
                        "Zezima");

        Mockito.when(
                        node.getValue())
                .thenReturn(
                        null);

        Mockito.when(
                        node.getRuneLiteFormatMessage())
                .thenReturn(
                        null);

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Santa",
                supportedAll());

        Assert.assertEquals(
                "",
                chatProcessor.calls
                        .get(0)
                        .message);
    }

    @Test
    public void observesAccountTypeForAuthoritativeSender()
    {
        final MessageNode node =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "<img=2>Zezima",
                        "Hello");

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Santa",
                supportedAll());

        Mockito.verify(
                        playerDirectory)
                .observeAccountType(
                        "<img=2>Zezima");
    }

    @Test
    public void privateChatOutDoesNotObserveRecipientAccountType()
    {
        final MessageNode node =
                node(
                        10,
                        100,
                        ChatMessageType.PRIVATECHATOUT,
                        "<img=2>Zezima",
                        "Hello");

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Santa",
                supportedAll());

        Mockito.verify(
                        playerDirectory,
                        Mockito.never())
                .observeAccountType(
                        Mockito.anyString());

        Assert.assertEquals(
                1,
                chatProcessor.calls
                        .size());
    }

    @Test
    public void blankSenderDoesNotObserveAccountType()
    {
        final MessageNode node =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "   ",
                        "Hello");

        setBuffers(
                buffer(
                        node));

        service.bootstrap(
                0L,
                "Santa",
                supportedAll());

        Mockito.verify(
                        playerDirectory,
                        Mockito.never())
                .observeAccountType(
                        Mockito.anyString());
    }

    @Test
    public void processedMessagesAreAddedToRepository()
    {
        final MessageNode first =
                node(
                        10,
                        100,
                        ChatMessageType.PUBLICCHAT,
                        "Zezima",
                        "First");

        final MessageNode second =
                node(
                        20,
                        200,
                        ChatMessageType.PRIVATECHAT,
                        "Santa",
                        "Second");

        setBuffers(
                buffer(
                        second,
                        first));

        service.bootstrap(
                200L,
                "Party Hat",
                supportedAll());

        final List<TaggedMessage> snapshot =
                repository.snapshot();

        Assert.assertEquals(
                2,
                snapshot.size());

        Assert.assertEquals(
                201L,
                snapshot.get(
                                0)
                        .getId());

        Assert.assertEquals(
                "First",
                snapshot.get(
                                0)
                        .getOriginalMessage());

        Assert.assertEquals(
                202L,
                snapshot.get(
                                1)
                        .getId());

        Assert.assertEquals(
                "Second",
                snapshot.get(
                                1)
                        .getOriginalMessage());
    }

    /*
     * HELPERS
     */

    private void setBuffers(
            ChatLineBuffer... buffers)
    {
        final Map<Integer, ChatLineBuffer> map =
                new LinkedHashMap<>();

        for (int i = 0;
             i < buffers.length;
             i++)
        {
            map.put(
                    i,
                    buffers[i]);
        }

        Mockito.when(
                        client.getChatLineMap())
                .thenReturn(
                        map);
    }

    private static ChatLineBuffer buffer(
            MessageNode... nodes)
    {
        final ChatLineBuffer buffer =
                Mockito.mock(
                        ChatLineBuffer.class);

        Mockito.when(
                        buffer.getLines())
                .thenReturn(
                        nodes);

        return buffer;
    }

    private static MessageNode node(
            int id,
            int timestamp,
            ChatMessageType type,
            String name,
            String value)
    {
        final MessageNode node =
                Mockito.mock(
                        MessageNode.class);

        Mockito.when(
                        node.getId())
                .thenReturn(
                        id);

        Mockito.when(
                        node.getTimestamp())
                .thenReturn(
                        timestamp);

        Mockito.when(
                        node.getType())
                .thenReturn(
                        type);

        Mockito.when(
                        node.getName())
                .thenReturn(
                        name);

        Mockito.when(
                        node.getValue())
                .thenReturn(
                        value);

        return node;
    }

    private static Predicate<ChatMessageType> supportedAll()
    {
        return type -> true;
    }

    private static final class RecordingChatProcessor
            extends ChatProcessor
    {
        private final List<ProcessCall> calls =
                new ArrayList<>();

        private RecordingChatProcessor()
        {
            super(
                    null,
                    null,
                    null);
        }

        @Override
        public TaggedMessage process(
                long id,
                ChatMessageType type,
                String sender,
                String message,
                String localPlayerName)
        {
            calls.add(
                    new ProcessCall(
                            id,
                            type,
                            sender,
                            message,
                            localPlayerName));

            return TaggedMessage.builder()
                    .id(
                            id)
                    .type(
                            type)
                    .originalSender(
                            sender)
                    .canonicalSender(
                            sender)
                    .originalMessage(
                            message)
                    .timestamp(
                            Instant.EPOCH)
                    .references(
                            Collections.emptyList())
                    .localMentionMatch(
                            com.runetags.mention.LocalMentionMatch.none())
                    .build();
        }
    }

    private static final class ProcessCall
    {
        private final long id;
        private final ChatMessageType type;
        private final String sender;
        private final String message;
        private final String localPlayerName;

        private ProcessCall(
                long id,
                ChatMessageType type,
                String sender,
                String message,
                String localPlayerName)
        {
            this.id =
                    id;

            this.type =
                    type;

            this.sender =
                    sender;

            this.message =
                    message;

            this.localPlayerName =
                    localPlayerName;
        }
    }
}