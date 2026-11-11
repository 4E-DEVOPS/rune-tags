package com.runetags.context;

import static com.runetags.context.ContextMetrics.*;
import static com.runetags.context.ContextSkills.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps named normal-world regions to contextual profile metrics.
 */
public final class ContextRegions {
	private static final Map<String, List<ProfileMetric>> BY_LOCATION = buildLocationMetrics();

	private ContextRegions() {
	}

	public static List<ProfileMetric> metricsForLocation(String locationName) {
		if (locationName == null) {
			return Collections.emptyList();
		}

		return BY_LOCATION.getOrDefault(locationName.toLowerCase(Locale.ROOT), Collections.emptyList());
	}

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
		put(map, "The Fractured Archive", TFA);
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
		put(map, "Tithe Farm", FARMING);
		put(map, "Tombs of Amascut", TOA, TOA_EXPERT);
		put(map, "Tombs of Amascut Lobby", TOA, TOA_EXPERT);
		put(map, "Tree Gnome Stronghold", AGILITY);
		put(map, "Ungael", VORKATH);
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

	private static void put(Map<String, List<ProfileMetric>> map, String location, ProfileMetric... metrics) {
		map.put(location.toLowerCase(Locale.ROOT), Collections.unmodifiableList(Arrays.asList(metrics)));
	}

}
