package org.bitwisemadness.infinitedungeon.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pool selection math (issue #39): layer-band filtering and the weighted
 * pick. Position-free and Minecraft-free, so the unit suite pins the formula
 * directly - the standing split where the harness can reach only the decline
 * paths (the #200 rule).
 */
public final class PoolMath {

    private PoolMath() {
    }

    /** The entries feeding the layer, input order preserved. */
    public static List<SpawnPoolEntry> filterByLayer(List<SpawnPoolEntry> entries, int layer) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<SpawnPoolEntry> kept = new ArrayList<>(entries.size());
        for (SpawnPoolEntry entry : entries) {
            if (entry.inLayer(layer)) {
                kept.add(entry);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * The weighted pick for a draw in {@code [0, 1)}: the first candidate
     * whose cumulative weight exceeds the scaled draw. The draw is the
     * caller's uniform random (the RandomSource at the boundary), so the
     * result is deterministic in its inputs - the JUnit seam. A draw at or
     * above 1 clamps to the last candidate, a negative draw to the first, and
     * an empty pool has no answer.
     */
    public static Optional<PoolCandidate> pick(List<PoolCandidate> candidates, double uniform) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        long total = 0;
        for (PoolCandidate candidate : candidates) {
            total += candidate.weight();
        }
        double scaled = uniform * total;
        long cumulative = 0;
        for (PoolCandidate candidate : candidates) {
            cumulative += candidate.weight();
            if (scaled < cumulative) {
                return Optional.of(candidate);
            }
        }
        // Only reachable for a draw at/above the total (or a non-finite draw):
        // the documented clamp keeps the answer inside the pool.
        return Optional.of(candidates.get(candidates.size() - 1));
    }
}
