package org.bitwisemadness.infinitedungeon;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.infinitedungeon.client.PortalTint;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.death.LobbyReturn;
import org.bitwisemadness.infinitedungeon.lock.DungeonLock;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchors;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeon.portal.PortalIgnition;
import org.bitwisemadness.infinitedungeon.spawn.DungeonSpawnGuard;
import org.bitwisemadness.infinitedungeon.time.DungeonTime;
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
        // The portal tint renders client-side; registering the CLIENT spec
        // here is safe on both distributions (it is only loaded on the client).
        PortalTint.register(container);
        PortalBlocks.register(modBus);
        // The per-player entry anchor is a data attachment: registered here,
        // persisted with the player.
        EntryAnchors.register(modBus);
        // The adventure lock: the previous gamemode snapshot, persisted with
        // the player, plus the three events that apply, release, and enforce it.
        DungeonLock.register(modBus);
        NeoForge.EVENT_BUS.addListener(DungeonLock::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(DungeonLock::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(DungeonLock::onBlockBreak);
        // Death policy: a dungeon death continues at the lobby - the first
        // layer. The marker attachment rides through respawn.
        LobbyReturn.register(modBus);
        NeoForge.EVENT_BUS.addListener(LobbyReturn::onDeath);
        NeoForge.EVENT_BUS.addListener(LobbyReturn::onRespawn);
        // The dungeon clock is held by the mod (the dimension type carries no
        // fixed time, so a server can let it flow).
        NeoForge.EVENT_BUS.addListener(DungeonTime::onLevelTick);
        // The spawn-point invariant: no player respawn is ever set inside the
        // dungeon dimension, though players may nap.
        NeoForge.EVENT_BUS.addListener(DungeonSpawnGuard::onCanPlayerSleep);
        NeoForge.EVENT_BUS.addListener(DungeonSpawnGuard::onPlayerWakeUp);
        NeoForge.EVENT_BUS.addListener(DungeonSpawnGuard::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DungeonSpawnGuard::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(DungeonSpawnGuard::onCommand);
        NeoForge.EVENT_BUS.addListener(DungeonSpawnGuard::onLivingDeath);
        // Dungeon portals: ignite from flint and steel or fire.
        NeoForge.EVENT_BUS.addListener(PortalIgnition::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(PortalIgnition::onEntityPlace);
        LOGGER.info("Infinite Dungeon initialized");
    }
}
