package org.bitwisemadness.infinitedungeon.loot;

/**
 * One slot mapping as the datapack declared it (issue #256): a slot id resolves
 * to a table for a theme and an inclusive layer band. Pure - ids stay strings
 * so the unit suite loads without a runtime (the {@code SpawnPoolEntry} rule).
 *
 * @param slot     the abstract slot id content references
 * @param theme    the theme the mapping serves, or {@link #ANY_THEME}
 * @param minLayer first layer served (inclusive)
 * @param maxLayer last layer served (inclusive)
 * @param table    the concrete loot table the slot resolves to
 */
public record LootSlotMapping(String slot,
                              String theme,
                              int minLayer,
                              int maxLayer,
                              String table) {

    /** The wildcard theme: matched only when no exact-theme mapping competes. */
    public static final String ANY_THEME = "*";

    public LootSlotMapping {
        if (minLayer > maxLayer) {
            throw new IllegalArgumentException(
                    "minLayer " + minLayer + " must not exceed maxLayer " + maxLayer);
        }
    }

    /** Whether this mapping serves the given layer index. */
    public boolean inLayer(int layer) {
        return layer >= minLayer && layer <= maxLayer;
    }

    /** Whether this mapping serves the given theme (exact or wildcard). */
    public boolean servesTheme(String requested) {
        return theme.equals(requested) || theme.equals(ANY_THEME);
    }
}
