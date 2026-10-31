package org.bitwisemadness.infinitedungeon.lanes;

import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

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
}
