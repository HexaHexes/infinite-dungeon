package org.bitwisemadness.infinitedungeon.loot;

import java.util.ArrayList;
import java.util.List;

/**
 * The injection selection math (issue #52): layer-band filtering and the
 * per-roll chance. Minecraft-free, so the unit suite pins the formula directly
 * - the {@link org.bitwisemadness.infinitedungeon.spawn.PoolMath} rule.
 */
public final class LootInjectionMath {

    private LootInjectionMath() {
    }

    /** The injections serving the layer, input order preserved. */
    public static List<LootInjection> filterByLayer(List<LootInjection> injections, int layer) {
        if (injections.isEmpty()) {
            return List.of();
        }
        List<LootInjection> kept = new ArrayList<>(injections.size());
        for (LootInjection injection : injections) {
            if (injection.inLayer(layer)) {
                kept.add(injection);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Whether a per-roll chance fires for a uniform draw in {@code [0, 1)}: the
     * draw is the caller's random (the LootContext's at the boundary), so the
     * answer is deterministic in its inputs - the JUnit seam.
     */
    public static boolean passesChance(double chance, double roll) {
        return roll < chance;
    }
}
