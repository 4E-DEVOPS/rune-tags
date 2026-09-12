package com.runetags.hiscores;

import com.runetags.Configurations;
import com.runetags.config.LookupProvider;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class PlayerLookupServiceTest
{
    /*
     * TESTS
     */

    @Test
    public void nullNameDoesNotReadLookupProvider()
    {
        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        final PlayerLookupService service =
                new PlayerLookupService(
                        config);

        service.lookup(
                null);

        Mockito.verifyNoInteractions(
                config);
    }

    @Test
    public void blankNameDoesNotReadLookupProvider()
    {
        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        final PlayerLookupService service =
                new PlayerLookupService(
                        config);

        service.lookup(
                "   ");

        Mockito.verifyNoInteractions(
                config);
    }

    @Test
    public void nonBlankNameReadsConfiguredProvider()
    {
        final Configurations config =
                Mockito.mock(
                        Configurations.class);

        Mockito.when(
                        config.lookupProvider())
                .thenReturn(
                        LookupProvider.HISCORES);

        final PlayerLookupService service =
                new PlayerLookupService(
                        config);

        /*
         * Verify provider routing without exercising LinkBrowser.browse(),
         * which would launch an external browser from this unit test.
         */
        try
        {
            service.lookup(
                    "Santa");
        }
        catch (RuntimeException ignored)
        {
            /*
             * LinkBrowser may be unavailable outside a running RuneLite desktop.
             */
        }

        Mockito.verify(
                        config,
                        Mockito.times(
                                1))
                .lookupProvider();
    }

    @Test
    public void pathEncodingPreservesSimpleName()
            throws Exception
    {
        Assert.assertEquals(
                "Zezima",
                encodePath(
                        "Zezima"));
    }

    @Test
    public void pathEncodingTrimsOuterWhitespace()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%20Clause",
                encodePath(
                        "   Santa Clause   "));
    }

    @Test
    public void pathEncodingUsesPercentTwentyForSpaces()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%20Clause",
                encodePath(
                        "Santa Clause"));
    }

    @Test
    public void pathEncodingPreservesUnderscores()
            throws Exception
    {
        Assert.assertEquals(
                "Santa_Clause",
                encodePath(
                        "Santa_Clause"));
    }

    @Test
    public void pathEncodingEscapesPlusCharacter()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%2BClause",
                encodePath(
                        "Santa+Clause"));
    }

    @Test
    public void pathEncodingEscapesAmpersand()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%26Clause",
                encodePath(
                        "Santa&Clause"));
    }

    @Test
    public void pathEncodingEscapesQuestionMark()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%3FClause",
                encodePath(
                        "Santa?Clause"));
    }

    @Test
    public void queryEncodingPreservesSimpleName()
            throws Exception
    {
        Assert.assertEquals(
                "Zezima",
                encodeQuery(
                        "Zezima"));
    }

    @Test
    public void queryEncodingTrimsOuterWhitespace()
            throws Exception
    {
        Assert.assertEquals(
                "Santa+Clause",
                encodeQuery(
                        "   Santa Clause   "));
    }

    @Test
    public void queryEncodingUsesPlusForSpaces()
            throws Exception
    {
        Assert.assertEquals(
                "Santa+Clause",
                encodeQuery(
                        "Santa Clause"));
    }

    @Test
    public void queryEncodingPreservesUnderscores()
            throws Exception
    {
        Assert.assertEquals(
                "Santa_Clause",
                encodeQuery(
                        "Santa_Clause"));
    }

    @Test
    public void queryEncodingEscapesPlusCharacter()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%2BClause",
                encodeQuery(
                        "Santa+Clause"));
    }

    @Test
    public void queryEncodingEscapesAmpersand()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%26Clause",
                encodeQuery(
                        "Santa&Clause"));
    }

    @Test
    public void queryEncodingEscapesEqualsCharacter()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%3DClause",
                encodeQuery(
                        "Santa=Clause"));
    }

    @Test
    public void pathAndQueryEncodingDifferOnlyForSpaces()
            throws Exception
    {
        Assert.assertEquals(
                "Santa%20Clause",
                encodePath(
                        "Santa Clause"));

        Assert.assertEquals(
                "Santa+Clause",
                encodeQuery(
                        "Santa Clause"));
    }

    /*
     * HELPERS
     */

    private static String encodePath(
            String value)
            throws Exception
    {
        final Method method =
                PlayerLookupService.class
                        .getDeclaredMethod(
                                "encodePath",
                                String.class);

        method.setAccessible(
                true);

        return (String) method.invoke(
                null,
                value);
    }

    private static String encodeQuery(
            String value)
            throws Exception
    {
        final Method method =
                PlayerLookupService.class
                        .getDeclaredMethod(
                                "encodeQuery",
                                String.class);

        method.setAccessible(
                true);

        return (String) method.invoke(
                null,
                value);
    }
}