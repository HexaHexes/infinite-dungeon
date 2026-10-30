# Infinite Dungeon

A dungeon dimension for NeoForge: descend through floors and layers that grow
more dangerous with depth, where the ambient magicule decides what can survive.
It was built for the Tensura RPG pack.

## What it does

In development. This early alpha ships the mod's foundation, and the dungeon's
floors are still being built, so entering one of the first portals is a leap of
faith. Over the coming alphas it grows:

- A dungeon dimension of seed-derived floors and layers, entered through a
  portal and exited through your own return anchor: walking in through a
  portal in your world stores it as yours, and walking back out of the dungeon
  through a portal returns you to it. Parties who entered separately each exit
  at their own portal. Destroyed anchors are re-formed on the way out; a
  missing anchor sends you to the world spawn with a message.
- Spawns and bosses tuned to the dungeon's magicule curve, with per-layer
  guardians and minibosses.
- Safe rooms, monster houses, traps, and loot that scales with depth.
- Inside the dungeon you play in adventure mode: block breaking is refused
  except for blocks a config whitelist allows, and your gamemode is restored
  the moment you leave. Dying in the dungeon sends you to its lobby, the
  first layer, rather than your ordinary respawn point.
- Beds never explode and you can nap in one; a nap never skips time and never
  touches your spawn point, which can never be set inside the dungeon
  dimension. Death always follows the dungeon's own exit policy.

The suite's shared mechanics live in [Infinite Dungeon
API](https://github.com/HexaHexes/infinite-dungeon-api); integration modules for
Tensura, Apotheosis, and friends build on top.

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft 1.21.1 | - |
| Required | NeoForge 21.1.259 | - |
| Required | Infinite Dungeon API | Dimension profiles, ambient magicule and entity power services, lifecycle events |
| Required | Dungeon Crawl | The dungeon dimension generates its dungeons from Dungeon Crawl structures |
| Required | Waystones | Layer entrances are Waystones, placed and named at world generation for fast travel between layers |

## Configuration

Server operators can reword the announcement texts (the adventure lock
explanation, the anchor fallback note, boss awakenings, and room events) in the
config file. Placeholders written as `{token}` in the text are filled in by the
feature that sends the message.

The time section holds whether the dungeon clock runs (`time.flows`, default
on, a plain day/night cycle) and the tick it is held at while frozen
(`time.fixedTick`, default 6000).

The floor count per layer is a range: `floorsPerLayerMin` (5) and `floorsPerLayerMax` (10) bound it, and the world seed decides the exact count for each layer inside that range. The first layer - the lobby - ignores both.

The portal section holds where portals may be built: `portal.buildableDimensions.mode`
(`whitelist` by default, `blacklist` for an exception list) plus
`portal.buildableDimensions.list`, which defaults to the overworld and the
dungeon. Travel through an already-formed portal is never gated.

A portal itself is a stone-brick frame in any nether-portal shape (corners
optional), lit with flint and steel or with fire. Portals built from obsidian
stay vanilla.

A separate **client** config file holds the portal tint: `portal.tint`, six
hex digits (`#` optional), applied to the portal's rendering over the vanilla
nether portal texture. The default `FFFFFF` leaves the texture exactly as it
ships.

`portal.allowConstruction` (default on) is the admin-fixture switch: when off,
every ignition attempt is refused with a message while already-formed portals
keep working.

## Credits

- Built on [NeoForge](https://neoforged.net/).

## License

GNU Lesser General Public License v2.1 — see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.

## In-game config editing

Mods built with this template can be edited in-game with
[Configured](https://www.curseforge.com/minecraft/mc-mods/configured) - install
it like any other mod; it adds no code to this one. Open the mod list, select a
mod, press Config, and change values directly: Configured writes through
NeoForge's config system, so a reload applies the new values without restarting
the game.

This template pre-wires the config-screen background in
`src/main/templates/META-INF/neoforge.mods.toml`
(`[modproperties.${mod_id}] configuredBackground`) - point it at any texture you
like. Note Configured only shows a menu for mods that actually register a
config: a template-derived mod with no registered settings has nothing to show.
