package org.bitwisemadness.infinitedungeon.loot;

import java.util.Optional;

/**
 * One injection as the datapack declared it (issue #52): the loot table to
 * roll, an inclusive layer band, a per-roll chance, and an optional
 * required-mod gate. Pure - the table stays an id string so the unit suite
 * loads without a runtime (the {@code SpawnPoolEntry} rule); the modifier
 * parses it at the boundary.
 *
 * @param table       the loot table id to inject
 * @param minLayer    first layer served (inclusive)
 * @param maxLayer    last layer served (inclusive)
 * @param chance      per-roll injection probability, in {@code [0, 1]}
 * @param requiredMod mod id that must be loaded for the injection to run
 */
public record LootInjection(String table,
                            int minLayer,
                            int maxLayer,
                            double chance,
                            Optional<String> requiredMod) {

    public LootInjection {
        if (minLayer > maxLayer) {
            throw new IllegalArgumentException(
                    "minLayer " + minLayer + " must not exceed maxLayer " + maxLayer);
        }
        if (chance < 0.0D || chance > 1.0D) {
            throw new IllegalArgumentException("chance must be in [0, 1], got " + chance);
        }
        requiredMod = requiredMod == null ? Optional.empty() : requiredMod;
    }

    /** Whether this injection runs at the given layer index. */
    public boolean inLayer(int layer) {
        return layer >= minLayer && layer <= maxLayer;
    }
}
