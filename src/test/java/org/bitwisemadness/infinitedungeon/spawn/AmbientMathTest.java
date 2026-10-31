package org.bitwisemadness.infinitedungeon.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The ambient spawn arithmetic (issue #40): the attempt count's rounding and
 * clamp, and the phase's determinism including negative coordinates - pure,
 * no Minecraft runtime (the #200 rule).
 */
public class AmbientMathTest {

    @Test
    void attemptsRoundTheCurveTarget() {
        // base 4 at S=0 with perPoint -> multiplier 1 -> 4.
        assertEquals(4, AmbientMath.attempts(4.0, 0.0, 0.5, 24));
        // base 4 * (1 + 0.5 * 0.5) = 5.0 exactly.
        assertEquals(5, AmbientMath.attempts(4.0, 0.5, 0.5, 24));
        // base 0 disables entirely, whatever the curve says.
        assertEquals(0, AmbientMath.attempts(0.0, 9.0, 0.5, 24));
        // Base below a half rounds down to no attempt; a half rounds up.
        assertEquals(0, AmbientMath.attempts(0.49, 0.0, 0.0, 24));
        assertEquals(1, AmbientMath.attempts(0.5, 0.0, 0.0, 24));
    }

    @Test
    void attemptsRespectTheAbsoluteClamp() {
        // base 100 * (1 + 10 * 1) = 1100 -> clamped by maxPerChunk.
        assertEquals(7, AmbientMath.attempts(100.0, 10.0, 1.0, 7));
        // The clamp itself may be tuned to zero: nothing spawns.
        assertEquals(0, AmbientMath.attempts(4.0, 1.0, 1.0, 0));
    }

    @Test
    void phaseIsDeterministicAndInRange() {
        long a = AmbientMath.phase(12, -7, 400);
        assertTrue(a >= 0 && a < 400);
        assertEquals(a, AmbientMath.phase(12, -7, 400));
        // Negative coordinates stay non-negative (floorMod, not rem).
        assertTrue(AmbientMath.phase(-50, -50, 400) >= 0);
        // Distinct chunks commonly stagger apart.
        assertTrue(AmbientMath.phase(1, 0, 400) != AmbientMath.phase(2, 0, 400));
    }

    @Test
    void phaseRefusesDegenerateIntervals() {
        assertThrows(IllegalArgumentException.class, () -> AmbientMath.phase(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AmbientMath.phase(0, 0, -1));
    }
}
