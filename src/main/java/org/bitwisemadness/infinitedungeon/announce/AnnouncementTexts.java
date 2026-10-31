package org.bitwisemadness.infinitedungeon.announce;

/**
 * The shipped announcement texts, kept pure so tests can guard the house
 * rules: ASCII-only, no emojis. Server operators reword these in the config;
 * the features that announce them name them in their own PRs.
 */
public final class AnnouncementTexts {

    /** Shown when the adventure lock is applied to a player. */
    public static final String ADVENTURE_LOCK =
            "You are locked to adventure mode inside the dungeon.";

    /** Shown when a destroyed anchor falls back to the world spawn. */
    public static final String ANCHOR_FALLBACK =
            "Your return anchor was destroyed; you have been returned to the world spawn.";

    /** Broadcast when a floor's guardian awakens. */
    public static final String BOSS_AWAKENING = "The guardian of this floor awakens.";

    /** Broadcast when a room event begins. */
    public static final String ROOM_EVENT = "Something stirs in this room.";

    /** Shown when a dungeon portal may not be built here. */
    public static final String PORTAL_NOT_ALLOWED = "Dungeon portals cannot be built here.";

    /** Shown when a layer transition refuses passage. */
    public static final String LANE_SEALED =
            "This passage is sealed until the layer's guardian falls.";

    /** Shown when a chamber is entered during the guardian's cooldown. */
    public static final String BOSS_RECOVERING =
            "The guardian has not yet recovered from its last defeat.";

    private AnnouncementTexts() {
    }
}
