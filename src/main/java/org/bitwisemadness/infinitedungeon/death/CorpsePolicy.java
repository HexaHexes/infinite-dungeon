package org.bitwisemadness.infinitedungeon.death;

/**
 * The Corpse cleanup's one decision, as pure logic (section 5): while
 * {@code integrations.corpse.despawnWhenEmpty} is on (the default), a corpse
 * whose inventory has been fully reclaimed is removed from the dungeon so it
 * does not litter the floors; a corpse still holding items is always left for
 * its owner to reclaim.
 *
 * <p>The gameplay-only halves - identifying a Corpse entity by class behind
 * the ModList gate, and the periodic sweep itself - live in
 * {@link CorpseCleanup} and are proven by the GameTest suite.</p>
 */
public final class CorpsePolicy {

    private CorpsePolicy() {
    }

    /** Whether the sweep removes this corpse: the config allows it and it is empty. */
    public static boolean shouldRemove(boolean despawnWhenEmpty, boolean emptyCorpse) {
        return despawnWhenEmpty && emptyCorpse;
    }
}
