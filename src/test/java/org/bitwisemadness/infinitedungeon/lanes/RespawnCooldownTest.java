package org.bitwisemadness.infinitedungeon.lanes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The respawn cooldown arithmetic (issue #43): the first-spawn policy, the
 * inclusive boundary, and the elapsed-time floor - pure, no Minecraft (the
 * #200 rule).
 */
public class RespawnCooldownTest {

    @Test
    void elapsedIsWholeSecondsFlooredAtZero() {
        assertEquals(10L, RespawnCooldown.elapsedSeconds(0L, 200L));
        assertEquals(9L, RespawnCooldown.elapsedSeconds(20L, 200L));
        assertEquals(0L, RespawnCooldown.elapsedSeconds(199L, 200L));
        // A defeat stamped ahead of now (clock oddity) reads as just-happened.
        assertEquals(0L, RespawnCooldown.elapsedSeconds(500L, 200L));
    }

    @Test
    void firstSpawnNeverWaits() {
        assertTrue(RespawnCooldown.canRespawn(Optional.empty(), 600L));
        assertTrue(RespawnCooldown.canRespawn(Optional.empty(), 0L));
    }

    @Test
    void cooldownIsInclusiveAtTheBoundary() {
        assertFalse(RespawnCooldown.canRespawn(Optional.of(599L), 600L));
        assertTrue(RespawnCooldown.canRespawn(Optional.of(600L), 600L));
        assertTrue(RespawnCooldown.canRespawn(Optional.of(601L), 600L));
        // A zero cooldown respawns the moment the defeat is stamped.
        assertTrue(RespawnCooldown.canRespawn(Optional.of(0L), 0L));
    }

    @Test
    void defeatStampsRoundTripThroughSeconds() {
        // 600 seconds in game ticks: 600 * 20, exactly at the boundary.
        long defeat = 12_000L;
        long now = 12_000L + 600L * 20L;
        assertEquals(600L, RespawnCooldown.elapsedSeconds(defeat, now));
        assertTrue(RespawnCooldown.canRespawn(
                Optional.of(RespawnCooldown.elapsedSeconds(defeat, now)), 600L));
    }
}
