package org.bitwisemadness.infinitedungeon.lanes;

import java.util.Optional;

/**
 * The guardian respawn cooldown arithmetic (issue #43, plan section 6):
 * seconds since a defeat, and whether the chamber may respawn yet. Pure - no
 * Minecraft - so the unit suite pins the first-spawn policy and the boundary
 * directly (the #200 rule).
 */
public final class RespawnCooldown {

    private RespawnCooldown() {
    }

    /**
     * Whole seconds between a defeat and now, floored at 0 (a defeat stamped
     * "in the future" by a clock oddity counts as just-happened, never as
     * negative elapsed time).
     */
    public static long elapsedSeconds(long defeatGameTime, long nowGameTime) {
        return Math.max(0L, (nowGameTime - defeatGameTime) / 20L);
    }

    /**
     * Whether the guardian may respawn now: never defeated (empty) = the
     * FIRST spawn has no wait; otherwise the cooldown must have fully
     * elapsed. Equality respawns - the boundary is inclusive.
     */
    public static boolean canRespawn(Optional<Long> elapsedSeconds, long cooldownSeconds) {
        return elapsedSeconds.isEmpty() || elapsedSeconds.get() >= cooldownSeconds;
    }
}
