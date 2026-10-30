package org.bitwisemadness.infinitedungeon.magicule;

/**
 * The particle mappings behind {@link MagiculeParticles} (plan section 7,
 * issue #200) - kept Minecraft-free so every edge is unit-testable, the
 * {@link org.bitwisemadness.infinitedungeon.portal.TravelPolicy} rule: the
 * unit-test classpath carries NeoForge and the API but not Minecraft, so any
 * pure mapping parked on the listener class cannot be loaded by a suite.
 *
 * <p>Concentration-to-count (density scaled, rounded, flood-capped) and
 * config-hex-to-tint (with the white fallback) live here; the emission loop,
 * config access and the world stay in {@link MagiculeParticles}.</p>
 */
public final class ParticleEmission {

    /** The flood guard: even absurd concentration/density cannot spam packets. */
    public static final int MAX_PER_EMISSION = 16;

    private static final float[] DEFAULT_TINT = { 1.0F, 1.0F, 1.0F };

    private ParticleEmission() {
    }

    /**
     * Particles per emission round: concentration scaled by density, rounded,
     * clamped to the flood guard. Non-positive or NaN inputs emit nothing.
     *
     * @param concentration the ambient M sampled at the player
     * @param density the configured particles-per-point density
     * @return the count, in [0, {@link #MAX_PER_EMISSION}]
     */
    public static int particleCount(double concentration, double density) {
        if (!(concentration > 0.0) || !(density > 0.0)) {
            return 0;
        }
        return (int) Math.min(MAX_PER_EMISSION, Math.round(concentration * density));
    }

    /**
     * Parses the configured RRGGBB tint into RGB floats. Empty, malformed or
     * null input keeps the particle's own white look - the config validator
     * refuses malformed values, this is the belt-and-braces edge.
     *
     * @param tint the config value (empty or six hex digits)
     * @return a fresh {r, g, b} array with components in [0, 1]
     */
    public static float[] parseTint(String tint) {
        if (tint == null || tint.length() != 6) {
            return DEFAULT_TINT.clone();
        }
        try {
            int rgb = Integer.parseInt(tint, 16);
            return new float[] {
                    ((rgb >> 16) & 0xFF) / 255.0F,
                    ((rgb >> 8) & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F };
        } catch (NumberFormatException ex) {
            return DEFAULT_TINT.clone();
        }
    }
}
