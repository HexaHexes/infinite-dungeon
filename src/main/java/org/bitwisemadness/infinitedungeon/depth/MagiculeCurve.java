package org.bitwisemadness.infinitedungeon.depth;

/**
 * The magicule concentration curve M(L,f) (plan section 7, issue #35): the
 * same shape as the difficulty curve with independent coefficients -
 * linear within a layer, quadratic between layers.
 *
 * <pre>
 * M(L, f) = magiculeBase + magiculeLinearPerFloor * f
 *           + magiculeQuadraticPerLayer * L^2
 * </pre>
 *
 * <p>Kept Minecraft-free so every edge case is unit-testable (the
 * {@link org.bitwisemadness.infinitedungeon.portal.TravelPolicy} rule).
 * Callers pass the coefficients from the {@code magicule.*} config section;
 * the value itself is plain concentration - gameplay-neutral toward players
 * in the base mod (player-side effects belong to Tensura reading our value),
 * and everything downstream - the API's ambient service provider (#201),
 * particles (#200), structure-local elevation (#218), attribute scaling - reads
 * this one number.</p>
 *
 * <p>The three plan curves (difficulty, magicule, density) are mutually
 * independent: each carries its own coefficient set, so M never borrows D's
 * config and the unit suites pin that separation. Indices follow #183's
 * axis (0 = the layer's entry band, growing downward) and negative values
 * are refused loudly, the {@code FloorSchedule} rule.</p>
 */
public final class MagiculeCurve {

    private MagiculeCurve() {
    }

    /**
     * Ambient magicule concentration at a depth coordinate.
     *
     * @param layer the layer index, 0 = lobby; grows downward
     * @param floor the floor index within the layer, 0 = entry band; grows downward
     * @param base the curve's value at the origin ({@code magicule.base})
     * @param linearPerFloor the per-floor increment ({@code magicule.linearPerFloor})
     * @param quadraticPerLayer the per-layer<sup>2</sup> increment ({@code magicule.quadraticPerLayer})
     * @return M(L, f)
     * @throws IllegalArgumentException when either index is negative
     */
    public static double concentration(int layer, int floor, double base,
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
}
