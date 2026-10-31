package org.bitwisemadness.infinitedungeon.spawn;

import org.bitwisemadness.infinitedungeon.depth.DensityCurve;

/**
 * The ambient spawn arithmetic (issue #40): the per-chunk attempt count and
 * the deterministic phase that spreads a cycle's chunks across the interval.
 * Pure - no Minecraft - so the unit suite pins both directly (the #200 rule).
 */
public final class AmbientMath {

    private AmbientMath() {
    }

    /**
     * The attempt count for one chunk area: the plan's target conversion
     * (baseline times {@code 1 + S * perPoint}, clamped by the absolute
     * safety ceiling) rounded to whole attempts. Inputs are config-shaped
     * (non-negative), so the result is at least 0.
     *
     * @param baseTarget   the consumer baseline ({@code spawns.baseAttempts})
     * @param density      the curve value S at the position's depth
     * @param perPoint     {@code density.perPoint}
     * @param maxPerChunk  {@code density.maxPerChunk}
     * @return the rounded, clamped attempt count
     */
    public static int attempts(double baseTarget, double density, double perPoint, int maxPerChunk) {
        double target = DensityCurve.spawnTarget(baseTarget, density, perPoint, maxPerChunk);
        long rounded = Math.round(target);
        if (rounded < 0) {
            return 0;
        }
        return (int) Math.min(rounded, (long) maxPerChunk);
    }

    /**
     * A chunk's phase within the attempt interval: deterministic from the
     * chunk coordinates alone, so a cycle spreads its chunks over the whole
     * interval with no bookkeeping, and negative coordinates stay in range.
     *
     * @throws IllegalArgumentException when the interval is below 1
     */
    public static long phase(long chunkX, long chunkZ, int interval) {
        if (interval < 1) {
            throw new IllegalArgumentException("interval must be at least 1, got " + interval);
        }
        return Math.floorMod(chunkX * 31L + chunkZ * 17L, interval);
    }
}
