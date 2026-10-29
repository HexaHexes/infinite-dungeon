# infinite-dungeon Development Plan

This is the living source of truth for the mod's design. It records every
decision made during planning so nothing is lost between sessions. Phases
check off as they land; design changes update this file in the same PR that
implements them.

## 1. Identity and conventions

- Repository: `HexaHexes/infinite-dungeon`, created from
  `HexaHexes/neoforge-mod-template`.
- Mod id: `infinite_dungeon`. Package: `org.bitwisemadness.infinitedungeon`.
  Jar: `infinite-dungeon-<version>.jar`.
- Minecraft 1.21.1, NeoForge 21.1.259, JDK 21.
- Versioning: `1.0.0-alpha.N` per finished phase; isolated clean `1.0.0` at
  release.
- Process: one changeset per PR, squash merge, version bump per finished
  feature or bugfix, CurseMaven coordinates only (never gitignored `libs/` or
  `testmods/`), public API only, no mixins, ASCII-only sources, no generated
  textures (reuse vanilla or pack textures; literal art is user-supplied).
- CI: the template's three checks (junit, gametest, version-bump). The two
  Tiered Echoes rulesets are added after this repo's first CI PR merges.

## 2. Concept

An infinite procedurally generated dungeon dimension in the anime dungeon-dive
style (DanMachi and its ilk). The dimension is adventure-mode locked. Dungeon
Crawl provides the dungeon generation; Waystones provide checkpoint
progression. Players descend as deep as they dare, farm bosses, and climb back
out through the portal they came in by.

## 3. Dimension and layout

- Dimension type is The-End-like and open: `min_y: -64`, `height: 2048`,
  `has_skylight: true`, `has_ceiling: false`, `natural: false`, `bed_works:
  true`, and no fixed time at the type level. `min_y` and `height` are
  locked; changing them later is destructive to existing worlds.
- The clock is owned by the mod, because the type cannot carry a switchable
  fixed time: the shipped default lets it run (`time.flows` true, a plain
  day/night cycle that dims skylight at night), and a server can hold it at
  `time.fixedTick` instead. Sleeping is allowed as flavour - it never skips
  time and never leaves a spawn point behind.
- The base generator is a void generator. The dimension has no floor and no
  ceiling of its own: the only terrain is our structures and Dungeon Crawl's
  dungeon pieces. Anything off a structure is void, and a fall is a real
  death that flows through the death policy (section 5).
- Layers live on X-lanes at `L * laneStride`. `laneStride` is Dungeon Crawl's
  natural placement grid: spacing 32 chunks = 512 blocks, separation 12,
  salt 10387313, as shipped by Dungeon Crawl 2.3.17. Our lane set mirrors
  Dungeon Crawl's effective registry values read at startup rather than
  hardcoded constants, so a load-time rewrite by a structure mod (Sparse
  Structures, section 16) cannot misalign the lanes.
- Infrastructure hangs off whichever Dungeon Crawl dungeon lands in the lane
  cell. Lightweight fallback halls exist only as an anti-dead-end guard.
- Floors per layer are variable, 5 to 10 inclusive, deterministic from
  (world seed, layer index). This is not a fixed config constant; only
  `floorsPerLayerMin` and `floorsPerLayerMax` are configurable.
- Floors stack vertically within a layer; the band height is derived to fit
  the dimension.
- The first layer is special: of fixed construction - the lobby players arrive
  in and die back into - and exempt from the seed-derived floor rule above;
  layers above it follow that rule. (Wave 1's fixed, build-on-arrival entrance
  point is today's stand-in for this lobby; Wave 2's lobby supersedes it.)
- Depth is position-derivable everywhere: depth maps to
  (layerIndex, floorInLayer) as a pure function of position and seed. Loot,
  spawns, waypoints, difficulty, and magicule concentration all derive from
  it.
- The main datapack adds our biome to Dungeon Crawl's biome tag gate
  `dungeoncrawl:has_structure/dungeon` (the tag declares
  `"replace": false`, so the addition is safe by construction) and
  ships our own lane structure set that references `dungeoncrawl:dungeon`
  directly with our own placement. A structure set may reference any
  structure, so no mirror structure or generator reuse is needed. The
  optional suppression datapack, which empties Dungeon Crawl's original
  set, therefore cannot kill our dimension.
- Wild bonus Dungeon Crawl dungeons may appear between lanes when the
  suppression pack is off (Dungeon Crawl's own set also applies to our
  biome); they are scaled and filtered like everything else.

## 4. Portal

- Nether-portal geometry with a configurable frame block (default: stone
  bricks), ignited with flint and steel or fire.
- The portal block reuses the vanilla nether portal texture with a
  configurable tint (client config).
- Per-player return anchor: every portal entry stores the dimension and
  position of the portal used in a per-player data attachment. Walking back
  out through any exit portal returns the player to their own anchor, so a
  party entering through separate overworld portals each exits at their own.
  Anchors persist with the player.
- If the anchor frame was destroyed, the portal is re-formed at the anchor
  coordinates (nether behavior, `portal.reformMissingPortal`, default on).
  With no anchor at all (admin teleport, first join), the fallback is
  overworld world spawn with an announcement message.
- Config:
  - `portal.allowConstruction` (default on): off refuses new ignition with a
    message while already-formed portals keep working. This is the
    "entrance is an admin-placed fixture" server mode.
  - `portal.returnMode` = `entry_portal` (default) | `world_spawn`.
  - `portal.reformMissingPortal` (default on).
  - `portal.buildableDimensions.mode` = `whitelist` (default) | `blacklist`,
    with `portal.buildableDimensions.list` (default: the overworld and the
    dungeon): where a dungeon portal may be *built*. Construction only -
    travel through an already-formed portal and the mod's own dungeon
    entrance are never gated, so existing entrances and a stranded player
    building a way out keep working.
- Anchors store a generic dimension id, so entry from any modded dimension
  works and returns there.
- Additional entry points beyond built frames: Dungeon Crawl's surface tower
  entrances (its `DungeonEntrance` piece) act as portals whenever they
  generate, and our own tower-entrance building expands existing village
  types at generation time (jigsaw pool injection), so packs always have
  discoverable entrances. Both are optional and toggleable.
- Wave 1 arrival placeholder (ruling recorded): the dungeon-side entrance -
  platform, frame, and lit portal at fixed coordinates - is built once on
  first arrival. It is position-deterministic, so pregenerated and organic
  worlds converge after the first visit, and it is a deliberate deviation from
  the no-visitor-edits rule (sections 16 and 18) made for a playable alpha;
  Wave 2's lane geometry and structure sets generate entrances properly and
  replace it.

## 5. Adventure lock and death policy

- Gamemode is swapped on entry and restored on exit. Block breaking is
  cancelled inside the dimension, with a configurable whitelist of
  player-placeable blocks. Corpse corpses are entities, not blocks, so
  they need no whitelist entry (spike finding). Announcements explain
  the lock.
- Death teleports the player to the dungeon lobby on final death only, and the
  lobby is the first layer of the dungeon - special, of fixed construction.
  PlayerRevive bleed-out is respected; a successful revive keeps the player in
  the dungeon. That integration is deferred indefinitely (issue #25), so until
  it lands every death is final and this trigger reads "on death in the
  dungeon"; the guard point is the death hook, so narrowing it later is a
  one-branch change.
- No respawn point can ever be set inside the dimension: players may nap in
  beds, but the nap is flavour only - the clock never skips time (with
  `natural: false` vanilla cannot skip nights, and the mod owns the clock),
  and the player's stored respawn target is snapshotted before the nap
  overwrites it and restored the moment the dungeon-bed target appears. The
  spawn-point command is refused while standing in the dungeon, respawn
  anchors already explode, and any stored dungeon respawn target is undone at
  death. The lobby teleport is therefore always the respawn path.
- The Corpse mod behaves normally: the corpse spawns at the death site with
  the inventory and is reclaimed by walking back.
  `integrations.corpse.despawnWhenEmpty` is configurable, and the mod's own
  sweep of emptied corpses runs in the dungeon only.
- Falling into the void is a normal death and follows the same path: Corpse
  keeps the inventory, PlayerRevive bleed-out is respected, and the lobby
  teleport happens on final death only.

## 6. Progression

- One Waystone per layer entrance, named "Layer N". Waystones, like all
  other infrastructure, are placed during chunk generation, never on first
  visit, so Chunky-pregenerated chunks are identical to organic ones
  (section 16).
- Waypoint policy: `waypoints.mode` = `all` | `layer_only` (default) | `none`,
  filtering Xaero's minimap and world map (in pack). Journeymap is a
  soft-dep with the same filter behind its own toggle.
- The layer guardian gates the transition to the next layer. The unlock flag
  persists per layer in world saved data once a guardian has been slain at
  least once.
- Guardians respawn on chamber entry after `bossRespawnCooldown` seconds
  (default 600), making them repeatable farm content with full boss loot on
  each kill. Within the cooldown the chamber is empty with a flavor message.
- Boss identity is fixed per layer and randomized by world seed: layer L always
  fields the same boss for a given seed, different seeds field different
  bosses, and respawns re-fight the same boss with an announcement.
- Roster: when Tensura: Reincarnated and/or Tensura: Not Enough Bosses are
  loaded, all of their bosses are included and ranked by EP/AP/MP read through
  the API's entity power stat service. Cross-family interleaving is free (a
  Tensura boss may be followed by an Ice and Fire boss), but within the Tensura
  family the order is monotone ascending with depth: a weaker Tensura-family
  boss never appears deeper than a stronger one from the same family.
- Layer fit: candidates are filtered by effective power after depth scaling
  (Tensura-family bosses are depth-exempt, so their raw EP/AP/MP is the metric)
  against a per-layer band derived from the difficulty curve
  (`boss.powerBandPercent`, default 30). Selection is seeded within the band;
  an empty band falls back to the nearest-power candidates so every layer
  fields a boss.
- Scaling: every boss scales with party size at spawn time. Tensura and
  Tensura: Not Enough Bosses bosses are exempt from the difficulty (depth)
  factor only, via the datapack-populated entity tag
  `infinite_dungeon:no_depth_scaling` (inert when the mods are absent); every
  other boss scales by difficulty and party size. Regular Tensura mobs, fodder
  and elites included, scale normally.
- Minibosses: `minibossStartLayer` (default 3), one every
  `minibossEveryNFloors` (default 3) counted within the layer, so layers with
  more floors get more minibosses. `minibossStatMultiplier` scales them.
  Minibosses drop keys that open sealed treasure rooms on deep layers
  (`treasureRoomsRequireKey`).

## 7. Difficulty and magicule curves

Difficulty, driving mob stats, traps, and loot:

```
D(L, f) = difficultyBase + difficultyLinearPerFloor * f
          + difficultyQuadraticPerLayer * L^2
```

Config `difficulty.*`, starting values:

| Key | Default |
|---|---|
| `base` | 0 |
| `linearPerFloor` | 0.15 |
| `quadraticPerLayer` | 0.05 |
| `statMultiplierPerPoint` | 1.0 |
| `trapDamagePerPoint` | 0.5 |
| `lootQuantityPerPoint` | 0.3 |
| `lootQualityPerPoint` | 0.2 |

The final stat multiplier is
`(1 + D * statMultiplierPerPoint) * partyMultiplier`; miniboss and guardian
multipliers stack on top. Setting the quadratic term to zero yields pure
linear progression.

Magicule concentration, same shape, independent coefficients:

```
M(L, f) = magiculeBase + magiculeLinearPerFloor * f
          + magiculeQuadraticPerLayer * L^2
```

Config `magicule.*`, starting values: `base` 0, `linearPerFloor` 0.1,
`quadraticPerLayer` 0.04, `particles` on, `tensuraAttributeScaling` on, plus
`structureElevation.*` for the amount and radius of each elevation source.

- The curve is registered with the API library's ambient magicule service as a
  provider for the dungeon dimension, so everything that consumes ambient
  magicule (the standalone spawn-safety module, future addons) sees it without
  depending on this mod.
- Structure-local elevation: dungeon structures, nether portals, our tower
  entrances, and other dungeon-like structures raise the ambient magicule of
  the surrounding area through the API's concentration modifiers.
  Generation-time elevation is seed-derived, so pregenerated chunks match
  organic ones; portal-created elevation is applied when the portal forms.
  Because the spawn-safety gate reads the same service, dungeon mouths become
  magicule-rich and, for enabled spawn categories, more dangerous.
- Ambient particles (density and optional tint keyed to M, vanilla particle
  textures only) and magicule-specific attribute scaling (Tensura's attributes
  by default, configurable) read the same value.
- The base mod is gameplay-neutral toward players: player-side magicule
  effects belong to Tensura's own systems reading our value (PR 7).
- Writing M into Tensura's per-chunk magicule store is attempted when the
  Tensura API allows it (spike finding: `ChunkStorage` exposes `setMagicule`,
  `setMaxMagicule`, and `addBlockModifier`, and may recompute values on chunk
  initialization, so any write must follow its initialization).
- All three curve functions are pure math and JUnit-tested: growth shape,
  monotonicity, coefficient edge cases, and their mutual independence.

Mob density, same shape, a third independent curve:

```
S(L, f) = densityBase + densityLinearPerFloor * f
          + densityQuadraticPerLayer * L^2
```

Config `density.*`, starting values:

| Key | Default | Meaning |
|---|---|---|
| `base` | 0 | offset |
| `linearPerFloor` | 0.1 | within-layer term |
| `quadraticPerLayer` | 0.03 | between-layer term |
| `perPoint` | 0.5 | converts points to a spawn-target multiplier: effective target = base target * (1 + S * perPoint) |
| `maxPerChunk` | 24 | absolute safety clamp |

- S drives how many mobs spawn (ambient spawn attempts per chunk area and
  monster-house wave sizes), never how strong each mob is; that is D.
- Room-type multipliers stack on top: safe rooms multiply by zero, monster
  houses multiply upward, boss chambers gate their fight size.
- The party multiplier affects per-mob strength only, not density. A
  `density.partyScaling` toggle may come later if packs want crowded
  multi-party floors.
- JUnit-tested alongside D and M.

## 8. Party scaling

- A `PartySizeResolver` computes the party multiplier with fixed precedence:
  OPAC, then FTB Teams, then vanilla teams. Radius and per-member multiplier
  are configurable. The result multiplies on top of the D-driven stats.

## 9. Spawns and roster pools

- Fodder and boss pools come from datapack JSON (section 10) with depth
  bands, weights, and per-entry `required_mod` filtering.
- All whitelisted attributes scale with depth; Tensura's attributes are
  included by default.
- Elite sizing: elites roll a random size clamped to configurable bounds
  relative to their variant's regular size, and are always at least slightly
  larger than the regular counterpart of the same variant
  (`spawns.eliteSizeLower` default 1.05, `spawns.eliteSizeUpper` default 1.4,
  plus an absolute cap on the scale attribute). Tensura mobs have multiple
  variants, some with their own sizes, so the baseline is the variant's regular
  size, not the species base, and sizing uses the scale attribute so hitboxes
  match. The tensura-apotheosis companion honors the same rule when it grows
  elites.
- Integration-seeded defaults: boss tier from Bosses'Rise, Cult of Azazel,
  L_Ender's Cataclysm, and Ice and Fire CE; fodder and elite tiers from
  Mowzie's Mobs, Mobs of Mythology, Variants & Ventures, Realm RPG: Fallen
  Adventurers, and Born in Chaos (soft-dep). Entities with ritual or arena
  scripted fights are excluded from defaults if they degrade when spawned
  plainly (reviewed during wiring). With Tensura installed, the Tensura/NEB
  bosses join the roster through the tensura integration module (section 6).

## 10. Datapack-first integration architecture

Principle: everything a pack author might rebalance is datapack data and
reloads with `/reload`. Config keeps only behavior toggles and curve
coefficients.

| Surface | Mechanism |
|---|---|
| Roster pools | `data/<ns>/infinite_dungeon/pools/*.json`, our own schema: entries with entity id or tag reference, weight, depth band, optional `required_mod` |
| Decoration palettes | `data/<ns>/infinite_dungeon/decorations/*.json`, per-room-type block lists, runtime-filtered |
| Room and event tuning | `data/<ns>/infinite_dungeon/rooms/*.json`, weights, densities, per-room parameters |
| Loot injection | `data/<ns>/infinite_dungeon/loot_injections/*.json` plus NeoForge global loot modifiers, depth-banded |
| Loot tables | standard datapack loot tables, datagen defaults under `src/main/generated` |
| Behavior toggles | config `integrations.*`, disable-only and ModList-gated |
| Curves and rules | config `difficulty.*`, `magicule.*`, `portal.*`, `boss.*` |
| Dimension profiles | `data/<ns>/infinite_dungeon/dimension_profile/*.json`: dimension id, layer limit, pool references, curve overrides, guardian rules, portal policy, announcements, spawn-safety exemption; addons add extra dungeon dimensions through profiles |

Plain tags are not used as the primary pool mechanism because a tag entry
referencing an absent mod's entity fails datapack validation on reload. Our
own schema filters registry ids and `required_mod` entries in code, skipping
missing ones silently, while still accepting tag references inside entries
(expanded through the holder sets, empty tags tolerated).

We ship exactly one dungeon dimension. Addons and datapacks may add more by
pairing a vanilla dimension and dimension_type with a dimension profile; every
gameworld system resolves its settings from the profile of the current
dimension, with config values supplying the global defaults and profile values
winning. Code addons register the same profile through the API library's
registry. A profile dimension inherits the seed-derived generation requirement
(section 16) and is exempt from spawn safety by default. The profile schema and
registry live in the API library (section 19).

## 11. Rooms and events

- Monster houses: a bait trigger, waves drawn from the fodder pool, and a
  hidden cache as the real reward.
- Treasure rooms: sealed, miniboss-key gated on deep layers, jackpot loot
  band, and a mimic chance.
- Safe rooms: at every layer entrance and optionally every K floors
  mid-layer; spawn-suppressed with regeneration.
- Trap gauntlets: darts, poison, pitfalls, rune plates, and crush
  corridors. Damage scales with D through `trapDamagePerPoint`. Vanilla
  textures only.
- Gateway challenge rooms: Gateways to Eternity arenas as wave-fight rooms
  on deep floors, difficulty-scaled through D, with gateway reward pools as
  payout. Phase 0 verifies gateway activation inside a custom dimension and
  how the wave spawner interacts with our spawn filtering.
- Fallen Adventurers decoration pass (environmental storytelling): slumped
  skeletons in corridors (variant hints at local hazards), remains in safe
  rooms, clusters around monster-house bait chests, remains at treasure
  doors, fallen challengers in boss chambers, and buried variants at layer
  entrances. Density is configurable and optionally scaled by D so deeper
  layers are visibly littered with failed parties.
- Rooms are code-authored in v1; the datapack room schema is the future
  extension point. Full puzzle systems beyond keys, switches, and gateway
  arenas are post-1.0.

## 12. Loot

- Loot quantity and quality scale with the difficulty curve through datagen
  loot tables.
- Position-keyed depth-injection global loot modifiers add other mods' items
  by depth band: Farmer's Delight and the Delight-series food, Lightman's
  Currency coins, End Remastered and Gateways items as rare deep treasure,
  and Sophisticated Backpacks as jackpot finds.
- Apotheosis bags appear as depth-tiered entries in the same framework, so
  deeper bands yield better bags, and Lightman's Currency coins also drop from
  dungeon mobs through the same depth-scaled modifiers.
- Skill Study Books join the depth-banded chest loot when that mod is present,
  with deeper bands carrying higher-tier books.
- Lootr per-player instancing applies everywhere
  (`integrations.lootr`).
- Loot containers stay in the vanilla chest and barrel family for Lootr
  reliability. Sophisticated Storage blocks are decoration only unless a
  spike confirms interop.
- An optional pity counter guards extremely deep runs from dry results.
- The pack's existing Loot Integrations mods handle base cross-mod loot
  compatibility; our modifiers are the separate depth-aware layer.

## 13. Integrations matrix

Core, no toggle:

| Mod | Coordinate |
|---|---|
| Dungeon Crawl | `dungeon-crawl-324973:7887320` (2.3.17; placement config is datapack-only) |
| Waystones | `waystones-245755:8969751` |

Optional, one `integrations.<key>` toggle each, default on, disable-only,
gated on ModList presence:

| Key | Coordinate |
|---|---|
| `tensura` | `tensura-reincarnated-643695:8793360` |
| `apotheosis` | `apotheosis-313970:8826922` (plus `placebo-283644:9153039`, `apothic-attributes-898963:9006294`) |
| `lootr` | `lootr-361276:8968561` |
| `opac` | `open-parties-and-claims-636608:9221991` |
| `ftbTeams` | soft-dep, not in pack; `dev.ftb.mods:ftb-teams-neoforge:2101.1.11` from maven.ftb.dev (CF 404468 file 8724782), needs architectury + ftblibrary |
| `corpse` | `corpse-316582:9188333` (plus `despawnWhenEmpty`) |
| `playerRevive` | `playerrevive-266890:8057637` |
| `xaero` | `xaeroworldmap-317780:9067277`, `xaerominimap-263420:9067173` |
| `journeymap` | soft-dep only; API `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1` from maven.blamejared (compileOnly, jarjar'ed into JM at runtime; JM 6.0.x = CF 32274 file 8923668) |
| `mowziesmobs` | `mowziesmobs-250498:7760267` |
| `bossesrise` | `block_factorys_bosses-1314084:8123167` |
| `cultofazazel` | `cultofazazelneoforge-1571005:8655331` |
| `mobsofmythology` | `mobs-of-mythology-699989:8269965` |
| `iceandfire` | `iceandfire-ce-1040076:7722420` |
| `cataclysm` | `L_Ender's Cataclysm-551586:8931182` |
| `endremastered` | `endrem-404183:8503446` |
| `farmersdelight` | `FarmersDelight-398521:8765184` |
| `brewinandchewin` | `BrewinAndChewin-637808:8309583` |
| `dungeonsdelight` | `neoforge-dungeonsdelight-1258923:8873261` |
| `frightsdelight` | `frightsdelight-927591:8058022` |
| `nethersdelight` | `MyNethersDelight-1003673:8854319` |
| `lightmanscurrency` | `lightmans-currency-472521:9048585` |
| `sophisticatedstorage` | `sophisticatedstorage-619320:9279428` (plus core `618298:9279274`), decoration only |
| `sophisticatedbackpacks` | `sophisticatedbackpacks-422301:9271144` |
| `dramaticdoors` | `DramaticDoors-380617:6479044` |
| `variantsventures` | `variantsandventures-981139:8982592` |
| `fallenadventurers` | `realmrpg_skeletons-916895:6058914`, decoration blocks (50 skeleton variants, blockstates verified) |
| `gateways` | `gateways-to-eternity-417802:6926284` (plus `bettergatewaystoeternity-1583270:8325455`) |
| `borninchaos` | soft-dep only, not in pack |
| `tensuraNEB` | Tensura: Not Enough Bosses, coordinate at wiring; joins the Tensura family roster |
| `travelerstitles` | `travelers-titles-neoforge-1015155:6294123` (5.1.3, CLIENT-ONLY, required dep YUNG's API); announcements - integration is lang keys plus waystone naming, zero Java coupling |
| `bosschecklist` | `boss-checklist-1355708:9109104` (5.3.2); data-only roster - display in assets/boss_checklist/bosses.json, server ids in data/boss_checklist/server_bosses_ids.json, defeated key = plain entity type id |
| `skillbooks` | `tensura-skill-books-1418495:9055170` (2.2.3, needs tensura + manascore); loot only - one item id with minecraft:custom_data.skill_id, gated by neoforge:conditions in its own pool |
| `alltheadventure` | Tensura: All the Adventure, coordinate at wiring; spike-gated integration |
| `easynpc` | `easy-npc-core-1308987:9310906` (core 7.17.1; the pack installs bundle `559312:9310929`); NPC characters only - every transaction is a Lightman's Currency trader |
| `irons_spellbooks` | Iron's Spells and Spellbooks, coordinate at wiring; advanced shop stock only |
| `bountiful` | Bountiful, coordinate at wiring; dungeon bounties only |
| `alltheadventure` | `tensura-all-the-adventure-1672789:9140357`; authoritative adventurer rank (section 20); needs `tensura` >= 2.0.1.2 (pinned `8793360` is 2.0.1.3) and `manascore` |
| `manascore` | `manascore-619025:8022425` (4.0.0.2); required by All The Adventure - verify the pack carries it, else the rank provider degrades to the fallback |

Code integrations in this table ship as their own modules in the suite
(section 19); data-only integrations stay as gated data inside the core mod's
datapack.

## 14. Spawn safety

Spawn safety is a standalone module in the suite, not a feature of this mod:
`infinite-dungeon-spawn-safety` (section 19) owns the config `safety.*`
surface, the `safety/` datapack schema, the classification application, and the
gate. Its design source of truth is that repository's plan; this section keeps
only the parts this mod is responsible for.

- The mod never gates spawns itself. It contributes values: the M curve is
  registered with the API library's ambient magicule service, and Tensura's
  attributes and per-chunk magicule become available through the tensura
  integration module's providers.
- Scope is fixed by the API: only natural spawns are ever gated, by positive
  allowlist. Spawners, structure placement, commands, eggs, and events are
  never blocked or allowed by magicule concentration, and chunk generation is
  untouched.
- The gate rule itself:

  `ambientM(chunk) >= statValue * attributeScale * (1 - deviationPercent / 100)`

  where `statValue` is the mob's own AP (aura), MP (magicule), or EP (stored
  energy), default EP, read through the API's entity power stat service.
  Mobs without existence data (Tensura absent) have no requirement, so the
  gate is a harmless no-op.
- Classification is derived, not curated, through the API's chain: datapack
  override, then `NeutralMob`, then `Monster` or the `MONSTER` category, then
  passive. Overrides live in the spawn-safety module's `classification.json`.
- Our dimension is exempt by default, because the module's default dimension
  list names only the overworld.
- In our dimension, feeding the M curve into Tensura's per-chunk magicule
  system is attempted when the API allows it (spike finding: the write API
  exists; timing is the risk); otherwise consumers read M through the service.

## 15. Overworld suppression datapack

- Ships as a separate companion zip in the repository and release assets. It
  cannot live inside the mod jar because mod datapacks auto-enable.
- Neutralizes Dungeon Crawl's original structure set so overworld dungeon
  generation stops. Default off. Affects newly generated chunks only.
- A one-file override, version-pinned to the Dungeon Crawl version in the
  pack. Phase 0 verifies that an empty structures list is valid.
- With suppression on, Dungeon Crawl's structure set is neutralized entirely,
  so its surface tower entrances do not generate either: entry is then through
  built portal frames and our own village tower-entrance structures
  (section 4). With suppression off, Dungeon Crawl's own entrances act as
  portals.

## 16. Compatibility: Chunky, Distant Horizons, Living Horizons

The dimension must be fully compatible with pregeneration and with distant
LOD rendering.

Chunky (pregeneration):

- Every piece of world content (lanes, floors, fallback halls, waystones,
  rooms, decorations) is produced at chunk-generation time as a pure
  function of world seed and position. There is no visitor-triggered world
  editing anywhere, so pregenerated chunks are identical to organically
  generated ones.
- Runtime state (boss unlock flags, respawn cooldowns) lives in world saved
  data and never touches chunk content.
- Phase 0 measures pregeneration throughput for a lane region (a
  2048-high dimension is expensive) and records a performance budget plus
  recommended Chunky settings in the docs.
- Chunky is not in the pack; it is server tooling and gets no
  integrations toggle. Coordinate lookup happens at verification time.

Distant Horizons (client LOD rendering):

- Verify the dimension renders correctly in Distant Horizons at its full
  2048-block height, including the ceiling bands and the lanes between the
  void.
- Every placed block is a vanilla or pack block (the portal block reuses
  the nether portal texture), so LOD coverage is complete by construction;
  the tinted portal is the only custom-rendered block and must degrade
  gracefully.
- No server-side code is expected; any per-dimension behavior is a client
  setting. In pack: `DistantHorizons-508933:9004764` (3.3.3).

Living Horizons (Distant Horizons addon, living entities at LOD distance):

- The mod spawns only existing entity types (all scaling is attributes), so
  the addon renders our mobs, minibosses, guardians, and gateway bosses
  through their own renderers. Verification covers third-party model
  bosses (Bosses'Rise, Cataclysm, Cult of Azazel) at LOD distance.
- Not in the pack; soft-dep verification at wiring, no toggle expected.

Generation isolation:

- Our biome ships in no vanilla biome tags (`minecraft:is_overworld`,
  `has_structure/*`, category tags, and so on), so NeoForge biome modifiers
  from other mods (structure additions, features, spawns) cannot reach our
  dimension, and dimension-scoped modifiers do not target our dimension key.
  This is a hard rule for every biome we ship, including profile dimensions.
- Foreign structure placement: Sparse Structures rewrites every structure
  set's spacing and separation at load and can optionally rewrite salts
  (`idBasedSalt`), so our lane set mirrors Dungeon Crawl's effective registry
  values at startup rather than constants, and companion packaging supplies a
  Sparse Structures config with factor 1.0 entries for `dungeoncrawl:dungeons`
  and our lane set.
- Structure Layout Optimizer patches jigsaw layout computation
  (performance-oriented and behavior-preserving by design); verification
  covers Dungeon Crawl halls generating identically with it loaded.
- Spawn-control mods in the pack target overworld or vanilla biome tags and
  are excluded by the tag hygiene above.

All three verification items are Phase 0 spikes; any finding that requires
code (for example a Distant Horizons dimension exclusion option) becomes a
client config knob rather than a gameplay integration.

## 17. Delivery phases

Phase 0 spike checklist:

- [x] Dungeon Crawl biome-gate JSON shape, structure set file, and salt
      (done: gate is the biome tag `dungeoncrawl:has_structure/dungeon`
      with replace false; set placement is random_spread spacing 32,
      separation 12, salt 10387313)
- [x] Custom generator pipeline for our mirror structure set (resolved
      without generator reuse: our lane set references
      `dungeoncrawl:dungeon` directly; runtime check remains for
      empty-structures-list validity and multi-set placement)
- [x] Waystones programmatic placement and naming API (done:
      `WaystonesAPI.placeWaystone`, `MutableWaystone.setName`,
      `activateWaystone`; the open nuance is database writes during chunk
      generation versus place-then-register)
- [x] Corpse empty-removal API and adventure-lock removal path (done: corpses
      are entities, so no block whitelist is needed; `despawnWhenEmpty` is our
      own tick scan over `isEmpty()` plus `remove()`)
- [x] PlayerRevive event ordering versus final death (done: bleed-out is not
      death; `PlayerRevivedEvent` cancels a pending lobby teleport, and real
      death flows through the vanilla death event)
- [x] Xaero waypoint filter API (done: no public API - no api package, no
      events, no waypoint packets; waypoint data is client-local and the
      `serverWaypoints` field is a world-folder bucket, not a sync channel;
      the one supported ingress is Xaero's own Waystones compat mirror,
      config-gated by origin; `layer_only` must be source-side through
      waystones or client reflection - decision in issue #33, findings on
      issue #5)
- [x] Tensura attribute names (AP, MP), attribute tag availability,
      per-chunk magicule concentration API (read side and write side), and
      environment hooks (done: AP = aura, MP = magicule, EP = stored energy on
      `IExistence`; ambient reads via `AreaMagiculeHelper`; writes via
      `ChunkStorage` with a reinitialize timing risk; unit alignment still
      needs an in-game sample)
- [x] Tensura unit-alignment sample for the `attributeScale` default
      (done from the pinned Tensura jar's entity_existence datapack: 152
      files; the vanilla namespace spans exactly 10..25000 with median 120
      while Tensura's own roster runs 100..1200000 with median 8000 - the old
      note was vanilla-only; recommended default attributeScale = 0.001 maps
      stats onto the M curve so vanilla p75 spawns near layer 7, Tensura
      median near layer 14, the vanilla ceiling near layer 25, and 1.2M
      boss-tier never spawns ambient; flags filed for the M=0 origin cell
      (#34 and spawn-safety) and EP-versus-MP stat sourcing (#55) - issue
      #154)
- [x] Natural-spawn attribute population hook (falsified as originally
      written, issue #82: on NeoForge 21.1.259 FinalizeSpawnEvent is
      dispatched only by BaseSpawner and TrialSpawner while NaturalSpawner
      reaches Mob.finalizeSpawn without it; the natural path's real hook is
      MobSpawnEvent.PositionCheck, posted by EventHooks.checkSpawnPosition
      after the mob is built and positioned but before it joins the world -
      verified in the spawn-safety module's S4 work; issue #37 therefore hooks
      PositionCheck or applies attributes at EntityJoinLevelEvent for spawns
      it cannot touch there, and the API's EntityPowerStatProvider javadoc
      wording is corrected in the api repo)
- [x] Traveler's Titles trigger surface (server-callable title commands,
      config titles, waystone arrival behavior, sound) (done: coordinate pinned
      - NeoForge 1.21.1 is travelers-titles-neoforge CF 1015155 file 6294123,
      mod id travelerstitles, required dep YUNG's API; the mod is CLIENT-ONLY
      so its permission-2 commands (/biometitle, /dimensiontitle, /tt_reload)
      never register on a dedicated server - not server, datapack or mod
      callable; waystone arrival titles fire AUTOMATICALLY by proximity
      (nearest discovered waystone within 30 blocks, rescanned every 10 ticks),
      so "Layer N" waystones show their name for free while dimension titles
      come from lang keys shipped in our own assets; sounds are one per
      category, volume-configurable; the integration is data-only with zero
      Java coupling, and the one runtime check (programmatic waystones versus
      the discovered list) is recorded on the issue - issue #153)
- [x] Foreign worldgen isolation audit (dump active structure sets, biome
      modifiers, and confirm nothing but Dungeon Crawl reaches our biome, tags,
      or dimension; verify the Sparse Structures salt rewrite and Structure
      Layout Optimizer against our lane set) (static half done: Sparse
      Structures rewrites structure-set JSON at datapack-registry load -
      spacing and separation scale by config factor (default 2, companion
      config pins 1.0) on every build, and on 3.0 only the salt is replaced
      by abs(setId.hashCode) when idBasedSalt is true (default), all
      deterministic but requiring our lane set's mirror-read to observe
      POST-rewrite values; Structure Layout Optimizer mixins jigsaw assembly
      only and never placement, so fixed-salt random_spread starts stay
      identical, with vanilla parity conditional on
      deduplicateShuffledTemplatePoolElementList=false and files at or above
      1.0.10; the audit tool list is recorded on the issue - /dumpstructuresets
      (ids only), per-dimension /locate as the isolation oracle, static greps
      of structure_set, neoforge/biome_modifier, biome tags and structure
      biomes fields, and our own startup log for the placement values no
      command prints - and today our datapack ships no biome, tags, sets or
      modifiers at all; the live half runs against a real world after Wave 2's
      #27 and #28 and again before release (#65) - issue #155)
- [x] Boss Checklist roster file merge behavior and defeated-tracking keys
      (done: coordinate pinned - Yori3o, CF 1355708 file 9109104, mod id
      boss_checklist, dependencies NeoForge + Minecraft only; the roster is
      data-only but SPLIT: display entries live in the asset file
      assets/boss_checklist/bosses.json (resource-stack merge, deduped by id,
      field-merged on collision unless replace is true) while
      data/boss_checklist/server_bosses_ids.json is a pure additive union on
      /reload; defeated state is a per-world map keyed by the PLAIN entity
      type id with the killer name as value, synced by payload and pruned when
      an id disappears, so every checklist boss needs its own distinct
      EntityType - flagged for the boss issues; our integration is three
      resources in our jar and inert without the mod - issue #156)
- [x] Skill Study Books item ids and skill encoding for loot tables (done:
      coordinate pinned - tensura_skill_books, CF 1418495 file 9055170,
      requires tensura + manascore; exactly ONE item id
      tensura_skill_books:skill_study_book with the skill carried in the
      vanilla minecraft:custom_data component as skill_id (random books use
      random_category), added in loot JSON via set_components; critical
      degrade finding: a table referencing the item fails to parse and is
      REPLACED WITH AN EMPTY TABLE when the mod is absent, so books live in a
      separate optional pool or a GLM behind neoforge:conditions and are never
      co-mingled with required dungeon loot - issue #157)
- [x] Tensura: All the Adventure contract pools, events, and dangerous
      encounter summon entrypoints (done from the pinned jar: pools are
      CODE-defined - 76 rank-gated ContractDefinition records in a static
      ContractCatalog, no datapack - with ContractCatalog.board(player, rank,
      ...) as the read-only presentation API for #143 and ContractRarity for
      reward scaling; acceptance and progress flow through their network
      payloads (Accept, Submit, Abandon, Reroll, Track) with no server-side
      accept call, a decision left to #143 and #144; encounter entrypoints are
      public static (GuildThreatManager.spawnPendingThreat,
      GuildEncounterSpawns ready/failed/succeeded, GuildExpeditionManager);
      there are no public events anywhere (AdventureServerEvents is internal);
      alpha volatility carried from #134 - issue #158, feeding #144)
- [x] Gateways activation inside a custom dimension and wave spawner
      interaction with spawn filtering (done: `GatePearlItem.useOn` carries no
      dimension check, so arenas open in the dungeon; waves spawn via
      `addFreshEntityWithPassengers` and the jar never references
      `MobSpawnEvent`, so the PositionCheck pool filter and gateway waves
      cannot interfere in either direction; `GateEvent` Opened / WaveStarted /
      WaveEnd / Completed are the challenge-room hooks of issue #49, and gates
      are datapack JSON under `data/gateways/gateways/` - issue #7)
- [x] FTB Teams and Journeymap APIs (soft-dep wiring) (done: FTB Teams
      2101.1.11 ships its API inside the jar - `FTBTeamsAPI.api().getManager()
      .getTeamForPlayerID` resolves the party, size = `Team.getMembers()`,
      leader = `getOwner()`, change events on the Architectury bus - all
      references confined to one class gated on ModList, else the resolver
      skips to vanilla; Journeymap 6.0.x carries API v2.0.0 at
      `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1` from
      maven.blamejared (compileOnly, never shade) with a SERVER-side waypoint
      CRUD API plus a cancellable client WaypointEvent, so `layer_only` is
      implementable with clean no-op degradation - issues #8 and #9)
- [x] Fallen Adventurers blockstate properties (MCreator blocks) (done: all
      49 skeleton blocks are single-property - `facing` north/east/south/west
      mapped to y rotations 0/90/180/270 and nothing else anywhere, no
      multipart and no uv_lock - so generation sets one horizontal facing and
      the blockstate file handles the rest; the 51 block loot tables are these
      49 plus two modelless `_no_spawn` blocks that stay out of decoration
      palettes - issue #10)
- [x] Sophisticated Storage versus Lootr interop (expected: decoration only)
      (confirmed: zero Lootr references in Sophisticated Storage or Core, and
      Lootr's `ILootrBlockEntityConverter` is never registered by SS, so SS
      containers are ordinary block entities that per-player instancing
      skips - SS stays decoration only and dungeon loot stays in vanilla
      containers, which Lootr instances by default; flag for issue #53:
      confirm our dimension converts by default, Lootr's dimension lists
      default empty - issue #11)
- [x] Chunky pregeneration determinism and throughput measurement (done:
      coordinate pinned - Curse 485681 file 6383261, Chunky-NeoForge-1.4.23,
      mod id chunky, no runtime deps; `/chunky world infinite_dungeon:dungeon`
      is source-verified; Chunky generates nothing itself - it drives the
      vanilla chunk pipeline through a load ticket, skips existing full chunks
      untouched, and never ticks generated chunks by default, so same seed
      plus same pack equals identical chunks; the verification protocol on
      issue #12 compares SHA256 of `r.X.Z.mca` under the dungeon's
      `dimensions/infinite_dungeon/dungeon/region` between a pregen world and
      an organic world, escalating to volatile-field-normalized NBT comparison
      on raw mismatch; throughput method pinned - cps from `/chunky progress`,
      budget equals `ceil(R/8)^2 / cps` with a 1.5-2x safety factor - the
      NeoForge dungeon number is the timed run left to the Wave 7 pack docs -
      issue #12)
- [x] Distant Horizons LOD rendering at full dimension height (done: DH
      3.3.3-1.21.1 generates LODs in custom dimensions automatically -
      denylist-only config with `ignoredDimensionCsv` empty - and reads world
      min/max Y dynamically from LevelHeightAccessor, so the 2048-high open
      dimension is supported; three known visual regressions are recorded on
      issue #13 with decisions pending: barrier is ignored by default so the
      ceiling is invisible at LOD (removing it renders a red band instead),
      draw-time colour multipliers are not sampled so the portal tint is lost
      at LOD unless baked into textures or supplied via the DH API
      color-override event, and a sealed ceiling may cave-cull below y60; the
      in-game protocol is on the issue)
- [x] Living Horizons entity rendering at LOD distance (third-party bosses)
      (premise corrected on issue #14: no such Distant Horizons addon exists -
      the Curse project of that name is an unrelated cosmetic mod with no
      1.21.1 builds; the real mod is LOD Entity Rendering, Modrinth EUtzxmSy
      and CF 1058409, released for 1.20.1 only with an unreleased 1.21.1
      branch, and it renders flat average-coloured boxes with a black fallback
      rather than models - GeckoLib bosses need an
      `average_colors/entity/*.json` resource pack and box scaling follows the
      server bounding box, which Tensura scaling leaves unverified; pursuing
      it would be a new feature issue)

Delivery is one feature per PR and per alpha bump. The suite is built in
dependency order (section 19): the API library first, then the standalone
spawn-safety module, then the core mod, then the integration modules:

- Wave 1 (core skeleton): scaffold; dimension and dimension type; portal
  construction and tinted rendering; return anchors and construction toggle;
  adventure lock; death policy.
- Wave 2 (core world): Dungeon Crawl wiring; lane and layer geometry with the
  depth API; fallback halls; layer transitions; waystones and waypoint policy;
  dimension profile resolution; tower-entrance portals and village expansion.
- Wave 3 (Tensura spine): difficulty curve and the attribute-scaling
  framework; magicule curve, provider registration, particles, and
  structure-local elevation; the tensura integration module (attributes,
  existence stats, per-chunk magicule write, Tensura and NEB boss roster).
- Wave 4 (core gameplay): density curve, pool schema, and ambient spawns;
  party scaling; the layer boss schedule (fixed per layer, seed-randomized,
  power-banded, with the depth-scaling exemptions) plus gating and respawn;
  minibosses and keys; safe rooms with the boundary block.
- Wave 5 (content): monster houses; treasure rooms; trap framework and trap
  set; mimics; gateway challenge rooms; decoration pass.
- Wave 6 (loot and integrations): depth loot tables; depth-injection framework
  with content defaults (Apotheosis bags, Lightman's Currency, Skill Study
  Books); Lootr; pity counter; Apotheosis; advancements; content roster and
  palette seeds; the optional integration modules (Traveler's Titles, Boss
  Checklist data, All the Adventure).
- Wave 7 (packaging): suppression companion datapack; Sparse Structures
  companion config; Chunky and pack integration docs; release 1.0.0.
- Wave 8 (guild and shops): the Adventurer Rank system, the populated lobby
  (reception, tiered shops on Lightman's Currency, waystone room), and
  rank-presented tasks and bounties. Its Phase 0 spikes (#132-#135) are
  complete and their findings are folded into section 20; the design lives
  there and the wave carries milestone "Wave 8 - guild and shops".

The API library's own waves (A1-A6) and the spawn-safety module's waves
(S1-S5) run before Wave 1, in their own repositories (section 19).

Gametest coverage plan: portal round-trip anchoring, anchor-unset fallback,
construction refusal, depth derivation, per-layer floor-count determinism,
difficulty, magicule, and density curve math, boss schedule determinism and
power-band fitting, adventure lock, boss gating and respawn cooldown, spawn
pool filtering, profile resolution, Lootr instancing, and pool JSON reload
filtering. Pure math additionally gets direct JUnit suites.

## 18. Decision record

Resolved during planning:

- Dungeon Crawl placement: accept the natural grid; infrastructure hangs off
  the in-lane dungeon (option Q3=a); fallback halls are anti-dead-end only.
- The lane structure set references `dungeoncrawl:dungeon` directly
  (spike finding: structure sets may reference any structure), so the
  mirror-structure concern dissolved; the biome gate is an additive
  override of the replace-false biome tag.
- Suppression is a separate optional datapack, off by default.
- Dungeon Crawl and Waystones are core and untoggleable; every other
  integration is optional, disable-only, and ModList-gated.
- Boss gating is on; the unlock persists once a guardian falls; guardians
  respawn on chamber entry after a configurable cooldown for farming.
- Difficulty is linear within a layer and quadratic between layers; the
  coefficients are config-tunable; magicule concentration follows the same
  shape with independent coefficients.
- Floors per layer are variable (5-10), deterministic from seed and layer
  index, not a fixed config value.
- Larger layers means more floors, hence more minibosses.
- Journeymap is a soft-dep only; Born in Chaos is a soft-dep only.
- Gateway challenge rooms are in scope for v1 (PR5 stretch); fuller puzzle
  systems are post-1.0.
- Realm RPG: Fallen Adventurers feeds decoration, not spawns (its skeletons
  are blocks, verified in the shipped jar).
- Most integration surfaces are datapack data with runtime filtering;
  config is reserved for behavior toggles and curve coefficients.
- Mob density is a third independent curve (linear within a layer,
  quadratic between layers) that drives spawn counts only, with a hard
  per-chunk safety cap.
- The dimension must be Chunky-pregeneration-safe: all world content is
  seed-derived at chunk-generation time (waystones included), with no
  visitor-triggered edits, and must render and behave correctly under
  Distant Horizons and Living Horizons.
- Features ship one by one (one PR and one alpha per feature). The suite is
  built in dependency order: the API library, then the standalone spawn-safety
  module, then the core mod, then the integration modules (section 19).
- Spawn safety is a standalone module: per-category spawn toggles with
  datapack whitelist/blacklist data, dimension whitelist/blacklist filtering
  (overworld affected, dungeon dimensions exempt by default), and a magicule
  gate comparing the ambient concentration against the mob's own AP (aura),
  MP (magicule), or EP (stored energy), default EP, with a configurable
  deviation band. Only natural spawns are ever gated.
- Mob classification is derived (datapack override, then the NeutralMob
  interface, then the Monster class or MONSTER category, then passive)
  rather than curated.
- Bosses are fixed per layer and randomized by world seed; the Tensura family
  is ordered monotone ascending by EP/AP/MP with free cross-family
  interleaving, selection fits a per-layer power band measured after depth
  scaling, and Tensura and NEB bosses are exempt from the depth factor while
  still scaling with party size.
- The dimension is The-End-like: a void generator with no floor or ceiling of
  its own. The clock is owned by the mod: it runs by default and can be held
  at a fixed tick as a server option; players may nap in beds.
- The player's respawn point is never settable inside the dungeon dimension:
  naps preserve the stored respawn target, the spawn-point command is refused,
  and any stored dungeon respawn is undone at death, so the lobby teleport is
  the only respawn path.
- The Wave-1 dungeon entrance is built once on first arrival at fixed
  coordinates: position-deterministic, so pregenerated and organic worlds
  converge - a deliberate, recorded deviation from the no-visitor-edits rule,
  pending Wave 2's generated entrances.
- Dungeon portals build only in whitelisted dimensions by default (the
  overworld and the dungeon itself), matching the spawn-safety module's
  overworld whitelist: no entrances sprout in the Nether, the End, or another
  mod's dimension. The gate covers construction only; travel is never gated.
- The dungeon's lobby is its first layer, of fixed construction (user ruling):
  death returns players there, the seed-derived floor rules apply to the layers
  above it, and Wave 2's fixed lobby replaces the Wave-1 arrival placeholder.
  PlayerRevive integration is deferred indefinitely (#25), so the lobby trigger
  currently applies to every death in the dungeon.
- The lobby is the dungeon's guild society, in five fixed floors (section 20):
  shops trade through Lightman's Currency traders - optional, degrade-only,
  with no custom screen anywhere; ranks are per player, authoritative from All
  The Adventure when it is loaded; tasks and bounties are shared with the team
  with rank averaging on by default; and NPC shops deliberately leave
  player-trade goods unsold so the player economy has gaps to fill.
- We ship one dungeon dimension; datapack dimension profiles (and the API's
  code registry) let addons add more with their own limits, pools, and curves.
- Elite mobs are randomly sized within configured bounds and always at least
  slightly larger than the regular counterpart of the same variant, with
  Tensura variant sizes as the baseline.
- Advancements cover layers conquered, bosses slain, and depth records through
  custom criteria fired by our own events.
- Dungeon structures, nether portals, and tower entrances raise local magicule
  concentration through the API's concentration modifiers; generation-time
  elevation is seed-derived.
- Traveler's Titles is an optional announcer for dimension entry and waystone
  arrival; floor and layer titles come from our own announcement system.
- Foreign worldgen is excluded by biome-tag hygiene, the Sparse Structures
  companion config exempts our structure sets, and our lane set mirrors
  Dungeon Crawl's effective registry placement values rather than constants.
- Boss Checklist, Skill Study Books, and Tensura: All the Adventure are
  optional integrations; the first two are data-only and the last is
  spike-gated.
- Suite modules consume each other from the org's GitHub Packages Maven
  registry, never from local jars or vendored copies.
- Announcement texts are config-driven so operators can reword them; shipped
  defaults stay ASCII-only with no emojis.

## 19. Module suite

The suite is split so no module depends on another by accident. Every module
is its own repository, its own mod, its own release, and its own issue board.

| Module | Responsibility | Depends on |
|---|---|---|
| `infinite-dungeon-api` | Public API and shared mechanics: dimension profiles, ambient magicule service, entity power stats, lifecycle events, datapack utilities, spawn classification | Minecraft, NeoForge |
| `infinite-dungeon-spawn-safety` | The standalone spawn gate: category toggles, dimension filter, and the magicule gate over natural spawns only | `-api` |
| `infinite-dungeon` (core) | Dimension, generation, entry, progression, spawns, bosses, rooms, loot, and spawn-safety-free gameplay | `-api`, Dungeon Crawl, Waystones |
| `infinite-dungeon-tensura` | Attribute scaling, existence stats (AP/MP/EP), per-chunk magicule provider and write, Tensura and NEB boss roster | `-api`, core, Tensura |
| `infinite-dungeon-apotheosis` | Apotheosis affixes, loot behavior, and elite interplay | `-api`, core, Apotheosis |
| `infinite-dungeon-gateways` | Gateway challenge rooms inside the dungeon | `-api`, core, Gateways to Eternity |
| `infinite-dungeon-alltheadventure` | Guild contracts and rank hooks (spike-gated) | `-api`, core, All the Adventure |
| `infinite-dungeon-travelerstitles` | Dimension-entry and waystone-arrival announcements | `-api`, core, Traveler's Titles |
| `infinite-dungeon-parties` | OPAC and FTB Teams party providers | `-api`, core, OPAC/FTB |
| `infinite-dungeon-deathcompat` | Corpse `despawnWhenEmpty` and PlayerRevive bleed-out handling | `-api`, core, Corpse/PlayerRevive |
| `infinite-dungeon-waypoints` (client) | Xaero and Journeymap waypoint filtering | `-api`, core, Xaero/Journeymap |
| `infinite-dungeon-easynpc` | NPC characters for the guild lobby: receptionist, guild master, adventurers | `-api`, core, EasyNPC |
| `infinite-dungeon-bountiful` | Bounties inside the dungeon, presented by Adventurer Rank | `-api`, core, Bountiful |

Companion datapacks stay release assets of the core repository: overworld
suppression (section 15), the Boss Checklist roster merge, and the Sparse
Structures config.

Rules for every module:

- Dependency direction is inward: modules depend on the API library, never on
  each other.
- Data-only integrations (rosters, decoration, loot) stay inside the core
  mod's datapack with `required_mod` gating; only integrations that need code
  become modules.
- Third-party dependencies resolve through CurseMaven (or the official
  Modrinth maven); suite modules resolve through GitHub Packages.
- Each module carries its own design plan under `docs/DEVELOPMENT_PLAN.md`,
  with this plan remaining the suite-level source of truth.
- Repositories are created upfront with their boards and milestones seeded, so
  work is planned in advance; implementation still proceeds in dependency
  order.

## 20. The Adventurer Guild and shops

The lobby - the dungeon's first layer, of fixed construction (section 3) - is
the dungeon's society: a guild that registers adventurers, shops that trade
them in, and a waystone room where parties gather.

### Lobby floors

| Floor | Purpose |
|---|---|
| 1 | Guild reception: adventurer registration, where ranks are granted, plus task and bounty handling, private reception rooms, the guild master's office, and decorative temporary lodging rooms |
| 2 | Guild-backed shops: adventurer essentials, and the guild buying monster materials and magic crystals |
| 3 | Specialised shops |
| 4 | Advanced specialised shops |
| 5 | Waystone room / respawn room: the arrival room for lobby teleports, littered with adventurer parties |

Two caveats apply throughout: the respawn room is a landing room and never a
respawn point (the no-spawn-point rule of section 5), and lodging beds are
decoration that neither explodes nor sets a spawn point.

### Adventurer Ranks

- Ranks are per player. Tasks and bounties are shared with the team.
- The source is a provider menu: Tensura: All The Adventure's own rank system
  is authoritative when it is loaded - integrated through its public API, no
  mixins; the core's own thin storage is the fallback. Consumers read one
  facade and never care which provider is live, and the facade stays
  adapter-shaped so a future provider slots in without redesign. The Phase 0
  survey (#135) found no third system worth adopting - no NeoForge 1.21.1
  mod ships an earned adventurer rank with a public API - so the menu holds
  exactly those two entries for now.
- Spike findings (#134) pinning the authoritative side: ranks read through
  All The Adventure's public statics (`adventurerRank(player)`, a rank enum
  from RANKLESS through SS) - public but unofficial and alpha-cadenced, so
  the pinned coordinate is re-inspected at wiring. There is no rank-change
  event: gating does not need one (the rank is read at each evaluation), but
  future rank-up announcements must poll a snapshot diff. `/tata rank
  set|advance` covers admin and tests. Pack flag from #135: TenSura Guild
  ships a rival F..S guild rank for the same base mod - the pack runs one
  guild-rank source, and ours reads All The Adventure. Apotheosis world
  tiers and Tensura awakening status are readable signals to map later,
  never sources.
- Shared offer lists resolve the team through PartySizeResolver precedence
  (OPAC, then FTB Teams, then vanilla teams - section 8). Offer policy:
  `guild.averageTeamRank` (default true) averages the team's ranks,
  `guild.highestTeamRank` (default false) picks the highest, and the team
  leader's rank is the base rule when both are off. Providers without a leader
  concept - vanilla teams - fall through to averaging, which is the default
  anyway.

### Shops

- Selling and buying are handled by Lightman's Currency traders: no custom
  screen and no vanilla merchant interface anywhere. Dungeon loot injects the
  same currency (section 12), so one economy closes the loop.
- Lightman's Currency is optional: when absent, shops and buy-back degrade
  disable-only with a clear reason, ModList-gated.
- Stock is data, per section 19's data-only rule: keyed by floor tier and shop
  category, priced in Lightman's Currency, gated by rank - purchase, and
  optionally selling or selling at full price - with each integration family's
  pack (Tensura, Apotheosis, Iron's Spells and Spellbooks, Ice and Fire CE)
  entering through required_mod. Lightman's ships no native datapack trades
  (#133): the data is ours, applied when the lobby is built - pushed into
  traders through `TraderAPI` at construction, or embedded with `/lcadmin
  traderdata prepareForStructure` - chosen at wiring, with the stock data
  always reviewable in this repo.
- Rank gating is Lightman-native (#134): availability rides a custom
  `TradeRuleType` registered through `TraderAPI` (every `TradeContext`
  carries the acting player), full-price versus discounted selling rides
  `TradeCostEvent` (`giveDiscount`/`hikePrice`/`setPricePercentage`), and
  per-player stock limits plus player discounts ship built in. Trader
  furniture is placement - display cases, shelves, vending machines - with
  terminals and network traders available per floor; traders persist in
  Lightman's storage keyed by their global id.
- Wares gaps: NPC shops deliberately exclude player-trade goods - dragon
  materials, mob eggs and the like - so players fill those gaps themselves and
  the player economy keeps moving.

### Populating the lobby (EasyNPC)

- NPCs are characters only: dialog, flavour, and task hand-out. Every
  transaction happens on the adjacent Lightman's trader block - both spikes
  confirmed no open-a-trader-for-player bridge exists (#133's command
  surface, #132's fake-player `Interact Block`) - so the receptionist stands
  beside the counter rather than behind a screen.
- Lobby NPCs are authored content, never generated: built once as EasyNPC
  presets and imported at fixed positions during lobby construction, not
  per-seed and not visitor-triggered (section 16 fit); spawner blocks are
  avoided in favour of preset imports.
- Double-economy guard: NPC trading mode is `None` and the default `Open
  Trading` action is stripped - EasyNPC ships a full merchant system that
  new NPCs pre-attach, and only Lightman's Currency may move money. Record
  retention is set to keep authored NPCs forever (`killedNPCRetentionDays=0`).
- The core mod carries zero EasyNPC class references: the mod is optional,
  glue lives in infinite-dungeon-easynpc behind ModList, and absence leaves
  empty rooms with core unaffected. Its API is explicitly unstable until
  declared stable (#132), so presets and commands come before API coupling;
  a five-floor lobby wants tens of NPCs, within normal entity budgets, with
  the mod's pause system available if needed.

### Tasks and bounties

- Bounties come from Bountiful and tasks from Tensura: All The Adventure, both
  presented dynamically from the Adventurer Rank and both shared with the
  team; both optional, degrade-only.

### Spike findings (Phase 0, complete)

All four spikes are closed with source-cited findings on their issues:

- EasyNPC capabilities (#132): strong fit - modid `easy_npc`, coordinate
  pinned in section 13; preset-authored NPCs, `None` trading, dialogs with
  scoreboard conditions and actions; the lobby subsection above carries its
  risks (double economy, record retention, unstable API, section 16 fit).
- Lightman's Currency trader API (#133): programmatic trader creation and
  custom trade rules through `TraderAPI`, the acting player on every
  `TradeContext`, no native datapack trades (stock applied by us at
  construction), no open-trader-for-player command (adjacency is the
  architecture).
- Adventurer Rank gating (#134): All The Adventure exists and loads in this
  pack (the pinned tensura 2.0.1.3 plus manascore 4.0.0.2); rank reads are
  public statics; rank gates are a custom `TradeRuleType`, rank prices are
  `TradeCostEvent`.
- Third-party rank survey (#135): nothing worth adopting; the provider menu
  stays All The Adventure plus the core fallback, adapter-shaped, with
  TenSura Guild recorded as the only alternative and a pack-level flag
  against running two guild-rank sources.

With these findings folded into the sections above, Wave 8 code may proceed
in milestone "Wave 8 - guild and shops".
