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
- Required at runtime once wired (Wave 2): Dungeon Crawl, Waystones - both core
  and untoggleable per the plan's decision record.
- Optional: everything else, through the suite's integration modules.

**Test:** `./gradlew runGameTestServer` - `InfiniteDungeonGameTests` (11 tests).
`expectedTests = 11` in `build.gradle` must match the `@GameTest` count.

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