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
 * <p>Layer 0 is the lobby: of fixed construction and exempt from this rule
 * (issue #131 owns its shape), so the schedule covers layers 1 and above and
 * refuses layer 0 loudly rather than guessing.</p>
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
                    "layer 0 is the fixed-construction lobby (issue #131); the seed-derived rule"
                            + " covers layers 1 and above, got layer " + layerIndex);
        }
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        if (low < 1) {
            throw new IllegalArgumentException("floor counts must be at least 1, got [" + min + ", " + max + "]");
        }
        // Golden-ratio salt per layer over a splitmix-style finalizer: adjacent
        // layers and adjacent seeds spread apart, the result is stable across
        // JVMs and versions (plain long arithmetic, no library hash), and
        // floorMod keeps the mapping inside the range for any long.
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
     * The floor a Y coordinate sits in, bottom-anchored: floor 0 is the
     * lowest band of the layer, the index grows upward, and anything outside
     * the band clamps in (the raw geometric mapping - descent direction is
     * the depth API's semantics, issue #29).
     */
    public static int floorIndexInLayer(int y, int minY, int availableHeight, int floors) {
        int height = floorHeight(availableHeight, floors);
        return Math.min(floors - 1, Math.max(0, Math.floorDiv(y - minY, height)));
    }
}
