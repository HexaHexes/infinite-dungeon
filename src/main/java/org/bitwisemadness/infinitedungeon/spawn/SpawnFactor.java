package org.bitwisemadness.infinitedungeon.spawn;

import org.bitwisemadness.infinitedungeonapi.stats.AttributeScaling;

/**
 * The spawn stat factor (plan sections 7/9, issue #37): pure arithmetic -
 * {@code (1 + keyed * perPoint) * partyMultiplier} where the keyed value is
 * D or M by the attribute's curve (api#67's row). Minecraft-free so the unit
 * suite loads without a runtime (the rule the #200 gate made structural);
 * the hook class that feeds it lives beside the event it listens to.
 */
public final class SpawnFactor {

    private SpawnFactor() {
    }

    /**
     * The multiplier applied to one attribute's base value at spawn.
     *
     * @param difficulty the D value at the spawn's depth
     * @param magicule the M value at the spawn's position
     * @param perPoint the configured per-point knob ({@code difficulty.statMultiplierPerPoint})
     * @param partyMultiplier the resolver's party multiplier (solo = 1.0)
     * @param curve which curve the attribute is keyed to (api#67 rows)
     * @return the factor; 1.0 at zero curve values with a solo party
     * @throws IllegalArgumentException when the party multiplier is negative
     */
    public static double of(double difficulty, double magicule, double perPoint,
            double partyMultiplier, AttributeScaling.Curve curve) {
        if (partyMultiplier < 0.0) {
            throw new IllegalArgumentException(
                    "partyMultiplier must be non-negative, got " + partyMultiplier);
        }
        double keyed = curve == AttributeScaling.Curve.MAGICULE ? magicule : difficulty;
        return (1.0 + keyed * perPoint) * partyMultiplier;
    }
}
