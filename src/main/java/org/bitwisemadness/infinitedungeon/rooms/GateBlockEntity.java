package org.bitwisemadness.infinitedungeon.rooms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bitwisemadness.infinitedungeon.lanes.LaneGeometry;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;

/**
 * The gate's truth (issue #252, D33): the open flag is gameplay state beside
 * the world - never generation state, so D13 holds and every gate tracks
 * itself with no global bookkeeping. The condition itself is NOT stored: it
 * is read live from {@link GateInstances} (a datapack reload therefore
 * reconfigures placed gates, exactly like spawn pools).
 *
 * <p>The layer-defeat source evaluates here on the cheap tick (every 20
 * game ticks), mirroring how {@link LayerUnlocks} answers every other seal
 * question; the key source is interaction-driven and never ticks for.</p>
 */
public class GateBlockEntity extends BlockEntity {

    private boolean open;

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

    /** The server-side tick: the layer-defeat source polls, nothing else. */
    static void serverTick(Level level, BlockPos pos, BlockState state, GateBlockEntity gate) {
        if (gate.open || level.getGameTime() % 20L != 0L) {
            return;
        }
        if (GateInstances.conditionAt(level, pos) != GateCondition.LAYER_DEFEATED) {
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int lane = LaneGeometry.laneIndex(pos.getX(), LaneGrid.laneStride());
        if (lane >= 1 && LayerUnlocks.of(server).isUnlocked(lane)) {
            gate.open();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("open", open);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("open");
    }
}
