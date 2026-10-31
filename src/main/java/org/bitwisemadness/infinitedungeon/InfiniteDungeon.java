package org.bitwisemadness.infinitedungeon;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.infinitedungeon.client.PortalTint;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.death.CorpseCleanup;
import org.bitwisemadness.infinitedungeon.death.LobbyReturn;
import org.bitwisemadness.infinitedungeon.depth.DepthTracker;
import org.bitwisemadness.infinitedungeon.lanes.BossRosterBootstrap;
import org.bitwisemadness.infinitedungeon.lanes.FallbackHallFeature;
import org.bitwisemadness.infinitedungeon.lanes.GuardianChambers;
import org.bitwisemadness.infinitedungeon.lanes.GuardianGating;
import org.bitwisemadness.infinitedungeon.lanes.GuardianScheduleHook;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.TransitionFramesFeature;
import org.bitwisemadness.infinitedungeon.lanes.WaystoneEntranceFeature;
import org.bitwisemadness.infinitedungeon.lock.DungeonLock;
import org.bitwisemadness.infinitedungeon.lock.ProtectionListeners;
import org.bitwisemadness.infinitedungeon.magicule.DungeonMagiculeProvider;
import org.bitwisemadness.infinitedungeon.magicule.MagiculeElevation;
import org.bitwisemadness.infinitedungeon.magicule.MagiculeParticles;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchors;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;
import org.bitwisemadness.infinitedungeon.portal.PortalIgnition;
import org.bitwisemadness.infinitedungeon.spawn.AmbientSpawner;
import org.bitwisemadness.infinitedungeon.spawn.DungeonSpawnGuard;
import org.bitwisemadness.infinitedungeon.spawn.Minibosses;
import org.bitwisemadness.infinitedungeon.spawn.SpawnAttributeScaling;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPoolReloadListener;
import org.bitwisemadness.infinitedungeon.time.DungeonTime;
import org.bitwisemadness.infinitedungeon.treasure.KeyItems;
import org.bitwisemadness.infinitedungeonapi.magicule.AmbientMagicule;
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
        KeyItems.register(modBus);
        // The per-player entry anchor is a data attachment: registered here,
        // persisted with the player.
        EntryAnchors.register(modBus);
        // The adventure lock: the previous gamemode snapshot, persisted with
        // the player, plus the three events that apply, release, and enforce it.
        DungeonLock.register(modBus);
        NeoForge.EVENT_BUS.addListener(DungeonLock::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(DungeonLock::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(DungeonLock::onBlockBreak);
        // Room-content protection (issue #251): the four verified hooks -
        // break, explosion, piston, mob griefing - all fast-path on a
        // declared registration, so they are inert until content registers.
        NeoForge.EVENT_BUS.addListener(ProtectionListeners::onBreak);
        NeoForge.EVENT_BUS.addListener(ProtectionListeners::onExplosion);
        NeoForge.EVENT_BUS.addListener(ProtectionListeners::onPiston);
        NeoForge.EVENT_BUS.addListener(ProtectionListeners::onMobGrief);
        // Death policy: a dungeon death continues at the lobby - the first
        // layer. The marker attachment rides through respawn.
        LobbyReturn.register(modBus);
        NeoForge.EVENT_BUS.addListener(LobbyReturn::onDeath);
        NeoForge.EVENT_BUS.addListener(LobbyReturn::onRespawn);
        // Corpse cleanup: an emptied corpse is swept from the dungeon floors
        // while the integration config allows it (ModList-gated inside).
        NeoForge.EVENT_BUS.addListener(CorpseCleanup::onLevelTick);
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
        // Lane geometry: the stride follows the lane set's effective registry
        // values, read once the datapack registries are frozen. The fallback
        // hall feature guards lane cells whose dungeon did not generate.
        LaneGrid.register();
        FallbackHallFeature.register(modBus);
        TransitionFramesFeature.register(modBus);
        WaystoneEntranceFeature.register(modBus);
        // No lobby feature since issue #246: the lobby is the Overworld
        // entrance site (Reading B), so the dungeon dimension generates no
        // lobby - its content starts at layer 1.
        // The ambient magicule service (API-owned) reads the dungeon's M(L,f)
        // through this provider - consumers reach it via the API contract,
        // never by calling into this mod (plan section 7, issue #201).
        AmbientMagicule.registerProvider(DungeonMagiculeProvider.PROVIDER);
        // Ambient magicule particles render the same number (plan section 7,
        // issue #200): visual only, dungeon-dimension gated like the clock.
        NeoForge.EVENT_BUS.addListener(MagiculeParticles::onLevelTick);
        // Elevation sources are world data (plan section 7, issue #218): the
        // level hooks re-feed the recorded set at start and unregister at
        // stop; the chunk hook derives new DC-dungeon mouths as chunks load.
        NeoForge.EVENT_BUS.addListener(MagiculeElevation::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(MagiculeElevation::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MagiculeElevation::onChunkLoad);
        // Spawn attribute scaling (plan sections 7/9, issue #37): the default
        // rows join the API's list (core configures, API holds - I32), and the
        // fresh-join hook scales in-dungeon mob spawns. The magicule toggle
        // pushes at first level load - config values do not exist yet at
        // construction time (the gate caught exactly that).
        SpawnAttributeScaling.registerDefaultRows();
        NeoForge.EVENT_BUS.addListener(SpawnAttributeScaling::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(SpawnAttributeScaling::onEntityJoin);
        // Spawn pools (plan section 10, issue #39): datapack JSON under
        // data/<ns>/infinite_dungeon/pools, reloaded with /reload like every
        // suite data type - core owns the registry, the API owns the
        // machinery (I32).
        NeoForge.EVENT_BUS.addListener(SpawnPoolReloadListener::onAddReloadListeners);
        // Ambient spawns (plan sections 7/9, issue #40): in-dimension attempts
        // sized by the density curve, types from the profile's fodder pool.
        NeoForge.EVENT_BUS.addListener(AmbientSpawner::onLevelTick);
        // Guardian schedule (plan section 6, issue #231): installs the
        // LayerUnlocks seam predicate at dungeon level load with the world
        // seed; empty roster = interim open state (#31's contract).
        // Boss roster (issue #60): derives the schedule's roster from the
        // profile's boss pool at the same load - the setRoster seam #231
        // left; empty pool = interim open state.
        NeoForge.EVENT_BUS.addListener(BossRosterBootstrap::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(GuardianScheduleHook::onLevelLoad);
        // Gating reaction (issue #42, with the C7a team-share rule via #79):
        // a guardian's fall opens its layer in world saved data, once.
        NeoForge.EVENT_BUS.addListener(GuardianGating::onBossSlain);
        // Guardian respawn (issue #43): chamber-entry spawns on the level
        // tick (singleton, cooldown, EVENT-typed so the natural-spawn gates
        // never block a scripted boss), and tagged guardian deaths become the
        // BossSlainEvent above plus the cooldown stamp.
        NeoForge.EVENT_BUS.addListener(GuardianChambers::onLevelTick);
        NeoForge.EVENT_BUS.addListener(GuardianChambers::onLivingDeath);
        // Miniboss raids (issue #41): floor-entry spawns beside the entrant
        // (the trigger's production poster is the depth tracker, #238 - this
        // listener ships testable-by-posting, the #42 pattern); tagged deaths
        // stamp the floor and drop the key.
        NeoForge.EVENT_BUS.addListener(Minibosses::onFloorEntered);
        // Depth tracker (issue #238): posts FloorEntered/LayerEntered and
        // sends the profile announcements - the production poster the raid
        // trigger and lifecycle consumers awaited.
        NeoForge.EVENT_BUS.addListener(DepthTracker::onLevelTick);
        NeoForge.EVENT_BUS.addListener(DepthTracker::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(Minibosses::onLivingDeath);
        LOGGER.info("Infinite Dungeon initialized");
    }
}
