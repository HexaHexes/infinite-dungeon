package org.bitwisemadness.infinitedungeon.loot;

import java.util.List;
import java.util.Optional;

/**
 * The loot-slot registry and resolver (issue #256): slots loaded from
 * {@code data/<ns>/infinite_dungeon/loot_slots/*.json} by
 * {@link LootSlotReloadListener}, resolved to a table by theme and layer.
 *
 * <p>The precedence is pure and documented at {@link #resolveFor}: exact theme
 * beats the wildcard, then the narrowest band wins, then load order. An unknown
 * slot is an empty answer, never an error - a pack may not ship every theme.</p>
 */
public final class LootSlots {

    private static volatile List<LootSlotMapping> datapack = List.of();

    private LootSlots() {
    }

    /** Replaces the mapping set; called with the complete state on every load. */
    static void setDatapack(List<LootSlotMapping> mappings) {
        datapack = List.copyOf(mappings);
    }

    /** Resolves the loaded mappings for a slot, theme and layer. */
    public static Optional<String> resolve(String slot, String theme, int layer) {
        return resolveFor(datapack, slot, theme, layer);
    }

    /**
     * The winning table id for a slot, theme and layer, or empty when nothing
     * serves it. Precedence: an exact-theme mapping beats a wildcard one; among
     * equals the narrowest layer band wins; a remaining tie keeps the earlier
     * mapping in load order.
     */
    public static Optional<String> resolveFor(List<LootSlotMapping> mappings,
            String slot, String theme, int layer) {
        LootSlotMapping best = null;
        for (LootSlotMapping mapping : mappings) {
            if (!mapping.slot().equals(slot)
                    || !mapping.inLayer(layer)
                    || !mapping.servesTheme(theme)) {
                continue;
            }
            if (best == null || isBetter(mapping, best)) {
                best = mapping;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.table());
    }

    private static boolean isBetter(LootSlotMapping candidate, LootSlotMapping best) {
        boolean candidateExact = !candidate.theme().equals(LootSlotMapping.ANY_THEME);
        boolean bestExact = !best.theme().equals(LootSlotMapping.ANY_THEME);
        if (candidateExact != bestExact) {
            return candidateExact;
        }
        int candidateBand = candidate.maxLayer() - candidate.minLayer();
        int bestBand = best.maxLayer() - best.minLayer();
        return candidateBand < bestBand;
    }
}
