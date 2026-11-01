package org.bitwisemadness.infinitedungeon.spawn;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.depth.DensityCurve;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.DungeonPosition;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * The ambient spawn driver (issue #40, plan sections 7/9): in-dimension mobs
 * drawn from the profile's fodder pool, banded by depth, sized by the density
 * curve. The density curve is THE ambient count law here simply because
 * nothing else spawns in the dungeon: the shipped biome carries empty spawn
 * lists (verified in spike #230), so vanilla NaturalSpawner has zero
 * candidates and there is nothing to cancel.
 *
 * <p>Each cycle (every {@code spawns.attemptInterval} ticks) every chunk in a
 * player's {@code spawns.playerRange} window whose deterministic phase matches
 * the cycle gets {@link AmbientMath#attempts} tries, each: a seeded-random
 * surface position inside the lane, a player nearby, then a pool pick at the
 * position's layer. The spawn itself is routed through
 * {@link EventHooks#checkSpawnPosition} with {@code MobSpawnType.NATURAL} -
 * vanilla's own ordering (spike #230: the ONLY type the API's
 * {@code NaturalSpawns} allowlist gates, so spawn-safety and core #71 see our
 * ambient spawns exactly like natural ones, per plan section 14) - then
 * {@code finalizeSpawn} and {@code addFreshEntity}. The #37 attribute hook
 * rides free: these are fresh joins in the dungeon dimension.</p>
 */
public final class AmbientSpawner {

    private AmbientSpawner() {
    }

    /** The level-tick hook; client ticks and other dimensions decline. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        tick(level);
    }

    /**
     * One cycle for a level; returns how many mobs were spawned (the gametest
     * seam - outside a dungeon dimension the answer is always 0).
     */
    static int tick(ServerLevel level) {
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return 0;
        }
        double base = DungeonConfigs.spawnsBaseAttempts();
        if (base <= 0) {
            return 0;
        }
        var players = level.players();
        if (players.isEmpty()) {
            return 0;
        }
        int interval = DungeonConfigs.spawnAttemptInterval();
        long cycle = Math.floorMod(level.getGameTime(), interval);
        int range = DungeonConfigs.spawnPlayerRange();

        DimensionProfile profile = DimensionProfiles.get(level.dimension().location().toString())
                .orElse(null);
        if (profile == null || profile.pools().isEmpty()
                || profile.pools().get().fodder().isEmpty()) {
            // No profile or no fodder pool: nothing to draw from - the empty
            // answer, never an error (a pack may add pools later).
            return 0;
        }
        String poolId = profile.pools().get().fodder().get();

        var parameters = LayoutParameters.forServer(level);
        int spawned = 0;
        for (long chunkKey : windowChunks(players, range)) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            if (AmbientMath.phase(chunkX, chunkZ, interval) != cycle) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue; // unloaded: never force generation for spawns
            }
            spawned += cycleChunk(level, parameters, players, poolId, base, range, chunkX, chunkZ);
        }
        return spawned;
    }

    private static int cycleChunk(ServerLevel level, LayoutParameters parameters,
            List<ServerPlayer> players, String poolId, double base, int range,
            int chunkX, int chunkZ) {
        int centreX = (chunkX << 4) + 8;
        int centreZ = (chunkZ << 4) + 8;
        if (!DungeonLayout.inDungeon(parameters, centreX)) {
            return 0; // west of lane 0 is void
        }
        int centreY = level.getHeight(Heightmap.Types.WORLD_SURFACE, centreX, centreZ);
        DungeonPosition depth = DungeonLayout.derive(parameters, centreX, centreY);
        int attempts = AmbientMath.attempts(base,
                DensityCurve.density(depth.layerIndex(), depth.floorInLayer(),
                        DungeonConfigs.densityBase(),
                        DungeonConfigs.densityLinearPerFloor(),
                        DungeonConfigs.densityQuadraticPerLayer()),
                DungeonConfigs.densityPerPoint(), DungeonConfigs.densityMaxPerChunk());
        int spawned = 0;
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (spawnAt(level, parameters, players, poolId, depth.layerIndex(), range,
                    chunkX, chunkZ)) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean spawnAt(ServerLevel level, LayoutParameters parameters,
            List<ServerPlayer> players, String poolId, int layer, int range,
            int chunkX, int chunkZ) {
        var random = level.random;
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        if (!DungeonLayout.inDungeon(parameters, x)) {
            return false; // the void edge of a split chunk
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (!nearbyPlayer(players, range, x, y, z)) {
            return false;
        }
        Optional<PoolCandidate> picked = SpawnPools.pick(poolId, layer, random);
        if (picked.isEmpty()) {
            return false; // every candidate band- or presence-filtered away
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse(picked.get().entityId()));
        // ENTITY_TYPE is a DefaultedRegistry: get() never returns null (an unknown id answers the
        // default entry), and candidates were presence-checked at expansion, so the id resolves to
        // itself; instanceof covers the create() null without a redundant branch.
        var created = type.create(level);
        if (!(created instanceof Mob mob)) {
            return false; // pools name mobs; anything else is declined
        }
        mob.setPos(x + 0.5, y, z + 0.5);
        if (!EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
            // The gate chain (spawn-safety, core #71) or vanilla's spawn rules
            // said no - light, obstruction, category - exactly as for a
            // natural spawn. The mob was never added, so nothing to remove.
            return false;
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
                MobSpawnType.NATURAL, null);
        return level.addFreshEntity(mob);
    }

    private static boolean nearbyPlayer(List<ServerPlayer> players, int range,
            int x, int y, int z) {
        double maxSqr = (double) range * range;
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(x + 0.5, y, z + 0.5) <= maxSqr) {
                return true;
            }
        }
        return false;
    }

    /** Every chunk coordinate within player range of any player, deduplicated. */
    private static Set<Long> windowChunks(List<ServerPlayer> players, int range) {
        int span = (range >> 4) + 1;
        Set<Long> chunks = new HashSet<>();
        for (ServerPlayer player : players) {
            int centreChunkX = player.getBlockX() >> 4;
            int centreChunkZ = player.getBlockZ() >> 4;
            for (int cx = centreChunkX - span; cx <= centreChunkX + span; cx++) {
                for (int cz = centreChunkZ - span; cz <= centreChunkZ + span; cz++) {
                    chunks.add((((long) cx) << 32) ^ (cz & 0xFFFFFFFFL));
                }
            }
        }
        return chunks;
    }
}
