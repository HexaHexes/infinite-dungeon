package org.bitwisemadness.infinitedungeon.lanes;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The per-layer unlock flags behind the guardian-gated transitions (plan
 * section 6, issue #31).
 *
 * <p>Unlocks persist per layer in world saved data once a layer's guardian has
 * been slain at least once. {@link #isSealed} answers the gate as
 * <em>assigned-and-not-unlocked</em>: a layer seals only once a guardian is
 * scheduled for it, which keeps the interim alphas playable while Wave 4's
 * boss schedule (#42) is still landing - and that wave wires the schedule
 * through {@link #setGuardianAssigned}, the single seam. Until then nothing is
 * assigned, every transition is open, and progression never bricks. The flag
 * only ever opens (slain-at-least-once), so sealed portals cannot re-lock.</p>
 */
public final class LayerUnlocks extends SavedData {

    private static final String STORAGE_KEY = "infinite_dungeon_layer_unlocks";

    private static final Factory<LayerUnlocks> FACTORY =
            new Factory<>(LayerUnlocks::new, LayerUnlocks::load);

    /**
     * Which layers have a scheduled guardian. Default: none - open until the
     * boss schedule exists (#42 sets this; gametests set it too).
     */
    private static IntPredicate guardianAssigned = layer -> false;

    private final Set<Integer> unlocked = new HashSet<>();

    private LayerUnlocks() {
    }

    /** The unlock flags of a level's saved data. */
    public static LayerUnlocks of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Wires guardian presence - called by the boss schedule (#42) and tests. */
    public static void setGuardianAssigned(IntPredicate predicate) {
        guardianAssigned = Objects.requireNonNull(predicate, "predicate");
    }

    /** Whether a guardian is scheduled for the layer (false until #42 exists). */
    public static boolean hasGuardian(int layer) {
        return guardianAssigned.test(layer);
    }

    /** Whether the layer's guardian has fallen at least once. */
    public boolean isUnlocked(int layer) {
        return unlocked.contains(layer);
    }

    /**
     * Records a guardian's fall; idempotent.
     *
     * @return true only on the FIRST fall for the layer - the completion
     *     transition the {@code LayerCompletedEvent} poster announces
     */
    public boolean unlock(int layer) {
        boolean firstFall = unlocked.add(layer);
        if (firstFall) {
            setDirty();
        }
        return firstFall;
    }

    /**
     * The counterpart: seals the layer again by forgetting its fall. No
     * production caller yet (slain-at-least-once persists by design until the
     * boss schedule says otherwise); the gametests use it for deterministic
     * setup, since harness world data survives between runs.
     */
    public void lock(int layer) {
        if (unlocked.remove(layer)) {
            setDirty();
        }
    }

    /**
     * Whether a transition into/out of the layer is sealed: assigned but not
     * yet unlocked. Unassigned layers (the interim state) are open.
     */
    public static boolean isSealed(ServerLevel level, int layer) {
        return hasGuardian(layer) && !of(level).isUnlocked(layer);
    }

    private static LayerUnlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new LayerUnlocks();
        for (int layer : tag.getIntArray("unlocked")) {
            data.unlocked.add(layer);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("unlocked", unlocked.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }
}
