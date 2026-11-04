package org.bitwisemadness.infinitedungeon.loot;

/**
 * The depth-scaling math for loot (issue #51): quantity and quality keyed to
 * the difficulty curve D. Kept Minecraft-free so every edge case is
 * unit-testable - the {@link org.bitwisemadness.infinitedungeon.depth.DifficultyCurve}
 * rule.
 */
public final class LootScaling {

    private LootScaling() {
    }

    /**
     * The rolled count of a loot entry, scaled by D.
     *
     * @param baseCount the table's rolled count
     * @param difficulty D at the loot origin
     * @param perPoint the config's {@code loot.quantityPerPoint}
     * @param maxStack the item's max stack size (the clamp ceiling)
     * @return the count, at least 1 and at most {@code maxStack}
     */
    public static int scaledCount(int baseCount, double difficulty, double perPoint, int maxStack) {
        long scaled = Math.round(baseCount * (1.0D + perPoint * difficulty));
        return (int) Math.max(1L, Math.min(maxStack, scaled));
    }

    /**
     * The probability that a table's rare sub-pool passes its depth gate.
     *
     * @param difficulty D at the loot origin
     * @param perPoint the config's {@code loot.qualityPerPoint}
     * @return {@code clamp(perPoint * D, 0, 1)}
     */
    public static double rareChance(double difficulty, double perPoint) {
        return Math.max(0.0D, Math.min(1.0D, perPoint * difficulty));
    }
}
