package org.bitwisemadness.infinitedungeon.depth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The magicule concentration curve (plan section 7; issue #35): growth shape,
 * monotonicity, coefficient edge cases, and - the plan's fourth requirement
 * for all three curves - mutual independence from the difficulty curve.
 *
 * <p>The defaults are the plan's starting values (base 0, linearPerFloor 0.1,
 * quadraticPerLayer 0.04).</p>
 */
public class MagiculeCurveTest {

    private static final double BASE = 0D;
    private static final double LINEAR = 0.1D;
    private static final double QUADRATIC = 0.04D;
    private static final double EPS = 1e-9;

    @Test
    void originIsTheBaseCoefficient() {
        assertEquals(0D, MagiculeCurve.concentration(0, 0, BASE, LINEAR, QUADRATIC), EPS);
        assertEquals(3D, MagiculeCurve.concentration(0, 0, 3D, 9D, 9D), EPS);
    }

    @Test
    void planDefaultsComputeTheDocumentedValue() {
        // M(10, 5) = 0 + 0.1*5 + 0.04*10^2 = 4.5 with the plan's starting values.
        assertEquals(4.5D, MagiculeCurve.concentration(10, 5, BASE, LINEAR, QUADRATIC), EPS);
    }

    @Test
    void shapeIsLinearInFloorAndQuadraticInLayer() {
        for (int layer = 0; layer < 6; layer++) {
            double d0 = MagiculeCurve.concentration(layer, 0, BASE, LINEAR, QUADRATIC);
            double d1 = MagiculeCurve.concentration(layer, 1, BASE, LINEAR, QUADRATIC);
            double d4 = MagiculeCurve.concentration(layer, 4, BASE, LINEAR, QUADRATIC);
            assertEquals(LINEAR, d1 - d0, EPS);
            assertEquals(4 * LINEAR, d4 - d0, EPS);
        }
        for (int layer = 0; layer < 6; layer++) {
            double here = MagiculeCurve.concentration(layer, 3, BASE, LINEAR, QUADRATIC);
            double next = MagiculeCurve.concentration(layer + 1, 3, BASE, LINEAR, QUADRATIC);
            assertEquals(QUADRATIC * (2 * layer + 1), next - here, EPS);
        }
    }

    @Test
    void monotonicInFloorAndInLayer() {
        // Per-axis monotonicity, the same discipline as DifficultyCurveTest -
        // the layer-entry reset consequence of restarting floor indices is
        // pinned there (it holds identically for M) and not re-asserted here.
        for (int layer = 0; layer <= 12; layer++) {
            for (int floor = 1; floor <= 12; floor++) {
                double deeper = MagiculeCurve.concentration(layer, floor, BASE, LINEAR, QUADRATIC);
                double shallower = MagiculeCurve.concentration(layer, floor - 1, BASE, LINEAR, QUADRATIC);
                assertTrue(deeper > shallower,
                        "M must rise with floor at layer " + layer + ": f=" + floor + " gave " + deeper
                                + " after " + shallower);
            }
        }
        for (int floor = 0; floor <= 12; floor++) {
            for (int layer = 1; layer <= 12; layer++) {
                double deeper = MagiculeCurve.concentration(layer, floor, BASE, LINEAR, QUADRATIC);
                double shallower = MagiculeCurve.concentration(layer - 1, floor, BASE, LINEAR, QUADRATIC);
                assertTrue(deeper > shallower,
                        "M must rise with layer at floor " + floor + ": L=" + layer + " gave " + deeper
                                + " after " + shallower);
            }
        }
    }

    @Test
    void zeroQuadraticYieldsPureLinear() {
        for (int layer = 0; layer < 8; layer++) {
            for (int floor = 0; floor < 8; floor++) {
                assertEquals(BASE + LINEAR * floor,
                        MagiculeCurve.concentration(layer, floor, BASE, LINEAR, 0D), EPS);
            }
        }
    }

    @Test
    void zeroCoefficientsGiveTheConstantBase() {
        for (int layer = 0; layer < 5; layer++) {
            for (int floor = 0; floor < 5; floor++) {
                assertEquals(0.75D, MagiculeCurve.concentration(layer, floor, 0.75D, 0D, 0D), EPS);
            }
        }
    }

    @Test
    void negativeCoefficientsComputeAsPureMath() {
        assertEquals(-0.4D, MagiculeCurve.concentration(0, 4, 0D, -0.1D, 0D), EPS);
        assertEquals(-1.6D, MagiculeCurve.concentration(2, 0, 0D, 0D, -0.4D), EPS);
    }

    @Test
    void negativeIndicesAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> MagiculeCurve.concentration(-1, 0, BASE, LINEAR, QUADRATIC));
        assertThrows(IllegalArgumentException.class,
                () -> MagiculeCurve.concentration(0, -1, BASE, LINEAR, QUADRATIC));
    }

    @Test
    void coefficientsAreIndependentOfTheDifficultyCurve() {
        // Plan section 7: the three curves are mutually independent - each
        // carries its OWN coefficient set. Same coordinates, each curve with
        // its own plan defaults yields its own documented value (4.5 vs 5.75),
        // and passing difficulty's coefficients to M reproduces difficulty's
        // arithmetic exactly - the curves are pure in their parameters, so
        // there is no shared or global coefficient state to couple them.
        double m = MagiculeCurve.concentration(10, 5, 0D, 0.1D, 0.04D);
        double d = DifficultyCurve.difficulty(10, 5, 0D, 0.15D, 0.05D);
        assertEquals(4.5D, m, EPS);
        assertEquals(5.75D, d, EPS);
        assertEquals(d, MagiculeCurve.concentration(10, 5, 0D, 0.15D, 0.05D), EPS);
    }
}
