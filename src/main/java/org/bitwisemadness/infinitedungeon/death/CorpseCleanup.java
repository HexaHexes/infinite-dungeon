package org.bitwisemadness.infinitedungeon.death;

import de.maxhenkel.corpse.entities.CorpseEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;

/**
 * Removes emptied corpses from the dungeon (section 5).
 *
 * <p>The Corpse mod behaves normally - a corpse spawns at the death site
 * holding the inventory and is reclaimed by walking back. What this adds is
 * the last step: once the inventory is fully reclaimed, an empty corpse would
 * otherwise litter the floors forever, so a periodic sweep removes it while
 * {@code integrations.corpse.despawnWhenEmpty} is on (the default). The sweep
 * runs in the dungeon only; Corpse elsewhere is untouched.</p>
 *
 * <p>The sweep is a periodic scan over the level's loaded entities rather
 * than join/leave bookkeeping: it holds no state, so it cannot drift out of
 * sync across chunk loads and restarts, and the dungeon's entity count makes
 * a scan every few seconds free. The {@code instanceof CorpseEntity} is
 * confined to {@link #isEmptyCorpse} behind the ModList gate - Corpse is a
 * compile-side only dependency, and that class must never be linked while
 * the mod is absent.</p>
 *
 * <p>Gameplay-only wiring, proven by the GameTest suite, which invokes
 * {@link #scan} directly because the harness cannot host the dungeon
 * dimension - the dimension gate is the same one-line check
 * {@code DungeonTime} uses, in the tick listener. The suite's mock players
 * never declare Corpse's particle payload channel, so every removal there
 * exercises the contained-failure path in {@link #removeCorpse}.</p>
 */
public final class CorpseCleanup {

    /** How often the dungeon is swept, in ticks (100 = every five seconds). */
    private static final long SCAN_INTERVAL_TICKS = 100L;

    private CorpseCleanup() {
    }

    /** Per-tick: sweep the dungeon on the scan interval. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DungeonDimensions.LEVEL.equals(level.dimension())) {
            return;
        }
        if (level.getGameTime() % SCAN_INTERVAL_TICKS != 0L) {
            return;
        }
        scan(level);
    }

    /**
     * One sweep of a level: remove every corpse whose inventory is fully
     * reclaimed while the config allows it. Public because the GameTest suite
     * calls it directly; the tick listener owns the dimension and interval
     * gates.
     */
    public static void scan(ServerLevel level) {
        if (!isCorpseLoaded()) {
            return;
        }
        boolean despawnWhenEmpty = DungeonConfigs.corpseDespawnWhenEmpty();
        List<Entity> reclaimed = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (CorpsePolicy.shouldRemove(despawnWhenEmpty, isEmptyCorpse(entity))) {
                reclaimed.add(entity);
            }
        }
        // Discard after the iteration: removal mutates the level's entity
        // storage that the iterable walks over.
        for (Entity entity : reclaimed) {
            removeCorpse(entity);
        }
    }

    /**
     * One removal, contained: Corpse's own {@code remove} override notifies
     * every player within 64 blocks with a particle payload, and a client that
     * never declared that channel - a vanilla client in production, the
     * harness's mock players in tests - makes the send throw. The corpse is
     * already removed by the time the notification fires, so the sweep logs
     * and continues rather than let a client-side detail abort the floors.
     */
    private static void removeCorpse(Entity entity) {
        try {
            entity.discard();
        } catch (UnsupportedOperationException ex) {
            InfiniteDungeon.LOGGER.warn("Corpse removal could not notify nearby players at {}: {}",
                    entity.blockPosition(), ex.getMessage());
        }
    }

    /** Whether the Corpse mod is present; gates every CorpseEntity reference. */
    private static boolean isCorpseLoaded() {
        return ModList.get().isLoaded("corpse");
    }

    /**
     * Whether this entity is a Corpse corpse with nothing left in it.
     *
     * <p>Only reachable behind {@link #isCorpseLoaded}: CorpseEntity is
     * compile-side only, so this instruction must never run without the mod.
     * </p>
     */
    private static boolean isEmptyCorpse(Entity entity) {
        return entity instanceof CorpseEntity corpse && corpse.isEmpty();
    }
}
