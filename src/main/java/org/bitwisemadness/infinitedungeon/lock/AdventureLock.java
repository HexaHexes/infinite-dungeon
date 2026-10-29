package org.bitwisemadness.infinitedungeon.lock;

import java.util.Collection;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPolicy;

/**
 * The adventure-mode lock's decisions, as pure logic (section 5).
 *
 * <p>The lock applies in the dungeon and nowhere else: gamemode is swapped on
 * entry and restored on exit, and block breaking is refused inside except for
 * a config whitelist of player-placeable blocks. Kept Minecraft-free so both
 * rules are unit-testable without a game runtime.</p>
 */
public final class AdventureLock {

    private AdventureLock() {
    }

    /** Whether the lock governs this dimension: the dungeon and nothing else. */
    public static boolean applies(String dimensionId) {
        return SpawnPolicy.DUNGEON_DIMENSION.equals(dimensionId);
    }

    /**
     * Whether a block id may be broken under the lock. Whitelisted entries are
     * compared as full, trimmed ids - a bare entry never matches, so packs
     * name {@code namespace:path} exactly.
     */
    public static boolean isBreakable(String blockId, Collection<String> whitelist) {
        if (blockId == null || whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        String id = blockId.trim();
        for (String entry : whitelist) {
            if (entry != null && entry.trim().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
