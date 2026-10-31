package org.bitwisemadness.infinitedungeon.lanes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.bitwisemadness.infinitedungeonapi.profile.BossRespawnMode;
import org.junit.jupiter.api.Test;

/** The respawn-policy decision (issue #259): the three modes, pinned directly. */
class RespawnGateTest {

    @Test
    void onceSpawnsOnlyBeforeAnyDefeat() {
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.ONCE, Optional.empty(), 600, false),
                "once must allow the first fight");
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.ONCE, Optional.empty(), 600, true),
                "a fought-first fight still needs the one allowed spawn");
        assertFalse(RespawnGate.maySpawn(BossRespawnMode.ONCE, Optional.of(1L), 600, false),
                "once must never respawn after any defeat, however new");
        assertFalse(RespawnGate.maySpawn(BossRespawnMode.ONCE, Optional.of(99999L), 1, true),
                "once must never respawn even long past the cooldown");
    }

    @Test
    void timerFollowsTheInclusiveCooldown() {
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.TIMER, Optional.empty(), 600, false),
                "the first spawn has no wait");
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.TIMER, Optional.of(600L), 600, true),
                "the boundary respawns - the cooldown is inclusive");
        assertFalse(RespawnGate.maySpawn(BossRespawnMode.TIMER, Optional.of(599L), 600, false),
                "inside the cooldown nothing spawns, fought or not");
    }

    @Test
    void perPlayerSpawnsForTheUnfoughtPlayerImmediately() {
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.PER_PLAYER, Optional.of(1L), 600, false),
                "an un-fought player must get their fight at once, past cooldown or not");
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.PER_PLAYER, Optional.empty(), 600, false),
                "an un-fought player needs no defeat to spawn against");
        assertFalse(RespawnGate.maySpawn(BossRespawnMode.PER_PLAYER, Optional.of(1L), 600, true),
                "a fought player serves the cooldown - farming stays as it is");
        assertTrue(RespawnGate.maySpawn(BossRespawnMode.PER_PLAYER, Optional.of(600L), 600, true),
                "a fought player still respawns once the cooldown elapses");
        assertFalse(RespawnGate.maySpawn(BossRespawnMode.PER_PLAYER, Optional.of(599L), 600, true),
                "a fought player inside the cooldown waits");
    }

    @Test
    void tickDelaysConvertToSecondsFlooringAtZero() {
        assertEquals(600L, RespawnGate.cooldownSeconds(12000L),
                "the shipped default is 600 seconds");
        assertEquals(1L, RespawnGate.cooldownSeconds(20L));
        assertEquals(0L, RespawnGate.cooldownSeconds(19L),
                "sub-second delays floor to zero rather than negative");
        assertEquals(0L, RespawnGate.cooldownSeconds(-5L),
                "a nonsense delay floors to zero instead of inverting the gate");
    }
}
