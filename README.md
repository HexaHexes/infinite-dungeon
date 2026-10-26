# Infinite Dungeon

A dungeon dimension for NeoForge: descend through floors and layers that grow
more dangerous with depth, where the ambient magicule decides what can survive.

## What it does

In development - this early alpha ships the mod's foundation. Over the coming
alphas it grows:

- A dungeon dimension of seed-derived floors and layers, entered through a
  portal and exited through your own return anchor.
- Spawns and bosses tuned to the dungeon's magicule curve, with per-layer
  guardians and minibosses.
- Safe rooms, monster houses, traps, and loot that scales with depth.

The suite's shared mechanics live in [Infinite Dungeon
API](https://github.com/HexaHexes/infinite-dungeon-api); integration modules for
Tensura, Apotheosis, and friends build on top.

## Getting it in-game

Install the mod and its required dependencies on a NeoForge 1.21.1 server or
client. Content arrives with the coming alphas; until then the mod loads and
does nothing on its own.

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft 1.21.1 | - |
| Required | NeoForge 21.1.259 | - |
| Required | Infinite Dungeon API | Dimension profiles, ambient magicule and entity power services, lifecycle events |

## Configuration

Server operators can reword the announcement texts (the adventure lock
explanation, the anchor fallback note, boss awakenings, and room events) in the
config file. Placeholders written as `{token}` in the text are filled in by the
feature that sends the message.

## Credits

- Built on [NeoForge](https://neoforged.net/).

## License

GNU Lesser General Public License v2.1 — see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.
