package org.bitwisemadness.infinitedungeon.depth;

/**
 * The difficulty curve D(L,f) (plan section 7, issue #34): linear within a
 * layer, quadratic between layers, both coefficients config-tunable.
 *
 * <pre>
 * D(L, f) = difficultyBase + difficultyLinearPerFloor * f
 *           + difficultyQuadraticPerLayer * L^2
 * </pre>
 *
 * <p>Kept Minecraft-free so every edge case is unit-testable - the
 * {@link org.bitwisemadness.infinitedungeon.portal.TravelPolicy} rule. Callers
 * pass the coefficients from the {@code difficulty.*} config section
 * ({@code DungeonConfigs.difficultyBase} and friends); setting the quadratic
 * term to zero yields pure linear progression, as the plan records. Difficulty
 * derives from depth like everything else (section 3): both indices are the
 * depth coordinates, floor 0 being the layer's entry band and the index
 * growing downward (#183's axis), so non-negative coefficients make D
 * non-decreasing along both descents.</p>
 *
 * <p>Coefficients are computed as given - the config's own range keeps them
 * sane, and the unit suite pins the zero/negative corners as pure math.
 * Negative <em>indices</em> are refused loudly rather than guessed at, the
 * {@code FloorSchedule} rule.</p>
 */
public final class DifficultyCurve {

    private DifficultyCurve() {
    }

    /**
     * Difficulty at a depth coordinate.
     *
     * @param layer the layer index, 0 = lobby; grows downward
     * @param floor the floor index within the layer, 0 = entry band; grows downward
     * @param base the curve's value at the origin ({@code difficulty.base})
     * @param linearPerFloor the per-floor increment ({@code difficulty.linearPerFloor})
     * @param quadraticPerLayer the per-layer<sup>2</sup> increment ({@code difficulty.quadraticPerLayer})
     * @return D(L, f)
     * @throws IllegalArgumentException when either index is negative
     */
    public static double difficulty(int layer, int floor, double base,
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
