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
timestamp + bold font test
Per-channel retention could be revisited later if real eviction problems appear.

[✓] 1. Authoritative account observations / PRIVATECHATOUT
[✓] 2. Lifecycle preservation
[✓] 3. Event-driven PlayerDirectory
[✓] 4. Targeted live QuickCard refresh
[✓] 5. Account/world classification finalization
[✓] 6. Native Chat Bootstrap
[✓] 7. Chat-type consistency audit
[ ] 8. Sender/hitbox/rendering reliability
    - newest/bottom chat line incorrectly appears bold
    - formatting must follow semantic message, not physical row
    - investigate highlight/bold flash when chat rows shift
    - duplicate/short-message row matching
    - sender/reference hitbox alignment/reliability
[ ] 9. Performance pass
[ ] 10. Phase 99 final testing

## Phase 7 — extra
- player notes
- runewatch
- quest points?
- italicize fix

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
    
> Create a font plugin based off our ChatFontLayoutService