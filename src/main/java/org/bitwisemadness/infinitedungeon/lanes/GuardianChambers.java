package org.bitwisemadness.infinitedungeon.lanes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.spawn.EventSpawnSanity;
import org.bitwisemadness.infinitedungeonapi.event.BossSlainEvent;
import org.bitwisemadness.infinitedungeonapi.profile.BossRespawnPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.GuardianRules;

/**
 * The guardian respawn loop (issue #43, plan section 6): chamber entry
 * respawns the layer's guardian after the cooldown, and the guardian's death
 * becomes the API event #42 consumes - this class is the production poster
 * #42's evidence promised.
 *
 * <p><b>Entry</b> (design + amendment on #43): a player within
 * {@code boss.entryRadius} of ANY departure interior of an assigned layer -
 * sites repeat along a lane, the guardian is a per-layer SINGLETON spawned at
 * the site you entered. {@link #consider} is the whole decision as a pure
 * outcome enum (the tick listener maps COOLDOWN to the rate-limited flavor
 * message), so gametests drive the real logic directly without needing a
 * player entity in the harness.</p>
 *
 * <p><b>Spawn</b>: identity from the shared {@code guardianIdFor} resolver
 * (never a re-roll - respawns re-fight the same boss), spawned with
 * {@code MobSpawnType.EVENT}, which the API's NaturalSpawns allowlist leaves
 * ungated (plan section 14: events are never blocked) while vanilla's
 * obstruction/light sanity still runs; difficulty and party scaling ride #37
 * as fresh joins. The entity is marked with scoreboard tags - persistent
 * across restarts, so aliveness and death identity survive saves without
 * registries.</p>
 *
 * <p><b>Death</b>: a tagged guardian's death posts {@link BossSlainEvent}
 * (synchronously consumed by #42's listener - unlock) and stamps
 * {@link GuardianDefeats} for the cooldown.</p>
 */
public final class GuardianChambers {

    /** Marks a spawned guardian entity; survives save/load with the entity. */
    static final String GUARDIAN_TAG = "infinite_dungeon_guardian";

    private static final String LAYER_TAG_PREFIX = "infinite_dungeon_guardian_layer_";

    /** The flavor message's rate limit per player (game ticks). */
    private static final long FLAVOR_INTERVAL = 300;

    private static final Map<String, Long> FLAVOR_AT = new HashMap<>();

    private GuardianChambers() {
    }

    /** What an approach at a position decided - the gametest's observable. */
    enum Outcome {
        /** Too far from any departure interior of this cell's lane. */
        OUT_OF_RANGE,
        /** The lane has no assigned guardian (interim state or lobby lanes). */
        NOT_ASSIGNED,
        /** The guardian already walks the chamber - singleton held. */
        ALIVE,
        /** Within the cooldown - the chamber stays empty (flavor message). */
        COOLDOWN,
        /** Identity or entity creation refused the spawn. */
        REFUSED,
        REFUSED_RULES,
        REFUSED_INSERT,
        /** The guardian spawned here, tagged for its layer. */
        SPAWNED
    }

    /** The level-tick scan hook; every second, dungeon dimensions only. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return;
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            Decision decision = consider(level, player.blockPosition(), player.getUUID(), now);
            if (decision.outcome() == Outcome.COOLDOWN && flavorDue(player, now)) {
                DungeonAnnouncements.send(player, Announcement.BOSS_RECOVERING);
            }
        }
    }

    /**
     * One player's approach at a position: the complete decision as
     * outcome plus the spawned entity when there is one. Package-visible so
     * the harness drives the real logic without a player entity (the
     * design's test seam - identity arrives as a plain UUID, which is all
     * the per-player respawn policy needs, issue #259). The entity rides the
     * result rather than being
     * re-found through {@code getAllEntities}: that iterates only VISIBLE
     * entity sections, so a seed-jittered spawn outside the harness's
     * loaded region would exist yet be unfindable (the CI flake the result
     * payload fixes).
     */
    record Decision(Outcome outcome, LivingEntity spawned) {

        static Decision of(Outcome outcome) {
            return new Decision(outcome, null);
        }
    }

    /**
     * The identity-free seam: an approach whose profile declares no
     * {@code bossRespawn} policy runs the legacy cooldown path, where the
     * UUID is never read - so legacy-coherence tests pass no identity at all.
     * Tests exercising an opt-in policy MUST use the four-argument form.
     */
    static Decision consider(ServerLevel level, BlockPos playerPos, long now) {
        return consider(level, playerPos, new UUID(0L, 0L), now);
    }

    static Decision consider(ServerLevel level, BlockPos playerPos, UUID player, long now) {
        var placement = LaneGrid.placement();
        if (placement == null) {
            return Decision.of(Outcome.OUT_OF_RANGE); // lanes unreadable: quiet, like pairTarget
        }
        long seed = level.getSeed();
        ChunkPos cell = placement.getPotentialStructureChunk(
                seed, playerPos.getX() >> 4, playerPos.getZ() >> 4);
        BlockPos interior = LaneTransitions.departureInterior(level.getMinBuildHeight(), cell);
        int radius = DungeonConfigs.bossEntryRadius();
        long dx = playerPos.getX() - interior.getX();
        long dy = playerPos.getY() - interior.getY();
        long dz = playerPos.getZ() - interior.getZ();
        if (dx * dx + dy * dy + dz * dz > (long) radius * radius) {
            return Decision.of(Outcome.OUT_OF_RANGE);
        }
        int layer = LaneGeometry.laneIndex(interior.getX(), LaneGrid.laneStride());
        if (layer < 0 || !LayerUnlocks.hasGuardian(layer)) {
            return Decision.of(Outcome.NOT_ASSIGNED);
        }
        if (guardianAlive(level, layer)) {
            return Decision.of(Outcome.ALIVE);
        }
        var elapsed = GuardianDefeats.of(level).elapsedSeconds(layer, now);
        var policy = policyFor(level);
        if (policy.isPresent()) {
            // Opted-in path (issue #259): the declared mode decides.
            BossRespawnPolicy declared = policy.get();
            boolean hasFought =
                    FightRecords.of(level).hasFought(layer, FightRecords.GUARDIAN, player);
            if (!RespawnGate.maySpawn(declared.mode(), elapsed,
                    RespawnGate.cooldownSeconds(declared.respawnTicks()), hasFought)) {
                return Decision.of(Outcome.COOLDOWN);
            }
        } else if (!RespawnCooldown.canRespawn(elapsed, cooldownFor(level, layer))) {
            // Legacy path, byte-for-byte: no declared policy, no behaviour change.
            return Decision.of(Outcome.COOLDOWN);
        }
        return spawn(level, layer, interior);
    }

    /** The spawn decision: SPAWNED with the guardian, or the refusing step named. */
    private static Decision spawn(ServerLevel level, int layer, BlockPos interior) {
        Optional<String> guardian = GuardianScheduleHook.guardianIdFor(level, layer);
        if (guardian.isEmpty()) {
            return Decision.of(Outcome.REFUSED);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse(guardian.get()));
        var created = type.create(level);
        if (!(created instanceof Mob mob)) {
            return Decision.of(Outcome.REFUSED);
        }
        mob.setPos(interior.getX() + 0.5, interior.getY(), interior.getZ() + 0.5);
        if (!EventSpawnSanity.passed(mob, level, MobSpawnType.EVENT)) {
            return Decision.of(Outcome.REFUSED_RULES);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(interior),
                MobSpawnType.EVENT, null);
        if (!level.addFreshEntity(mob)) {
            return Decision.of(Outcome.REFUSED_INSERT);
        }
        mob.addTag(GUARDIAN_TAG);
        mob.addTag(LAYER_TAG_PREFIX + layer);
        DungeonAnnouncements.broadcast(level, Announcement.BOSS_AWAKENING);
        return new Decision(Outcome.SPAWNED, mob);
    }

    /** Whether the layer's tagged guardian currently walks the level. */
    private static boolean guardianAlive(ServerLevel level, int layer) {
        String tag = LAYER_TAG_PREFIX + layer;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** The cooldown seconds: profile guardian.respawnCooldown over config. */
    private static long cooldownFor(ServerLevel level, int layer) {
        String dimensionId = level.dimension().location().toString();
        return DimensionProfiles.get(dimensionId)
                .flatMap(profile -> profile.guardian())
                .map(GuardianRules::respawnCooldown)
                .orElseGet(DungeonConfigs::bossRespawnCooldownSeconds);
    }

    /**
     * The declared respawn policy; empty when the profile opts out, which is
     * the legacy cooldown path (issue #259: behaviour changes only where a
     * pack opts in - the API's per-player default is NOT today's behaviour).
     */
    private static Optional<BossRespawnPolicy> policyFor(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        return DimensionProfiles.get(dimensionId).flatMap(DimensionProfile::bossRespawn);
    }

    /**
     * The fall's present players - plus the credited killer, wherever they
     * stood - have had their fight (D39's "present when it fell" half).
     * Recorded whether or not a policy is declared, so opting in later
     * recognises fights that already happened.
     */
    private static void recordFighters(ServerLevel level, int layer, LivingEntity fallen, Entity killer) {
        FightRecords.recordPresentAt(level, layer, FightRecords.GUARDIAN, fallen, killer,
                DungeonConfigs.bossEntryRadius());
    }

    private static boolean flavorDue(ServerPlayer player, long now) {
        String key = player.getUUID() + "@" + player.level().dimension().location();
        Long last = FLAVOR_AT.get(key);
        if (last != null && now - last < FLAVOR_INTERVAL) {
            return false;
        }
        FLAVOR_AT.put(key, now);
        return true;
    }

    /**
     * The death side: a tagged guardian's fall becomes the API event and
     * stamps the cooldown. The tag is the authority - identity was decided at
     * spawn and survives restarts, so roster changes between spawn and death
     * never orphan a legitimate kill.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().getTags().contains(GUARDIAN_TAG)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return;
        }
        Integer layer = layerOf(event.getEntity());
        if (layer == null) {
            return;
        }
        NeoForge.EVENT_BUS.post(new BossSlainEvent(level.dimension(), layer, event.getEntity()));
        GuardianDefeats.of(level).record(layer, level.getGameTime());
        recordFighters(level, layer, event.getEntity(), event.getSource().getEntity());
    }

    private static Integer layerOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(LAYER_TAG_PREFIX)) {
                try {
                    return Integer.valueOf(tag.substring(LAYER_TAG_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return null; // foreign/corrupt tag: never unlock on a guess
                }
            }
        }
        return null;
    }
}
