package com.runetags.hiscores;

import com.runetags.player.AccountType;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class HiscoreProfileDataTest
{
    /*
     * TESTS
     */

    @Test
    public void preservesCombatAndTotalLevels()
    {
        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        null);

        Assert.assertEquals(
                Integer.valueOf(126),
                data.getCombatLevel());

        Assert.assertEquals(
                Integer.valueOf(2277),
                data.getTotalLevel());
    }

    @Test
    public void nullLevelsArePreserved()
    {
        final HiscoreProfileData data =
                new HiscoreProfileData(
                        null,
                        null,
                        AccountType.NORMAL,
                        null);

        Assert.assertNull(
                data.getCombatLevel());

        Assert.assertNull(
                data.getTotalLevel());
    }

    @Test
    public void preservesAccountType()
    {
        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.HARDCORE,
                        null);

        Assert.assertEquals(
                AccountType.HARDCORE,
                data.getAccountType());
    }

    @Test
    public void nullAccountTypeBecomesUnknown()
    {
        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        null,
                        null);

        Assert.assertEquals(
                AccountType.UNKNOWN,
                data.getAccountType());
    }

    @Test
    public void nullContextValuesBecomeEmptyMap()
    {
        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        null);

        Assert.assertNotNull(
                data.getContextValues());

        Assert.assertTrue(
                data.getContextValues()
                        .isEmpty());
    }

    @Test
    public void preservesContextValues()
    {
        final Map<String, Integer> values =
                new LinkedHashMap<>();

        values.put(
                "VORKATH",
                750);

        values.put(
                "ZULRAH",
                500);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        values);

        Assert.assertEquals(
                Integer.valueOf(750),
                data.getContextValue(
                        "VORKATH"));

        Assert.assertEquals(
                Integer.valueOf(500),
                data.getContextValue(
                        "ZULRAH"));
    }

    @Test
    public void getContextValueReturnsNullForNullKey()
    {
        final Map<String, Integer> values =
                new LinkedHashMap<>();

        values.put(
                "VORKATH",
                750);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        values);

        Assert.assertNull(
                data.getContextValue(
                        null));
    }

    @Test
    public void getContextValueReturnsNullForUnknownKey()
    {
        final Map<String, Integer> values =
                new LinkedHashMap<>();

        values.put(
                "VORKATH",
                750);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        values);

        Assert.assertNull(
                data.getContextValue(
                        "ZULRAH"));
    }

    @Test
    public void contextValuesAreDefensivelyCopied()
    {
        final Map<String, Integer> source =
                new LinkedHashMap<>();

        source.put(
                "VORKATH",
                750);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        source);

        source.put(
                "ZULRAH",
                500);

        source.put(
                "VORKATH",
                999);

        Assert.assertEquals(
                1,
                data.getContextValues()
                        .size());

        Assert.assertEquals(
                Integer.valueOf(750),
                data.getContextValue(
                        "VORKATH"));

        Assert.assertNull(
                data.getContextValue(
                        "ZULRAH"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void contextValuesAreUnmodifiable()
    {
        final Map<String, Integer> values =
                new LinkedHashMap<>();

        values.put(
                "VORKATH",
                750);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        values);

        data.getContextValues()
                .put(
                        "ZULRAH",
                        500);
    }

    @Test
    public void contextValuesPreserveInsertionOrder()
    {
        final Map<String, Integer> values =
                new LinkedHashMap<>();

        values.put(
                "ZULRAH",
                500);

        values.put(
                "VORKATH",
                750);

        values.put(
                "YAMA",
                25);

        final HiscoreProfileData data =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        values);

        final Iterator<String> keys =
                data.getContextValues()
                        .keySet()
                        .iterator();

        Assert.assertEquals(
                "ZULRAH",
                keys.next());

        Assert.assertEquals(
                "VORKATH",
                keys.next());

        Assert.assertEquals(
                "YAMA",
                keys.next());

        Assert.assertFalse(
                keys.hasNext());
    }

    @Test
    public void equalityIncludesAllProfileData()
    {
        final Map<String, Integer> firstValues =
                new LinkedHashMap<>();

        firstValues.put(
                "VORKATH",
                750);

        final Map<String, Integer> secondValues =
                new LinkedHashMap<>();

        secondValues.put(
                "VORKATH",
                750);

        final HiscoreProfileData first =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.IRONMAN,
                        firstValues);

        final HiscoreProfileData second =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.IRONMAN,
                        secondValues);

        Assert.assertEquals(
                first,
                second);

        Assert.assertEquals(
                first.hashCode(),
                second.hashCode());
    }

    @Test
    public void differentProfileDataIsNotEqual()
    {
        final HiscoreProfileData first =
                new HiscoreProfileData(
                        126,
                        2277,
                        AccountType.NORMAL,
                        null);

        final HiscoreProfileData second =
                new HiscoreProfileData(
                        110,
                        2100,
                        AccountType.IRONMAN,
                        null);

        Assert.assertNotEquals(
                first,
                second);
    }

    /*
     * HELPERS
     */
}