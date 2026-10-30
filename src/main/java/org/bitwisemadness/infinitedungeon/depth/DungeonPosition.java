package org.bitwisemadness.infinitedungeon.depth;

/**
 * Where a position sits in the dungeon's layer stack (plan section 3).
 *
 * @param layerIndex the lane the position is in; 0 is the lobby layer
 * @param floorInLayer the floor within that layer, counted downward from its
 *         entry band at the top (floor 0 on the lobby); grows as the player
 *         descends - issue #183's axis
 * @param depth the global floor ordinal the position has descended - the
 *         lobby is depth 0, layer 1's floors start at 1, and every deeper
 *         floor of every deeper layer adds one (see {@link DungeonLayout})
 */
public record DungeonPosition(int layerIndex, int floorInLayer, int depth) {
}
