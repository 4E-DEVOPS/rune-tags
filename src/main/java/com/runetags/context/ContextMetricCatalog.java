package com.runetags.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sparse contextual metric annotations layered on top of the full location map.
 *
 * The full location database remains independent from contextual metrics.
 *
 * Context metrics may represent:
 *
 * - boss killcounts
 * - minigame/activity scores
 * - context-relevant skill levels
 */
public final class ContextMetricCatalog
{
    /*
     * ---------------------------------------------------------------------
     * Shared activity / raid metrics
     * ---------------------------------------------------------------------
     */

    private static final ContextMetric CM =
            metric(
                    "CM",
                    "CHAMBERS_OF_XERIC_CHALLENGE_MODE");

    private static final ContextMetric COX =
            metric(
                    "CoX",
                    "CHAMBERS_OF_XERIC");

    private static final ContextMetric HMT =
            metric(
                    "HMT",
                    "THEATRE_OF_BLOOD_HARD_MODE");

    private static final ContextMetric LMS =
            metric(
                    "LMS",
                    "LAST_MAN_STANDING");

    private static final ContextMetric LUNAR_CHESTS =
            metric(
                    "Lunar Chests",
                    "LUNAR_CHESTS");

    private static final ContextMetric NEX =
            metric(
                    "Nex",
                    "NEX");

    private static final ContextMetric TOA =
            metric(
                    "ToA",
                    "TOMBS_OF_AMASCUT");

    private static final ContextMetric TOA_EXPERT =
            metric(
                    "Expert",
                    "TOMBS_OF_AMASCUT_EXPERT");

    private static final ContextMetric TOB =
            metric(
                    "ToB",
                    "THEATRE_OF_BLOOD");

    private static final ContextMetric VORKATH =
            metric(
                    "Vorkath KC",
                    "VORKATH");

    private static final ContextMetric ZULRAH =
            metric(
                    "Zulrah KC",
                    "ZULRAH");

    /*
     * ---------------------------------------------------------------------
     * Shared skill metrics
     * ---------------------------------------------------------------------
     */

    private static final ContextMetric AGILITY =
            metric(
                    "Agility",
                    "AGILITY");

    private static final ContextMetric COOKING =
            metric(
                    "Cooking",
                    "COOKING");

    private static final ContextMetric CRAFTING =
            metric(
                    "Crafting",
                    "CRAFTING");

    private static final ContextMetric FARMING =
            metric(
                    "Farming",
                    "FARMING");

    private static final ContextMetric FISHING =
            metric(
                    "Fishing",
                    "FISHING");

    private static final ContextMetric FLETCHING =
            metric(
                    "Fletching",
                    "FLETCHING");

    private static final ContextMetric HERBLORE =
            metric(
                    "Herblore",
                    "HERBLORE");

    private static final ContextMetric HUNTER =
            metric(
                    "Hunter",
                    "HUNTER");

    private static final ContextMetric MAGIC =
            metric(
                    "Magic",
                    "MAGIC");

    private static final ContextMetric MINING =
            metric(
                    "Mining",
                    "MINING");

    private static final ContextMetric PRAYER =
            metric(
                    "Prayer",
                    "PRAYER");

    private static final ContextMetric RANGED =
            metric(
                    "Ranged",
                    "RANGED");

    private static final ContextMetric RUNECRAFT =
            metric(
                    "Runecrafting",
                    "RUNECRAFT");

    private static final ContextMetric SAILING =
            metric(
                    "Sailing",
                    "SAILING");

    private static final ContextMetric SLAYER =
            metric(
                    "Slayer",
                    "SLAYER");

    private static final ContextMetric SMITHING =
            metric(
                    "Smithing",
                    "SMITHING");

    private static final ContextMetric STRENGTH =
            metric(
                    "Strength",
                    "STRENGTH");

    private static final ContextMetric THIEVING =
            metric(
                    "Thieving",
                    "THIEVING");

    private static final ContextMetric WOODCUTTING =
            metric(
                    "Woodcutting",
                    "WOODCUTTING");

    private static final Map<String, List<ContextMetric>> BY_LOCATION =
            buildLocationMetrics();

    private static final Map<String, ContextOverride> BY_NPC =
            buildNpcOverrides();

    private ContextMetricCatalog()
    {
    }

    public static List<ContextMetric> metricsForLocation(
            String locationName)
    {
        if (locationName == null)
        {
            return Collections.emptyList();
        }

        return BY_LOCATION.getOrDefault(
                locationName.toLowerCase(Locale.ROOT),
                Collections.emptyList());
    }

    public static ContextOverride overrideForNpc(
            String npcName)
    {
        if (npcName == null)
        {
            return null;
        }

        return BY_NPC.get(
                npcName.toLowerCase(Locale.ROOT));
    }

    /*
     * ---------------------------------------------------------------------
     * Location mappings
     * ---------------------------------------------------------------------
     */

    private static Map<String, List<ContextMetric>> buildLocationMetrics()
    {
        final Map<String, List<ContextMetric>> map =
                new HashMap<>();

        /*
         * Abyss
         */
        put(
                map,
                "Abyss",
                RUNECRAFT);

        /*
         * Abyssal Area
         */
        put(
                map,
                "Abyssal Area",
                RUNECRAFT);

        /*
         * Abyssal Sire
         */
        put(
                map,
                "Abyssal Nexus",
                metric(
                        "Abyssal Sire KC",
                        "ABYSSAL_SIRE"));

        /*
         * Agility Pyramid
         */
        put(
                map,
                "Agility Pyramid",
                AGILITY);

        /*
         * Air Altar
         */
        put(
                map,
                "Air Altar",
                RUNECRAFT);

        /*
         * Amoxliatl
         */
        put(
                map,
                "Amoxliatl",
                metric(
                        "Amoxliatl KC",
                        "AMOXLIATL"));

        /*
         * Ancient Prison
         */
        put(
                map,
                "Ancient Prison",
                NEX);

        /*
         * Ape Atoll
         */
        put(
                map,
                "Ape Atoll",
                AGILITY);

        /*
         * Araxxor
         */
        put(
                map,
                "Araxxor's Cavern",
                metric(
                        "Araxxor KC",
                        "ARAXXOR"));

        /*
         * Ardougne
         */
        put(
                map,
                "Ardougne",
                THIEVING);

        /*
         * Auburnvale
         */
        put(
                map,
                "Auburnvale",
                FLETCHING);

        /*
         * Barbarian Outpost
         */
        put(
                map,
                "Barbarian Outpost",
                AGILITY);

        /*
         * Barrows
         */
        put(
                map,
                "Barrows",
                metric(
                        "Barrows Chests",
                        "BARROWS_CHESTS"));

        /*
         * Baxtorian Falls
         */
        put(
                map,
                "Baxtorian Falls",
                FISHING);

        /*
         * Blast Mine
         */
        put(
                map,
                "Blast Mine",
                MINING);

        /*
         * Blast Furnace
         */
        put(
                map,
                "Blast Furnace",
                SMITHING);

        /*
         * Blood Altar
         */
        put(
                map,
                "Blood Altar",
                RUNECRAFT);

        /*
         * Body Altar
         */
        put(
                map,
                "Body Altar",
                RUNECRAFT);

        /*
         * Bounty Hunter
         */
        put(
                map,
                "Daimon's Crater",
                metric(
                        "Hunter",
                        "BOUNTY_HUNTER_HUNTER"),
                metric(
                        "Rogue",
                        "BOUNTY_HUNTER_ROGUE"));

        /*
         * Brimhaven Agility Arena
         */
        put(
                map,
                "Brimhaven Agility Arena",
                AGILITY);

        /*
         * Brutus
         */
        put(
                map,
                "Lumbridge Cow Pen",
                metric(
                        "Brutus KC",
                        "BRUTUS"));

        /*
         * Brimhaven Dungeon
         */
        put(
                map,
                "Brimhaven Dungeon",
                SLAYER);

        /*
         * Bryophyta
         */
        put(
                map,
                "Bryophyta's Lair",
                metric(
                        "Bryophyta KC",
                        "BRYOPHYTA"));

        /*
         * Canifis
         */
        put(
                map,
                "Canifis",
                AGILITY);

        /*
         * Catherby
         */
        put(
                map,
                "Catherby",
                FISHING);

        /*
         * Catacombs of Kourend
         */
        put(
                map,
                "Catacombs of Kourend",
                SLAYER);

        /*
         * Cerberus
         */
        put(
                map,
                "Cerberus' Lair",
                metric(
                        "Cerberus KC",
                        "CERBERUS"));

        /*
         * Chambers of Xeric
         */
        put(
                map,
                "Chambers of Xeric",
                COX,
                CM);

        put(
                map,
                "Great Olm",
                COX,
                CM);

        /*
         * Chaos Altar
         */
        put(
                map,
                "Chaos Altar",
                RUNECRAFT);

        /*
         * Colossal Wyrm Remains
         */
        put(
                map,
                "Colossal Wyrm Remains",
                AGILITY);

        /*
         * Corporeal Beast
         */
        put(
                map,
                "Corporeal Beast",
                metric(
                        "Corporeal Beast KC",
                        "CORPOREAL_BEAST"));

        /*
         * Cosmic Altar
         */
        put(
                map,
                "Cosmic Altar",
                RUNECRAFT);

        /*
         * Crafting Guild
         */
        put(
                map,
                "Crafting Guild",
                CRAFTING);

        /*
         * Dagannoth Kings
         */
        put(
                map,
                "Dagannoth Kings",
                metric(
                        "Prime",
                        "DAGANNOTH_PRIME"),
                metric(
                        "Rex",
                        "DAGANNOTH_REX"),
                metric(
                        "Supreme",
                        "DAGANNOTH_SUPREME"));

        /*
         * Death Altar
         */
        put(
                map,
                "Death Altar",
                RUNECRAFT);

        /*
         * Deepfin Point
         */
        put(
                map,
                "Deepfin Point",
                SAILING);

        /*
         * Desert Mining Camp
         */
        put(
                map,
                "Desert Mining Camp",
                MINING);

        /*
         * Deranged Archaeologist
         */
        put(
                map,
                "Fossil Island",
                metric(
                        "Deranged Archaeologist KC",
                        "DERANGED_ARCHAEOLOGIST"));

        /*
         * Dondakan's mine
         */
        put(
                map,
                "Dondakan's mine",
                MINING);

        /*
         * Dwarven Mine
         */
        put(
                map,
                "Dwarven Mine",
                MINING);

        /*
         * Doom of Mokhaiotl
         */
        put(
                map,
                "Doom of Mokhaiotl",
                metric(
                        "Doom of Mokhaiotl KC",
                        "DOOM_OF_MOKHAIOTL"));

        /*
         * Duke Sucellus / Phantom Muspah
         */
        put(
                map,
                "Ghorrock Prison",
                metric(
                        "Duke Sucellus KC",
                        "DUKE_SUCELLUS"),
                metric(
                        "Phantom Muspah KC",
                        "PHANTOM_MUSPAH"));

        /*
         * Earth Altar
         */
        put(
                map,
                "Earth Altar",
                RUNECRAFT);

        /*
         * Edgeville Dungeon / Obor
         */
        put(
                map,
                "Edgeville Dungeon",
                metric(
                        "Obor KC",
                        "OBOR"));

        /*
         * Emir's Arena
         */
        put(
                map,
                "Emir's Arena",
                metric(
                        "PvP Arena Rank",
                        "PVP_ARENA_RANK"));

        /*
         * Farming Guild
         */
        put(
                map,
                "Farming Guild",
                FARMING);

        /*
         * Fight Caves
         */
        put(
                map,
                "Fight Caves",
                metric(
                        "Jad KC",
                        "TZTOK_JAD"));

        /*
         * Fire Altar
         */
        put(
                map,
                "Fire Altar",
                RUNECRAFT);

        /*
         * Fishing Guild
         */
        put(
                map,
                "Fishing Guild",
                FISHING);

        /*
         * Fishing Platform
         */
        put(
                map,
                "Fishing Platform",
                FISHING);

        /*
         * Fishing Trawler
         */
        put(
                map,
                "Fishing Trawler",
                FISHING);

        /*
         * Forthos Dungeon
         */
        put(
                map,
                "Forthos Dungeon",
                metric(
                        "Sarachnis KC",
                        "SARACHNIS"));

        /*
         * Fortis Colosseum
         */
        put(
                map,
                "Fortis Colosseum",
                metric(
                        "Sol Heredit KC",
                        "SOL_HEREDIT"));

        /*
         * Fremennik Slayer Dungeon
         */
        put(
                map,
                "Fremennik Slayer Dungeon",
                SLAYER);

        /*
         * Giants' Foundry
         */
        put(
                map,
                "Giants' Foundry",
                SMITHING);

        /*
         * God Wars Dungeon
         */
        put(
                map,
                "God Wars Dungeon",
                metric(
                        "General Graardor",
                        "GENERAL_GRAARDOR"),
                metric(
                        "Kree'Arra",
                        "KREEARRA"),
                metric(
                        "K'ril Tsutsaroth",
                        "KRIL_TSUTSAROTH"),
                metric(
                        "Commander Zilyana",
                        "COMMANDER_ZILYANA"),
                NEX);

        /*
         * Grotesque Guardians
         */
        put(
                map,
                "Grotesque Guardians",
                metric(
                        "Grotesque Guardians KC",
                        "GROTESQUE_GUARDIANS"));

        /*
         * Guardians of the Rift
         */
        put(
                map,
                "Guardians of the Rift",
                metric(
                        "Rifts Closed",
                        "RIFTS_CLOSED"));

        /*
         * Hallowed Sepulchre
         */
        put(
                map,
                "Hallowed Sepulchre",
                AGILITY);

        /*
         * Hespori
         */
        put(
                map,
                "Hespori Lair",
                metric(
                        "Hespori KC",
                        "HESPORI"));

        /*
         * Hunter Guild
         */
        put(
                map,
                "Hunter Guild",
                HUNTER);

        /*
         * Hunter Guild Caverns
         */
        put(
                map,
                "Hunter Guild Caverns",
                HUNTER);

        /*
         * Iowerth Dungeon
         */
        put(
                map,
                "Iowerth Dungeon",
                SLAYER);

        /*
         * Isle of Souls
         */
        put(
                map,
                "Isle of Souls",
                metric(
                        "Soul Wars Zeal",
                        "SOUL_WARS_ZEAL"));

        /*
         * Jatizso Mine
         */
        put(
                map,
                "Jatizso Mine",
                MINING);

        /*
         * Kalphite Queen
         */
        put(
                map,
                "Kalphite Cave",
                metric(
                        "Kalphite Queen KC",
                        "KALPHITE_QUEEN"));

        put(
                map,
                "Kalphite Lair",
                metric(
                        "Kalphite Queen KC",
                        "KALPHITE_QUEEN"));

        /*
         * Karuulm Slayer Dungeon
         */
        put(
                map,
                "Karuulm Slayer Dungeon",
                metric(
                        "Alchemical Hydra KC",
                        "ALCHEMICAL_HYDRA"));

        /*
         * King Black Dragon
         */
        put(
                map,
                "King Black Dragon Lair",
                metric(
                        "King Black Dragon KC",
                        "KING_BLACK_DRAGON"));

        /*
         * Kraken
         */
        put(
                map,
                "Kraken Cove",
                metric(
                        "Kraken KC",
                        "KRAKEN"));

        /*
         * Last Man Standing
         */
        put(
                map,
                "Deserted Island",
                LMS);

        put(
                map,
                "Ferox Enclave",
                LMS);

        put(
                map,
                "Wild Varrock",
                LMS);

        /*
         * Law Altar
         */
        put(
                map,
                "Law Altar",
                RUNECRAFT);

        /*
         * Lizardman Caves
         */
        put(
                map,
                "Lizardman Caves",
                SLAYER);

        /*
         * Lizardman Temple
         */
        put(
                map,
                "Lizardman Temple",
                SLAYER);

        /*
         * Lunar Isle
         */
        put(
                map,
                "Lunar Isle",
                RUNECRAFT);

        /*
         * Mad Angel
         */
        put(
                map,
                "Wyrmscraig",
                metric(
                        "Mad Angel KC",
                        "MAD_ANGEL"));

        /*
         * Mage Arena Bank
         */
        put(
                map,
                "Mage Arena Bank",
                MAGIC);

        /*
         * Mage Training Arena
         */
        put(
                map,
                "Mage Training Arena",
                MAGIC);

        /*
         * Maggot King
         */
        put(
                map,
                "Sangvesti",
                metric(
                        "Maggot King KC",
                        "MAGGOT_KING"));

        /*
         * Mimic
         */
        put(
                map,
                "Watson's House",
                metric(
                        "Mimic KC",
                        "MIMIC"));

        /*
         * Mind Altar
         */
        put(
                map,
                "Mind Altar",
                RUNECRAFT);

        /*
         * Mining Guild
         */
        put(
                map,
                "Mining Guild",
                MINING);

        /*
         * Mole Lair
         */
        put(
                map,
                "Mole Lair",
                metric(
                        "Giant Mole KC",
                        "GIANT_MOLE"));

        /*
         * Monastery
         */
        put(
                map,
                "Monastery",
                PRAYER);

        /*
         * Moonrise Brewery and Winery
         */
        put(
                map,
                "Moonrise Brewery and Winery",
                HERBLORE);

        /*
         * Mor Ul Rek
         */
        put(
                map,
                "Mor Ul Rek",
                metric(
                        "Zuk KC",
                        "TZKAL_ZUK"));

        /*
         * Motherlode Mine
         */
        put(
                map,
                "Motherlode Mine",
                MINING);

        /*
         * Nardah
         */
        put(
                map,
                "Nardah",
                PRAYER);

        /*
         * Nature Altar
         */
        put(
                map,
                "Nature Altar",
                RUNECRAFT);

        /*
         * Ourania Cave
         */
        put(
                map,
                "Ourania Cave",
                RUNECRAFT);

        /*
         * Perilous Moons
         */
        put(
                map,
                "Blood Moon",
                LUNAR_CHESTS);

        put(
                map,
                "Blue Moon",
                LUNAR_CHESTS);

        put(
                map,
                "Eclipse Moon",
                LUNAR_CHESTS);

        /*
         * Phantom Muspah
         */
        put(
                map,
                "Phantom Muspah",
                metric(
                        "Phantom Muspah KC",
                        "PHANTOM_MUSPAH"));

        /*
         * Port Roberts
         */
        put(
                map,
                "Port Roberts",
                SAILING);

        /*
         * Puro-Puro
         */
        put(
                map,
                "Puro-Puro",
                HUNTER);

        /*
         * Pyramid Plunder
         */
        put(
                map,
                "Pyramid Plunder",
                THIEVING);

        /*
         * Quarry
         */
        put(
                map,
                "Quarry",
                MINING);

        /*
         * Ranging Guild
         */
        put(
                map,
                "Ranging Guild",
                RANGED);

        /*
         * Rogues' Den
         */
        put(
                map,
                "Rogues' Den",
                THIEVING);

        /*
         * Royal Titans
         */
        put(
                map,
                "Royal Titans",
                metric(
                        "Royal Titans KC",
                        "THE_ROYAL_TITANS"));

        /*
         * Rune Essence Mine
         */
        put(
                map,
                "Rune Essence Mine",
                MINING);

        /*
         * Scorpia
         */
        put(
                map,
                "Scorpia's Lair",
                metric(
                        "Scorpia KC",
                        "SCORPIA"));

        /*
         * Seers' Village
         */
        put(
                map,
                "Seers' Village",
                AGILITY);

        /*
         * Shilo Village Mine
         */
        put(
                map,
                "Shilo Village Mine",
                MINING);

        /*
         * Shipyard
         */
        put(
                map,
                "Shipyard",
                SAILING);

        /*
         * Sisterhood Sanctuary
         */
        put(
                map,
                "Sisterhood Sanctuary",
                metric(
                        "Nightmare KC",
                        "NIGHTMARE"),
                metric(
                        "Phosani's Nightmare KC",
                        "PHOSANIS_NIGHTMARE"));

        /*
         * Skotizo
         */
        put(
                map,
                "Skotizo's Lair",
                metric(
                        "Skotizo KC",
                        "SKOTIZO"));

        /*
         * Slayer Tower
         */
        put(
                map,
                "Slayer Tower",
                SLAYER);

        /*
         * Smoke Devil Dungeon
         */
        put(
                map,
                "Smoke Devil Dungeon",
                metric(
                        "Thermonuclear Smoke Devil KC",
                        "THERMONUCLEAR_SMOKE_DEVIL"));

        /*
         * Soul Altar
         */
        put(
                map,
                "Soul Altar",
                RUNECRAFT);

        /*
         * Stronghold Slayer Cave
         */
        put(
                map,
                "Stronghold Slayer Cave",
                SLAYER);

        /*
         * Tarhaearn Mine
         */
        put(
                map,
                "Tarhaearn Mine",
                MINING);

        /*
         * Taverley Dungeon
         */
        put(
                map,
                "Taverley Dungeon",
                SLAYER);

        /*
         * Tempoross
         */
        put(
                map,
                "Tempoross Cove",
                metric(
                        "Tempoross",
                        "TEMPOROSS"));

        /*
         * The Darkfrost
         */
        put(
                map,
                "The Darkfrost",
                metric(
                        "Hueycoatl KC",
                        "THE_HUEYCOATL"));

        /*
         * The Gauntlet
         */
        put(
                map,
                "The Gauntlet",
                metric(
                        "Corrupted Gauntlet KC",
                        "THE_CORRUPTED_GAUNTLET"),
                metric(
                        "Gauntlet KC",
                        "THE_GAUNTLET"));

        /*
         * The Inferno
         */
        put(
                map,
                "The Inferno",
                metric(
                        "Zuk KC",
                        "TZKAL_ZUK"));

        /*
         * The Leviathan
         */
        put(
                map,
                "The Scar",
                metric(
                        "Leviathan KC",
                        "THE_LEVIATHAN"));

        /*
         * The Pandemonium
         */
        put(
                map,
                "The Pandemonium",
                SAILING);

        /*
         * The Stranglewood
         */
        put(
                map,
                "The Stranglewood",
                metric(
                        "Vardorvis KC",
                        "VARDORVIS"));

        /*
         * The Summer Shore
         */
        put(
                map,
                "The Summer Shore",
                metric(
                        "Shellbane Gryphon KC",
                        "SHELLBANE_GRYPHON"));

        /*
         * The Whisperer
         */
        put(
                map,
                "Ruins of Camdozaal",
                metric(
                        "Whisperer KC",
                        "THE_WHISPERER"));

        /*
         * Theatre of Blood
         */
        put(
                map,
                "Ver Sinhaza",
                TOB,
                HMT);

        put(
                map,
                "The Maiden of Sugadinti",
                TOB,
                HMT);

        put(
                map,
                "Nylocas Vasilias",
                TOB,
                HMT);

        put(
                map,
                "Pestilent Bloat",
                TOB,
                HMT);

        put(
                map,
                "Sotetseg",
                TOB,
                HMT);

        put(
                map,
                "Verzik Vitur",
                TOB,
                HMT);

        put(
                map,
                "Xarpus",
                TOB,
                HMT);

        /*
         * Tithe Farm
         */
        put(
                map,
                "Tithe Farm",
                FARMING);

        /*
         * Tombs of Amascut
         */
        put(
                map,
                "Tombs of Amascut",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Tombs of Amascut Lobby",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Akkha",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Ba-Ba",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Kephri",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Osmumten's Burial Chamber",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Path of Apmeken",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Path of Crondis",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Path of Het",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Path of Scabaras",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "The Nexus",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "The Wardens",
                TOA,
                TOA_EXPERT);

        put(
                map,
                "Zebak",
                TOA,
                TOA_EXPERT);

        /*
         * Tree Gnome Stronghold
         */
        put(
                map,
                "Tree Gnome Stronghold",
                AGILITY);

        /*
         * Ungael / Vorkath
         */
        put(
                map,
                "Ungael",
                VORKATH);

        /*
         * Varrock
         */
        put(
                map,
                "Varrock",
                COOKING,
                SMITHING);

        /*
         * Varrock Sewers / Scurrius
         */
        put(
                map,
                "Varrock Sewers",
                metric(
                        "Scurrius KC",
                        "SCURRIUS"));

        /*
         * Volcanic Mine
         */
        put(
                map,
                "Volcanic Mine",
                MINING);

        /*
         * Warriors' Guild
         */
        put(
                map,
                "Warriors' Guild",
                STRENGTH);

        /*
         * Water Altar
         */
        put(
                map,
                "Water Altar",
                RUNECRAFT);

        /*
         * Werewolf Agility Course
         */
        put(
                map,
                "Werewolf Agility Course",
                AGILITY);

        /*
         * Wilderness bosses
         */

        put(
                map,
                "Callisto's Den",
                metric(
                        "Artio KC",
                        "ARTIO"),
                metric(
                        "Callisto KC",
                        "CALLISTO"));

        put(
                map,
                "Hunter's End",
                metric(
                        "Artio KC",
                        "ARTIO"),
                metric(
                        "Callisto KC",
                        "CALLISTO"));

        put(
                map,
                "Skeletal Tomb",
                metric(
                        "Calvar'ion KC",
                        "CALVARION"),
                metric(
                        "Vet'ion KC",
                        "VETION"));

        put(
                map,
                "Vet'ion's Rest",
                metric(
                        "Calvar'ion KC",
                        "CALVARION"),
                metric(
                        "Vet'ion KC",
                        "VETION"));

        put(
                map,
                "Silk Chasm",
                metric(
                        "Spindel KC",
                        "SPINDEL"),
                metric(
                        "Venenatis KC",
                        "VENENATIS"));

        put(
                map,
                "Web Chasm",
                metric(
                        "Spindel KC",
                        "SPINDEL"),
                metric(
                        "Venenatis KC",
                        "VENENATIS"));

        /*
         * Wizards' Tower
         */
        put(
                map,
                "Wizards' Tower",
                MAGIC);

        /*
         * Wintertodt
         */
        put(
                map,
                "Wintertodt",
                metric(
                        "Wintertodt",
                        "WINTERTODT"));

        /*
         * Woodcutting Guild
         */
        put(
                map,
                "Woodcutting Guild",
                WOODCUTTING);

        /*
         * Woodcutting Guild Dungeon
         */
        put(
                map,
                "Woodcutting Guild Dungeon",
                WOODCUTTING);

        /*
         * Wrath Altar
         */
        put(
                map,
                "Wrath Altar",
                RUNECRAFT);

        /*
         * Yama
         */
        put(
                map,
                "Yama",
                metric(
                        "Yama KC",
                        "YAMA"));

        /*
         * Yanille
         */
        put(
                map,
                "Yanille",
                MAGIC);

        /*
         * Yanille Agility Dungeon
         */
        put(
                map,
                "Yanille Agility Dungeon",
                AGILITY);

        /*
         * Zalcano
         */
        put(
                map,
                "Zalcano",
                metric(
                        "Zalcano KC",
                        "ZALCANO"));

        /*
         * Zulrah
         */
        put(
                map,
                "Zul-Andra",
                ZULRAH);

        return Collections.unmodifiableMap(map);
    }

    /*
     * ---------------------------------------------------------------------
     * NPC overrides
     * ---------------------------------------------------------------------
     */

    private static Map<String, ContextOverride> buildNpcOverrides()
    {
        final Map<String, ContextOverride> map =
                new HashMap<>();

        npc(
                map,
                "abyssal sire",
                "Abyssal Nexus",
                metric(
                        "Abyssal Sire KC",
                        "ABYSSAL_SIRE"));

        npc(
                map,
                "alchemical hydra",
                "Karuulm Slayer Dungeon",
                metric(
                        "Alchemical Hydra KC",
                        "ALCHEMICAL_HYDRA"));

        npc(
                map,
                "amoxliatl",
                "Amoxliatl",
                metric(
                        "Amoxliatl KC",
                        "AMOXLIATL"));

        npc(
                map,
                "araxxor",
                "Araxxor's Cavern",
                metric(
                        "Araxxor KC",
                        "ARAXXOR"));

        npc(
                map,
                "brutus",
                "Lumbridge Cow Pen",
                metric(
                        "Brutus KC",
                        "BRUTUS"));

        npc(
                map,
                "bryophyta",
                "Bryophyta's Lair",
                metric(
                        "Bryophyta KC",
                        "BRYOPHYTA"));

        npc(
                map,
                "cerberus",
                "Cerberus' Lair",
                metric(
                        "Cerberus KC",
                        "CERBERUS"));

        npc(
                map,
                "chaos elemental",
                "Chaos Elemental",
                metric(
                        "Chaos Elemental KC",
                        "CHAOS_ELEMENTAL"));

        npc(
                map,
                "chaos fanatic",
                "Chaos Fanatic",
                metric(
                        "Chaos Fanatic KC",
                        "CHAOS_FANATIC"));

        npc(
                map,
                "corporeal beast",
                "Corporeal Beast",
                metric(
                        "Corporeal Beast KC",
                        "CORPOREAL_BEAST"));

        npc(
                map,
                "crazy archaeologist",
                "Crazy Archaeologist",
                metric(
                        "Crazy Archaeologist KC",
                        "CRAZY_ARCHAEOLOGIST"));

        npc(
                map,
                "deranged archaeologist",
                "Fossil Island",
                metric(
                        "Deranged Archaeologist KC",
                        "DERANGED_ARCHAEOLOGIST"));

        npc(
                map,
                "doom of mokhaiotl",
                "Doom of Mokhaiotl",
                metric(
                        "Doom of Mokhaiotl KC",
                        "DOOM_OF_MOKHAIOTL"));

        npc(
                map,
                "duke sucellus",
                "Ghorrock Prison",
                metric(
                        "Duke Sucellus KC",
                        "DUKE_SUCELLUS"));

        npc(
                map,
                "giant mole",
                "Mole Lair",
                metric(
                        "Giant Mole KC",
                        "GIANT_MOLE"));

        /*
         * God Wars Dungeon
         */
        npc(
                map,
                "commander zilyana",
                "Commander Zilyana",
                metric(
                        "Commander Zilyana",
                        "COMMANDER_ZILYANA"));

        npc(
                map,
                "general graardor",
                "General Graardor",
                metric(
                        "General Graardor",
                        "GENERAL_GRAARDOR"));

        npc(
                map,
                "kree'arra",
                "Kree'Arra",
                metric(
                        "Kree'Arra",
                        "KREEARRA"));

        npc(
                map,
                "k'ril tsutsaroth",
                "K'ril Tsutsaroth",
                metric(
                        "K'ril Tsutsaroth",
                        "KRIL_TSUTSAROTH"));

        npc(
                map,
                "nex",
                "Ancient Prison",
                NEX);

        /*
         * Grotesque Guardians
         */
        npc(
                map,
                "dawn",
                "Grotesque Guardians",
                metric(
                        "Grotesque Guardians KC",
                        "GROTESQUE_GUARDIANS"));

        npc(
                map,
                "dusk",
                "Grotesque Guardians",
                metric(
                        "Grotesque Guardians KC",
                        "GROTESQUE_GUARDIANS"));

        npc(
                map,
                "great olm",
                "Great Olm",
                COX,
                CM);

        npc(
                map,
                "hespori",
                "Hespori Lair",
                metric(
                        "Hespori KC",
                        "HESPORI"));

        npc(
                map,
                "kalphite queen",
                "Kalphite Queen",
                metric(
                        "Kalphite Queen KC",
                        "KALPHITE_QUEEN"));

        npc(
                map,
                "king black dragon",
                "King Black Dragon Lair",
                metric(
                        "King Black Dragon KC",
                        "KING_BLACK_DRAGON"));

        npc(
                map,
                "kraken",
                "Kraken Cove",
                metric(
                        "Kraken KC",
                        "KRAKEN"));

        npc(
                map,
                "mad angel",
                "Wyrmscraig",
                metric(
                        "Mad Angel KC",
                        "MAD_ANGEL"));

        npc(
                map,
                "maggot king",
                "Sangvesti",
                metric(
                        "Maggot King KC",
                        "MAGGOT_KING"));

        npc(
                map,
                "the mimic",
                "Watson's House",
                metric(
                        "Mimic KC",
                        "MIMIC"));

        npc(
                map,
                "obor",
                "Edgeville Dungeon",
                metric(
                        "Obor KC",
                        "OBOR"));

        /*
         * Perilous Moons
         */
        npc(
                map,
                "blood moon",
                "Blood Moon",
                LUNAR_CHESTS);

        npc(
                map,
                "blue moon",
                "Blue Moon",
                LUNAR_CHESTS);

        npc(
                map,
                "eclipse moon",
                "Eclipse Moon",
                LUNAR_CHESTS);

        npc(
                map,
                "phantom muspah",
                "Ghorrock Prison",
                metric(
                        "Phantom Muspah KC",
                        "PHANTOM_MUSPAH"));

        /*
         * Royal Titans
         */
        npc(
                map,
                "brandr, queen of fire",
                "Royal Titans",
                metric(
                        "Royal Titans KC",
                        "THE_ROYAL_TITANS"));

        npc(
                map,
                "eldric, king of frost",
                "Royal Titans",
                metric(
                        "Royal Titans KC",
                        "THE_ROYAL_TITANS"));

        npc(
                map,
                "sarachnis",
                "Forthos Dungeon",
                metric(
                        "Sarachnis KC",
                        "SARACHNIS"));

        npc(
                map,
                "scurrius",
                "Varrock Sewers",
                metric(
                        "Scurrius KC",
                        "SCURRIUS"));

        npc(
                map,
                "shellbane gryphon",
                "The Summer Shore",
                metric(
                        "Shellbane Gryphon KC",
                        "SHELLBANE_GRYPHON"));

        /*
         * Sisterhood Sanctuary
         */
        npc(
                map,
                "phosani's nightmare",
                "Sisterhood Sanctuary",
                metric(
                        "Phosani's Nightmare KC",
                        "PHOSANIS_NIGHTMARE"));

        npc(
                map,
                "the nightmare",
                "Sisterhood Sanctuary",
                metric(
                        "Nightmare KC",
                        "NIGHTMARE"));

        npc(
                map,
                "skotizo",
                "Skotizo's Lair",
                metric(
                        "Skotizo KC",
                        "SKOTIZO"));

        npc(
                map,
                "sol heredit",
                "Fortis Colosseum",
                metric(
                        "Sol Heredit KC",
                        "SOL_HEREDIT"));

        npc(
                map,
                "tempoross",
                "Tempoross Cove",
                metric(
                        "Tempoross",
                        "TEMPOROSS"));

        /*
         * Theatre of Blood
         */
        npc(
                map,
                "nylocas vasilias",
                "Nylocas Vasilias",
                TOB,
                HMT);

        npc(
                map,
                "pestilent bloat",
                "Pestilent Bloat",
                TOB,
                HMT);

        npc(
                map,
                "sotetseg",
                "Sotetseg",
                TOB,
                HMT);

        npc(
                map,
                "the maiden of sugadinti",
                "The Maiden of Sugadinti",
                TOB,
                HMT);

        npc(
                map,
                "verzik vitur",
                "Verzik Vitur",
                TOB,
                HMT);

        npc(
                map,
                "xarpus",
                "Xarpus",
                TOB,
                HMT);

        npc(
                map,
                "thermonuclear smoke devil",
                "Smoke Devil Dungeon",
                metric(
                        "Thermonuclear Smoke Devil KC",
                        "THERMONUCLEAR_SMOKE_DEVIL"));

        npc(
                map,
                "the hueycoatl",
                "The Darkfrost",
                metric(
                        "Hueycoatl KC",
                        "THE_HUEYCOATL"));

        npc(
                map,
                "the leviathan",
                "The Scar",
                metric(
                        "Leviathan KC",
                        "THE_LEVIATHAN"));

        npc(
                map,
                "the whisperer",
                "Ruins of Camdozaal",
                metric(
                        "Whisperer KC",
                        "THE_WHISPERER"));

        /*
         * Tombs of Amascut
         */
        npc(
                map,
                "akkha",
                "Akkha",
                TOA,
                TOA_EXPERT);

        npc(
                map,
                "ba-ba",
                "Ba-Ba",
                TOA,
                TOA_EXPERT);

        npc(
                map,
                "kephri",
                "Kephri",
                TOA,
                TOA_EXPERT);

        npc(
                map,
                "zebak",
                "Zebak",
                TOA,
                TOA_EXPERT);

        npc(
                map,
                "tzkal-zuk",
                "The Inferno",
                metric(
                        "Zuk KC",
                        "TZKAL_ZUK"));

        npc(
                map,
                "tztok-jad",
                "Fight Caves",
                metric(
                        "Jad KC",
                        "TZTOK_JAD"));

        npc(
                map,
                "vardorvis",
                "The Stranglewood",
                metric(
                        "Vardorvis KC",
                        "VARDORVIS"));

        npc(
                map,
                "vorkath",
                "Ungael",
                VORKATH);

        /*
         * Wilderness bosses
         */
        npc(
                map,
                "artio",
                "Artio",
                metric(
                        "Artio KC",
                        "ARTIO"));

        npc(
                map,
                "callisto",
                "Callisto",
                metric(
                        "Callisto KC",
                        "CALLISTO"));

        npc(
                map,
                "calvar'ion",
                "Calvar'ion",
                metric(
                        "Calvar'ion KC",
                        "CALVARION"));

        npc(
                map,
                "spindel",
                "Spindel",
                metric(
                        "Spindel KC",
                        "SPINDEL"));

        npc(
                map,
                "venenatis",
                "Venenatis",
                metric(
                        "Venenatis KC",
                        "VENENATIS"));

        npc(
                map,
                "vet'ion",
                "Vet'ion",
                metric(
                        "Vet'ion KC",
                        "VETION"));

        npc(
                map,
                "yama",
                "Yama",
                metric(
                        "Yama KC",
                        "YAMA"));

        npc(
                map,
                "zalcano",
                "Zalcano",
                metric(
                        "Zalcano KC",
                        "ZALCANO"));

        npc(
                map,
                "zulrah",
                "Zulrah",
                ZULRAH);

        return Collections.unmodifiableMap(map);
    }

    private static void put(
            Map<String, List<ContextMetric>> map,
            String location,
            ContextMetric... metrics)
    {
        map.put(
                location.toLowerCase(Locale.ROOT),
                Collections.unmodifiableList(
                        Arrays.asList(metrics)));
    }

    private static void npc(
            Map<String, ContextOverride> map,
            String npcName,
            String locationName,
            ContextMetric... metrics)
    {
        map.put(
                npcName.toLowerCase(Locale.ROOT),
                new ContextOverride(
                        locationName,
                        Collections.unmodifiableList(
                                Arrays.asList(metrics))));
    }

    private static ContextMetric metric(
            String label,
            String hiscoreSkillName)
    {
        return new ContextMetric(
                label,
                hiscoreSkillName);
    }

    @lombok.Value
    public static class ContextOverride
    {
        String locationName;
        List<ContextMetric> metrics;
    }
}
