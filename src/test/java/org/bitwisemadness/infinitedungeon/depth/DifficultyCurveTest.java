package org.bitwisemadness.infinitedungeon.depth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The difficulty curve (plan section 7; issue #34): shape, monotonicity, and
 * coefficient edge cases.
 *
 * <p>The defaults are the plan's starting values (base 0, linearPerFloor 0.15,
 * quadraticPerLayer 0.05) - the suite pins their arithmetic so a config-table
 * typo or a formula edit breaks a test, not a dungeon.</p>
 */
public class DifficultyCurveTest {

    private static final double BASE = 0D;
    private static final double LINEAR = 0.15D;
    private static final double QUADRATIC = 0.05D;
    private static final double EPS = 1e-9;

    @Test
    void originIsTheBaseCoefficient() {
        assertEquals(0D, DifficultyCurve.difficulty(0, 0, BASE, LINEAR, QUADRATIC), EPS);
        assertEquals(2.5D, DifficultyCurve.difficulty(0, 0, 2.5D, 9D, 9D), EPS);
    }

    @Test
    void planDefaultsComputeTheDocumentedValue() {
        // D(10, 5) = 0 + 0.15*5 + 0.05*10^2 = 5.75 with the plan's starting values.
        assertEquals(5.75D, DifficultyCurve.difficulty(10, 5, BASE, LINEAR, QUADRATIC), EPS);
    }

    @Test
    void shapeIsLinearInFloorAndQuadraticInLayer() {
        // Within a layer each floor adds exactly the linear term.
        for (int layer = 0; layer < 6; layer++) {
            double d0 = DifficultyCurve.difficulty(layer, 0, BASE, LINEAR, QUADRATIC);
            double d1 = DifficultyCurve.difficulty(layer, 1, BASE, LINEAR, QUADRATIC);
            double d4 = DifficultyCurve.difficulty(layer, 4, BASE, LINEAR, QUADRATIC);
            assertEquals(LINEAR, d1 - d0, EPS);
            assertEquals(4 * LINEAR, d4 - d0, EPS);
        }
        // Between layers the step grows linearly: L^2 -> (L+1)^2 adds 2L+1
        // quadratic units on top of the floor term (which is held fixed).
        for (int layer = 0; layer < 6; layer++) {
            double here = DifficultyCurve.difficulty(layer, 3, BASE, LINEAR, QUADRATIC);
            double next = DifficultyCurve.difficulty(layer + 1, 3, BASE, LINEAR, QUADRATIC);
            assertEquals(QUADRATIC * (2 * layer + 1), next - here, EPS);
        }
    }

    @Test
    void monotonicInFloorAndInLayer() {
        // Two-axis monotonicity: with non-negative coefficients D never
        // decreases along either depth axis separately - within a layer every
        // floor deeper is harder, and at any fixed floor index every layer
        // deeper is harder (floor 0 is that layer's entry band, #183's axis).
        //
        // The claim is deliberately per-axis: floor indices restart at each
        // layer's entry, so the player-path sequence resets the floor term at
        // every layer transition by design - the shape test above pins that
        // arithmetic instead of pretending it away.
        for (int layer = 0; layer <= 12; layer++) {
            for (int floor = 1; floor <= 12; floor++) {
                double deeper = DifficultyCurve.difficulty(layer, floor, BASE, LINEAR, QUADRATIC);
                double shallower = DifficultyCurve.difficulty(layer, floor - 1, BASE, LINEAR, QUADRATIC);
                assertTrue(deeper > shallower,
                        "D must rise with floor at layer " + layer + ": f=" + floor + " gave " + deeper
                                + " after " + shallower);
            }
        }
        for (int floor = 0; floor <= 12; floor++) {
            for (int layer = 1; layer <= 12; layer++) {
                double deeper = DifficultyCurve.difficulty(layer, floor, BASE, LINEAR, QUADRATIC);
                double shallower = DifficultyCurve.difficulty(layer - 1, floor, BASE, LINEAR, QUADRATIC);
                assertTrue(deeper > shallower,
                        "D must rise with layer at floor " + floor + ": L=" + layer + " gave " + deeper
                                + " after " + shallower);
            }
        }
    }

    @Test
    void zeroQuadraticYieldsPureLinear() {
        // The plan's recorded escape hatch: quadratic 0 -> linear progression.
        for (int layer = 0; layer < 8; layer++) {
            for (int floor = 0; floor < 8; floor++) {
                assertEquals(BASE + LINEAR * floor,
                        DifficultyCurve.difficulty(layer, floor, BASE, LINEAR, 0D), EPS);
            }
        }
    }

    @Test
    void zeroCoefficientsGiveTheConstantBase() {
        for (int layer = 0; layer < 5; layer++) {
            for (int floor = 0; floor < 5; floor++) {
                assertEquals(1.25D, DifficultyCurve.difficulty(layer, floor, 1.25D, 0D, 0D), EPS);
            }
        }
    }

    @Test
    void negativeCoefficientsComputeAsPureMath() {
        // The config range keeps coefficients in [0,100]; the function itself
        // is plain math, so the corners stay unit-testable rather than
        // throwing at callers who computed their own values.
        assertEquals(-0.6D, DifficultyCurve.difficulty(0, 4, 0D, -0.15D, 0D), EPS);
        assertEquals(-2.0D, DifficultyCurve.difficulty(2, 0, 0D, 0D, -0.5D), EPS);
    }

    @Test
    void negativeIndicesAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> DifficultyCurve.difficulty(-1, 0, BASE, LINEAR, QUADRATIC));
        assertThrows(IllegalArgumentException.class,
                () -> DifficultyCurve.difficulty(0, -1, BASE, LINEAR, QUADRATIC));
        assertThrows(IllegalArgumentException.class,
                () -> DifficultyCurve.difficulty(-3, -7, BASE, LINEAR, QUADRATIC));
    }

    @Test
    void layerEntryResetsTheFloorTerm() {
        // Documented consequence of the accepted shape: f restarts at each
        // layer's entry band, so every transition drops the floor term while
        // the quadratic term lifts the band - with the plan defaults layer 1's
        // entry (0.05) sits below layer 0's deepest floor (1.35 for 10
        // floors). The entries themselves still rise monotonically with L;
        // deep enough, the quadratic term outruns the reset entirely.
        double layer0Deepest = DifficultyCurve.difficulty(0, 9, BASE, LINEAR, QUADRATIC);
        double layer1Entry = DifficultyCurve.difficulty(1, 0, BASE, LINEAR, QUADRATIC);
        assertTrue(layer1Entry < layer0Deepest,
                "reset consequence: entry(1)=" + layer1Entry + " vs deepest(0)=" + layer0Deepest);
        assertTrue(DifficultyCurve.difficulty(0, 0, BASE, LINEAR, QUADRATIC)
                < DifficultyCurve.difficulty(1, 0, BASE, LINEAR, QUADRATIC));
        assertTrue(DifficultyCurve.difficulty(1, 0, BASE, LINEAR, QUADRATIC)
                < DifficultyCurve.difficulty(2, 0, BASE, LINEAR, QUADRATIC));
    }
}
