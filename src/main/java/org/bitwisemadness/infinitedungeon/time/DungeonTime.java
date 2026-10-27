package org.bitwisemadness.infinitedungeon.time;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;

/**
 * Holds the dungeon clock while the time policy says not to flow.
 *
 * <p>The dimension type deliberately carries no {@code fixed_time}: it is
 * baked into a frozen registry object and cannot be toggled at runtime, so the
 * mod owns the clock instead. While {@code time.flows} is false the level's
 * time of day is held at {@code time.fixedTick} after every tick - the shipped
 * frozen-noon default. When a server turns {@code flows} on, this listener
 * stands aside and the clock runs, which is purely atmospheric: the End-style
 * sky render is static, and night only dims skylight.</p>
 */
public final class DungeonTime {

    private DungeonTime() {
    }

    /** Per-tick: hold the dungeon clock at the configured tick while frozen. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DungeonDimensions.LEVEL.equals(level.dimension())) {
            return;
        }
        TimePolicy policy = DungeonConfigs.timePolicy();
        if (policy.flows()) {
            return;
        }
        if (level.getDayTime() != policy.fixedTick()) {
            level.setDayTime(policy.fixedTick());
        }
    }
}
