package org.bitwisemadness.infinitedungeon.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Sanity for SCRIPTED raids (issues #41 and #43): post the position check so
 * listeners keep their veto, then require only vanilla's obstruction - not
 * its spawn PREFERENCE rules.
 *
 * <p>The distinction is plan section 14's: "events are never blocked" - the
 * API's NaturalSpawns allowlist already exempts EVENT spawns from the gates,
 * and {@code EventHooks.checkSpawnPosition}'s default half
 * ({@code checkSpawnRules}) enforces natural-spawn PREFERENCES. For zombies
 * that preference is {@code PathfinderMob.getWalkTargetValue} (measured at
 * -0.5 on the harness - a value the vanilla intermediate jar reads as a
 * constant 0, so the runtime is NeoForge-patched and brighter than the
 * bytecode I first trusted): monsters declining bright ground is right for
 * NATURAL spawns and wrong for a scripted raid on a lit floor - a guardian
 * chamber or miniboss floor must not go unraided because a lamp sits there.
 * Obstruction still runs (no suffocation, no liquid), and a listener setting
 * {@code FAIL} still vetoes everything.</p>
 */
public final class EventSpawnSanity {

    private EventSpawnSanity() {
    }

    /** Whether the scripted spawn may proceed: listener veto + obstruction. */
    public static boolean passed(Mob mob, ServerLevel level, MobSpawnType type) {
        var event = new MobSpawnEvent.PositionCheck(mob, level, type, null);
        NeoForge.EVENT_BUS.post(event);
        if (event.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL) {
            return false;
        }
        return mob.checkSpawnObstruction(level);
    }
}
