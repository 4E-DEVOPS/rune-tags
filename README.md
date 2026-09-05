# RuneTags

RuneTags is a RuneLite Plugin Hub project for player mentions, explicit `@tags`,
quick player profiles, local mention notifications, targeting, and mention history.

## Current milestone2

This repository currently contains the Phase 1 foundation:

- Plugin/config skeleton
- Structured message/reference models
- RuneScape-name normalization
- Explicit `@tag` parser
- Local self/unique-highlight matcher
- Nearby-player directory refresh
- Initial chat processing pipeline
- Unit tests for the parser and normalizer

The next milestone is precise chat rendering/click hit-testing, followed by the
quick-profile overlay and asynchronous RuneProfile → Wise Old Man → Hiscore
provider stack.

# RuneTags development phases

## TODO

## Phase 7 — extra
- player notes
- runewatch

## Phase 8 — Suggestions
- Passive @username suggestion overlay
- Configurable source filters
- Up/down selection
- configurable completion Keybind (default END)
- modify only local chat input; never send chat automatically

## Phase 9 — Release hardening
- External request warnings required by Plugin Hub
- timeouts/rate limiting/caching
- third-party notices
- tests and CI
- check if runetags maintains mentions and chat history after nerd log.
- Plugin Hub submission

## Phase 99 — Debug / Testing
- Validate NPC-only context refinement
- Validate representative boss/minigame/skill contexts
- Validate shared multi-metric contexts
- Validate activity Hiscore mappings
- Add developer /test/ harness for deterministic region/NPC scenarios
- exhaustive canvas-edge/clamping validation
- Final cache validation:
    - confirm 10-minute success cache
    - confirm 60-second negative cache
    - prevent/confirm no duplicate in-flight automatic requests
    - confirm Lookup remains completely independent
- Add instance area checks for Player Owned House

V2 - Mail / Friends
    
PlayerProfile 
[ICON: Account Type] PlayerName     (NOTEPAD: Add Note) (STAR: Favorite) (X: Close) 
├─ Current RSN (hover or click: Previous RSNs) 
│ 
├─ Local 
│ ├─ Tags 
│ ├─ Notes 
│ └─ RuneWatch Cases 
│ 
├─ Identity 
│ ├─ Status/World • Location 
│ └─ Channel • Rank 
│ 
├─ Stats 
│ ├─ Combat • Total Level 
│ └─ Account Build 
│ 
├─ Metrics 
│ ├─ EHP • EHB 
│ └─ Skill or Boss KC (Location Dependant) 
│ 
├─ Recent 
│ └─ Achievement OR Gain 
│ 
└─ [Target] [Lookup]