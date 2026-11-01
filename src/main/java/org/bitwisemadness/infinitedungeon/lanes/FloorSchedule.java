package org.bitwisemadness.infinitedungeon.lanes;

/**
 * Floors per layer and the vertical band they stack in (plan section 3).
 *
 * <p>The count is seed-derived, not a fixed config constant: layer L of a
 * given world always fields the same number of floors within the configured
 * {@code [min, max]} range (defaults 5 to 10), and the derivation is a pure
 * function of (world seed, layer index) - no world access, no registry, so
 * the whole rule is JUnit-testable and pregeneration-stable (section 16).</p>
 *
 * <p>Layer 0 is the lobby, and since issue #246 the lobby is the Overworld
 * entrance site - the dungeon dimension's content starts at layer 1, so the
 * schedule covers layers 1 and above and refuses layer 0 loudly rather than
 * guessing.</p>
 */
public final class FloorSchedule {

    private FloorSchedule() {
    }

    /**
     * The floor count for a layer, derived from (world seed, layer index).
     * Always within {@code [min, max]} after normalization - a config with
     * min above max is treated as its own mirror, never a crash.
     */
    public static int floorsInLayer(long worldSeed, int layerIndex, int min, int max) {
        if (layerIndex < 1) {
            throw new IllegalArgumentException(
                    "layer 0 is the lobby, now the Overworld entrance site (issue #246);"
                            + " the seed-derived rule covers layers 1 and above, got layer "
                            + layerIndex);
        }
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        if (low < 1) {
            throw new IllegalArgumentException("floor counts must be at least 1, got [" + min + ", " + max + "]");
        }
        // Golden-ratio salt per layer over a splitmix-style finalizer: adjacent layers and adjacent
        // seeds spread apart, the result is stable across JVMs and versions (plain long arithmetic,
        // no library hash), and floorMod keeps the mapping inside the range for any long.
        long mixed = worldSeed + layerIndex * 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed = mixed ^ (mixed >>> 31);
        return low + (int) Math.floorMod(mixed, (long) high - low + 1);
    }

    /**
     * The height of one floor inside a layer's band: the band spans the
     * dimension's available height and floors divide it equally, so
     * {@code floors * floorHeight <= availableHeight} holds by construction -
     * the band always fits the dimension (section 3).
     */
    public static int floorHeight(int availableHeight, int floors) {
        if (floors < 1) {
            throw new IllegalArgumentException("floors must be at least 1, got " + floors);
        }
        if (availableHeight < floors) {
            throw new IllegalArgumentException(
                    "available height " + availableHeight + " cannot hold " + floors + " floors");
        }
        return availableHeight / floors;
    }

    /**
     * The floor a Y coordinate sits in, top-anchored: floor 0 is the layer's
     * entry band at the top and the index grows downward, so descending
     * raises the floor - the descend-into-danger contract the design record
     * fixes (section 7's difficulty grows with floorInLayer; the DanMachi
     * floor numbering the concept cites counts downward). Outside the band
     * clamps in: below is deeper, above is shallower (issue #183 corrected
     * this from the original bottom-anchored mapping before any consumer
     * relied on it).
     */
    public static int floorIndexInLayer(int y, int minY, int availableHeight, int floors) {
        int height = floorHeight(availableHeight, floors);
        return Math.min(floors - 1, Math.max(0, floors - 1 - Math.floorDiv(y - minY, height)));
    }

    /**
     * The Y the deepest floor's band stands on - the world's own floor plane
     * (transition departures stand here; issue #31).
     */
    public static int deepestFloorBaseY(int minY) {
        return minY;
    }

    /**
     * The Y the entry floor (floor 0, the top band) stands on - where a layer
     * is entered (transition arrivals; issue #31 on issue #183's axis). A
     * single-floor layer shares the world floor.
     */
    public static int entryFloorBaseY(int minY, int availableHeight, int floors) {
        return minY + (floors - 1) * floorHeight(availableHeight, floors);
    }
}
