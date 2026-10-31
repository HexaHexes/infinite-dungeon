package org.bitwisemadness.infinitedungeon.lanes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-layer guardian defeat timestamps (issue #43, plan section 6): when a
 * guardian last fell, so the chamber knows when it may respawn. Kept beside
 * {@link LayerUnlocks} but never inside it - different concerns (every defeat
 * stamps a time; the unlock flag opens exactly once) and different lifetimes
 * (a re-lock of flags has no counterpart here).
 *
 * <p>The test seam mirrors #31's: {@link #record} takes an arbitrary game
 * time, so gametests force elapsed and within-cooldown states deterministically
 * against the harness's persistent world data.</p>
 */
public final class GuardianDefeats extends SavedData {

    private static final String STORAGE_KEY = "infinite_dungeon_guardian_defeats";

    private static final Factory<GuardianDefeats> FACTORY =
            new Factory<>(GuardianDefeats::new, GuardianDefeats::load);

    private final Map<Integer, Long> defeatGameTime = new HashMap<>();

    private GuardianDefeats() {
    }

    /** The defeat record of a level's saved data. */
    public static GuardianDefeats of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Stamps a guardian's fall at the given game time (idempotent overwrite). */
    public void record(int layer, long gameTime) {
        defeatGameTime.put(layer, gameTime);
        setDirty();
    }

    /**
     * Seconds since the layer's guardian fell, or empty when it never has
     * (the first-spawn state - {@link RespawnCooldown#canRespawn} treats
     * empty as "spawn immediately").
     */
    public Optional<Long> elapsedSeconds(int layer, long nowGameTime) {
        Long defeat = defeatGameTime.get(layer);
        if (defeat == null) {
            return Optional.empty();
        }
        return Optional.of(RespawnCooldown.elapsedSeconds(defeat, nowGameTime));
    }

    /** Forgets the layer's defeat (test hygiene; no production caller). */
    public void forget(int layer) {
        if (defeatGameTime.remove(layer) != null) {
            setDirty();
        }
    }

    private static GuardianDefeats load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new GuardianDefeats();
        int[] layers = tag.getIntArray("layers");
        long[] times = tag.getLongArray("times");
        for (int i = 0; i < layers.length && i < times.length; i++) {
            data.defeatGameTime.put(layers[i], times[i]);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        int[] layers = defeatGameTime.keySet().stream().mapToInt(Integer::intValue).toArray();
        long[] times = new long[layers.length];
        for (int i = 0; i < layers.length; i++) {
            times[i] = defeatGameTime.get(layers[i]);
        }
        tag.putIntArray("layers", layers);
        tag.putLongArray("times", times);
        return tag;
    }
}
