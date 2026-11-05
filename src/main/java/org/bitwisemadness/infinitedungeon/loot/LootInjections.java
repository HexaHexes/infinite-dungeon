package org.bitwisemadness.infinitedungeon.loot;

import java.util.List;

/**
 * The loot-injection registry (issue #52): the injections loaded from
 * {@code data/<ns>/infinite_dungeon/loot_injections/*.json} by
 * {@link LootInjectionReloadListener}, read by
 * {@link DepthInjectionModifier}. Replaced whole on every load.
 */
public final class LootInjections {

    private static volatile List<LootInjection> datapack = List.of();

    private LootInjections() {
    }

    /** Replaces the injection set; called with the complete state on every load. */
    static void setDatapack(List<LootInjection> injections) {
        datapack = List.copyOf(injections);
    }

    /** Every loaded injection, in datapack load order. */
    public static List<LootInjection> all() {
        return datapack;
    }
}
