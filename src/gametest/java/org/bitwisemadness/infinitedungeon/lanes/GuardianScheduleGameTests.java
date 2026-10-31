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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeonapi.event.BossSlainEvent;
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

            NeoForge.EVENT_BUS.post(new BossSlainEvent(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_end")),
                    0, zombie));
            helper.assertTrue(LayerUnlocks.isSealed(level, 0),
                    "a non-dungeon dimension must decline even with the right boss");

            NeoForge.EVENT_BUS.post(new BossSlainEvent(level.dimension(), 0, zombie));
            helper.assertTrue(!LayerUnlocks.isSealed(level, 0),
                    "the guardian's fall must open the gate - world state every "
                            + "player reads, the C7a team-share rule by construction");
            helper.assertTrue(unlocks.isUnlocked(0),
                    "the flag must be recorded in world saved data");

            NeoForge.EVENT_BUS.post(new BossSlainEvent(level.dimension(), 0, zombie));
            helper.assertTrue(unlocks.isUnlocked(0),
                    "a second fall must stay idempotent - the flag only ever opens");
            helper.succeed();
        } finally {
            GuardianSchedule.setRoster(List.of());
            LayerUnlocks.setGuardianAssigned(layer -> false);
            unlocks.lock(0);
            DimensionProfiles.remove(harnessDimension);
        }
    }
}
