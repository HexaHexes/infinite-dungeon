# Agent rules and conventions - Infinite Dungeon (core)

**Repo:** `HexaHexes/infinite-dungeon` - **Mod ID:** `infinite_dungeon` - **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 - **Jar:** `build/libs/infinite_dungeon-<version>.jar`

The dungeon dimension: descending floors and layers, magicule-tuned spawns and
bosses, safe rooms, and depth-scaled loot. The suite's shared mechanics live in
`infinite-dungeon-api`; this mod ships the gameplay.

**Dependencies**
- Required: Minecraft, NeoForge, `infinite-dungeon-api` (GitHub Packages Maven).
- Required at runtime once wired (Wave 2): Dungeon Crawl, Waystones - both core
  and untoggleable per the plan's decision record.
- Optional: everything else, through the suite's integration modules.

**Test:** `./gradlew runGameTestServer` - `InfiniteDungeonGameTests` (1 test).
`expectedTests = 1` in `build.gradle` must match the `@GameTest` count.

**Gotchas**
- `api_version` pins the published API release: bump deliberately, never
  implicitly, and only once a release has published the artifact. The toml's
  required range derives from the same value.
- The plan's Phase 0 checklist and the C-series amendments live in
  `docs/DEVELOPMENT_PLAN.md`; amendments land with their implementing waves.
- The GameTestServer can wedge in its progress loop; cancel and rerun before
  diagnosing code (tracked in this repo's issue tracker).

---

## Branch sync (do this first)

Before starting any new development in this repo:

1. `git status` — confirm what uncommitted work exists. Never disturb or sweep it.
2. `git pull --ff-only` — sync with the remote. A fast-forward is the normal case,
   because we push straight to the shared branch.
3. If the pull would **not** fast-forward (the branch has diverged), or if it would
   collide with local uncommitted work, **stop and ask** rather than merging, rebasing
   or stashing on your own.

## Scope doctrine

- **One mod + bundled datapack.** No KubeJS scripts.
- **Data-driven over code** — prefer JSON/datapack content to Java when both work.
- **Public API only, no mixins** in this repository.

## Verification

- Verify with the in-repo GameTest run (`./gradlew runGameTestServer`). Repos without
  GameTests verify with `./gradlew build`. Never verify against a live CurseForge
  instance.
- Keep `expectedTests` in `build.gradle` in sync with the `@GameTest` methods.
- **Client-render features cannot be proven headless.** The honest scope is
  "build green + the user's in-game check" - say so rather than overclaiming.

## Build environment

- JDK 21 (`JAVA_HOME` -> JDK 21); the toolchain targets Java 21.
- The Gradle wrapper is committed. `./gradlew build` compiles,
  `./gradlew runGameTestServer` runs the GameTest server.
- **Dependencies resolve through Maven, never local folders.** Suite modules come
  from the org's GitHub Packages registry; third-party mods use CurseMaven
  coordinates (`curse.maven:<slug>-<projectId>:<fileId>`, repository
  `https://cursemaven.com`) or the official Modrinth maven. Gitignored `libs/` and
  `testmods/` staging folders are retired. Pin the exact version the repo was
  verified against; bump pins deliberately, never implicitly.
- On `run/` file-lock errors, kill any stale `java` process and retry.

## PowerShell quirks (this org is developed on Windows)

- A `git push` "error" in PowerShell is usually stderr passthrough — success is the
  `hash..hash  branch -> branch` line, not the absence of red text.
- `New-Object Type(args)` breaks on arithmetic — use `::new()` or a parenthesized
  argument.
- **Never write files with `Set-Content -Encoding UTF8`** — PowerShell 5.1 writes a
  UTF-8 BOM that breaks Gradle's parser and YAML. Use
  `[System.IO.File]::WriteAllText($path, $text)` (no BOM). Re-check for BOMs after
  any script-written edit.

## Git & release

- Remote is SSH: `git@github.com:HexaHexes/<name>.git`.
- Push only after tests are green **and** the user has given the go.
- Never sweep the user's uncommitted work into a commit — commit only what is intended.
- Commit subjects: concise, imperative.
- **Version bump on finish:** always bump the version when a new feature or bugfix
  is finished.
- **One feature or bugfix per branch:** develop each on its own feature branch and
  merge it into main/master through a pull request with a **squash merge**. The PR
  body must detail the work done, following `.github/pull_request_template.md`.

## House style

- **No icons** unless the user explicitly asks.
- READMEs are **player-facing only**: pitch, mechanics in plain terms, in-game usage,
  required/optional dependencies, credits, license. No build internals, no class names.
- This repo is **LGPL v2.1**, copyright Bitwise Madness. `NOTICE` / attribution files
  are mandatory and must never be deleted.
- Deploy to the Tensura RPG instance **only** when the user asks.

## Naming

- **Repo and folder:** lowercase, hyphenated (e.g. `tensura-colony-gear`).
- **`mod_id`:** lowercase with underscores (e.g. `tensura_colony_gear`). Minecraft
  requires `[a-z][a-z0-9_]{1,63}`.
- **Jar / `archivesName`:** follows `mod_id` by default.
- **Java base package:** `org.bitwisemadness.<project-name>` - repo name lowercased,
  separators stripped (e.g. `ds-tensura-fix` -> `org.bitwisemadness.dstensurafix`).
  The Maven `group` in `build.gradle` matches (`org.bitwisemadness`).
- Keep repo name, folder name, `mod_id`, and the Java package aligned.
- **This template is canonical for org-wide rules.** When a rule is added or changed
  in any repo's `agents/AGENTS.md`, mirror the same change into the template so new
  repos inherit it.

## CI & release model

- `ci.yml` currently enforces the **version-bump guard only**: a beta or stable bump must
  change the version file and nothing else; an `-alpha` bump may bundle work; a PR labelled
  `breaking` forces the next clean bump to raise the major or minor version.
- **Build and GameTest jobs are deferred** even though the original blocker is
  gone: the API now publishes through GitHub Packages, so a clean checkout can
  compile. Wiring the jobs is tracked in the issue tracker. Until they land,
  run `./gradlew build` and the GameTest task locally before pushing - local
  verification is the real gate for this repo.
- `version-bump.yml` opens an isolated version-bump PR (only the version file changes).
- CurseForge publishing (`curseforge.yml`) is **deferred** until the mod has a CurseForge
  listing and the `CURSEFORGE_PROJECT_ID` / `CURSEFORGE_API_KEY` secrets. Do not add it
  speculatively.
