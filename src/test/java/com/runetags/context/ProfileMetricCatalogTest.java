package com.runetags.context;

import com.runetags.context.ProfileMetricCatalog.ContextOverride;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ProfileMetricCatalogTest
{
    /*
     * TESTS
     */

    @Test
    public void nullLocationReturnsEmptyMetrics()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        null);

        Assert.assertNotNull(
                metrics);

        Assert.assertTrue(
                metrics.isEmpty());
    }

    @Test
    public void unmappedLocationReturnsEmptyMetrics()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Definitely Unknown Place");

        Assert.assertNotNull(
                metrics);

        Assert.assertTrue(
                metrics.isEmpty());
    }

    @Test
    public void locationLookupIsCaseInsensitive()
    {
        final List<ProfileMetric> normal =
                ProfileMetricCatalog.metricsForLocation(
                        "Chambers of Xeric");

        final List<ProfileMetric> lower =
                ProfileMetricCatalog.metricsForLocation(
                        "chambers of xeric");

        final List<ProfileMetric> upper =
                ProfileMetricCatalog.metricsForLocation(
                        "CHAMBERS OF XERIC");

        Assert.assertEquals(
                normal,
                lower);

        Assert.assertEquals(
                normal,
                upper);
    }

    @Test
    public void runecraftingLocationUsesRunecraftingMetric()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Abyss");

        Assert.assertEquals(
                1,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Runecrafting",
                "RUNECRAFT");
    }

    @Test
    public void agilityLocationUsesAgilityMetric()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Agility Pyramid");

        Assert.assertEquals(
                1,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Agility",
                "AGILITY");
    }

    @Test
    public void slayerLocationUsesSlayerMetric()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Brimhaven Dungeon");

        Assert.assertEquals(
                1,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Slayer",
                "SLAYER");
    }

    @Test
    public void bossLocationUsesBossKillcountMetric()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Cerberus' Lair");

        Assert.assertEquals(
                1,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Cerberus KC",
                "CERBERUS");
    }

    @Test
    public void chambersOfXericContainsRaidMetricsInOrder()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Chambers of Xeric");

        Assert.assertEquals(
                2,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "CoX",
                "CHAMBERS_OF_XERIC");

        assertMetric(
                metrics.get(1),
                "CM",
                "CHAMBERS_OF_XERIC_CHALLENGE_MODE");
    }

    @Test
    public void greatOlmLocationMatchesChambersMetrics()
    {
        final List<ProfileMetric> chambers =
                ProfileMetricCatalog.metricsForLocation(
                        "Chambers of Xeric");

        final List<ProfileMetric> olm =
                ProfileMetricCatalog.metricsForLocation(
                        "Great Olm");

        Assert.assertEquals(
                chambers,
                olm);
    }

    @Test
    public void theatreOfBloodContainsNormalAndHardModeMetrics()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Ver Sinhaza");

        Assert.assertEquals(
                2,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "ToB",
                "THEATRE_OF_BLOOD");

        assertMetric(
                metrics.get(1),
                "HMT",
                "THEATRE_OF_BLOOD_HARD_MODE");
    }

    @Test
    public void tombsOfAmascutContainsNormalAndExpertMetrics()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Tombs of Amascut");

        Assert.assertEquals(
                2,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "ToA",
                "TOMBS_OF_AMASCUT");

        assertMetric(
                metrics.get(1),
                "Expert",
                "TOMBS_OF_AMASCUT_EXPERT");
    }

    @Test
    public void tombsBossRoomMatchesRaidMetrics()
    {
        final List<ProfileMetric> raid =
                ProfileMetricCatalog.metricsForLocation(
                        "Tombs of Amascut");

        final List<ProfileMetric> akkha =
                ProfileMetricCatalog.metricsForLocation(
                        "Akkha");

        Assert.assertEquals(
                raid,
                akkha);
    }

    @Test
    public void godWarsDungeonContainsExpectedFiveMetricsInOrder()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "God Wars Dungeon");

        Assert.assertEquals(
                5,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "General Graardor",
                "GENERAL_GRAARDOR");

        assertMetric(
                metrics.get(1),
                "Kree'Arra",
                "KREEARRA");

        assertMetric(
                metrics.get(2),
                "K'ril Tsutsaroth",
                "KRIL_TSUTSAROTH");

        assertMetric(
                metrics.get(3),
                "Commander Zilyana",
                "COMMANDER_ZILYANA");

        assertMetric(
                metrics.get(4),
                "Nex",
                "NEX");
    }

    @Test
    public void ancientPrisonContainsOnlyNex()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Ancient Prison");

        Assert.assertEquals(
                1,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Nex",
                "NEX");
    }

    @Test
    public void bountyHunterLocationContainsHunterAndRogueMetrics()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Daimon's Crater");

        Assert.assertEquals(
                2,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Hunter",
                "BOUNTY_HUNTER_HUNTER");

        assertMetric(
                metrics.get(1),
                "Rogue",
                "BOUNTY_HUNTER_ROGUE");
    }

    @Test
    public void zulAndraContainsZulrahMetric()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Zul-Andra");

        Assert.assertEquals(
                1,
                metrics.size());

        assertMetric(
                metrics.get(0),
                "Zulrah KC",
                "ZULRAH");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void mappedLocationMetricsAreUnmodifiable()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Chambers of Xeric");

        metrics.add(
                new ProfileMetric(
                        "Injected",
                        "INJECTED"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void unmappedLocationMetricsAreUnmodifiable()
    {
        final List<ProfileMetric> metrics =
                ProfileMetricCatalog.metricsForLocation(
                        "Definitely Unknown Place");

        metrics.add(
                new ProfileMetric(
                        "Injected",
                        "INJECTED"));
    }

    @Test
    public void nullNpcReturnsNoOverride()
    {
        Assert.assertNull(
                ProfileMetricCatalog.overrideForNpc(
                        null));
    }

    @Test
    public void unmappedNpcReturnsNoOverride()
    {
        Assert.assertNull(
                ProfileMetricCatalog.overrideForNpc(
                        "Definitely Unknown NPC"));
    }

    @Test
    public void npcLookupIsCaseInsensitive()
    {
        final ContextOverride normal =
                ProfileMetricCatalog.overrideForNpc(
                        "Vorkath");

        final ContextOverride lower =
                ProfileMetricCatalog.overrideForNpc(
                        "vorkath");

        final ContextOverride upper =
                ProfileMetricCatalog.overrideForNpc(
                        "VORKATH");

        Assert.assertNotNull(
                normal);

        Assert.assertEquals(
                normal,
                lower);

        Assert.assertEquals(
                normal,
                upper);
    }

    @Test
    public void vorkathNpcOverridesLocationAndMetric()
    {
        final ContextOverride override =
                ProfileMetricCatalog.overrideForNpc(
                        "Vorkath");

        Assert.assertNotNull(
                override);

        Assert.assertEquals(
                "Ungael",
                override.getLocationName());

        Assert.assertEquals(
                1,
                override.getMetrics()
                        .size());

        assertMetric(
                override.getMetrics()
                        .get(0),
                "Vorkath KC",
                "VORKATH");
    }

    @Test
    public void greatOlmNpcContainsBothChambersMetrics()
    {
        final ContextOverride override =
                ProfileMetricCatalog.overrideForNpc(
                        "Great Olm");

        Assert.assertNotNull(
                override);

        Assert.assertEquals(
                "Great Olm",
                override.getLocationName());

        Assert.assertEquals(
                2,
                override.getMetrics()
                        .size());

        assertMetric(
                override.getMetrics()
                        .get(0),
                "CoX",
                "CHAMBERS_OF_XERIC");

        assertMetric(
                override.getMetrics()
                        .get(1),
                "CM",
                "CHAMBERS_OF_XERIC_CHALLENGE_MODE");
    }

    @Test
    public void akkhaNpcContainsBothTombsMetrics()
    {
        final ContextOverride override =
                ProfileMetricCatalog.overrideForNpc(
                        "Akkha");

        Assert.assertNotNull(
                override);

        Assert.assertEquals(
                "Akkha",
                override.getLocationName());

        Assert.assertEquals(
                2,
                override.getMetrics()
                        .size());

        assertMetric(
                override.getMetrics()
                        .get(0),
                "ToA",
                "TOMBS_OF_AMASCUT");

        assertMetric(
                override.getMetrics()
                        .get(1),
                "Expert",
                "TOMBS_OF_AMASCUT_EXPERT");
    }

    @Test
    public void wildernessBossNpcUsesSpecificBossContext()
    {
        final ContextOverride override =
                ProfileMetricCatalog.overrideForNpc(
                        "Artio");

        Assert.assertNotNull(
                override);

        Assert.assertEquals(
                "Artio",
                override.getLocationName());

        Assert.assertEquals(
                1,
                override.getMetrics()
                        .size());

        assertMetric(
                override.getMetrics()
                        .get(0),
                "Artio KC",
                "ARTIO");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void npcOverrideMetricsAreUnmodifiable()
    {
        final ContextOverride override =
                ProfileMetricCatalog.overrideForNpc(
                        "Vorkath");

        Assert.assertNotNull(
                override);

        override.getMetrics()
                .add(
                        new ProfileMetric(
                                "Injected",
                                "INJECTED"));
    }

    /*
     * HELPERS
     */

    private static void assertMetric(
            ProfileMetric metric,
            String expectedLabel,
            String expectedHiscoreSkillName)
    {
        Assert.assertNotNull(
                metric);

        Assert.assertEquals(
                expectedLabel,
                metric.getLabel());

        Assert.assertEquals(
                expectedHiscoreSkillName,
                metric.getHiscoreSkillName());
    }
}