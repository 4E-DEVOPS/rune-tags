package com.runetags.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps location and NPC context names to contextual profile metrics.
 */
public final class ProfileMetricCatalog {

	/*
	 * ================================================================
	 * SHARED ACTIVITY / RAID HISCORES
	 * ================================================================
	 */
	private static final ProfileMetric CM = metric("CM", "CHAMBERS_OF_XERIC_CHALLENGE_MODE");
	private static final ProfileMetric COX = metric("CoX", "CHAMBERS_OF_XERIC");
	private static final ProfileMetric HMT = metric("HMT", "THEATRE_OF_BLOOD_HARD_MODE");
	private static final ProfileMetric LMS = metric("LMS", "LAST_MAN_STANDING");
	private static final ProfileMetric LUNAR_CHESTS = metric("Lunar Chests", "LUNAR_CHESTS");
	private static final ProfileMetric NEX = metric("Nex", "NEX");
	private static final ProfileMetric TOA = metric("ToA", "TOMBS_OF_AMASCUT");
	private static final ProfileMetric TOA_EXPERT = metric("Expert", "TOMBS_OF_AMASCUT_EXPERT");
	private static final ProfileMetric TOB = metric("ToB", "THEATRE_OF_BLOOD");
	private static final ProfileMetric VORKATH = metric("Vorkath KC", "VORKATH");
	private static final ProfileMetric ZULRAH = metric("Zulrah KC", "ZULRAH");

	/*
	 * ================================================================
	 * SHARED SKILL HISCORES
	 * ================================================================
	 */
	private static final ProfileMetric AGILITY = metric("Agility", "AGILITY");
	private static final ProfileMetric COOKING = metric("Cooking", "COOKING");
	private static final ProfileMetric CRAFTING = metric("Crafting", "CRAFTING");
	private static final ProfileMetric FARMING = metric("Farming", "FARMING");
	private static final ProfileMetric FISHING = metric("Fishing", "FISHING");
	private static final ProfileMetric FLETCHING = metric("Fletching", "FLETCHING");
	private static final ProfileMetric HERBLORE = metric("Herblore", "HERBLORE");
	private static final ProfileMetric HUNTER = metric("Hunter", "HUNTER");
	private static final ProfileMetric MAGIC = metric("Magic", "MAGIC");
	private static final ProfileMetric MINING = metric("Mining", "MINING");
	private static final ProfileMetric PRAYER = metric("Prayer", "PRAYER");
	private static final ProfileMetric RANGED = metric("Ranged", "RANGED");
	private static final ProfileMetric RUNECRAFT = metric("Runecrafting", "RUNECRAFT");
	private static final ProfileMetric SAILING = metric("Sailing", "SAILING");
	private static final ProfileMetric SLAYER = metric("Slayer", "SLAYER");
	private static final ProfileMetric SMITHING = metric("Smithing", "SMITHING");
	private static final ProfileMetric STRENGTH = metric("Strength", "STRENGTH");
	private static final ProfileMetric THIEVING = metric("Thieving", "THIEVING");
	private static final ProfileMetric WOODCUTTING = metric("Woodcutting", "WOODCUTTING");

	private static final Map<String, List<ProfileMetric>> BY_LOCATION = buildLocationMetrics();
	private static final Map<String, ContextOverride> BY_NPC = buildNpcOverrides();

	private ProfileMetricCatalog() {
	}

	public static List<ProfileMetric> metricsForLocation(String locationName) {
		if (locationName == null) {
			return Collections.emptyList();
		}

		return BY_LOCATION.getOrDefault(locationName.toLowerCase(Locale.ROOT), Collections.emptyList());
	}

	public static ContextOverride overrideForNpc(String npcName) {
		if (npcName == null) {
			return null;
		}

		return BY_NPC.get(npcName.toLowerCase(Locale.ROOT));
	}

	/*
	 * ================================================================
	 * LOCATION MAPPINGS
	 * ================================================================
	 */

	private static Map<String, List<ProfileMetric>> buildLocationMetrics() {
		final Map<String, List<ProfileMetric>> map = new HashMap<>();

		put(map, "Abyss", RUNECRAFT);
		put(map, "Abyssal Area", RUNECRAFT);
		put(map, "Abyssal Nexus", metric("Abyssal Sire KC", "ABYSSAL_SIRE"));
		put(map, "Agility Pyramid", AGILITY);
		put(map, "Air Altar", RUNECRAFT);
		put(map, "Amoxliatl", metric("Amoxliatl KC", "AMOXLIATL"));
		put(map, "Ancient Prison", NEX);
		put(map, "Ape Atoll", AGILITY);
		put(map, "Araxxor's Cavern", metric("Araxxor KC", "ARAXXOR"));
		put(map, "Ardougne", THIEVING);
		put(map, "Auburnvale", FLETCHING);
		put(map, "Barbarian Outpost", AGILITY);
		put(map, "Barrows", metric("Barrows Chests", "BARROWS_CHESTS"));
		put(map, "Baxtorian Falls", FISHING);
		put(map, "Blast Mine", MINING);
		put(map, "Blast Furnace", SMITHING);
		put(map, "Blood Altar", RUNECRAFT);
		put(map, "Body Altar", RUNECRAFT);
		put(map, "Daimon's Crater", metric("Hunter", "BOUNTY_HUNTER_HUNTER"), metric("Rogue", "BOUNTY_HUNTER_ROGUE"));
		put(map, "Brimhaven Agility Arena", AGILITY);
		put(map, "Lumbridge Cow Pen", metric("Brutus KC", "BRUTUS"));
		put(map, "Brimhaven Dungeon", SLAYER);
		put(map, "Bryophyta's Lair", metric("Bryophyta KC", "BRYOPHYTA"));
		put(map, "Canifis", AGILITY);
		put(map, "Catherby", FISHING);
		put(map, "Catacombs of Kourend", SLAYER);
		put(map, "Cerberus' Lair", metric("Cerberus KC", "CERBERUS"));
		put(map, "Chambers of Xeric", COX, CM);
		put(map, "Great Olm", COX, CM);
		put(map, "Chaos Altar", RUNECRAFT);
		put(map, "Colossal Wyrm Remains", AGILITY);
		put(map, "Corporeal Beast", metric("Corporeal Beast KC", "CORPOREAL_BEAST"));
		put(map, "Cosmic Altar", RUNECRAFT);
		put(map, "Crafting Guild", CRAFTING);
		put(map, "Dagannoth Kings",
				metric("Prime", "DAGANNOTH_PRIME"),
				metric("Rex", "DAGANNOTH_REX"),
				metric("Supreme", "DAGANNOTH_SUPREME"));
		put(map, "Death Altar", RUNECRAFT);
		put(map, "Deepfin Point", SAILING);
		put(map, "Desert Mining Camp", MINING);
		put(map, "Fossil Island", metric("Deranged Archaeologist KC", "DERANGED_ARCHAEOLOGIST"));
		put(map, "Dondakan's mine", MINING);
		put(map, "Dwarven Mine", MINING);
		put(map, "Doom of Mokhaiotl", metric("Doom of Mokhaiotl KC", "DOOM_OF_MOKHAIOTL"));
		put(map, "Ghorrock Prison",
				metric("Duke Sucellus KC", "DUKE_SUCELLUS"),
				metric("Phantom Muspah KC", "PHANTOM_MUSPAH"));
		put(map, "Earth Altar", RUNECRAFT);
		put(map, "Edgeville Dungeon", metric("Obor KC", "OBOR"));
		put(map, "Emir's Arena", metric("PvP Arena Rank", "PVP_ARENA_RANK"));
		put(map, "Farming Guild", FARMING);
		put(map, "Fight Caves", metric("Jad KC", "TZTOK_JAD"));
		put(map, "Fire Altar", RUNECRAFT);
		put(map, "Fishing Guild", FISHING);
		put(map, "Fishing Platform", FISHING);
		put(map, "Fishing Trawler", FISHING);
		put(map, "Forthos Dungeon", metric("Sarachnis KC", "SARACHNIS"));
		put(map, "Fortis Colosseum", metric("Sol Heredit KC", "SOL_HEREDIT"));
		put(map, "Fremennik Slayer Dungeon", SLAYER);
		put(map, "Giants' Foundry", SMITHING);
		put(map, "God Wars Dungeon",
				metric("General Graardor", "GENERAL_GRAARDOR"),
				metric("Kree'Arra", "KREEARRA"),
				metric("K'ril Tsutsaroth", "KRIL_TSUTSAROTH"),
				metric("Commander Zilyana", "COMMANDER_ZILYANA"),
				NEX);
		put(map, "Grotesque Guardians", metric("Grotesque Guardians KC", "GROTESQUE_GUARDIANS"));
		put(map, "Guardians of the Rift", metric("Rifts Closed", "RIFTS_CLOSED"));
		put(map, "Hallowed Sepulchre", AGILITY);
		put(map, "Hespori Lair", metric("Hespori KC", "HESPORI"));
		put(map, "Hunter Guild", HUNTER);
		put(map, "Hunter Guild Caverns", HUNTER);
		put(map, "Iowerth Dungeon", SLAYER);
		put(map, "Isle of Souls", metric("Soul Wars Zeal", "SOUL_WARS_ZEAL"));
		put(map, "Jatizso Mine", MINING);
		put(map, "Kalphite Cave", metric("Kalphite Queen KC", "KALPHITE_QUEEN"));
		put(map, "Kalphite Lair", metric("Kalphite Queen KC", "KALPHITE_QUEEN"));
		put(map, "Karuulm Slayer Dungeon", metric("Alchemical Hydra KC", "ALCHEMICAL_HYDRA"));
		put(map, "King Black Dragon Lair", metric("King Black Dragon KC", "KING_BLACK_DRAGON"));
		put(map, "Kraken Cove", metric("Kraken KC", "KRAKEN"));
		put(map, "Deserted Island", LMS);
		put(map, "Ferox Enclave", LMS);
		put(map, "Wild Varrock", LMS);
		put(map, "Law Altar", RUNECRAFT);
		put(map, "Lizardman Caves", SLAYER);
		put(map, "Lizardman Temple", SLAYER);
		put(map, "Lunar Isle", RUNECRAFT);
		put(map, "Wyrmscraig", metric("Mad Angel KC", "MAD_ANGEL"));
		put(map, "Mage Arena Bank", MAGIC);
		put(map, "Mage Training Arena", MAGIC);
		put(map, "Sangvesti", metric("Maggot King KC", "MAGGOT_KING"));
		put(map, "Watson's House", metric("Mimic KC", "MIMIC"));
		put(map, "Mind Altar", RUNECRAFT);
		put(map, "Mining Guild", MINING);
		put(map, "Mole Lair", metric("Giant Mole KC", "GIANT_MOLE"));
		put(map, "Monastery", PRAYER);
		put(map, "Moonrise Brewery and Winery", HERBLORE);
		put(map, "Mor Ul Rek", metric("Zuk KC", "TZKAL_ZUK"));
		put(map, "Motherlode Mine", MINING);
		put(map, "Nardah", PRAYER);
		put(map, "Nature Altar", RUNECRAFT);
		put(map, "Ourania Cave", RUNECRAFT);
		put(map, "Blood Moon", LUNAR_CHESTS);
		put(map, "Blue Moon", LUNAR_CHESTS);
		put(map, "Eclipse Moon", LUNAR_CHESTS);
		put(map, "Phantom Muspah", metric("Phantom Muspah KC", "PHANTOM_MUSPAH"));
		put(map, "Port Roberts", SAILING);
		put(map, "Puro-Puro", HUNTER);
		put(map, "Pyramid Plunder", THIEVING);
		put(map, "Quarry", MINING);
		put(map, "Ranging Guild", RANGED);
		put(map, "Rogues' Den", THIEVING);
		put(map, "Royal Titans", metric("Royal Titans KC", "THE_ROYAL_TITANS"));
		put(map, "Rune Essence Mine", MINING);
		put(map, "Scorpia's Lair", metric("Scorpia KC", "SCORPIA"));
		put(map, "Seers' Village", AGILITY);
		put(map, "Shilo Village Mine", MINING);
		put(map, "Shipyard", SAILING);
		put(map, "Sisterhood Sanctuary",
				metric("Nightmare KC", "NIGHTMARE"),
				metric("Phosani's Nightmare KC", "PHOSANIS_NIGHTMARE"));
		put(map, "Skotizo's Lair", metric("Skotizo KC", "SKOTIZO"));
		put(map, "Slayer Tower", SLAYER);
		put(map, "Smoke Devil Dungeon", metric("Thermonuclear Smoke Devil KC", "THERMONUCLEAR_SMOKE_DEVIL"));
		put(map, "Soul Altar", RUNECRAFT);
		put(map, "Stronghold Slayer Cave", SLAYER);
		put(map, "Tarhaearn Mine", MINING);
		put(map, "Taverley Dungeon", SLAYER);
		put(map, "Tempoross Cove", metric("Tempoross", "TEMPOROSS"));
		put(map, "The Darkfrost", metric("Hueycoatl KC", "THE_HUEYCOATL"));
		put(map, "The Gauntlet",
				metric("Corrupted Gauntlet KC", "THE_CORRUPTED_GAUNTLET"),
				metric("Gauntlet KC", "THE_GAUNTLET"));
		put(map, "The Inferno", metric("Zuk KC", "TZKAL_ZUK"));
		put(map, "The Scar", metric("Leviathan KC", "THE_LEVIATHAN"));
		put(map, "The Pandemonium", SAILING);
		put(map, "The Stranglewood", metric("Vardorvis KC", "VARDORVIS"));
		put(map, "The Summer Shore", metric("Shellbane Gryphon KC", "SHELLBANE_GRYPHON"));
		put(map, "Ruins of Camdozaal", metric("Whisperer KC", "THE_WHISPERER"));
		put(map, "Ver Sinhaza", TOB, HMT);
		put(map, "The Maiden of Sugadinti", TOB, HMT);
		put(map, "Nylocas Vasilias", TOB, HMT);
		put(map, "Pestilent Bloat", TOB, HMT);
		put(map, "Sotetseg", TOB, HMT);
		put(map, "Verzik Vitur", TOB, HMT);
		put(map, "Xarpus", TOB, HMT);
		put(map, "Tithe Farm", FARMING);
		put(map, "Tombs of Amascut", TOA, TOA_EXPERT);
		put(map, "Tombs of Amascut Lobby", TOA, TOA_EXPERT);
		put(map, "Akkha", TOA, TOA_EXPERT);
		put(map, "Ba-Ba", TOA, TOA_EXPERT);
		put(map, "Kephri", TOA, TOA_EXPERT);
		put(map, "Osmumten's Burial Chamber", TOA, TOA_EXPERT);
		put(map, "Path of Apmeken", TOA, TOA_EXPERT);
		put(map, "Path of Crondis", TOA, TOA_EXPERT);
		put(map, "Path of Het", TOA, TOA_EXPERT);
		put(map, "Path of Scabaras", TOA, TOA_EXPERT);
		put(map, "The Nexus", TOA, TOA_EXPERT);
		put(map, "The Wardens", TOA, TOA_EXPERT);
		put(map, "Zebak", TOA, TOA_EXPERT);
		put(map, "Tree Gnome Stronghold", AGILITY);
		put(map, "Ungael", VORKATH);
		put(map, "Varrock", COOKING, SMITHING);
		put(map, "Varrock Sewers", metric("Scurrius KC", "SCURRIUS"));
		put(map, "Volcanic Mine", MINING);
		put(map, "Warriors' Guild", STRENGTH);
		put(map, "Water Altar", RUNECRAFT);
		put(map, "Werewolf Agility Course", AGILITY);
		put(map, "Callisto's Den", metric("Artio KC", "ARTIO"), metric("Callisto KC", "CALLISTO"));
		put(map, "Hunter's End", metric("Artio KC", "ARTIO"), metric("Callisto KC", "CALLISTO"));
		put(map, "Skeletal Tomb", metric("Calvar'ion KC", "CALVARION"), metric("Vet'ion KC", "VETION"));
		put(map, "Vet'ion's Rest", metric("Calvar'ion KC", "CALVARION"), metric("Vet'ion KC", "VETION"));
		put(map, "Silk Chasm", metric("Spindel KC", "SPINDEL"), metric("Venenatis KC", "VENENATIS"));
		put(map, "Web Chasm", metric("Spindel KC", "SPINDEL"), metric("Venenatis KC", "VENENATIS"));
		put(map, "Wizards' Tower", MAGIC);
		put(map, "Wintertodt", metric("Wintertodt", "WINTERTODT"));
		put(map, "Woodcutting Guild", WOODCUTTING);
		put(map, "Woodcutting Guild Dungeon", WOODCUTTING);
		put(map, "Wrath Altar", RUNECRAFT);
		put(map, "Yama", metric("Yama KC", "YAMA"));
		put(map, "Yanille", MAGIC);
		put(map, "Yanille Agility Dungeon", AGILITY);
		put(map, "Zalcano", metric("Zalcano KC", "ZALCANO"));
		put(map, "Zul-Andra", ZULRAH);

		return Collections.unmodifiableMap(map);
	}

	/*
	 * ================================================================
	 * NPC OVERRIDES
	 * ================================================================
	 */

	private static Map<String, ContextOverride> buildNpcOverrides() {
		final Map<String, ContextOverride> map = new HashMap<>();

		npc(map, "abyssal sire", "Abyssal Nexus", metric("Abyssal Sire KC", "ABYSSAL_SIRE"));
		npc(map, "alchemical hydra", "Karuulm Slayer Dungeon", metric("Alchemical Hydra KC", "ALCHEMICAL_HYDRA"));
		npc(map, "amoxliatl", "Amoxliatl", metric("Amoxliatl KC", "AMOXLIATL"));
		npc(map, "araxxor", "Araxxor's Cavern", metric("Araxxor KC", "ARAXXOR"));
		npc(map, "brutus", "Lumbridge Cow Pen", metric("Brutus KC", "BRUTUS"));
		npc(map, "bryophyta", "Bryophyta's Lair", metric("Bryophyta KC", "BRYOPHYTA"));
		npc(map, "cerberus", "Cerberus' Lair", metric("Cerberus KC", "CERBERUS"));
		npc(map, "chaos elemental", "Chaos Elemental", metric("Chaos Elemental KC", "CHAOS_ELEMENTAL"));
		npc(map, "chaos fanatic", "Chaos Fanatic", metric("Chaos Fanatic KC", "CHAOS_FANATIC"));
		npc(map, "corporeal beast", "Corporeal Beast", metric("Corporeal Beast KC", "CORPOREAL_BEAST"));
		npc(map, "crazy archaeologist", "Crazy Archaeologist", metric("Crazy Archaeologist KC", "CRAZY_ARCHAEOLOGIST"));
		npc(map, "deranged archaeologist", "Fossil Island", metric("Deranged Archaeologist KC", "DERANGED_ARCHAEOLOGIST"));
		npc(map, "doom of mokhaiotl", "Doom of Mokhaiotl", metric("Doom of Mokhaiotl KC", "DOOM_OF_MOKHAIOTL"));
		npc(map, "duke sucellus", "Ghorrock Prison", metric("Duke Sucellus KC", "DUKE_SUCELLUS"));
		npc(map, "giant mole", "Mole Lair", metric("Giant Mole KC", "GIANT_MOLE"));
		npc(map, "commander zilyana", "Commander Zilyana", metric("Commander Zilyana", "COMMANDER_ZILYANA"));
		npc(map, "general graardor", "General Graardor", metric("General Graardor", "GENERAL_GRAARDOR"));
		npc(map, "kree'arra", "Kree'Arra", metric("Kree'Arra", "KREEARRA"));
		npc(map, "k'ril tsutsaroth", "K'ril Tsutsaroth", metric("K'ril Tsutsaroth", "KRIL_TSUTSAROTH"));
		npc(map, "nex", "Ancient Prison", NEX);
		npc(map, "dawn", "Grotesque Guardians", metric("Grotesque Guardians KC", "GROTESQUE_GUARDIANS"));
		npc(map, "dusk", "Grotesque Guardians", metric("Grotesque Guardians KC", "GROTESQUE_GUARDIANS"));
		npc(map, "great olm", "Great Olm", COX, CM);
		npc(map, "hespori", "Hespori Lair", metric("Hespori KC", "HESPORI"));
		npc(map, "kalphite queen", "Kalphite Queen", metric("Kalphite Queen KC", "KALPHITE_QUEEN"));
		npc(map, "king black dragon", "King Black Dragon Lair", metric("King Black Dragon KC", "KING_BLACK_DRAGON"));
		npc(map, "kraken", "Kraken Cove", metric("Kraken KC", "KRAKEN"));
		npc(map, "mad angel", "Wyrmscraig", metric("Mad Angel KC", "MAD_ANGEL"));
		npc(map, "maggot king", "Sangvesti", metric("Maggot King KC", "MAGGOT_KING"));
		npc(map, "the mimic", "Watson's House", metric("Mimic KC", "MIMIC"));
		npc(map, "obor", "Edgeville Dungeon", metric("Obor KC", "OBOR"));
		npc(map, "blood moon", "Blood Moon", LUNAR_CHESTS);
		npc(map, "blue moon", "Blue Moon", LUNAR_CHESTS);
		npc(map, "eclipse moon", "Eclipse Moon", LUNAR_CHESTS);
		npc(map, "phantom muspah", "Ghorrock Prison", metric("Phantom Muspah KC", "PHANTOM_MUSPAH"));
		npc(map, "brandr, queen of fire", "Royal Titans", metric("Royal Titans KC", "THE_ROYAL_TITANS"));
		npc(map, "eldric, king of frost", "Royal Titans", metric("Royal Titans KC", "THE_ROYAL_TITANS"));
		npc(map, "sarachnis", "Forthos Dungeon", metric("Sarachnis KC", "SARACHNIS"));
		npc(map, "scurrius", "Varrock Sewers", metric("Scurrius KC", "SCURRIUS"));
		npc(map, "shellbane gryphon", "The Summer Shore", metric("Shellbane Gryphon KC", "SHELLBANE_GRYPHON"));
		npc(map, "phosani's nightmare", "Sisterhood Sanctuary", metric("Phosani's Nightmare KC", "PHOSANIS_NIGHTMARE"));
		npc(map, "the nightmare", "Sisterhood Sanctuary", metric("Nightmare KC", "NIGHTMARE"));
		npc(map, "skotizo", "Skotizo's Lair", metric("Skotizo KC", "SKOTIZO"));
		npc(map, "sol heredit", "Fortis Colosseum", metric("Sol Heredit KC", "SOL_HEREDIT"));
		npc(map, "tempoross", "Tempoross Cove", metric("Tempoross", "TEMPOROSS"));
		npc(map, "nylocas vasilias", "Nylocas Vasilias", TOB, HMT);
		npc(map, "pestilent bloat", "Pestilent Bloat", TOB, HMT);
		npc(map, "sotetseg", "Sotetseg", TOB, HMT);
		npc(map, "the maiden of sugadinti", "The Maiden of Sugadinti", TOB, HMT);
		npc(map, "verzik vitur", "Verzik Vitur", TOB, HMT);
		npc(map, "xarpus", "Xarpus", TOB, HMT);
		npc(map, "thermonuclear smoke devil", "Smoke Devil Dungeon", metric("Thermonuclear Smoke Devil KC", "THERMONUCLEAR_SMOKE_DEVIL"));
		npc(map, "the hueycoatl", "The Darkfrost", metric("Hueycoatl KC", "THE_HUEYCOATL"));
		npc(map, "the leviathan", "The Scar", metric("Leviathan KC", "THE_LEVIATHAN"));
		npc(map, "the whisperer", "Ruins of Camdozaal", metric("Whisperer KC", "THE_WHISPERER"));
		npc(map, "akkha", "Akkha", TOA, TOA_EXPERT);
		npc(map, "ba-ba", "Ba-Ba", TOA, TOA_EXPERT);
		npc(map, "kephri", "Kephri", TOA, TOA_EXPERT);
		npc(map, "zebak", "Zebak", TOA, TOA_EXPERT);
		npc(map, "tzkal-zuk", "The Inferno", metric("Zuk KC", "TZKAL_ZUK"));
		npc(map, "tztok-jad", "Fight Caves", metric("Jad KC", "TZTOK_JAD"));
		npc(map, "vardorvis", "The Stranglewood", metric("Vardorvis KC", "VARDORVIS"));
		npc(map, "vorkath", "Ungael", VORKATH);
		npc(map, "artio", "Artio", metric("Artio KC", "ARTIO"));
		npc(map, "callisto", "Callisto", metric("Callisto KC", "CALLISTO"));
		npc(map, "calvar'ion", "Calvar'ion", metric("Calvar'ion KC", "CALVARION"));
		npc(map, "spindel", "Spindel", metric("Spindel KC", "SPINDEL"));
		npc(map, "venenatis", "Venenatis", metric("Venenatis KC", "VENENATIS"));
		npc(map, "vet'ion", "Vet'ion", metric("Vet'ion KC", "VETION"));
		npc(map, "yama", "Yama", metric("Yama KC", "YAMA"));
		npc(map, "zalcano", "Zalcano", metric("Zalcano KC", "ZALCANO"));
		npc(map, "zulrah", "Zulrah", ZULRAH);

		return Collections.unmodifiableMap(map);
	}

	private static void put(Map<String, List<ProfileMetric>> map, String location, ProfileMetric... metrics) {
		map.put(location.toLowerCase(Locale.ROOT), Collections.unmodifiableList(Arrays.asList(metrics)));
	}

	private static void npc(Map<String, ContextOverride> map, String npcName, String locationName, ProfileMetric... metrics) {
		map.put(npcName.toLowerCase(Locale.ROOT), new ContextOverride(locationName, Collections.unmodifiableList(Arrays.asList(metrics))));
	}

	private static ProfileMetric metric(String label, String hiscoreSkillName) {
		return new ProfileMetric(label, hiscoreSkillName);
	}

	@lombok.Value
	public static class ContextOverride {
		String locationName;
		List<ProfileMetric> metrics;
	}
}
