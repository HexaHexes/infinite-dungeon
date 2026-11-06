package org.bitwisemadness.infinitedungeon.loot;

import java.util.Optional;

/**
 * One injection as the datapack declared it (issues #52/#256): the loot source
 * to roll, an inclusive layer band, a per-roll chance, and an optional
 * required-mod gate.
 *
 * <p>The source is a raw {@code table} <em>or</em> an abstract {@code slot}
 * (exactly one) - a slot resolves through {@link LootSlots} by theme and layer,
 * which is the theme axis. Pure - ids stay strings so the unit suite loads
 * without a runtime (the {@code SpawnPoolEntry} rule).</p>
 *
 * @param table       the concrete loot table id to inject, if not a slot
 * @param slot        the abstract slot id to resolve, if not a table
 * @param minLayer    first layer served (inclusive)
 * @param maxLayer    last layer served (inclusive)
 * @param chance      per-roll injection probability, in {@code [0, 1]}
 * @param requiredMod mod id that must be loaded for the injection to run
 */
public record LootInjection(Optional<String> table,
                            Optional<String> slot,
                            int minLayer,
                            int maxLayer,
                            double chance,
                            Optional<String> requiredMod) {

    public LootInjection {
        table = table == null ? Optional.empty() : table;
        slot = slot == null ? Optional.empty() : slot;
        if (table.isPresent() == slot.isPresent()) {
            throw new IllegalArgumentException(
                    "an injection needs exactly one of table or slot");
        }
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
