package com.runetags.chat;

import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ChatHitboxRegistryTest
{
    /*
     * TESTS
     */

    @Test
    public void startsEmpty()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        Assert.assertTrue(
                registry.snapshot()
                        .isEmpty());

        Assert.assertFalse(
                registry.find(
                                new Point(
                                        10,
                                        10))
                        .isPresent());
    }

    @Test
    public void replaceStoresHitboxes()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        final ReferenceHitbox first =
                hitbox(
                        1L,
                        new Rectangle(
                                0,
                                0,
                                20,
                                20),
                        "Zezima");

        final ReferenceHitbox second =
                hitbox(
                        2L,
                        new Rectangle(
                                30,
                                0,
                                20,
                                20),
                        "Santa");

        registry.replace(
                Arrays.asList(
                        first,
                        second));

        final List<ReferenceHitbox> snapshot =
                registry.snapshot();

        Assert.assertEquals(
                2,
                snapshot.size());

        Assert.assertSame(
                first,
                snapshot.get(
                        0));

        Assert.assertSame(
                second,
                snapshot.get(
                        1));
    }

    @Test
    public void replaceUsesDefensiveCopy()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        final ReferenceHitbox first =
                hitbox(
                        1L,
                        new Rectangle(
                                0,
                                0,
                                20,
                                20),
                        "Zezima");

        final List<ReferenceHitbox> source =
                new ArrayList<>();

        source.add(
                first);

        registry.replace(
                source);

        source.clear();

        Assert.assertEquals(
                1,
                registry.snapshot()
                        .size());

        Assert.assertSame(
                first,
                registry.snapshot()
                        .get(
                                0));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotIsUnmodifiable()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        registry.replace(
                Arrays.asList(
                        hitbox(
                                1L,
                                new Rectangle(
                                        0,
                                        0,
                                        20,
                                        20),
                                "Zezima")));

        registry.snapshot()
                .add(
                        hitbox(
                                2L,
                                new Rectangle(
                                        30,
                                        0,
                                        20,
                                        20),
                                "Santa"));
    }

    @Test
    public void findReturnsContainingHitbox()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        final ReferenceHitbox first =
                hitbox(
                        1L,
                        new Rectangle(
                                0,
                                0,
                                20,
                                20),
                        "Zezima");

        final ReferenceHitbox second =
                hitbox(
                        2L,
                        new Rectangle(
                                30,
                                0,
                                20,
                                20),
                        "Santa");

        registry.replace(
                Arrays.asList(
                        first,
                        second));

        final ReferenceHitbox found =
                registry.find(
                                new Point(
                                        35,
                                        10))
                        .orElse(
                                null);

        Assert.assertNotNull(
                found);

        Assert.assertSame(
                second,
                found);

        Assert.assertEquals(
                2L,
                found.getMessageId());
    }

    @Test
    public void findReturnsFirstContainingHitbox()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        final ReferenceHitbox first =
                hitbox(
                        1L,
                        new Rectangle(
                                0,
                                0,
                                30,
                                30),
                        "Zezima");

        final ReferenceHitbox second =
                hitbox(
                        2L,
                        new Rectangle(
                                10,
                                10,
                                30,
                                30),
                        "Santa");

        registry.replace(
                Arrays.asList(
                        first,
                        second));

        final ReferenceHitbox found =
                registry.find(
                                new Point(
                                        15,
                                        15))
                        .orElse(
                                null);

        Assert.assertNotNull(
                found);

        Assert.assertSame(
                first,
                found);
    }

    @Test
    public void findReturnsEmptyWhenPointMatchesNothing()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        registry.replace(
                Arrays.asList(
                        hitbox(
                                1L,
                                new Rectangle(
                                        0,
                                        0,
                                        20,
                                        20),
                                "Zezima"),
                        hitbox(
                                2L,
                                new Rectangle(
                                        30,
                                        0,
                                        20,
                                        20),
                                "Santa")));

        Assert.assertFalse(
                registry.find(
                                new Point(
                                        100,
                                        100))
                        .isPresent());
    }

    @Test
    public void nullPointReturnsEmpty()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        registry.replace(
                Arrays.asList(
                        hitbox(
                                1L,
                                new Rectangle(
                                        0,
                                        0,
                                        20,
                                        20),
                                "Zezima")));

        Assert.assertFalse(
                registry.find(
                                null)
                        .isPresent());
    }

    @Test
    public void hitboxWithNullBoundsDoesNotMatch()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        registry.replace(
                Arrays.asList(
                        hitbox(
                                1L,
                                null,
                                "Zezima")));

        Assert.assertFalse(
                registry.find(
                                new Point(
                                        10,
                                        10))
                        .isPresent());
    }

    @Test
    public void clearRemovesAllHitboxes()
    {
        final ChatHitboxRegistry registry =
                new ChatHitboxRegistry();

        registry.replace(
                Arrays.asList(
                        hitbox(
                                1L,
                                new Rectangle(
                                        0,
                                        0,
                                        20,
                                        20),
                                "Zezima"),
                        hitbox(
                                2L,
                                new Rectangle(
                                        30,
                                        0,
                                        20,
                                        20),
                                "Santa")));

        registry.clear();

        Assert.assertTrue(
                registry.snapshot()
                        .isEmpty());

        Assert.assertFalse(
                registry.find(
                                new Point(
                                        10,
                                        10))
                        .isPresent());
    }

    /*
     * HELPERS
     */

    private static ReferenceHitbox hitbox(
            long messageId,
            Rectangle bounds,
            String playerName)
    {
        final PlayerReference reference =
                PlayerReference.builder()
                        .rawText(
                                playerName)
                        .normalizedToken(
                                playerName)
                        .lookupName(
                                playerName)
                        .startOffset(
                                0)
                        .endOffset(
                                playerName.length())
                        .type(
                                ReferenceType.MENTION)
                        .locallyResolved(
                                false)
                        .identity(
                                null)
                        .chatType(
                                null)
                        .build();

        return new ReferenceHitbox(
                messageId,
                bounds,
                reference,
                ReferenceLayoutService.Surface.CHATBOX);
    }
}