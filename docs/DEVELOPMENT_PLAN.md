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

- Dimension type: `min_y: -64`, `height: 2048`. This is locked; changing it
  later is destructive to existing worlds.
- Layers live on X-lanes at `L * laneStride`. `laneStride` is Dungeon Crawl's
  natural placement grid (32 chunks = 512 blocks; the salt is read from
  Dungeon Crawl's structure set file in Phase 0).
- Infrastructure hangs off whichever Dungeon Crawl dungeon lands in the lane
  cell. Lightweight fallback halls exist only as an anti-dead-end guard.
- Floors per layer are variable, 5 to 10 inclusive, deterministic from
  (world seed, layer index). This is not a fixed config constant; only
  `floorsPerLayerMin` and `floorsPerLayerMax` are configurable.
- Floors stack vertically within a layer; the band height is derived to fit
  the dimension.
- Depth is position-derivable everywhere: depth maps to
  (layerIndex, floorInLayer) as a pure function of position and seed. Loot,
  spawns, waypoints, difficulty, and magicule concentration all derive from
  it.
- The main datapack adds our biome to Dungeon Crawl's gate additively and
  ships our own mirror structure set, so the optional suppression datapack
  (which neutralizes Dungeon Crawl's original set) cannot kill our dimension.
- Wild bonus Dungeon Crawl dungeons may appear between lanes when the
  suppression pack is off; they are scaled and filtered like everything else.

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
- Anchors store a generic dimension id, so entry from any modded dimension
  works and returns there.

## 5. Adventure lock and death policy

- Gamemode is swapped on entry and restored on exit. Block breaking is
  cancelled inside the dimension, with a configurable whitelist of
  player-placeable blocks (including placed corpses). Announcements explain
  the lock.
- Death teleports the player to the dungeon lobby on final death only.
  PlayerRevive bleed-out is respected; a successful revive keeps the player
  in the dungeon.
- The Corpse mod behaves normally: the corpse spawns at the death site with
  the inventory and is reclaimed by walking back.
  `integrations.corpse.despawnWhenEmpty` is configurable.

## 6. Progression

- One Waystone per layer entrance, named "Layer N". Waystones, like all
  other infrastructure, are placed during chunk generation, never on first
  visit, so Chunky-pregenerated chunks are identical to organic ones
  (section 15).
- Waypoint policy: `waypoints.mode` = `all` | `layer_only` (default) | `none`,
  filtering Xaero's minimap and world map (in pack). Journeymap is a
  soft-dep with the same filter behind its own toggle.
- The layer guardian gates the transition to the next layer. The unlock flag
  persists per layer in world saved data once a guardian has been slain at
  least once.
- Guardians respawn on chamber entry after `bossRespawnCooldown` seconds
  (default 600), making them repeatable farm content with full boss loot on
  each kill. Within the cooldown the chamber is empty with a flavor message.
  Each respawn rolls a fresh boss from the boss whitelist, scaled by the
  current difficulty value and party size at spawn time, with an
  announcement.
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
`quadraticPerLayer` 0.04, `particles` on, `tensuraAttributeScaling` on.

- `getMagiculeConcentration(pos)` is exposed through the public API as a
  pure position-derived function.
- M drives ambient magicule particles (density and optional tint keyed to M,
  vanilla particle textures only) and magicule-specific attribute scaling
  (Tensura's attributes by default, configurable).
- The base mod is gameplay-neutral toward players: player-side magicule
  effects belong to Tensura's own systems reading our value (PR 7).
- Both functions are pure math and JUnit-tested: growth shape,
  monotonicity, coefficient edge cases, and independence of the two curves.

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
- Integration-seeded defaults: boss tier from Bosses'Rise, Cult of Azazel,
  L_Ender's Cataclysm, and Ice and Fire CE; fodder and elite tiers from
  Mowzie's Mobs, Mobs of Mythology, Variants & Ventures, Realm RPG: Fallen
  Adventurers, and Born in Chaos (soft-dep). Entities with ritual or arena
  scripted fights are excluded from defaults if they degrade when spawned
  plainly (reviewed during wiring).

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

Plain tags are not used as the primary pool mechanism because a tag entry
referencing an absent mod's entity fails datapack validation on reload. Our
own schema filters registry ids and `required_mod` entries in code, skipping
missing ones silently, while still accepting tag references inside entries
(expanded through holder sets, empty tags tolerated).

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
| `ftbTeams` | soft-dep, not in pack, coordinate at wiring |
| `corpse` | `corpse-316582:9188333` (plus `despawnWhenEmpty`) |
| `playerRevive` | `playerrevive-266890:8057637` |
| `xaero` | `xaeroworldmap-317780:9067277`, `xaerominimap-263420:9067173` |
| `journeymap` | soft-dep only |
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

## 14. Overworld suppression datapack

- Ships as a separate companion zip in the repository and release assets. It
  cannot live inside the mod jar because mod datapacks auto-enable.
- Neutralizes Dungeon Crawl's original structure set so overworld dungeon
  generation stops. Default off. Affects newly generated chunks only.
- A one-file override, version-pinned to the Dungeon Crawl version in the
  pack. Phase 0 verifies that an empty structures list is valid.

## 15. Compatibility: Chunky, Distant Horizons, Living Horizons

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

All three verification items are Phase 0 spikes; any finding that requires
code (for example a Distant Horizons dimension exclusion option) becomes a
client config knob rather than a gameplay integration.

## 16. Delivery phases

Phase 0 spike checklist:

- [ ] Dungeon Crawl biome-gate JSON shape, structure set file, and salt
- [ ] Custom generator pipeline for our mirror structure set
- [ ] Waystones programmatic placement and naming API
- [ ] Corpse empty-removal API and adventure-lock removal path
- [ ] PlayerRevive event ordering versus final death
- [ ] Xaero waypoint filter API
- [ ] Tensura attribute names, attribute tag availability, and environment or
      magicule hooks
- [ ] Gateways activation inside a custom dimension and wave spawner
      interaction with spawn filtering
- [ ] FTB Teams and Journeymap APIs (soft-dep wiring)
- [ ] Fallen Adventurers blockstate properties (MCreator blocks)
- [ ] Sophisticated Storage versus Lootr interop (expected: decoration only)
- [ ] Chunky pregeneration determinism and throughput measurement
- [ ] Distant Horizons LOD rendering at full dimension height
- [ ] Living Horizons entity rendering at LOD distance (third-party bosses)

PR sequence, each bumping the alpha:

- [ ] PR1: scaffold from template (mod id, package, README, AGENTS.md,
      gametest harness)
- [ ] PR2: dimension, portal, adventure lock, lobby, death policy, Corpse and
      PlayerRevive handling
- [ ] PR3: Dungeon Crawl wiring, lanes and layers, transitions, safe rooms,
      Waystones, waypoint policy
- [ ] PR4: spawns, difficulty, magicule, and density curves, party scaling,
      minibosses, guardians with gating and respawn
- [ ] PR5: rooms and events (monster houses, treasure rooms, traps, mimics,
      decoration pass, gateway challenge rooms)
- [ ] PR6: loot curves, Lootr, pity counter
- [ ] PR7: Tensura and Apotheosis behavior integration
- [ ] PR8: content-default integrations (roster seeds, loot injections,
      decoration palettes as datapack defaults)
- [ ] PR9: suppression companion datapack
- [ ] Release: isolated clean 1.0.0

Gametest coverage plan: portal round-trip anchoring, anchor-unset fallback,
construction refusal, depth derivation, per-layer floor-count determinism,
difficulty, magicule, and density curve math, adventure lock, boss gating
and respawn cooldown, spawn pool filtering, Lootr instancing, and pool JSON
reload filtering. Pure math additionally gets direct JUnit suites.

## 17. Decision record

Resolved during planning:

- Dungeon Crawl placement: accept the natural grid; infrastructure hangs off
  the in-lane dungeon (option Q3=a); fallback halls are anti-dead-end only.
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
