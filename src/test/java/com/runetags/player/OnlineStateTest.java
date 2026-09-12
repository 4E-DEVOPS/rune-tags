package com.runetags.player;

import org.junit.Assert;
import org.junit.Test;

public class OnlineStateTest
{
    /*
     * TESTS
     */

    @Test
    public void enumContainsExpectedStates()
    {
        Assert.assertArrayEquals(
                new OnlineState[]
                        {
                                OnlineState.ONLINE,
                                OnlineState.OFFLINE,
                                OnlineState.UNKNOWN
                        },
                OnlineState.values());
    }

    @Test
    public void valueOfResolvesEveryState()
    {
        for (OnlineState state
                : OnlineState.values())
        {
            Assert.assertSame(
                    state,
                    OnlineState.valueOf(
                            state.name()));
        }
    }

    @Test
    public void statesRemainDistinct()
    {
        Assert.assertNotEquals(
                OnlineState.ONLINE,
                OnlineState.OFFLINE);

        Assert.assertNotEquals(
                OnlineState.ONLINE,
                OnlineState.UNKNOWN);

        Assert.assertNotEquals(
                OnlineState.OFFLINE,
                OnlineState.UNKNOWN);
    }
}