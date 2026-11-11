package com.runetags.context;

import static com.runetags.context.ContextMetrics.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.runelite.api.NPC;
import net.runelite.api.WorldView;

/**
 * Maps loaded encounter NPCs to specific location and metric overrides.
 */
public class ContextNPCs {
	private static final Map<String, ContextDefinition> BY_NPC = buildNpcContexts();

	public List<ContextDefinition> findAll(WorldView worldView) {
		if (worldView == null) {
			return Collections.emptyList();
		}

		final Iterable<? extends NPC> npcs = worldView.npcs();
		if (npcs == null) {
			return Collections.emptyList();
		}

		final List<ContextDefinition> matches = new ArrayList<>();
		for (NPC npc : npcs) {
			if (npc == null || npc.getName() == null || npc.getName().trim().isEmpty()) {
				continue;
			}

			final ContextDefinition definition = contextForName(npc.getName());
			if (definition != null) {
				matches.add(definition);
			}
		}

		return matches;
	}

	public static ContextDefinition contextForName(String npcName) {
		if (npcName == null) {
			return null;
		}

		return BY_NPC.get(npcName.toLowerCase(Locale.ROOT));
	}

	private static Map<String, ContextDefinition> buildNpcContexts() {
		final Map<String, ContextDefinition> map = new HashMap<>();

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
		npc(map, "archive guardian", "Archive Guardian", TFA);
		npc(map, "bork", "Bork and the Smorks", TFA);
		npc(map, "the smorks", "Bork and the Smorks", TFA);
		npc(map, "dagon'hai elder", "Xarth", TFA);
		npc(map, "dagon'hai monk", "Xarth", TFA);
		npc(map, "dreadnoth the jellied", "Dreadnoth the Jellied", TFA);
		npc(map, "ezekiel", "Ezekiel", TFA);
		npc(map, "moia", "Moia", TFA);
		npc(map, "ocellus", "Ocellus", TFA);
		npc(map, "xarth", "Xarth", TFA);
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

	private static void npc(Map<String, ContextDefinition> map, String npcName, String locationName, ProfileMetric... metrics) {
		map.put(npcName.toLowerCase(Locale.ROOT), ContextDefinition.of(locationName, metrics));
	}
}
