package org.bitwisemadness.infinitedungeon.spawn;

/**
 * The miniboss floor assignment and stat stack (issue #41, plan sections
 * 6/7): pure - no Minecraft - so the unit suite pins the boundaries directly
 * (the #200 rule).
 */
public final class MinibossFloors {

    private MinibossFloors() {
    }

    /**
     * Whether a (layer, floor) fields a miniboss: layers from
     * {@code startLayer} inclusive, floors divisible by {@code everyNFloors},
     * floor 0 never (entrances are safe rooms, plan section 11).
     *
     * @throws IllegalArgumentException when everyNFloors is below 1 (config
     *                                  ranges guarantee it; the pure contract
     *                                  refuses rather than divides by zero)
     */
    public static boolean assigned(int layer, int floor, int startLayer, int everyNFloors) {
        if (everyNFloors < 1) {
            throw new IllegalArgumentException(
                    "everyNFloors must be at least 1, got " + everyNFloors);
        }
        if (layer < startLayer || floor < 1) {
            return false;
        }
        return floor % everyNFloors == 0;
    }

    /**
     * The final stat factor: the spawn factor (depth and party, #37) with the
     * miniboss multiplier stacked LAST, exactly as plan section 7 words it
     * ("the miniboss and guardian multipliers stack on top").
     *
     * @throws IllegalArgumentException on negative inputs (config ranges
     *                                  keep them non-negative; the pure
     *                                  contract still refuses)
     */
    public static double stacked(double spawnFactor, double minibossMultiplier) {
        if (spawnFactor < 0.0 || minibossMultiplier < 0.0) {
            throw new IllegalArgumentException(
                    "factors must be non-negative: " + spawnFactor + ", " + minibossMultiplier);
        }
        return spawnFactor * minibossMultiplier;
    }
}
