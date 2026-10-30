package org.bitwisemadness.infinitedungeon.magicule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The particle mappings (plan section 7; issue #200): concentration-to-count
 * with its flood guard, and config-hex-to-tint with its white fallback - both
 * on the Minecraft-free {@link ParticleEmission} home, so the suite loads
 * without a Minecraft runtime (the TravelPolicy rule; the listener class is
 * runtime-only, like every event listener here).
 */
public class ParticleEmissionTest {

    private static final float EPS = 1e-6f;

    @Test
    void countScalesWithConcentrationAndDensity() {
        assertEquals(0, ParticleEmission.particleCount(0.0, 1.0));
        assertEquals(5, ParticleEmission.particleCount(4.5, 1.0));
        assertEquals(1, ParticleEmission.particleCount(2.0, 0.5));
        assertEquals(9, ParticleEmission.particleCount(4.5, 2.0));
        // Rounding can land on zero: a faint concentration with low density
        // simply does not emit this round.
        assertEquals(0, ParticleEmission.particleCount(0.4, 1.0));
    }

    @Test
    void nonPositiveAndNaNInputsEmitNothing() {
        assertEquals(0, ParticleEmission.particleCount(-1.0, 1.0));
        assertEquals(0, ParticleEmission.particleCount(4.5, -0.5));
        assertEquals(0, ParticleEmission.particleCount(0.0, 0.0));
        assertEquals(0, ParticleEmission.particleCount(Double.NaN, 1.0));
        assertEquals(0, ParticleEmission.particleCount(4.5, Double.NaN));
    }

    @Test
    void floodGuardClampsAbsurdInputs() {
        assertEquals(ParticleEmission.MAX_PER_EMISSION,
                ParticleEmission.particleCount(1000.0, 100.0));
        assertEquals(ParticleEmission.MAX_PER_EMISSION,
                ParticleEmission.particleCount(45.0, 1.0),
                "deep-layer concentration is capped");
    }

    @Test
    void tintParsesHexAndKeepsTheDefaultLook() {
        assertArrayEquals(new float[] { 1.0F, 1.0F, 1.0F },
                ParticleEmission.parseTint(""), EPS);
        assertArrayEquals(new float[] { 1.0F, 0.0F, 0.0F },
                ParticleEmission.parseTint("FF0000"), EPS);
        assertArrayEquals(new float[] { 0.4F, 0.8F, 1.0F },
                ParticleEmission.parseTint("66CCFF"), EPS);
        // Malformed input (the config validator refuses it upstream) falls
        // back to white rather than to a garbage colour.
        assertArrayEquals(new float[] { 1.0F, 1.0F, 1.0F },
                ParticleEmission.parseTint("xyz"), EPS);
        assertArrayEquals(new float[] { 1.0F, 1.0F, 1.0F },
                ParticleEmission.parseTint("12345"), EPS);
        assertArrayEquals(new float[] { 1.0F, 1.0F, 1.0F },
                ParticleEmission.parseTint(null), EPS);
    }

    @Test
    void eachTintCallReturnsItsOwnArray() {
        // Callers build a particle from the array; a shared mutable result
        // would let one emission corrupt the next.
        float[] first = ParticleEmission.parseTint("66CCFF");
        first[0] = 0.0F;
        assertArrayEquals(new float[] { 0.4F, 0.8F, 1.0F },
                ParticleEmission.parseTint("66CCFF"), EPS);
    }
}
