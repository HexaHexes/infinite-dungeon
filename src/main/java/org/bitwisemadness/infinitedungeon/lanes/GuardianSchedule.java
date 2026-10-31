package org.bitwisemadness.infinitedungeon.lanes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bitwisemadness.infinitedungeon.depth.DifficultyCurve;

/**
 * The layer guardian schedule (issue #231, plan section 6): which boss fields
 * which layer for a given world seed. Pure - roster, coefficients and seed go
 * in, an entity id comes out - so determinism, band fitting, and the
 * family-monotone invariant are all unit-testable directly (the #200 rule).
 *
 * <p><b>Selection per layer</b> (derived from layer 0 upward, so the answer
 * is a pure function of its inputs - memoization at the caller is only a
 * cache):</p>
 * <ol>
 *   <li>watermark: a candidate sharing a family with an assignment at any
 *       lower layer must be at least as powerful as that assignment - the
 *       inductive enforcement of "a weaker family member never appears
 *       deeper" (design amendment on #231);</li>
 *   <li>band: effective power (raw for depth-exempt families, raw times D for
 *       scaled ones) inside {@code center * (1 -/+ percent/100)} with
 *       {@code center = rosterMedianRaw * D(layer)} - the one expression the
 *       records left unpinned, chosen to add no constants and to make the
 *       D-derivation genuinely shape exempt selection (the design comment);</li>
 *   <li>empty band: the nearest-power candidates by effective distance to
 *       the center, so every layer fields a boss while the roster stands;</li>
 *   <li>seeded pick: an index mixed from world seed and layer - same inputs,
 *       same schedule, any call order.</li>
 * </ol>
 *
 * <p>D is evaluated at the layer's entry floor (floor 0): guardians are a
 * per-layer concept and the entry band is the layer's reference depth.</p>
 */
public final class GuardianSchedule {

    private static volatile List<BossCandidate> roster = List.of();

    private GuardianSchedule() {
    }

    /** Replaces the roster; providers (#60, family modules) call this. */
    public static void setRoster(List<BossCandidate> candidates) {
        roster = List.copyOf(candidates);
    }

    /** The current roster (an unmodifiable view). */
    public static List<BossCandidate> roster() {
        return roster;
    }

    /**
     * The layer's guardian, or empty when the roster is.
     *
     * @param layer       the layer index (0 = lobby)
     * @param worldSeed   the world's seed
     * @param difficultyBase     {@code difficulty.base}
     * @param difficultyLinear   {@code difficulty.linearPerFloor}
     * @param difficultyQuadratic {@code difficulty.quadraticPerLayer}
     * @param bandPercent the band half-width in percent (config or profile)
     */
    public static Optional<String> select(int layer, long worldSeed,
            double difficultyBase, double difficultyLinear, double difficultyQuadratic,
            double bandPercent) {
        List<BossCandidate> candidates = roster;
        if (candidates.isEmpty()) {
            return Optional.empty(); // interim contract: unassigned, transitions open
        }
        double median = medianRawPower(candidates);
        Map<String, Double> watermarks = new HashMap<>();
        Optional<String> chosen = Optional.empty();
        for (int depth = 0; depth <= layer; depth++) {
            chosen = pickAt(candidates, depth, worldSeed, median,
                    difficultyBase, difficultyLinear, difficultyQuadratic,
                    bandPercent, watermarks);
            chosen.ifPresent(id -> candidates.stream()
                    .filter(candidate -> candidate.entityId().equals(id))
                    .findFirst()
                    .ifPresent(candidate -> candidate.family().ifPresent(
                            family -> watermarks.merge(family, candidate.rawPower(),
                                    Math::max))));
        }
        return chosen;
    }

    /** One layer's pick, narrowing the eligible set through the watermark. */
    private static Optional<String> pickAt(List<BossCandidate> candidates, int layer,
            long worldSeed, double median,
            double difficultyBase, double difficultyLinear, double difficultyQuadratic,
            double bandPercent, Map<String, Double> watermarks) {
        double difficulty = DifficultyCurve.difficulty(layer, 0,
                difficultyBase, difficultyLinear, difficultyQuadratic);

        List<BossCandidate> eligible = new ArrayList<>(candidates.size());
        for (BossCandidate candidate : candidates) {
            Optional<Double> watermark = candidate.family().map(watermarks::get);
            if (watermark.isPresent() && candidate.rawPower() < watermark.get()) {
                continue; // weaker than its family's deeper-safe floor
            }
            eligible.add(candidate);
        }
        if (eligible.isEmpty()) {
            return Optional.empty(); // only for an under-populated roster
        }

        double center = median * difficulty;
        double halfWidth = center * bandPercent / 100.0;
        List<BossCandidate> band = new ArrayList<>(eligible.size());
        for (BossCandidate candidate : eligible) {
            double effective = candidate.effectivePower(difficulty);
            if (effective >= center - halfWidth && effective <= center + halfWidth) {
                band.add(candidate);
            }
        }
        List<BossCandidate> pool = band.isEmpty()
                ? nearestToCenter(eligible, difficulty, center)
                : band;

        long draw = mix64(worldSeed ^ (0x9E3779B97F4A7C15L * (layer + 1)));
        int index = (int) Math.floorMod(draw, (long) pool.size());
        return Optional.of(pool.get(index).entityId());
    }

    /**
     * The splitmix64 finalizer - the seeded pick's whole randomness. One
     * draw never needs PRNG state, so a single-use {@code java.util.Random}
     * would only earn SpotBugs' DMI_RANDOM_USED_ONLY_ONCE (and the shared
     * exclude file's scope rule puts that pattern behind an issue-first
     * decision on the template). This is pure, deterministic in its inputs,
     * and mixes well for small pools.
     */
    private static long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return mixed;
    }

    /** The candidates whose effective power sits closest to the center. */
    private static List<BossCandidate> nearestToCenter(List<BossCandidate> eligible,
            double difficulty, double center) {
        double best = Double.MAX_VALUE;
        for (BossCandidate candidate : eligible) {
            best = Math.min(best, Math.abs(candidate.effectivePower(difficulty) - center));
        }
        double threshold = best + 1e-9;
        List<BossCandidate> nearest = new ArrayList<>();
        for (BossCandidate candidate : eligible) {
            if (Math.abs(candidate.effectivePower(difficulty) - center) <= threshold) {
                nearest.add(candidate);
            }
        }
        return nearest;
    }

    /** The roster's median raw power (midpoint of the two middles when even). */
    private static double medianRawPower(List<BossCandidate> candidates) {
        List<Double> powers = candidates.stream()
                .map(BossCandidate::rawPower)
                .sorted(Comparator.naturalOrder())
                .toList();
        int size = powers.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return powers.get(middle);
        }
        return (powers.get(middle - 1) + powers.get(middle)) / 2.0;
    }
}
