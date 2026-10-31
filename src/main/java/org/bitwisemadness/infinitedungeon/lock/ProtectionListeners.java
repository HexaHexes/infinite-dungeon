package org.bitwisemadness.infinitedungeon.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * The four enforcement hooks behind {@link ProtectedBlocks} (issue #251).
 *
 * <p>Each hook was verified against the NeoForge 21.1.259 sources before any
 * code was written: {@link ExplosionEvent.Detonate#getAffectedBlocks()} is the
 * explosion's live to-blow list, so protection is removed from it rather than
 * cancelling the blast; {@link PistonEvent.Pre} is cancellable and rebuilds
 * the structure resolver, which after {@code resolve()} names every block the
 * push would move ({@code toPush}) or annihilate ({@code toDestroy}); {@link BlockEvent.BreakEvent} is cancellable;
 * and {@link EntityMobGriefingEvent} fires wherever the mob-griefing gamerule
 * is checked - the only hook covering enderman pickup - but it carries no
 * block, so griefing refusal is scoped to dimensions where protection is
 * declared rather than to protected blocks alone.</p>
 *
 * <p>Every hook fast-paths on a declared registration for the dimension, so
 * unprotected worlds pay one list scan per event and production behaviour
 * changes only once content registers something (nothing does yet - rooms are
 * unimplemented).</p>
 */
public final class ProtectionListeners {

    private ProtectionListeners() {
    }

    /** Explosions: drop protected positions from the blast, keep the blast. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!ProtectedBlocks.hasRegistration(level.dimension())) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> ProtectedBlocks.isProtected(level, pos));
    }

    /** Pistons: refuse the whole move when any involved block is protected. */
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level)
                || !ProtectedBlocks.hasRegistration(level.dimension())) {
            return;
        }
        if (ProtectedBlocks.isProtected(level, event.getPos())) {
            event.setCanceled(true);
            return;
        }
        var helper = event.getStructureHelper();
        // The event hands over a fresh resolver, so resolve() here exactly as
        // the piston would: a false path moves and destroys nothing, so there
        // is nothing to refuse.
        if (helper == null || !helper.resolve()) {
            return;
        }
        for (BlockPos pos : helper.getToPush()) {
            if (ProtectedBlocks.isProtected(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : helper.getToDestroy()) {
            if (ProtectedBlocks.isProtected(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Mob griefing: refused outright in dimensions that declare protection. */
    public static void onMobGrief(EntityMobGriefingEvent event) {
        if (ProtectedBlocks.hasRegistration(event.getEntity().level().dimension())) {
            event.setCanGrief(false);
        }
    }

    /**
     * Player breaks: refused even where the adventure whitelist would allow
     * the block, so a whitelist entry can never override declared protection.
     */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && ProtectedBlocks.isProtected(level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
