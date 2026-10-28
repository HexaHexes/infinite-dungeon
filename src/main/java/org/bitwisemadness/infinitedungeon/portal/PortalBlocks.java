package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The core mod's blocks: the dungeon portal interior.
 *
 * <p>Rendered through vanilla's own nether-portal models (the plan's texture
 * rule), so no new textures exist. Not carried as an item: the portal is
 * built by igniting a frame, never placed by hand.</p>
 */
public final class PortalBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(InfiniteDungeon.MOD_ID);

    public static final DeferredBlock<DungeonPortalBlock> DUNGEON_PORTAL =
            BLOCKS.registerBlock("dungeon_portal", DungeonPortalBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .noCollission()
                            .strength(-1.0F)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 11)
                            .pushReaction(PushReaction.BLOCK)
                            .noLootTable());

    private PortalBlocks() {
    }

    /** Registers the blocks on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
