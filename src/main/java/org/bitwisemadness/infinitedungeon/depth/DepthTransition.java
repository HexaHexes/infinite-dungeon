package org.bitwisemadness.infinitedungeon.depth;

/**
 * The depth-transition classification (issue #238): given where a player WAS
 * and where they ARE, what happened. Pure - no Minecraft - so the unit suite
 * pins every edge directly (the #200 rule).
 */
public final class DepthTransition {

    /** What a depth observation means for the player it came from. */
    public enum Kind {
        /** Outside the dungeon (or in the void): nothing announced. */
        OUTSIDE,
        /** First observation (join, re-entry, or after leaving): announce. */
        ENTRY,
        /** A new layer: the layer event and announcement. */
        LAYER,
        /** A new floor within the same layer: the floor event only. */
        FLOOR,
        /** Nothing moved. */
        SAME
    }

    private DepthTransition() {
    }

    /**
     * Classifies the move from {@code previous} (null = never seen) to the
     * current observation. An out-of-dungeon observation is OUTSIDE whatever
     * the coordinates say - the void counts as gone, so re-entering lands on
     * ENTRY again.
     *
     * @param previous   the player's last in-dungeon (layer, floor), or null
     * @param inDungeon  whether the current position derives inside the dungeon
     * @param layer      the derived layer (meaningful when inDungeon)
     * @param floor      the derived floor (meaningful when inDungeon)
     */
    public static Kind classify(int[] previous, boolean inDungeon, int layer, int floor) {
        if (!inDungeon) {
            return Kind.OUTSIDE;
        }
        if (previous == null || previous.length != 2) {
            return Kind.ENTRY;
        }
        if (previous[0] != layer) {
            return Kind.LAYER;
        }
        return previous[1] == floor ? Kind.SAME : Kind.FLOOR;
    }
}
