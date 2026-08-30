package com.runetags.chat;

import org.junit.Assert;
import org.junit.Test;

public class MessageMarkupMapTest
{
    @Test
    public void mapsPlainMessage()
    {
        MessageMarkupMap map = MessageMarkupMap.create("hello world");

        Assert.assertTrue(map.matchesPlain("hello world"));
        Assert.assertEquals(0, map.rawBoundary(0));
        Assert.assertEquals(5, map.rawBoundary(5));
        Assert.assertEquals(11, map.rawBoundary(11));
    }

    @Test
    public void skipsColorMarkup()
    {
        MessageMarkupMap map =
            MessageMarkupMap.create("<col=ff0000>hello</col> world");

        Assert.assertTrue(map.matchesPlain("hello world"));

        int helloStart = map.rawBoundary(0);
        int helloEnd = map.rawBoundary(5);

        Assert.assertEquals("<col=ff0000>".length(), helloStart);
        Assert.assertTrue(helloEnd > helloStart);
    }
}
