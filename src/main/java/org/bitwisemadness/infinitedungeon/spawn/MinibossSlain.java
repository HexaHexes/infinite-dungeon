package org.bitwisemadness.infinitedungeon.spawn;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-(layer, floor) miniboss slain flags (issue #41): a floor's miniboss
 * re-appears on entry UNTIL first slain, then stays cleared - keys must stay
 * scarce, and the gate philosophy's "only ever opens" spirit applies. Its own
 * SavedData beside {@code LayerUnlocks} and {@code GuardianDefeats} - the
 * third distinct concern, never merged into either.
 *
 * <p>Keys encode as {@code layer << 32 | floor} (both indices are small,
 * non-negative), so storage is one long array. The test seam mirrors the
 * others: {@link #slay} takes no time, and {@link #forget} restores premise.</p>
 */
public final class MinibossSlain extends SavedData {

    private static final String STORAGE_KEY = "infinite_dungeon_miniboss_slain";

    private static final Factory<MinibossSlain> FACTORY =
            new Factory<>(MinibossSlain::new, MinibossSlain::load);

    private final Set<Long> slain = new HashSet<>();

    private MinibossSlain() {
    }

    /** The record of a level's saved data. */
    public static MinibossSlain of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Whether the (layer, floor) miniboss has fallen. */
    public boolean isSlain(int layer, int floor) {
        return slain.contains(key(layer, floor));
    }

    /** Marks the (layer, floor) miniboss fallen (idempotent). */
    public void slay(int layer, int floor) {
        if (slain.add(key(layer, floor))) {
            setDirty();
        }
    }

    /** Forgets the floor's fall (test hygiene; no production caller). */
    public void forget(int layer, int floor) {
        if (slain.remove(key(layer, floor))) {
            setDirty();
        }
    }

    private static long key(int layer, int floor) {
        return (((long) layer) << 32) | (floor & 0xFFFFFFFFL);
    }

    private static MinibossSlain load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new MinibossSlain();
        for (long packed : tag.getLongArray("slain")) {
            data.slain.add(packed);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] packed = slain.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("slain", packed);
        return tag;
    }
}
