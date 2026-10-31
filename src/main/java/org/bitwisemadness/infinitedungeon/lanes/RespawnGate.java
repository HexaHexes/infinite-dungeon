package org.bitwisemadness.infinitedungeon.lanes;

import java.util.Optional;
import org.bitwisemadness.infinitedungeonapi.profile.BossRespawnMode;

/**
 * The respawn-policy decision (issue #259, core side of api#85): pure, so the
 * unit suite pins the three modes directly (the #200 rule), sitting beside
 * {@link RespawnCooldown} which it composes rather than replaces.
 *
 * <p>The semantics per mode:</p>
 * <ul>
 * <li>{@code ONCE} - one defeat per world: spawn only while the defeat record
 * is absent, exactly once forever after.</li>
 * <li>{@code TIMER} - the shipped inclusive-cooldown rule, timed from the
 * defeat stamp.</li>
 * <li>{@code PER_PLAYER} - a player who has not had their fight yet spawns
 * the encounter immediately (the fairness guarantee, whatever the cooldown
 * says); a player who already fought serves the normal cooldown, so farming
 * behaves as it does today.</li>
 * </ul>
 *
 * <p>Absent policy never reaches this class: the consumer runs the legacy
 * cooldown path instead (opt-in only, per issue #259's baseline).</p>
 */
public final class RespawnGate {

    private RespawnGate() {
    }

    /**
     * Whether the encounter may spawn for this player under the mode.
     *
     * @param mode           the declared respawn mode
     * @param elapsedSeconds seconds since the defeat, empty when never defeated
     * @param cooldownSeconds the timer/per-player cooldown length
     * @param hasFought      whether THIS player already had their fight
     */
    public static boolean maySpawn(
            BossRespawnMode mode, Optional<Long> elapsedSeconds, long cooldownSeconds, boolean hasFought) {
        return switch (mode) {
            case ONCE -> elapsedSeconds.isEmpty();
            case TIMER -> RespawnCooldown.canRespawn(elapsedSeconds, cooldownSeconds);
            case PER_PLAYER -> !hasFought || RespawnCooldown.canRespawn(elapsedSeconds, cooldownSeconds);
        };
    }

    /** The timer delay in whole seconds for a policy's tick count. */
    public static long cooldownSeconds(long respawnTicks) {
        return Math.max(0L, respawnTicks / 20L);
    }
}
