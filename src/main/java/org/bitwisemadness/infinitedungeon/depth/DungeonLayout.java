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
 * <li>The <b>lobby</b> is layer 0, and since issue #246 (Reading B) it lives
 * in the <b>Overworld entrance site</b>, not in the dungeon dimension -
 * so lane 0's region is no longer part of the dungeon and derives as
 * OUTSIDE. {@link #inDungeon} answers whether a position is inside the
 * dungeon at all (layer 1 or deeper), and {@link #derive} refuses lane 0
 * and everything west of it loudly rather than inventing a depth for the
 * void.</li>
 * <li>Depth counts floors descended: within a layer floor 0 is the entry
 * band at the <b>top</b> and floorInLayer grows downward as the player
 * descends (so section 7's difficulty, which grows with floorInLayer, deepens
 * with the dive); layer 1's floors start at depth 1 (the lobby contributes
 * exactly one floor of headroom at depth 0), and depth has no gaps across
 * layer boundaries - layer L's deepest floor is followed by layer L+1's entry
 * floor (issue #183 corrected the axis direction before any consumer
 * relied on it).</li>
 * </ul>
 */
public final class DungeonLayout {

    /**
     * The lobby's contribution to every deeper depth: it is one floor of fixed
     * construction at depth 0 - the lobby is the Overworld entrance site since
     * issue #246, so the numbering keeps that one floor of headroom and layer
     * 1 still starts at depth 1.
     */
    public static final int LOBBY_FLOORS = 1;

    private DungeonLayout() {
    }

    /**
     * Whether a position lies inside the dungeon's layer stack. Since issue
     * #246 the lobby is the Overworld entrance site (Reading B), so the
     * dungeon dimension's content starts at layer 1 - lane 0's region is no
     * longer inside the dungeon at all, and a player standing there derives
     * as OUTSIDE.
     */
    public static boolean inDungeon(LayoutParameters parameters, int x) {
        return LaneGeometry.laneIndex(x, parameters.laneStride()) >= 1;
    }

    /** The derived position. Refuses lane 0 and west (see class javadoc). */
    public static DungeonPosition derive(LayoutParameters parameters, int x, int y) {
        int layerIndex = LaneGeometry.laneIndex(x, parameters.laneStride());
        if (layerIndex < 1) {
            throw new IllegalArgumentException(
                    "no layer exists in lane 0 or west of it (x=" + x + ", stride="
                            + parameters.laneStride()
                            + "); the lobby is the Overworld entrance site (issue #246),"
                            + " check inDungeon first");
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
