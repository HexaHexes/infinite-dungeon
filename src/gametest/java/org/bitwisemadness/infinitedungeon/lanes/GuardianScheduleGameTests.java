package org.bitwisemadness.infinitedungeon.lanes;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeonapi.event.BossSlainEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerCompletedEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * The guardian schedule's seam integration (issue #231; plan's gametest list:
 * "boss schedule determinism and power-band fitting" - the pure math is
 * JUnit-pinned, this harness pins what the harness CAN reach: determinism
 * through the real entry point and the LayerUnlocks seam reacting to roster
 * presence, including the interim contract). Same package as the lanes
 * classes, so the tests restore seam state exactly as #31's gametests do.
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardianScheduleGameTests {

    private GuardianScheduleGameTests() {
    }

    @GameTest(template = "empty")
    public static void guardian_schedule_feeds_the_layer_unlock_seam(GameTestHelper helper) {
        // D = 1 for every layer (base 1, no increments); roster {100, 300}
        // bands at 30% around median 100 -> [70, 130] -> only 300 fits, so
        // the pick is deterministic WITHOUT relying on seed luck, and the
        // family watermark keeps later layers at or above it.
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("minecraft:zombie", 100.0, false, Optional.of("f")),
                new BossCandidate("minecraft:creeper", 300.0, false, Optional.of("f"))));
        try {
            Optional<String> first = GuardianSchedule.select(3, 42L, 1.0, 0.0, 0.0, 30.0);
            Optional<String> again = GuardianSchedule.select(3, 42L, 1.0, 0.0, 0.0, 30.0);
            helper.assertTrue(first.isPresent() && first.equals(again),
                    "the same seed must field the same boss twice: " + first + " / " + again);
            helper.assertTrue(first.get().equals("minecraft:creeper"),
                    "the band plus watermark must land the in-band family boss: " + first);

            // The #31 seam: assignment reflects a populated roster...
            LayerUnlocks.setGuardianAssigned(layer -> GuardianSchedule.select(
                    layer, 42L, 1.0, 0.0, 0.0, 30.0).isPresent());
            helper.assertTrue(LayerUnlocks.hasGuardian(0),
                    "a populated roster must assign guardians");

            // ...and the interim contract holds when the roster stands empty.
            GuardianSchedule.setRoster(List.of());
            helper.assertTrue(!GuardianSchedule.select(0, 42L, 1.0, 0.0, 0.0, 30.0).isPresent(),
                    "an empty roster must assign nothing (transitions stay open)");
            LayerUnlocks.setGuardianAssigned(layer -> GuardianSchedule.select(
                    layer, 42L, 1.0, 0.0, 0.0, 30.0).isPresent());
            helper.assertTrue(!LayerUnlocks.hasGuardian(0),
                    "an empty roster must leave the seam unassigned");
            helper.succeed();
        } finally {
            // Harness hygiene: restore the state #31's contract names as the
            // default, so tests running in any order see a clean seam.
            GuardianSchedule.setRoster(List.of());
            LayerUnlocks.setGuardianAssigned(layer -> false);
        }
    }

    /**
     * The gating reaction end to end (issue #42, with C7a via #79): against
     * the REAL listener, seam, and world saved data - a non-guardian kill
     * declines, a non-dungeon dimension declines, the guardian's fall opens
     * the layer (the same world-state read both portal sites and every other
     * player see - the team-share rule satisfied by construction), and a
     * second fall stays idempotent. The harness is the overworld, so the
     * dimension is registered as a dungeon for the duration; no core test
     * consults DimensionProfiles concurrently (verified), the empty-pool
     * guard keeps the ambient driver silent throughout, and `finally` restores
     * roster, seam, flag, and profile for test-order hygiene.
     *
     * Extended by #249: the same flow pins the LayerCompletedEvent
     * announcement - never for declined kills, exactly once for the first
     * guardian fall, never for respawn deaths, and (no player exists anywhere
     * near this harness) as the zero-player completion case, since the chain
     * carries no presence guard.
     */
    @GameTest(template = "empty")
    public static void guardian_slain_unlocks_the_layer_once_and_for_all(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String harnessDimension = level.dimension().location().toString();
        DimensionProfiles.register(new DimensionProfile(
                harnessDimension,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        // One candidate: the zombie IS every layer's guardian - deterministic
        // without relying on band or seed luck.
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("minecraft:zombie", 100.0, false, Optional.empty())));
        LayerUnlocks.setGuardianAssigned(layer -> layer == 0);
        LayerUnlocks unlocks = LayerUnlocks.of(level);
        unlocks.lock(0); // harness world data survives between runs: force the premise
        LayerCompletedCounter completions = new LayerCompletedCounter();
        NeoForge.EVENT_BUS.register(completions);
        try {
            helper.assertTrue(LayerUnlocks.isSealed(level, 0),
                    "precondition: the assigned layer must start sealed");

            var creeper = EntityType.CREEPER.create(level);
            helper.assertTrue(creeper != null, "the wrong boss must be creatable");
            var zombie = EntityType.ZOMBIE.create(level);
            helper.assertTrue(zombie != null, "the guardian must be creatable");

            NeoForge.EVENT_BUS.post(new BossSlainEvent(level.dimension(), 0, creeper));
            helper.assertTrue(LayerUnlocks.isSealed(level, 0),
                    "a non-guardian's fall must not open the gate");
            helper.assertTrue(completions.fired == 0,
                    "a non-guardian's fall must not announce layer completion");

            NeoForge.EVENT_BUS.post(new BossSlainEvent(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_end")),
                    0, zombie));
            helper.assertTrue(LayerUnlocks.isSealed(level, 0),
                    "a non-dungeon dimension must decline even with the right boss");
            helper.assertTrue(completions.fired == 0,
                    "a declined kill must not announce layer completion");

            NeoForge.EVENT_BUS.post(new BossSlainEvent(level.dimension(), 0, zombie));
            helper.assertTrue(!LayerUnlocks.isSealed(level, 0),
                    "the guardian's fall must open the gate - world state every "
                            + "player reads, the C7a team-share rule by construction");
            helper.assertTrue(unlocks.isUnlocked(0),
                    "the flag must be recorded in world saved data");
            helper.assertTrue(completions.fired == 1,
                    "the first guardian fall must announce layer completion exactly once, got "
                            + completions.fired);
            helper.assertTrue(completions.layer == 0
                            && harnessDimension.equals(completions.dimensionId),
                    "the completion must carry the completed layer and its dimension, got "
                            + completions.layer + "/" + completions.dimensionId);

            NeoForge.EVENT_BUS.post(new BossSlainEvent(level.dimension(), 0, zombie));
            helper.assertTrue(unlocks.isUnlocked(0),
                    "a second fall must stay idempotent - the flag only ever opens");
            helper.assertTrue(completions.fired == 1,
                    "a respawned guardian's fall must not re-fire: once per layer per world, got "
                            + completions.fired);
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(completions);
            GuardianSchedule.setRoster(List.of());
            LayerUnlocks.setGuardianAssigned(layer -> false);
            unlocks.lock(0);
            DimensionProfiles.remove(harnessDimension);
        }
    }

    /**
     * Records LayerCompletedEvent posts; public so the bus can invoke it.
     * Bus posts and the test's reads all happen on the server thread,
     * so plain fields suffice.
     */
    public static final class LayerCompletedCounter {

        int fired;
        int layer = -1;
        String dimensionId = "";

        @SubscribeEvent
        public void onLayerCompleted(LayerCompletedEvent event) {
            fired++;
            layer = event.layer();
            dimensionId = event.dimensionId();
        }
    }

    /**
     * The respawn loop end to end (issue #43): against the REAL chamber
     * decision, seam, SavedData, and the #42 event chain - unassigned quiet,
     * range decline, first entry spawns at once (never-defeated policy),
     * singleton held, death -> BossSlainEvent -> unlock + cooldown stamp,
     * within-cooldown empty, past-cooldown return. The harness world is solid
     * at the derived depth where the dungeon is void, so the body space is
     * cleared for vanilla's obstruction sanity; cell derivation follows #31's
     * LaneGrid test pattern; `finally` restores every static and world datum
     * (test methods are atomic - no interleaving mid-body between this and
     * #42's registration).
     */
    @GameTest(template = "empty")
    public static void guardian_chamber_respawns_on_entry_after_the_cooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = level.getSeed();
        int minBuild = level.getMinBuildHeight();

        var laneSet = level.getServer().registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET).get(LaneGrid.LANE_SET_ID);
        helper.assertTrue(laneSet != null, "the lane structure set must exist");
        LaneGrid.update(laneSet);
        var placement = LaneGrid.placement();
        helper.assertTrue(placement != null, "the lane placement must be readable");

        ChunkPos cell = placement.getPotentialStructureChunk(seed, 16, 0);
        var interior = LaneTransitions.departureInterior(minBuild, cell);
        helper.assertTrue(LaneGeometry.laneIndex(interior.getX(), LaneGrid.laneStride()) == 0,
                "the probe cell must sit in lane 0, got " + interior);
        // The cell's chunk is seed-jittered inside region 0, unloaded beyond
        // the harness's spawn radius on some seeds: addFreshEntity accepts an
        // unloaded chunk's entity, but getAllEntities iterates only visible
        // sections (the CI flake, root-caused). Forcing is ASYNC - the chunk
        // loads over the following ticks - so the whole scenario runs one
        // delay AFTER setup, exactly like production entries, which are
        // seconds apart on the per-second scan.
        level.setChunkForced(cell.x, cell.z, true);
        level.setBlock(interior, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(interior.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(interior.above(2), Blocks.AIR.defaultBlockState(), 3);

        // Premise resets at START: the harness world persists between runs and
        // across continuations (a failure inside the hop never reaches the
        // end-of-test restore, so the next run must self-heal - the guardian
        // purge below doubles as the leftover cleaner).
        String harnessDimension = level.dimension().location().toString();
        DimensionProfiles.register(new DimensionProfile(
                harnessDimension,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("minecraft:zombie", 100.0, false, Optional.empty())));
        LayerUnlocks.setGuardianAssigned(layer -> false);
        var defeats = GuardianDefeats.of(level);
        defeats.forget(0);
        LivingEntity[] spawnedHolder = new LivingEntity[1];

        helper.runAfterDelay(20, () -> {
            // Re-establish shared statics INSIDE the hop: sibling tests in
            // this batch mutate the same roster/seam in their finally blocks,
            // and a sibling body landing between setup and hop wiped them (the
            // identity-REFUSED regression).
            GuardianSchedule.setRoster(List.of(
                    new BossCandidate("minecraft:zombie", 100.0, false, Optional.empty())));
            LayerUnlocks.setGuardianAssigned(layer -> false);
            // ...and the profile: the sibling miniboss test registers and
            // REMOVES the shared key around this window - #42's gate reads it
            // first, so a vanished profile silently skipped the unlock (the
            // death-chain regression).
            DimensionProfiles.register(new DimensionProfile(
                    level.dimension().location().toString(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            for (var entity : level.getAllEntities()) {
                if (entity.getTags().contains(GuardianChambers.GUARDIAN_TAG)) {
                    entity.discard(); // leftovers of earlier runs are not premise
                }
            }
            long now = level.getGameTime();
            helper.assertTrue(GuardianChambers.consider(level, interior, now).outcome()
                            == GuardianChambers.Outcome.NOT_ASSIGNED,
                    "before assignment the chamber must stay quiet");

            LayerUnlocks.setGuardianAssigned(layer -> layer == 0);
            helper.assertTrue(GuardianChambers.consider(level, interior.offset(-40, 0, 0), now).outcome()
                            == GuardianChambers.Outcome.OUT_OF_RANGE,
                    "an approach outside entryRadius must not trigger");

            var firstSpawn = GuardianChambers.consider(level, interior, now);
            helper.assertTrue(firstSpawn.outcome()
                            == GuardianChambers.Outcome.SPAWNED,
                    "the first entry must spawn the guardian at once (no first-spawn wait); got "
                            + firstSpawn.outcome());
            LivingEntity alive = firstSpawn.spawned();
            spawnedHolder[0] = alive;
            helper.assertTrue(alive != null, "the spawn payload must carry the guardian");
            helper.assertTrue(alive.getTags().contains(GuardianChambers.GUARDIAN_TAG)
                            && alive.getTags().contains("infinite_dungeon_guardian_layer_0"),
                    "guardian and layer tags must be set");

            var secondEntry = GuardianChambers.consider(level, interior, now);
            helper.assertTrue(secondEntry.outcome()
                            == GuardianChambers.Outcome.ALIVE,
                    "the singleton must hold on a second entry; got " + secondEntry.outcome());

            NeoForge.EVENT_BUS.post(new LivingDeathEvent(alive, level.damageSources().generic()));
            alive.discard(); // the real death removes the body too
            helper.assertTrue(!LayerUnlocks.isSealed(level, 0),
                    "the death must open the layer through the full event chain (#42)");
            helper.assertTrue(defeats.elapsedSeconds(0, level.getGameTime()).isPresent(),
                    "the death must stamp the cooldown");

            var duringCooldown = GuardianChambers.consider(level, interior, level.getGameTime());
            helper.assertTrue(duringCooldown.outcome()
                            == GuardianChambers.Outcome.COOLDOWN,
                    "within the cooldown the chamber must stay empty; got "
                            + duringCooldown.outcome());

            defeats.record(0, level.getGameTime() - 601L * 20L);
            var returnSpawn = GuardianChambers.consider(level, interior, level.getGameTime());
            helper.assertTrue(returnSpawn.outcome()
                            == GuardianChambers.Outcome.SPAWNED,
                    "past the cooldown the guardian must return on entry; got "
                            + returnSpawn.outcome());

            // End-of-test restore (happy path; start-premise covers failures).
            if (spawnedHolder[0] != null) {
                spawnedHolder[0].discard();
            }
            GuardianSchedule.setRoster(List.of());
            LayerUnlocks.setGuardianAssigned(layer -> false);
            defeats.forget(0);
            DimensionProfiles.remove(harnessDimension);
            level.setChunkForced(cell.x, cell.z, false);
            helper.succeed();
        });
    }
}
