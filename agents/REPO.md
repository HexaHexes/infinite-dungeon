# Repo rules — Infinite Dungeon (core)

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/infinite-dungeon` · **Mod ID:** `infinite_dungeon` · **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 · **Jar:** `build/libs/infinite_dungeon-<version>.jar`

The dungeon dimension: descending floors and layers, magicule-tuned spawns and
bosses, safe rooms, and depth-scaled loot. The suite's shared mechanics live in
`infinite-dungeon-api`; this mod ships the gameplay.

**Dependencies**
- Required: Minecraft, NeoForge, `infinite-dungeon-api` (GitHub Packages Maven).
- Required: Dungeon Crawl (`dungeoncrawl [2.3.17,)` in neoforge.mods.toml) -
  wired by the lane structure set (issue #27): the dimension generates
  `dungeoncrawl:dungeon` and gates it through the biome tag; devRuntime carries
  the pinned coordinate `curse.maven:dungeon-crawl-324973:7887320`. Core and
  untoggleable per the plan's decision record.
- Required once wired (still Wave 2): Waystones - lands with its own issues
  (#68 chunk-gen placement, #33 waypoint policy).
- Optional: everything else, through the suite's integration modules.

**Test:** `./gradlew runGameTestServer` - `InfiniteDungeonGameTests` (14 tests).
`expectedTests = 14` in `build.gradle` must match the `@GameTest` count.

**Remote:** `github.com/HexaHexes/infinite-dungeon` (public, default branch
`main`).

---

## Gotchas (do not regress them)

- `api_version` pins the published API release: bump deliberately, never
  implicitly, and only once a release has published the artifact. The toml's
  required range derives from the same value.
- The plan's Phase 0 checklist and the C-series amendments live in
  `docs/DEVELOPMENT_PLAN.md`; amendments land with their implementing waves.
- The GameTestServer can wedge in its progress loop; cancel and rerun before
  diagnosing code (tracked in this repo's issue tracker).

## Naming - this repo

- Repo and folder: `infinite-dungeon`; `mod_id`: `infinite_dungeon`; Java base
  package `org.bitwisemadness.infinitedungeon`, with the Maven `group` in
  `build.gradle` matching.

## CI & release model - this repo

- `ci.yml` runs the template's checks: junit, gametest, version-bump, plus
  the `static` job (SpotBugs + Spotless, **enforced** since the template #52
  flip: findings fail `spotbugsMain` on the `check` graph, violations fail
  `spotlessCheck`) and the report-upload step. `codeql.yml` (analyze) and the
  dependency-review pair join them as required checks.
- `release.yml` cuts the GitHub release for `v<mod_version>` on a push to `main`
  (pre-releases only when the repo variable `ALPHA_RELEASES` is true), and
  `publish.yml` publishes the Maven artifact to the org's GitHub Packages
  registry when that release is published - see the org rulebook for the
  consumer-side auth details (`PACKAGE_READ_TOKEN`).