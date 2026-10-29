package org.bitwisemadness.infinitedungeon.portal;

/**
 * What a portal walk should do, as pure logic.
 *
 * <p>Kept Minecraft-free so the travel decision is unit-testable: entering
 * from a normal dimension binds the anchor and travels; walking into a portal
 * inside the dungeon returns the player to their own anchor; with no anchor
 * stored, nothing happens - the missing-anchor fallback belongs to the
 * anchor-handling issue, not this one.</p>
 */
public final class TravelPolicy {

    /** The action a portal walk resolves to. */
    public enum Action {
        /** Outside the dungeon: store the anchor and travel in. */
        BIND_AND_ENTER,
        /** Inside the dungeon with an anchor: travel back to it. */
        RETURN_TO_ANCHOR,
        /** Inside the dungeon with no anchor: do nothing (missing-anchor issue). */
        STAY_PUT
    }

    private TravelPolicy() {
    }

    /** Decides one portal walk. */
    public static Action decide(boolean inDungeon, boolean anchorPresent) {
        if (!inDungeon) {
            return Action.BIND_AND_ENTER;
        }
        return anchorPresent ? Action.RETURN_TO_ANCHOR : Action.STAY_PUT;
    }
}
