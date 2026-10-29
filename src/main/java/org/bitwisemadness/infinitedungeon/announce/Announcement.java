package org.bitwisemadness.infinitedungeon.announce;

import java.util.Objects;
import java.util.function.Supplier;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The announcements the mod can send, each backed by a config value so server
 * operators reword the text without touching data.
 *
 * <p>New announcements join this enum in the PR of the feature that first
 * sends them, together with their config entry and default text.</p>
 */
public enum Announcement {

    /** The adventure lock explanation, sent to the player it applies to. */
    ADVENTURE_LOCK(DungeonConfigs::adventureLock),

    /** The destroyed-anchor fallback note, sent to the player it applies to. */
    ANCHOR_FALLBACK(DungeonConfigs::anchorFallback),

    /** Broadcast to a level when a floor's guardian awakens. */
    BOSS_AWAKENING(DungeonConfigs::bossAwakening),

    /** Broadcast to a level when a room event begins. */
    ROOM_EVENT(DungeonConfigs::roomEvent),

    /** Shown when a dungeon portal may not be built here: the dimension gate or the construction toggle refused it. */
    PORTAL_NOT_ALLOWED(DungeonConfigs::portalNotAllowed);

    private final Supplier<String> text;

    Announcement(Supplier<String> text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /** The configured text for this announcement. */
    public String text() {
        return text.get();
    }
}
