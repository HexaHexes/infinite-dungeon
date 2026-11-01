package org.bitwisemadness.infinitedungeon.rooms;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;

/**
 * The gate's truth (issue #252, D33; #255 adds the trial stamps): the open
 * flag is gameplay state beside the world - never generation state, so D13
 * holds and every gate tracks itself with no global bookkeeping. The
 * condition itself is NOT stored: it is read live from {@link GateInstances}
 * (a datapack reload therefore reconfigures placed gates, exactly like spawn
 * pools).
 *
 * <p>The layer-defeat source evaluates here on the cheap tick (every 20
 * game ticks), mirroring how {@link LayerUnlocks} answers every other seal
 * question; the key source is interaction-driven and never ticks for. The
 * trial source (#255) stamps each declared spawner on that same tick - the
 * stamps are saved state beside the gate because spawners re-arm
 * independently, so completion must accumulate rather than be re-observed
 * all at once.</p>
 */
public class GateBlockEntity extends BlockEntity {

    private boolean open;
    private final Set<BlockPos> completedTrials = new HashSet<>();

    public GateBlockEntity(BlockPos pos, BlockState state) {
        super(GateBlocks.GATE_ENTITY.get(), pos, state);
    }

    /** Whether this gate has opened (the blockstate mirrors it). */
    public boolean isOpen() {
        return open;
    }

    /**
     * The entity is the truth (D33), so it corrects the mirror on load. This
     * matters because {@code BlockStateProperties.OPEN}'s first value is
     * {@code true}, making the default blockstate open - a gate placed without
     * going through a placement hook (worldgen, structure load, the test
     * harness) would otherwise spawn ajar. A freshly-loaded flag is the source
     * of truth, so the blockstate is forced to match it once.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!open && level != null && !level.isClientSide
                && getBlockState().hasProperty(GateBlock.OPEN)
                && getBlockState().getValue(GateBlock.OPEN)) {
            // Same block, different property value: the chunk reuses this
            // entity rather than recreating it, so this cannot recurse.
            level.setBlock(worldPosition, getBlockState().setValue(GateBlock.OPEN, false), 3);
        }
    }

    /** Opens permanently; the flag persists and the blockstate follows. */
    public void open() {
        if (open) {
            return;
        }
        open = true;
        setChanged();
        if (level != null && getBlockState().hasProperty(GateBlock.OPEN)) {
            level.setBlock(worldPosition, getBlockState().setValue(GateBlock.OPEN, true), 3);
        }
    }

    /** Whether this gate has observed the trial spawner's reward (idempotent). */
    public boolean hasCompletedTrial(BlockPos trial) {
        return completedTrials.contains(trial);
    }

    /** Stamps one trial complete - sticky forever, even if the spawner re-arms. */
    public void completeTrial(BlockPos trial) {
        if (completedTrials.add(trial.immutable())) {
            setChanged();
        }
    }

    /** The server-side tick: the polled conditions, nothing else. */
    static void serverTick(Level level, BlockPos pos, BlockState state, GateBlockEntity gate) {
        if (gate.open || level.getGameTime() % 20L != 0L) {
            return;
        }
        GateDeclaration declaration = GateInstances.declarationAt(level, pos);
        if (declaration == null) {
            return;
        }
        switch (declaration.condition()) {
            case LAYER_DEFEATED -> layerDefeatSource(level, pos, gate);
            case TRIALS_COMPLETE -> trialSource(level, gate, declaration);
            case KEY -> {
                // Interaction-driven: the ticker never opens a key gate.
            }
        }
    }

    private static void layerDefeatSource(Level level, BlockPos pos, GateBlockEntity gate) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int lane = LaneGeometry.laneIndex(pos.getX(), LaneGrid.laneStride());
        if (lane >= 1 && LayerUnlocks.of(server).isUnlocked(lane)) {
            gate.open();
        }
    }

    /**
     * The trial source (#255): each declared spawner is stamped the first
     * time it is observed giving its reward, and the gate opens once every
     * declared spawner carries its stamp. Stamps live on the gate (D33), so
     * a spawner that re-arms after its cooldown cannot un-complete it - the
     * all-at-once observation a multi-spawner room would otherwise need
     * never has to happen.
     */
    private static void trialSource(Level level, GateBlockEntity gate, GateDeclaration declaration) {
        for (BlockPos trial : declaration.trialSpawners()) {
            if (!gate.hasCompletedTrial(trial) && rewardGiven(level, trial)) {
                gate.completeTrial(trial);
            }
        }
        if (!declaration.trialSpawners().isEmpty()
                && declaration.trialSpawners().stream().allMatch(gate::hasCompletedTrial)) {
            gate.open();
        }
    }

    /** Reward state: the spawner finished its wave and is ejecting (or past) the reward. */
    private static boolean rewardGiven(Level level, BlockPos trial) {
        BlockState state = level.getBlockState(trial);
        if (!state.hasProperty(BlockStateProperties.TRIAL_SPAWNER_STATE)) {
            return false; // no spawner at the declared spot: never completes
        }
        TrialSpawnerState spawnerState = state.getValue(BlockStateProperties.TRIAL_SPAWNER_STATE);
        return spawnerState == TrialSpawnerState.EJECTING_REWARD
                || spawnerState == TrialSpawnerState.COOLDOWN;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("open", open);
        // Flat [x, y, z, ...] triples: CompoundTag has no string-list methods
        // (the recorded truth), and int arrays need no escaping.
        int[] flat = new int[completedTrials.size() * 3];
        int index = 0;
        for (BlockPos trial : completedTrials) {
            flat[index++] = trial.getX();
            flat[index++] = trial.getY();
            flat[index++] = trial.getZ();
        }
        tag.putIntArray("trials", flat);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("open");
        completedTrials.clear();
        int[] flat = tag.getIntArray("trials");
        for (int i = 0; i + 2 < flat.length; i += 3) {
            completedTrials.add(new BlockPos(flat[i], flat[i + 1], flat[i + 2]));
        }
    }
}
