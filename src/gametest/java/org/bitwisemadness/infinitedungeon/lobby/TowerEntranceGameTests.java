package org.bitwisemadness.infinitedungeon.lobby;

import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;

/**
 * The tower-entrance building (issue #199): the datagen-authored template
 * loads and is structurally valid, the start pool references it, the feature
 * places it behind the API's entrance-policy gate, the portal treatment is
 * active at generation (Piece 3), the toggles gate it, and the village
 * injection ships it as a plains house.
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
        helper.assertTrue(template.filterBlocks(BlockPos.ZERO, settings, PortalBlocks.DUNGEON_PORTAL.get())
                        .isEmpty(),
                "the template must ship the shell - the active portal is the feature's post-step (Piece 3)");
        helper.succeed();
    }

    /** The feature places the building at a suitable origin, portal active. */
    @GameTest(template = "empty")
    public static void tower_entrance_feature_places_the_building(GameTestHelper helper) {
        boolean wasEnabled = DungeonConfigs.towerEntranceEnabled();
        boolean wasPortal = DungeonConfigs.towerEntrancePortal();
        DungeonConfigs.setTowerEntranceEnabled(true);
        DungeonConfigs.setTowerEntrancePortal(true);
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(4, 5, 4));
            paveSite(helper);
            helper.assertTrue(TowerEntranceFeature.build(helper.getLevel(), origin,
                    helper.getLevel().getRandom()), "the feature must place the building");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 0, 6)).is(Blocks.JIGSAW),
                    "the jigsaw connection must sit on the door threshold");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(0, 0, 0)).is(Blocks.STONE_BRICKS),
                    "the tower's stone-brick walls must be placed");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 1, 1)).is(Blocks.OBSIDIAN),
                    "the portal shell's bottom must be obsidian");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 2, 1))
                            .is(PortalBlocks.DUNGEON_PORTAL.get()),
                    "the portal interior must be active at generation, got "
                            + helper.getLevel().getBlockState(origin.offset(3, 2, 1)));
        } finally {
            DungeonConfigs.setTowerEntranceEnabled(wasEnabled);
            DungeonConfigs.setTowerEntrancePortal(wasPortal);
        }
        helper.succeed();
    }

    /** An unsuitable site (no solid ground) is never silently built on. */
    @GameTest(template = "empty")
    public static void tower_entrance_policy_skips_an_unsuitable_site(GameTestHelper helper) {
        boolean wasEnabled = DungeonConfigs.towerEntranceEnabled();
        DungeonConfigs.setTowerEntranceEnabled(true);
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(4, 5, 4));
            helper.assertTrue(!TowerEntranceFeature.build(helper.getLevel(), origin,
                            helper.getLevel().getRandom()),
                    "the API entrance policy must refuse an unsuitable site");
        } finally {
            DungeonConfigs.setTowerEntranceEnabled(wasEnabled);
        }
        helper.succeed();
    }

    /** The portal toggle off leaves the building with its obsidian shell only. */
    @GameTest(template = "empty")
    public static void tower_entrance_portal_toggle_leaves_the_shell(GameTestHelper helper) {
        boolean wasEnabled = DungeonConfigs.towerEntranceEnabled();
        boolean wasPortal = DungeonConfigs.towerEntrancePortal();
        DungeonConfigs.setTowerEntranceEnabled(true);
        DungeonConfigs.setTowerEntrancePortal(false);
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(4, 5, 4));
            paveSite(helper);
            helper.assertTrue(TowerEntranceFeature.build(helper.getLevel(), origin,
                    helper.getLevel().getRandom()), "the feature must place the building");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(3, 2, 1)).is(Blocks.AIR),
                    "the portal toggle off must leave the interior as the shell only");
        } finally {
            DungeonConfigs.setTowerEntranceEnabled(wasEnabled);
            DungeonConfigs.setTowerEntrancePortal(wasPortal);
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

    /** The village injection ships the tower as a plains house. */
    @GameTest(template = "empty")
    public static void tower_entrance_injected_into_the_plains_houses_pool(GameTestHelper helper) {
        var resource = helper.getLevel().getServer().getResourceManager().getResource(
                ResourceLocation.withDefaultNamespace(
                        "worldgen/template_pool/village/plains/houses.json"));
        helper.assertTrue(resource.isPresent(),
                "the houses pool override must ship");
        try (java.io.InputStream input = resource.get().open()) {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertTrue(json.contains("infinite_dungeon:tower_entrance"),
                    "the houses pool must carry the tower element: " + json);
            helper.assertTrue(json.contains("minecraft:village/plains/houses/plains_small_house_1"),
                    "the override must preserve the vanilla houses");
        } catch (Exception e) {
            throw new AssertionError("the houses pool override must parse", e);
        }
        helper.succeed();
    }

    /** Solid ground under the tower's full 7x7 footprint (the policy's site). */
    private static void paveSite(GameTestHelper helper) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                helper.setBlock(new BlockPos(4 + dx, 4, 4 + dz), Blocks.STONE.defaultBlockState());
            }
        }
    }
}
