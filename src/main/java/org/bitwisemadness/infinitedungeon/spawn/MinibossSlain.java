package org.bitwisemadness.infinitedungeon.spawn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.bitwisemadness.infinitedungeon.lanes.RespawnCooldown;

/**
 * Per-(layer, floor) miniboss slain flags (issue #41): a floor's miniboss
 * re-appears on entry UNTIL first slain, then stays cleared - keys must stay
 * scarce, and the gate philosophy's "only ever opens" spirit applies. Its own
 * SavedData beside {@code LayerUnlocks} and {@code GuardianDefeats} - the
 * third distinct concern, never merged into either.
 *
 * <p>Keys encode as {@code layer << 32 | floor} (both indices are small,
 * non-negative), so storage is one long array. The test seam mirrors the
 * others: the no-time {@link #slay} stamps nothing (an un-timed fall never
 * feeds a TIMER policy - production always stamps, issue #259), and
 * {@link #forget} restores premise. Timestamps serialize under their own
 * tag, so pre-policy saves load unchanged.</p>
 */
public final class MinibossSlain extends SavedData {

    private static final String STORAGE_KEY = "infinite_dungeon_miniboss_slain";

    private static final Factory<MinibossSlain> FACTORY =
            new Factory<>(MinibossSlain::new, MinibossSlain::load);

    private final Set<Long> slain = new HashSet<>();
    private final Map<Long, Long> slayTime = new HashMap<>();

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

    /** Marks the (layer, floor) miniboss fallen (idempotent, un-timed). */
    public void slay(int layer, int floor) {
        if (slain.add(key(layer, floor))) {
            setDirty();
        }
    }

    /** Marks the fall at a game time - the production path (issue #259's TIMER). */
    public void slay(int layer, int floor, long gameTime) {
        long packed = key(layer, floor);
        if (slain.add(packed)) {
            setDirty();
        }
        Long previous = slayTime.put(packed, gameTime);
        if (previous == null || previous.longValue() != gameTime) {
            setDirty(); // a re-slay moves the TIMER anchor and must persist
        }
    }

    /**
     * Whole seconds since the fall: empty when the floor never fell or fell
     * without a timestamp (legacy saves and the no-time test seam) - a TIMER
     * policy treats an un-timed fall as one-time rather than guessing an age.
     */
    public Optional<Long> elapsedSeconds(int layer, int floor, long nowGameTime) {
        Long slay = slayTime.get(key(layer, floor));
        if (slay == null) {
            return Optional.empty();
        }
        return Optional.of(RespawnCooldown.elapsedSeconds(slay, nowGameTime));
    }

    /** Forgets the floor's fall (test hygiene; no production caller). */
    public void forget(int layer, int floor) {
        long packed = key(layer, floor);
        if (slain.remove(packed)) {
            setDirty();
        }
        if (slayTime.remove(packed) != null) {
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
        // Alternating [key, time] pairs under a separate tag: absent in
        // pre-policy saves, whose falls are then un-timed by definition.
        long[] pairs = tag.getLongArray("slainTimes");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            data.slayTime.put(pairs[i], pairs[i + 1]);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] packed = slain.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("slain", packed);
        long[] times = new long[slayTime.size() * 2];
        int index = 0;
        for (Map.Entry<Long, Long> entry : slayTime.entrySet()) {
            times[index++] = entry.getKey();
            times[index++] = entry.getValue();
        }
        tag.putLongArray("slainTimes", times);
        return tag;
    }
}
