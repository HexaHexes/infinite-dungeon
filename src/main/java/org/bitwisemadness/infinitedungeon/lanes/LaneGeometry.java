package org.bitwisemadness.infinitedungeon.lanes;

/**
 * The lane grid: layers live on X-lanes, layer L anchored at
 * {@code L * laneStride} (plan section 3).
 *
 * <p>Pure integer geometry with no Minecraft types, so the derivation is
 * JUnit-testable and every later wave (depth API, infrastructure placement)
 * composes the same functions. The stride itself is not hardcoded here: it is
 * Dungeon Crawl's placement grid read at startup from the effective registry
 * values ({@link LaneGrid}), so a load-time rewrite by a structure mod cannot
 * misalign the lanes.</p>
 */
public final class LaneGeometry {

    private LaneGeometry() {
    }

    /**
     * The lane a position belongs to: lane L owns X in
     * {@code [L * laneStride, (L + 1) * laneStride)}. Floor division, so
     * negative X maps to negative lanes below the first one.
     */
    public static int laneIndex(int x, int laneStride) {
        requireStride(laneStride);
        return Math.floorDiv(x, laneStride);
    }

    /** The anchor X of a lane: {@code layerIndex * laneStride}. */
    public static int laneOriginX(int layerIndex, int laneStride) {
        requireStride(laneStride);
        return layerIndex * laneStride;
    }

    private static void requireStride(int laneStride) {
        if (laneStride < 1) {
            throw new IllegalArgumentException("laneStride must be positive, got " + laneStride);
        }
    }
}
