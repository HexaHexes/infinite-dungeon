package org.bitwisemadness.infinitedungeon.portal;

import org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode;

/**
 * What a portal walk should do, as pure logic - the full exit policy from
 * section 4.
 *
 * <p>Kept Minecraft-free so every branch is unit-testable. Outside the dungeon
 * the walk binds the anchor and travels in. Inside it, the stored anchor and
 * the configured return mode decide: back through your own portal, or out to
 * the overworld world spawn (configured). No anchor at all - admin teleport,
 * first join - falls back to the world spawn with the announcement; and when a
 * destroyed anchor cannot be re-formed, so does that. Only the last two
 * announce, because the shipped message speaks of a destroyed anchor.</p>
 */
public final class TravelPolicy {

    /** The action a portal walk resolves to. */
    public enum Action {
        /** Outside the dungeon: store the anchor and travel in. */
        BIND_AND_ENTER,
        /** Inside the dungeon with a usable anchor: travel back to it. */
        RETURN_TO_ANCHOR,
        /** Inside the dungeon: configured exit to the world spawn (no message). */
        WORLD_SPAWN_BY_CONFIG,
        /** Inside the dungeon with no anchor: world spawn plus the fallback message. */
        WORLD_SPAWN_NO_ANCHOR,
        /** The anchor survived but cannot be used (destroyed, not re-formed): world spawn plus the fallback message. */
        WORLD_SPAWN_DESTROYED
    }

    private TravelPolicy() {
    }

    /** Decides one portal walk from the anchor and return mode. */
    public static Action decide(boolean inDungeon, boolean anchorPresent, PortalReturnMode returnMode) {
        if (!inDungeon) {
            return Action.BIND_AND_ENTER;
        }
        if (!anchorPresent) {
            return Action.WORLD_SPAWN_NO_ANCHOR;
        }
        return returnMode == PortalReturnMode.WORLD_SPAWN
                ? Action.WORLD_SPAWN_BY_CONFIG
                : Action.RETURN_TO_ANCHOR;
    }

    /**
     * Decides what happens when an anchor portal turns out to be unusable:
     * re-forming it returns the player to it, otherwise the world spawn with
     * the message.
     */
    public static Action afterReformAttempt(boolean reformEnabled, boolean rebuildSucceeded) {
        return reformEnabled && rebuildSucceeded ? Action.RETURN_TO_ANCHOR : Action.WORLD_SPAWN_DESTROYED;
    }

    /** Whether the fallback message belongs with this action. */
    public static boolean announcesFallback(Action action) {
        return action == Action.WORLD_SPAWN_NO_ANCHOR || action == Action.WORLD_SPAWN_DESTROYED;
    }
}
