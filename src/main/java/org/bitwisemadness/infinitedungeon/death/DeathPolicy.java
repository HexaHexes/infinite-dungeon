package org.bitwisemadness.infinitedungeon.death;

import org.bitwisemadness.infinitedungeon.spawn.SpawnPolicy;

/**
 * The death policy's one decision, as pure logic (section 5): a death inside
 * the dungeon returns the player to the lobby - the first layer of the
 * dungeon, which is special and of fixed construction.
 *
 * <p>The plan says "on final death only". PlayerRevive is deferred
 * indefinitely, so every death is final until that integration lands and
 * narrows this to the real death path; the game-facing hook documents the
 * guard point.</p>
 */
public final class DeathPolicy {

    private DeathPolicy() {
    }

    /** Whether a death in this dimension sends the player to the lobby. */
    public static boolean returnsToLobby(String dimensionId) {
        return SpawnPolicy.isDungeon(dimensionId);
    }
}
