package org.bitwisemadness.infinitedungeon.portal;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPolicy;

/**
 * Which dimensions a dungeon portal may be constructed in, as pure logic -
 * the same whitelist/blacklist shape the spawn-safety module ships, so pack
 * authors in this suite already know it.
 *
 * <p>Construction only: travel through an existing portal and the mod's own
 * dungeon entrance are never gated by this. The shipped default is a
 * whitelist of the overworld and the dungeon itself, so no entrances sprout
 * in the Nether, the End, or another mod's dimension - and a stranded player
 * inside the dungeon can always build their own way out.</p>
 *
 * @param mode       whitelist (only listed dimensions) or blacklist (all but listed)
 * @param dimensions the dimension ids involved
 */
public record BuildableDimensions(Mode mode, Set<String> dimensions) {

    /** How the list is read. */
    public enum Mode {
        /** Only the listed dimensions may have portals built. */
        WHITELIST,
        /** Every dimension except the listed ones. */
        BLACKLIST
    }

    /** The shipped default: overworld entrances, plus the dungeon itself. */
    public static final BuildableDimensions DEFAULT =
            new BuildableDimensions(Mode.WHITELIST,
                    Set.of("minecraft:overworld", SpawnPolicy.DUNGEON_DIMENSION));

    public BuildableDimensions {
        mode = mode == null ? Mode.WHITELIST : mode;
        if (dimensions == null || dimensions.isEmpty()) {
            dimensions = Set.of();
        } else {
            // Trim and drop blanks: config lists are hand-typed.
            Set<String> cleaned = new LinkedHashSet<>();
            for (String raw : dimensions) {
                if (raw != null) {
                    String value = raw.trim();
                    if (!value.isEmpty()) {
                        cleaned.add(value);
                    }
                }
            }
            dimensions = Set.copyOf(cleaned);
        }
    }

    /** Parses the config mode, defaulting to the whitelist. */
    public static Mode parseMode(String raw) {
        return "blacklist".equalsIgnoreCase(raw == null ? null : raw.trim())
                ? Mode.BLACKLIST
                : Mode.WHITELIST;
    }

    /** Whether a dungeon portal may be built in this dimension. */
    public boolean allows(String dimensionId) {
        boolean listed = dimensionId != null && dimensions.contains(dimensionId);
        return switch (mode) {
            case WHITELIST -> listed;
            case BLACKLIST -> !listed;
        };
    }
}
