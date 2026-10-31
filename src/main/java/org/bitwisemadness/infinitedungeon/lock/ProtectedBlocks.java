package org.bitwisemadness.infinitedungeon.lock;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;

/**
 * Declared protection for room-content blocks (issue #251, after the D41
 * comparison decision): a block a room depends on - a spawner, a summoning
 * block, a gate, a key seal - survives the four destruction paths until its
 * declared lifetime ends.
 *
 * <p><b>Lifetimes</b> ({@code until}): {@code defeated} (default) protects
 * until the block's layer guardian has fallen at least once - evaluated
 * against {@link LayerUnlocks} for the lane the position stands in, so the
 * check is a deterministic lookup with no per-block saved state;
 * {@code always} protects indefinitely; {@code never} is the explicit opt-out
 * and is never registered at all. Room-granular conditions (a specific room's
 * boss or challenge) join through the gate-block condition sources when
 * issues #252/#255 land; they are deliberately not invented here.</p>
 *
 * <p><b>Sources.</b> Code registration ships now, because rooms are not
 * implemented yet and no production position source exists (verified at
 * implementation). The datapack source rides the first content consumer
 * (#47, #49, or the #78 rooms schema), so no loader ships ahead of anything
 * that could populate it.</p>
 *
 * <p><b>Coverage</b> is exactly the four verified hooks in
 * {@link ProtectionListeners}; no other destruction path is assumed to be
 * caught, because none is event-covered on NeoForge 21.1.259.</p>
 */
public final class ProtectedBlocks {

    /**
     * How long protection lasts, in their D41 vocabulary: {@code defeated},
     * {@code always}, {@code never}.
     */
    public enum Until {
        DEFEATED,
        ALWAYS,
        NEVER;

        /**
         * Parses a declared lifetime, naming the offending value and its
         * location when the word is not one of the three.
         *
         * @param raw  the declared word, case-insensitive
         * @param where what declared it, for the error message
         */
        public static Until parse(String raw, String where) {
            Objects.requireNonNull(raw, "raw");
            String normalized = raw.toLowerCase(Locale.ROOT);
            for (Until value : values()) {
                if (value.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("protection lifetime declared in " + where
                    + " must be defeated, always or never; got " + raw);
        }
    }

    /** One declared set of protected positions in one dimension. */
    private record Registration(ResourceKey<Level> dimension, Set<BlockPos> positions, Until until) {
    }

    /**
     * Flat and copy-on-write: registration happens at content-registration
     * time and queries run on explosion and piston events, where a torn read
     * must never be possible.
     */
    private static final List<Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();

    private ProtectedBlocks() {
    }

    /**
     * Declares positions protected for their lifetime. {@code never} is
     * skipped outright (an opt-out adds nothing before any list inheritance
     * exists), and an empty declaration is a no-op.
     */
    public static void register(ResourceKey<Level> dimension, Collection<BlockPos> positions, Until until) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(until, "until");
        if (until == Until.NEVER || positions.isEmpty()) {
            return;
        }
        REGISTRATIONS.add(new Registration(dimension, Set.copyOf(positions), until));
    }

    /** Removes every registration; test teardown, never called in production. */
    public static void clear() {
        REGISTRATIONS.clear();
    }

    /** Whether any protection is declared for the dimension (the listeners' fast path). */
    static boolean hasRegistration(ResourceKey<Level> dimension) {
        for (Registration registration : REGISTRATIONS) {
            if (registration.dimension().equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the position is protected right now, in its dimension. */
    public static boolean isProtected(Level level, BlockPos pos) {
        for (Registration registration : REGISTRATIONS) {
            if (registration.dimension().equals(level.dimension()) && registration.positions().contains(pos)
                    && protects(registration.until(), layerDefeated(level, pos))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The lifetime rule, pure: {@code defeated} releases the moment the
     * layer's guardian has fallen at least once. JUnit-tested directly.
     */
    static boolean protects(Until until, boolean layerDefeated) {
        return switch (until) {
            case ALWAYS -> true;
            case NEVER -> false;
            case DEFEATED -> !layerDefeated;
        };
    }

    /**
     * Whether the position's layer guardian has fallen. Lane 0 and west have
     * no guardian ever (they are outside the dungeon since #246), so a
     * {@code defeated} declaration there stays protected.
     */
    private static boolean layerDefeated(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        int lane = LaneGeometry.laneIndex(pos.getX(), LaneGrid.laneStride());
        if (lane < 1) {
            return false;
        }
        return LayerUnlocks.of(server).isUnlocked(lane);
    }
}
