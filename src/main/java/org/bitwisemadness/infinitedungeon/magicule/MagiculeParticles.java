package org.bitwisemadness.infinitedungeon.magicule;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeonapi.magicule.AmbientMagicule;
import org.joml.Vector3f;

/**
 * Ambient magicule particles around players in the dungeon (plan section 7,
 * issue #200): density keyed to M, vanilla dust texture only, optional tint -
 * visual presentation of the same number everything else reads.
 *
 * <p>The concentration comes from the API's ambient service (the #220
 * provider's answer plus any modifiers), so structure-local elevation (#218)
 * will show in the particles without touching this class. Emission runs every
 * few ticks around each player in the dungeon dimension; the dimension gate
 * mirrors {@link org.bitwisemadness.infinitedungeon.time.DungeonTime}'s
 * listener, so the harness level (never the dungeon) is always a no-op.</p>
 *
 * <p>The mappings - count from concentration, tint from config hex - live in
 * {@link ParticleEmission}, Minecraft-free so the unit suites can load them
 * (this class itself is runtime-only, like every event listener here).</p>
 */
public final class MagiculeParticles {

    /** Emission cadence: one round every N ticks. */
    private static final int EMIT_INTERVAL_TICKS = 5;

    /** Half-width of the random spawn box around the player, in blocks. */
    private static final double SPREAD = 3.0;

    private MagiculeParticles() {
    }

    /** Per-tick: emit a round of ambient particles around dungeon players. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DungeonDimensions.LEVEL.equals(level.dimension())) {
            return;
        }
        if (!DungeonConfigs.magiculeParticles()) {
            return;
        }
        double density = DungeonConfigs.magiculeParticleDensity();
        if (density <= 0.0) {
            return;
        }
        if (level.getGameTime() % EMIT_INTERVAL_TICKS != 0L) {
            return;
        }
        float[] tint = ParticleEmission.parseTint(DungeonConfigs.magiculeParticleTint());
        DustParticleOptions dust =
                new DustParticleOptions(new Vector3f(tint[0], tint[1], tint[2]), 1.0F);
        for (ServerPlayer player : level.players()) {
            int count = ParticleEmission.particleCount(
                    AmbientMagicule.query(level, player.blockPosition()), density);
            if (count <= 0) {
                continue;
            }
            level.sendParticles(dust,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    count, SPREAD, SPREAD, SPREAD, 0.0);
        }
    }
}
