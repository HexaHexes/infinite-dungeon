package org.bitwisemadness.infinitedungeon.rooms;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The room gate block and its state-holding entity (issue #252, D33): the
 * block ships this repo's first block entity, because the gate's open/closed
 * state is gameplay state living beside the world - several gates track
 * themselves with no global bookkeeping, and generation never knows about
 * them (D13).
 *
 * <p>Unbreakable and piston-immovable by construction (strength -1, push
 * reaction BLOCK): D33's "boss gates are always protected" enforced
 * intrinsically, without depending on #251's registry. Not carried as an
 * item - gates are placed by content or the datapack, never by hand (the
 * {@link PortalBlocks} precedent's reasoning; the model reuses a vanilla
 * texture per the no-new-textures rule).</p>
 */
public final class GateBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(InfiniteDungeon.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InfiniteDungeon.MOD_ID);

    public static final DeferredBlock<GateBlock> GATE =
            BLOCKS.registerBlock("gate", GateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(-1.0F, 3600000.0F)
                            .sound(SoundType.METAL)
                            .pushReaction(PushReaction.BLOCK)
                            .noLootTable());

    public static final Supplier<BlockEntityType<GateBlockEntity>> GATE_ENTITY =
            BLOCK_ENTITIES.register("gate",
                    () -> BlockEntityType.Builder.of(GateBlockEntity::new, GATE.get())
                            .build(net.minecraft.Util.fetchChoiceType(
                                    net.minecraft.util.datafix.fixes.References.BLOCK_ENTITY,
                                    "infinite_dungeon:gate")));

    private GateBlocks() {
    }

    /** Registers the block and its entity on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
