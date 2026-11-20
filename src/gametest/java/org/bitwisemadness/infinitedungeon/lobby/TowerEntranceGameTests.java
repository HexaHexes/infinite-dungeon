package org.bitwisemadness.infinitedungeon.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The tower-entrance building (issue #199, Piece 1): the datagen-authored
 * template loads and is structurally valid, the start pool references it, the
 * feature places it, and the toggle gates it.
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TowerEntranceGameTests {

    private TowerEntranceGameTests() {
    }

    /** The authored template loads and carries the fixed building's invariants. */
    @GameTest(template = "empty")
    public static void tower_entrance_template_loads_and_is_valid(GameTestHelper helper) {
        var template = helper.getLevel().getServer().getStructureManager()
                .getOrCreate(TowerEntranceFeature.TEMPLATE_ID);
        helper.assertTrue(template.getSize().getX() == 7
                        && template.getSize().getY() == 8
                        && template.getSize().getZ() == 7,
                "the template must be the fixed 7x8x7 building, got " + template.getSize());
        var settings = new StructurePlaceSettings();
        var jigsaws = template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW);
        helper.assertTrue(!jigsaws.isEmpty(),
                "the building must carry its jigsaw connection block");
        CompoundTag jigsaw = jigsaws.get(0).nbt();
        helper.assertTrue("minecraft:roads".equals(jigsaw.getString("name"))
                        && "minecraft:village/plains/streets".equals(jigsaw.getString("target")),
                "the jigsaw must attach to the plains village streets (Piece 2 contract)");
        helper.assertTrue("minecraft:empty".equals(jigsaw.getString("pool")),
                "the jigsaw must be a house leaf (minecraft:empty pool)");
        helper.assertTrue(!template.filterBlocks(BlockPos.ZERO, settings, Blocks.OBSIDIAN).isEmpty(),
                "the building must carry its obsidian portal shell");
        helper.succeed();
    }

    /** The feature places the authored building at the origin. */
    @GameTest(template = "empty")
    public static void tower_entrance_feature_places_the_building(GameTestHelper helper) {
        boolean wasEnabled = DungeonConfigs.towerEntranceEnabled();
        DungeonConfigs.setTowerEntranceEnabled(true);
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(0, 5, 0));
            helper.assertTrue(TowerEntranceFeature.build(helper.getLevel(), origin,
                    helper.getLevel().getRandom()), "the feature must place the building");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 0, 6)).is(Blocks.JIGSAW),
                    "the jigsaw connection must sit on the door threshold");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(0, 0, 0)).is(Blocks.STONE_BRICKS),
                    "the tower's stone-brick walls must be placed");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 1, 1)).is(Blocks.OBSIDIAN),
                    "the portal shell's bottom must be obsidian");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 2, 1)).is(Blocks.AIR),
                    "the portal shell's center must stay open");
        } finally {
            DungeonConfigs.setTowerEntranceEnabled(wasEnabled);
        }
        helper.succeed();
    }

    /** The toggle gates the feature: off, the gate refuses (place() no-ops). */
    @GameTest(template = "empty")
    public static void tower_entrance_feature_obeys_the_toggle(GameTestHelper helper) {
        boolean wasEnabled = DungeonConfigs.towerEntranceEnabled();
        DungeonConfigs.setTowerEntranceEnabled(false);
        try {
            helper.assertTrue(!TowerEntranceFeature.enabled(),
                    "the toggle must gate the feature off");
        } finally {
            DungeonConfigs.setTowerEntranceEnabled(wasEnabled);
        }
        helper.assertTrue(TowerEntranceFeature.enabled(),
                "restoring the toggle must re-enable the gate");
        helper.succeed();
    }
}
