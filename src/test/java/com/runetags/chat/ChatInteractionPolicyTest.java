package com.runetags.chat;

import com.runetags.Configurations;
import com.runetags.config.ClickablePlayerMode;
import com.runetags.reference.PlayerReference;
import com.runetags.reference.ReferenceType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ChatInteractionPolicyTest
{
    private Configurations config;

    @Before
    public void setUp()
    {
        config =
                Mockito.mock(
                        Configurations.class);
    }

    /*
     * TESTS
     */

    @Test
    public void nullReferenceIsNotClickable()
    {
        Assert.assertFalse(
                ChatInteractionPolicy.isClickable(
                        null,
                        config));
    }

    @Test
    public void nullConfigIsNotClickable()
    {
        Assert.assertFalse(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.MENTION),
                        null));
    }

    @Test
    public void allModeAllowsSender()
    {
        clickableMode(
                ClickablePlayerMode.ALL);

        Assert.assertTrue(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.SENDER),
                        config));
    }

    @Test
    public void allModeAllowsMention()
    {
        clickableMode(
                ClickablePlayerMode.ALL);

        Assert.assertTrue(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.MENTION),
                        config));
    }

    @Test
    public void allModeAllowsTag()
    {
        clickableMode(
                ClickablePlayerMode.ALL);

        Assert.assertTrue(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.TAG),
                        config));
    }

    @Test
    public void mentionsModeAllowsMentionsAndTagsOnly()
    {
        clickableMode(
                ClickablePlayerMode.MENTIONS);

        Assert.assertFalse(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.SENDER),
                        config));

        Assert.assertTrue(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.MENTION),
                        config));

        Assert.assertTrue(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.TAG),
                        config));
    }

    @Test
    public void taggedOnlyModeAllowsTagsOnly()
    {
        clickableMode(
                ClickablePlayerMode.TAGGED_ONLY);

        Assert.assertFalse(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.SENDER),
                        config));

        Assert.assertFalse(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.MENTION),
                        config));

        Assert.assertTrue(
                ChatInteractionPolicy.isClickable(
                        reference(
                                ReferenceType.TAG),
                        config));
    }

    /*
     * HELPERS
     */

    private void clickableMode(
            ClickablePlayerMode mode)
    {
        Mockito.when(
                        config.clickablePlayers())
                .thenReturn(
                        mode);
    }

    private static PlayerReference reference(
            ReferenceType type)
    {
        return PlayerReference.builder()
                .rawText(
                        "Zezima")
                .normalizedToken(
                        "Zezima")
                .lookupName(
                        "Zezima")
                .startOffset(
                        0)
                .endOffset(
                        6)
                .type(
                        type)
                .locallyResolved(
                        false)
                .identity(
                        null)
                .chatType(
                        null)
                .build();
    }
}