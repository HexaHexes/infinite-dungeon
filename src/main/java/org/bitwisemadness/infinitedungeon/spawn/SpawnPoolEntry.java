package org.bitwisemadness.infinitedungeon.spawn;

import java.util.Optional;
import org.bitwisemadness.infinitedungeonapi.data.ResourceRef;

/**
 * One pool entry as the datapack declared it (issue #39, plan section 10):
 * an id-or-tag reference, a positive weight, an inclusive layer band, and an
 * optional required-mod gate. Pure - no Minecraft types - so the unit suite
 * loads without a runtime (the rule the #200 gate made structural) and so the
 * parsed shape can be validated in isolation.
 *
 * <p>The reference stays a {@link ResourceRef} until a query expands it:
 * tags and registry contents are settled by the reload that delivered this
 * file, and any change to either arrives through the same {@code /reload}
 * trigger as the pools themselves.</p>
 *
 * @param ref         the entity id or {@code #tag} reference
 * @param weight      selection weight, at least 1
 * @param minLayer    first layer index served (inclusive, default 0)
 * @param maxLayer    last layer index served (inclusive, default unbounded)
 * @param requiredMod mod id that must be loaded for the entry to feed spawns
 */
public record SpawnPoolEntry(ResourceRef ref,
                             int weight,
                             int minLayer,
                             int maxLayer,
                             Optional<String> requiredMod) {

    public SpawnPoolEntry {
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be at least 1, got " + weight);
        }
        if (minLayer > maxLayer) {
            throw new IllegalArgumentException(
                    "minLayer " + minLayer + " must not exceed maxLayer " + maxLayer);
        }
        requiredMod = requiredMod == null ? Optional.empty() : requiredMod;
    }

    /** Whether this entry feeds spawns at the given layer index. */
    public boolean inLayer(int layer) {
        return layer >= minLayer && layer <= maxLayer;
    }
}
