package org.bitwisemadness.infinitedungeon.depth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The density curve and its conversions (plan section 7; issue #36): growth
 * shape, per-axis monotonicity, coefficient edges, the perPoint multiplier and
 * maxPerChunk clamp - plus the three-curve independence trio section 7
 * requires ("JUnit-tested alongside D and M").
 *
 * <p>Defaults are the plan's starting values (base 0, linearPerFloor 0.1,
 * quadraticPerLayer 0.03, perPoint 0.5, maxPerChunk 24).</p>
 */
public class DensityCurveTest {

    private static final double BASE = 0D;
    private static final double LINEAR = 0.1D;
    private static final double QUADRATIC = 0.03D;
    private static final double PER_POINT = 0.5D;
    private static final int MAX = 24;
    private static final double EPS = 1e-9;

    @Test
    void originIsTheBaseCoefficient() {
        assertEquals(0D, DensityCurve.density(0, 0, BASE, LINEAR, QUADRATIC), EPS);
        assertEquals(1.5D, DensityCurve.density(0, 0, 1.5D, 9D, 9D), EPS);
    }

    @Test
    void planDefaultsComputeTheDocumentedValue() {
        // S(10, 5) = 0 + 0.1*5 + 0.03*10^2 = 3.5 with the plan's starting values.
        assertEquals(3.5D, DensityCurve.density(10, 5, BASE, LINEAR, QUADRATIC), EPS);
    }

    @Test
    void shapeIsLinearInFloorAndQuadraticInLayer() {
        for (int layer = 0; layer < 6; layer++) {
            double d0 = DensityCurve.density(layer, 0, BASE, LINEAR, QUADRATIC);
            double d1 = DensityCurve.density(layer, 1, BASE, LINEAR, QUADRATIC);
            double d4 = DensityCurve.density(layer, 4, BASE, LINEAR, QUADRATIC);
            assertEquals(LINEAR, d1 - d0, EPS);
            assertEquals(4 * LINEAR, d4 - d0, EPS);
        }
        for (int layer = 0; layer < 6; layer++) {
            double here = DensityCurve.density(layer, 3, BASE, LINEAR, QUADRATIC);
            double next = DensityCurve.density(layer + 1, 3, BASE, LINEAR, QUADRATIC);
            assertEquals(QUADRATIC * (2 * layer + 1), next - here, EPS);
        }
    }

    @Test
    void monotonicInFloorAndInLayer() {
        // Per-axis, as with D and M - the layer-entry reset consequence is
        // pinned once in DifficultyCurveTest and holds identically here.
        for (int layer = 0; layer <= 12; layer++) {
            for (int floor = 1; floor <= 12; floor++) {
                double deeper = DensityCurve.density(layer, floor, BASE, LINEAR, QUADRATIC);
                double shallower = DensityCurve.density(layer, floor - 1, BASE, LINEAR, QUADRATIC);
                assertTrue(deeper > shallower,
                        "S must rise with floor at layer " + layer + ": f=" + floor + " gave " + deeper);
            }
        }
        for (int floor = 0; floor <= 12; floor++) {
            for (int layer = 1; layer <= 12; layer++) {
                double deeper = DensityCurve.density(layer, floor, BASE, LINEAR, QUADRATIC);
                double shallower = DensityCurve.density(layer - 1, floor, BASE, LINEAR, QUADRATIC);
                assertTrue(deeper > shallower,
                        "S must rise with layer at floor " + floor + ": L=" + layer + " gave " + deeper);
            }
        }
    }

    @Test
    void coefficientEdges() {
        // Zero quadratic = pure linear (the shared escape hatch), all-zero =
        // constant, negatives compute as pure math, negative indices refused.
        for (int layer = 0; layer < 8; layer++) {
            assertEquals(BASE + LINEAR * 3,
                    DensityCurve.density(layer, 3, BASE, LINEAR, 0D), EPS);
            assertEquals(2.25D, DensityCurve.density(layer, 3, 2.25D, 0D, 0D), EPS);
        }
        assertEquals(-0.4D, DensityCurve.density(0, 4, 0D, -0.1D, 0D), EPS);
        assertThrows(IllegalArgumentException.class,
                () -> DensityCurve.density(-1, 0, BASE, LINEAR, QUADRATIC));
        assertThrows(IllegalArgumentException.class,
                () -> DensityCurve.density(0, -1, BASE, LINEAR, QUADRATIC));
    }

    @Test
    void multiplierFollowsTheDocumentedFormula() {
        assertEquals(1.0D, DensityCurve.spawnMultiplier(0.0, PER_POINT), EPS);
        assertEquals(1.0D, DensityCurve.spawnMultiplier(3.5, 0.0), EPS,
                "perPoint 0 pins targets to the baseline - the documented off switch");
        assertEquals(2.75D, DensityCurve.spawnMultiplier(3.5, 0.5), EPS);
    }

    @Test
    void targetScalesThenClampsAtTheSafetyCeiling() {
        // 10 * (1 + 3.5*0.5) = 27.5 -> the 24 clamp.
        assertEquals(24.0D, DensityCurve.spawnTarget(10.0, 3.5, PER_POINT, MAX), EPS);
        // At the boundary the clamp passes the value through untouched...
        assertEquals(24.0D, DensityCurve.spawnTarget(24.0, 0.0, PER_POINT, MAX), EPS);
        // ...below it the curve scales freely...
        assertEquals(7.0D, DensityCurve.spawnTarget(4.0, 1.5, PER_POINT, MAX), EPS);
        // ...and a zero clamp stops everything.
        assertEquals(0.0D, DensityCurve.spawnTarget(10.0, 3.5, PER_POINT, 0), EPS);
        assertThrows(IllegalArgumentException.class,
                () -> DensityCurve.spawnTarget(10.0, 1.0, PER_POINT, -1));
    }

    @Test
    void allThreeCurvesAreMutuallyIndependent() {
        // Section 7 ships three curves with their own coefficient sets; at the
        // same coordinates each yields its own documented value, and each
        // curve fed another's coefficients reproduces that arithmetic - pure
        // parameters, no shared state anywhere.
        double d = DifficultyCurve.difficulty(10, 5, 0D, 0.15D, 0.05D);
        double m = MagiculeCurve.concentration(10, 5, 0D, 0.1D, 0.04D);
        double s = DensityCurve.density(10, 5, 0D, 0.1D, 0.03D);
        assertEquals(5.75D, d, EPS);
        assertEquals(4.5D, m, EPS);
        assertEquals(3.5D, s, EPS);
        // Pure parameters: whichever curve evaluates a given coefficient set
        // produces that set's value - the functions differ only by name, so
        // cross-passing coefficients must reproduce the other curve's number.
        assertEquals(s, DifficultyCurve.difficulty(10, 5, 0D, 0.1D, 0.03D), EPS,
                "density's coefficients must yield density's value in any curve");
        assertEquals(d, DensityCurve.density(10, 5, 0D, 0.15D, 0.05D), EPS,
                "difficulty's coefficients must yield difficulty's value in any curve");
    }
}
