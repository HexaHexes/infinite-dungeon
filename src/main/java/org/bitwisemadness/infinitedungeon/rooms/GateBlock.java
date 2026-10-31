package org.bitwisemadness.infinitedungeon.rooms;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bitwisemadness.infinitedungeon.treasure.KeyItems;

/**
 * The room gate (issue #252): closed until its condition says otherwise,
 * open state mirrored into the blockstate so the outline and collision follow
 * the world's truth rather than a query.
 *
 * <p>Conditions are read LIVE from {@link GateInstances} on every
 * interaction and tick - never cached - so a datapack reload reconfigures
 * already-placed gates exactly as it reconfigures spawn pools. Interaction:
 * the key source consumes one {@code infinite_dungeon:key} on click; the
 * layer-defeat source never needs a click (its ticker evaluates it); an
 * unconfigured gate is closed and inert - configuration is explicit, never
 * assumed.</p>
 */
public class GateBlock extends BaseEntityBlock {

    public static final MapCodec<GateBlock> CODEC = simpleCodec(GateBlock::new);

    /** The world-visible open state; the entity owns the truth, this mirrors. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public GateBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GateBlock> codec() {
        return CODEC;
    }

    /** The open flag must be declared here - the blockstate carries it. */
    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateBlockEntity(pos, state);
    }

    /** Rendered from the blockstate model - no block entity renderer. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (serverLevel, pos, gateState, blockEntity) -> {
            if (blockEntity instanceof GateBlockEntity gate) {
                GateBlockEntity.serverTick(serverLevel, pos, gateState, gate);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GateBlockEntity gate)) {
            return InteractionResult.PASS;
        }
        if (GateInstances.conditionAt(level, pos) != GateCondition.KEY) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(KeyItems.KEY.get())) {
            return InteractionResult.sidedSuccess(level.isClientSide); // swing; wrong item, nothing consumed
        }
        if (!level.isClientSide) {
            held.shrink(1);
            gate.open();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty(); // open means open: entities walk through
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return Shapes.empty();
        }
        return super.getShape(state, level, pos, context);
    }

    /** A closed gate propagates no skylight; an open one is a hole. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter reader, BlockPos pos) {
        return state.getValue(OPEN) || super.propagatesSkylightDown(state, reader, pos);
    }
}
