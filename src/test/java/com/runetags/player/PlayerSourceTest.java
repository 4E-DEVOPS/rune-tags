package com.runetags.player;

import org.junit.Assert;
import org.junit.Test;

public class PlayerSourceTest
{
    /*
     * TESTS
     */

    @Test
    public void enumContainsExpectedSources()
    {
        Assert.assertArrayEquals(
                new PlayerSource[]
                        {
                                PlayerSource.FRIEND,
                                PlayerSource.CLAN,
                                PlayerSource.GUEST_CLAN,
                                PlayerSource.FRIENDS_CHAT,
                                PlayerSource.PARTY,
                                PlayerSource.NEARBY,
                                PlayerSource.REMOTE
                        },
                PlayerSource.values());
    }

    @Test
    public void valueOfResolvesEverySource()
    {
        for (PlayerSource source
                : PlayerSource.values())
        {
            Assert.assertSame(
                    source,
                    PlayerSource.valueOf(
                            source.name()));
        }
    }

    @Test
    public void everyExpectedSourceIsDistinct()
    {
        Assert.assertEquals(
                7,
                PlayerSource.values()
                        .length);

        for (PlayerSource left
                : PlayerSource.values())
        {
            for (PlayerSource right
                    : PlayerSource.values())
            {
                if (left == right)
                {
                    continue;
                }

                Assert.assertNotEquals(
                        left,
                        right);
            }
        }
    }
}