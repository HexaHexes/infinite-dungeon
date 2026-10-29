package org.bitwisemadness.infinitedungeon.portal;

import com.mojang.serialization.MapCodec;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The dungeon portal block: the interior of a lit dungeon portal.
 *
 * <p>Renders with vanilla's nether-portal model (the plan's texture rule),
 * spans one horizontal axis like vanilla's own portal, and collapses when its
 * frame is broken - the in-plane neighbours must be portal or frame blocks.
 * Travel through it arrives with the anchor wave (#19); this block only
 * exists.</p>
 */
public final class DungeonPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final MapCodec<DungeonPortalBlock> CODEC = simpleCodec(DungeonPortalBlock::new);

    public DungeonPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends DungeonPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    /**
     * Collapses when an in-plane neighbour is neither portal nor frame - the
     * same chain reaction that makes vanilla portals fall apart when their
     * frame is broken.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis portalAxis = state.getValue(AXIS);
        if (direction.getAxis() != portalAxis && direction != Direction.UP && direction != Direction.DOWN) {
            // Perpendicular to the portal plane: the blocks behind the portal
            // can be anything.
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }
        if (!isPortalOrFrame(neighborState, level)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** A portal step: bind or read the anchor and teleport (travel wave). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        DungeonPortalTravel.handle(level, pos, entity);
    }

    private static boolean isPortalOrFrame(BlockState state, LevelAccessor level) {
        if (state.is(Blocks.AIR)) {
            return false;
        }
        if (state.getBlock() instanceof DungeonPortalBlock) {
            return true;
        }
        return state.is(Objects.requireNonNull(DungeonConfigs.frameBlock()));
    }
}
