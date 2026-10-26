package org.bitwisemadness.infinitedungeon;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.slf4j.Logger;

/**
 * Infinite Dungeon entry point.
 *
 * <p>The dungeon dimension: descending floors and layers, magicule-tuned spawns
 * and bosses, safe rooms, and loot that scales with depth. The suite's shared
 * mechanics live in infinite-dungeon-api; this mod ships the gameplay.</p>
 */
@Mod(InfiniteDungeon.MOD_ID)
public final class InfiniteDungeon {

    public static final String MOD_ID = "infinite_dungeon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public InfiniteDungeon(IEventBus modBus, ModContainer container) {
        // The announcement texts are config-driven; the config file is owned
        // and registered here, and later features add their entries to it.
        DungeonConfigs.register(container);
        LOGGER.info("Infinite Dungeon initialized");
    }
}
