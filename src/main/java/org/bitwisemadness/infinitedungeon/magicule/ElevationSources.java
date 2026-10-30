package org.bitwisemadness.infinitedungeon.magicule;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The recorded elevation-source positions of one level (plan section 7, issue
 * #218): world data, so pregenerated chunks, organic chunks and restarts all
 * converge on the same sources - the sources are stored when they appear and
 * re-registered from here when the level loads, because the API's modifier
 * registry is in-memory only.
 *
 * <p>Positions only: amount and radius are read from the
 * {@code structureElevation.*} config at registration, so one recording serves
 * every config - a restart picks up config changes over the same sources. The
 * membership test here is also the dedupe that makes repeated chunk-load
 * derivation idempotent (a source is registered exactly once per level).</p>
 *
 * <p>Stored as flattened x/y/z triples - plain ints, no codec doubt; the save
 * shape is pinned by the gametest's NBT round-trip.</p>
 */
public final class ElevationSources extends SavedData {

    private static final String STORAGE_KEY = "infinite_dungeon_elevation_sources";

    /** The factory the level's data storage binds; see {@link #of}. */
    public static final Factory<ElevationSources> FACTORY =
            new Factory<>(ElevationSources::new, ElevationSources::load);

    private final Set<BlockPos> positions = new HashSet<>();

    private ElevationSources() {
    }

    /** The recorded sources of a level's saved data. */
    public static ElevationSources of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Whether the position is already recorded. */
    public boolean contains(BlockPos pos) {
        return positions.contains(pos);
    }

    /**
     * Records a new source.
     *
     * @return true when the position was new (the caller should register it);
     *         false when it was already recorded (the caller must NOT register
     *         again - that is the dedupe)
     */
    public boolean add(BlockPos pos) {
        if (positions.add(pos)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Removes a recorded source; true when it was present. */
    public boolean remove(BlockPos pos) {
        if (positions.remove(pos)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Every recorded position; the level-load path re-registers these. */
    public Iterable<BlockPos> all() {
        return positions;
    }

    /** The NBT reader (public for the round-trip test and reload paths). */
    public static ElevationSources load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ElevationSources();
        int[] flat = tag.getIntArray("positions");
        for (int i = 0; i + 2 < flat.length; i += 3) {
            data.positions.add(new BlockPos(flat[i], flat[i + 1], flat[i + 2]));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        int[] flat = new int[positions.size() * 3];
        int i = 0;
        for (BlockPos pos : positions) {
            flat[i++] = pos.getX();
            flat[i++] = pos.getY();
            flat[i++] = pos.getZ();
        }
        tag.putIntArray("positions", flat);
        return tag;
    }
}
