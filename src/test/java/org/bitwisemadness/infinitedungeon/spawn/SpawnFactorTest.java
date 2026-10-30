package org.bitwisemadness.infinitedungeon.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bitwisemadness.infinitedungeonapi.stats.AttributeScaling;
import org.junit.jupiter.api.Test;

/**
 * The spawn factor (plan sections 7/9, issue #37): the formula, the curve
 * selection that ties it to api#67's rows, and the edges - pure math, no
 * Minecraft runtime (the rule the #200 gate made structural).
 */
public class SpawnFactorTest {

    private static final double EPS = 1e-9;

    @Test
    void difficultyRowsReadDifficulty() {
        // (1 + 2.0 * 1.0) * 1.0 = 3.0 at a solo party.
        assertEquals(3.0, SpawnFactor.of(2.0, 99.0, 1.0, 1.0,
                AttributeScaling.Curve.DIFFICULTY), EPS);
        // The magicule argument is ignored for DIFFICULTY rows - pure in its
        // parameters: same inputs, same output, whatever M holds.
        assertEquals(3.0, SpawnFactor.of(2.0, 0.0, 1.0, 1.0,
                AttributeScaling.Curve.DIFFICULTY), EPS);
    }

    @Test
    void magiculeRowsReadMagicule() {
        // (1 + 4.0 * 0.5) * 1.0 = 3.0 keyed to M; D is the ignored argument.
        assertEquals(3.0, SpawnFactor.of(77.0, 4.0, 0.5, 1.0,
                AttributeScaling.Curve.MAGICULE), EPS);
        assertEquals(3.0, SpawnFactor.of(0.0, 4.0, 0.5, 1.0,
                AttributeScaling.Curve.MAGICULE), EPS);
    }

    @Test
    void partyMultiplierComposesOnTop() {
        // The plan's shape: the party multiplier multiplies ON TOP of the
        // curve-driven factor.
        assertEquals(6.0, SpawnFactor.of(2.0, 0.0, 1.0, 2.0,
                AttributeScaling.Curve.DIFFICULTY), EPS);
        assertEquals(4.5, SpawnFactor.of(2.0, 0.0, 1.0, 1.5,
                AttributeScaling.Curve.DIFFICULTY), EPS);
    }

    @Test
    void zeroCurveValuesWithSoloPartyAreNeutral() {
        assertEquals(1.0, SpawnFactor.of(0.0, 0.0, 0.5, 1.0,
                AttributeScaling.Curve.DIFFICULTY), EPS);
        // perPoint 0: the knob off leaves the multiplier at the party factor.
        assertEquals(2.0, SpawnFactor.of(50.0, 50.0, 0.0, 2.0,
                AttributeScaling.Curve.DIFFICULTY), EPS);
    }

    @Test
    void negativePartyMultiplierIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> SpawnFactor.of(1.0, 1.0, 1.0, -1.0, AttributeScaling.Curve.DIFFICULTY));
    }
}
