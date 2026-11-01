package org.bitwisemadness.infinitedungeon.lanes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The guardian schedule (issue #231): determinism and call-order
 * independence, band fitting with the D-derived center, the family-monotone
 * invariant, the empty-band fallback, and the interim contract - pure, no
 * Minecraft runtime (the #200 rule).
 */
public class GuardianScheduleTest {

    /** D(L,0) = 1 for every layer: base 1, no increments. */
    private static final double FLAT_DIFFICULTY = 1.0;
    private static final double[] FLAT = {1.0, 0.0, 0.0};

    private static Optional<String> select(int layer, long seed, double bandPercent) {
        return GuardianSchedule.select(layer, seed, FLAT[0], FLAT[1], FLAT[2], bandPercent);
    }

    @Test
    void emptyRosterAssignsNothing() {
        GuardianSchedule.setRoster(List.of());
        assertTrue(select(0, 1L, 30.0).isEmpty());
        assertTrue(select(9, 1L, 30.0).isEmpty());
    }

    @Test
    void candidateValidationRefusesImpossibleShapes() {
        assertThrows(IllegalArgumentException.class,
                () -> new BossCandidate(" ", 1.0, false, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new BossCandidate("minecraft:zombie", 0.0, false, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new BossCandidate("minecraft:zombie", -1.0, false, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new BossCandidate("minecraft:zombie", Double.NaN, false, Optional.empty()));
    }

    @Test
    void sameSeedSameScheduleInAnyCallOrder() {
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("mod:alpha", 100.0, false, Optional.empty()),
                new BossCandidate("mod:beta", 100.0, false, Optional.empty()),
                new BossCandidate("mod:gamma", 400.0, false, Optional.empty())));
        Optional<String> direct = select(5, 42L, 30.0);
        Optional<String> early = select(2, 42L, 30.0);
        Optional<String> late = select(5, 42L, 30.0);
        assertTrue(direct.isPresent());
        assertEquals(direct, late, "the same seed must yield the same layer 5");
        assertTrue(select(5, 42L, 30.0).equals(direct) && early.isPresent(),
                "call order must not shape the answer (pure recomputation)");
    }

    @Test
    void bandFitsAroundTheMedianCenter() {
        // D = 1: effective = raw, median 100 -> band 30% = [70, 130].
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("mod:small_a", 100.0, false, Optional.empty()),
                new BossCandidate("mod:small_b", 100.0, false, Optional.empty()),
                new BossCandidate("mod:large", 400.0, false, Optional.empty())));
        for (int layer = 0; layer < 4; layer++) {
            String picked = select(layer, 7L, 30.0).orElseThrow();
            assertTrue(picked.equals("mod:small_a") || picked.equals("mod:small_b"),
                    "layer " + layer + " must fit the band [70,130], picked " + picked);
        }
        // A wider band still excludes nothing that fits, a zero band pins the center.
        String pinned = select(0, 7L, 0.0).orElseThrow();
        assertTrue(pinned.equals("mod:small_a") || pinned.equals("mod:small_b"),
                "percent 0 pins the pick to the median members, got " + pinned);
    }

    @Test
    void familyMembersNeverWeakenWithDepth() {
        // One depth-exempt family spanning the band width; D grows with depth (base 1, quadratic 1
        // -> 1, 2, 5, 10...) so the center climbs while exempt raw power stays put - the watermark
        // must keep the picked raw power non-decreasing across layers.
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("mod:weak", 300.0, true, Optional.of("family")),
                new BossCandidate("mod:mid", 500.0, true, Optional.of("family")),
                new BossCandidate("mod:strong", 900.0, true, Optional.of("family"))));
        double previous = -1.0;
        for (int layer = 0; layer < 5; layer++) {
            String id = GuardianSchedule.select(layer, 5L, 1.0, 0.0, 1.0, 100.0)
                    .orElseThrow(() -> new AssertionError(
                            "every layer must field a boss from this roster"));
            double raw = switch (id) {
                case "mod:weak" -> 300.0;
                case "mod:mid" -> 500.0;
                case "mod:strong" -> 900.0;
                default -> throw new AssertionError("unexpected pick " + id);
            };
            assertTrue(raw >= previous,
                    "family watermark broken at layer " + layer + ": " + raw + " < " + previous);
            previous = raw;
        }
    }

    @Test
    void emptyBandFallsBackToTheNearestPower() {
        // One exempt boss: layer 1 with D = 2 doubles the center to 2000 while
        // its effective power stays 1000 - outside any tight band - and the
        // nearest-power fallback still fields it, so every layer has a boss.
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("mod:solo", 1000.0, true, Optional.of("only"))));
        Optional<String> layerOne = GuardianSchedule.select(1, 9L, 1.0, 0.0, 1.0, 10.0);
        assertEquals(Optional.of("mod:solo"), layerOne);
        for (int layer = 0; layer < 12; layer++) {
            assertTrue(GuardianSchedule.select(layer, 9L, 1.0, 0.0, 1.0, 10.0).isPresent(),
                    "layer " + layer + " must always field a boss while the roster stands");
        }
    }

    @Test
    void differentSeedsMayFieldDifferentBosses() {
        GuardianSchedule.setRoster(List.of(
                new BossCandidate("mod:a", 100.0, false, Optional.empty()),
                new BossCandidate("mod:b", 100.0, false, Optional.empty()),
                new BossCandidate("mod:c", 100.0, false, Optional.empty()),
                new BossCandidate("mod:d", 100.0, false, Optional.empty()),
                new BossCandidate("mod:e", 100.0, false, Optional.empty())));
        // With five equal band members the odds two given seeds agree on a
        // layer are low; some layer in a wide sweep must differ (seed-aware,
        // never flaky: the seeds are fixed, so the outcome is fixed too).
        boolean anyDifference = false;
        for (int layer = 0; layer < 10 && !anyDifference; layer++) {
            if (!select(layer, 1L, 30.0).equals(select(layer, 2L, 30.0))) {
                anyDifference = true;
            }
        }
        assertTrue(anyDifference, "different seeds must be able to field different bosses");
        assertNotEquals(select(0, 1L, 30.0), Optional.empty());
    }
}
