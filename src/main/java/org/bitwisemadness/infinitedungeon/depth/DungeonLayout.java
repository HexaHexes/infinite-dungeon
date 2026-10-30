package org.bitwisemadness.infinitedungeon.depth;

import org.bitwisemadness.infinitedungeon.lanes.FloorSchedule;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;

/**
 * Position-to-depth derivation (plan section 3, issue #29): a pure function
 * from (position, seed) to (depth, layerIndex, floorInLayer), exposed
 * publicly - loot, spawns, waypoints, difficulty, and magicule concentration
 * all derive from it, and every later wave composes these same functions.
 *
 * <p>Pure by construction: all state arrives through {@link LayoutParameters}
 * and the position itself, so the derivation is JUnit-testable and identical
 * on pregenerated and organic chunks (section 16).</p>
 *
 * <p>Semantics, fixed here as the downstream contract:</p>
 * <ul>
 * <li>Layer 0 is the lobby: fixed construction, depth 0, floor 0 for any Y -
 * its shape is issue #131's, so the derivation never guesses floors there.</li>
 * <li>Depth counts floors descended: layer 1's floors start at depth 1 (the
 * lobby contributes exactly one floor of headroom), each deeper floor adds
 * one, and the last floor of layer L sits immediately above the first floor
 * of layer L+1 - depth has no gaps across layer boundaries.</li>
 * <li>Negative lanes hold no layers: {@link #inDungeon} answers whether a
 * position is inside the dungeon at all, and {@link #derive} refuses positions
 * west of the first lane loudly rather than inventing a depth for the void.</li>
 * </ul>
 */
public final class DungeonLayout {

    /**
     * The lobby's contribution to every deeper depth: it is one floor of fixed
     * construction at depth 0 (revisited with issue #131 should the lobby grow
     * floors).
     */
    public static final int LOBBY_FLOORS = 1;

    private DungeonLayout() {
    }

    /** Whether a position lies inside the dungeon's layer stack (lane 0 or deeper). */
    public static boolean inDungeon(LayoutParameters parameters, int x) {
        return LaneGeometry.laneIndex(x, parameters.laneStride()) >= 0;
    }

    /** The derived position. Refuses X west of lane 0 (see class javadoc). */
    public static DungeonPosition derive(LayoutParameters parameters, int x, int y) {
        int layerIndex = LaneGeometry.laneIndex(x, parameters.laneStride());
        if (layerIndex < 0) {
            throw new IllegalArgumentException(
                    "no layer exists west of the first lane (x=" + x + ", stride="
                            + parameters.laneStride() + "); check inDungeon first");
        }
        if (layerIndex == 0) {
            return new DungeonPosition(0, 0, 0);
        }
        int floors = FloorSchedule.floorsInLayer(parameters.worldSeed(), layerIndex,
                parameters.floorsMin(), parameters.floorsMax());
        int floor = FloorSchedule.floorIndexInLayer(y, parameters.minY(),
                parameters.availableHeight(), floors);
        return new DungeonPosition(layerIndex, floor, LOBBY_FLOORS + floorsBefore(parameters, layerIndex) + floor);
    }

    /**
     * The floors of every layer above the lobby and below the given layer -
     * the depth offset of that layer's first floor. Public because loot depth
     * bands and spawn pools want the boundary as much as the derivation does.
     */
    public static int floorsBefore(LayoutParameters parameters, int layerIndex) {
        if (layerIndex < 1) {
            return 0;
        }
        int total = 0;
        for (int layer = 1; layer < layerIndex; layer++) {
            total += FloorSchedule.floorsInLayer(parameters.worldSeed(), layer,
                    parameters.floorsMin(), parameters.floorsMax());
        }
        return total;
    }
}
