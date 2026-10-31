package org.bitwisemadness.infinitedungeon.rooms;

/**
 * How a gate opens (issue #252, the D33 comparison decision): two shipped
 * sources, with trial-spawner and adopted-boss sources registering into the
 * same evaluation when issue #255 lands.
 *
 * <ul>
 * <li>{@link #KEY} - a player right-clicks the gate with the miniboss key
 * ({@code infinite_dungeon:key}, issue #41); one is consumed and the gate
 * opens permanently.</li>
 * <li>{@link #LAYER_DEFEATED} - the gate's own lane has a guardian and it has
 * fallen: polled on the gate's cheap tick, evaluated from
 * {@code LayerUnlocks} like every other seal question.</li>
 * </ul>
 */
public enum GateCondition {

    KEY,
    LAYER_DEFEATED;

    /**
     * Parses a declared condition, naming the offending value and where it was
     * declared; the reload listener lets a malformed file skip with a warning
     * rather than half-load.
     */
    public static GateCondition parse(String raw, String where) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "a gate in " + where + " needs a condition; got null");
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "key" -> KEY;
            case "layer_defeated" -> LAYER_DEFEATED;
            default -> throw new IllegalArgumentException(
                    "a gate in " + where + " needs condition key or layer_defeated; got " + raw);
        };
    }
}
