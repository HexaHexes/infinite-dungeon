package org.bitwisemadness.infinitedungeon.rooms;

/**
 * How a gate opens (issue #252, the D33 comparison decision; #255 adds the
 * trial source): shipped sources, with further sources registering into the
 * same evaluation as content lands.
 *
 * <ul>
 * <li>{@link #KEY} - a player right-clicks the gate with the miniboss key
 * ({@code infinite_dungeon:key}, issue #41); one is consumed and the gate
 * opens permanently.</li>
 * <li>{@link #LAYER_DEFEATED} - the gate's own lane has a guardian and it has
 * fallen: polled on the gate's cheap tick, evaluated from
 * {@code LayerUnlocks} like every other seal question.</li>
 * <li>{@link #TRIALS_COMPLETE} - every trial spawner the gate declares has
 * given its reward (issue #255): polled on the same tick, completion stamped
 * per spawner so independently re-arming spawners still add up.</li>
 * </ul>
 */
public enum GateCondition {

    KEY,
    LAYER_DEFEATED,
    TRIALS_COMPLETE;

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
            case "trials_complete" -> TRIALS_COMPLETE;
            default -> throw new IllegalArgumentException(
                    "a gate in " + where
                            + " needs condition key, layer_defeated or trials_complete; got " + raw);
        };
    }
}
