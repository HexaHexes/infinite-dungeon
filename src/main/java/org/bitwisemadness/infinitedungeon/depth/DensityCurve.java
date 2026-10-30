package org.bitwisemadness.infinitedungeon.depth;

/**
 * The mob density curve S(L,f) and its spawn-target conversions (plan section
 * 7, issue #36): the third independent curve - same shape as difficulty and
 * magicule with its own coefficients - and the only one that drives HOW MANY
 * mobs spawn. Never how strong: per-mob strength is the difficulty curve's
 * number, and the party multiplier touches strength only.
 *
 * <pre>
 * S(L, f) = densityBase + densityLinearPerFloor * f
 *           + densityQuadraticPerLayer * L^2
 * effective target = base target * (1 + S * densityPerPoint)
 * </pre>
 *
 * <p>Kept Minecraft-free so every edge case is unit-testable - the
 * {@link org.bitwisemadness.infinitedungeon.portal.TravelPolicy} rule. Callers
 * pass coefficients from the {@code density.*} config section; room-type
 * multipliers (safe rooms multiply by zero, monster houses upward, boss
 * chambers gate their fight size) stack at the consumer, above this
 * conversion. The {@code maxPerChunk} clamp is the absolute safety ceiling on
 * a spawn target - a consumer's number never exceeds it, whatever the curve
 * says.</p>
 */
public final class DensityCurve {

    private DensityCurve() {
    }

    /**
     * Density at a depth coordinate.
     *
     * @param layer the layer index, 0 = lobby; grows downward
     * @param floor the floor index within the layer, 0 = entry band; grows downward
     * @param base the curve's value at the origin ({@code density.base})
     * @param linearPerFloor the per-floor increment ({@code density.linearPerFloor})
     * @param quadraticPerLayer the per-layer<sup>2</sup> increment ({@code density.quadraticPerLayer})
     * @return S(L, f)
     * @throws IllegalArgumentException when either index is negative
     */
    public static double density(int layer, int floor, double base,
            double linearPerFloor, double quadraticPerLayer) {
        if (layer < 0) {
            throw new IllegalArgumentException(
                    "layer must be non-negative (0 is the lobby), got " + layer);
        }
        if (floor < 0) {
            throw new IllegalArgumentException(
                    "floor must be non-negative (0 is the entry band), got " + floor);
        }
        return base + linearPerFloor * floor + quadraticPerLayer * layer * layer;
    }

    /**
     * The spawn-target multiplier for a density value: {@code 1 + S * perPoint}.
     * With {@code perPoint = 0} density never scales the baseline - the
     * documented way to run fixed spawn targets regardless of depth.
     *
     * @param density the curve value S
     * @param perPoint the configured points-to-multiplier conversion ({@code density.perPoint})
     * @return the multiplier; 1.0 when either input is zero
     */
    public static double spawnMultiplier(double density, double perPoint) {
        return 1.0 + density * perPoint;
    }

    /**
     * The effective spawn target: the baseline scaled by the curve, then
     * capped by the absolute safety clamp.
     *
     * @param baseTarget the consumer's baseline target for the chunk area
     * @param density the curve value S
     * @param perPoint the configured conversion ({@code density.perPoint})
     * @param maxPerChunk the absolute ceiling ({@code density.maxPerChunk})
     * @return min(baseTarget * (1 + S * perPoint), maxPerChunk)
     * @throws IllegalArgumentException when the clamp is negative
     */
    public static double spawnTarget(double baseTarget, double density,
            double perPoint, int maxPerChunk) {
        if (maxPerChunk < 0) {
            throw new IllegalArgumentException(
                    "maxPerChunk must be non-negative, got " + maxPerChunk);
        }
        return Math.min(baseTarget * spawnMultiplier(density, perPoint), maxPerChunk);
    }
}
