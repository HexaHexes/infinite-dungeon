package org.bitwisemadness.infinitedungeon.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;

/**
 * Smoke test: proves the mod loads and the GameTest harness runs.
 *
 * <p>Keep the {@code expectedTests} count in {@code build.gradle} in sync with
 * the number of {@code @GameTest} methods here, or the harness fails the
 * run.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfiniteDungeonGameTests {

    private InfiniteDungeonGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * The dungeon dimension's type loads from the mod datapack with the values
     * the plan locks in: a fixed time, no ceiling, and the min_y/height pair
     * that must never change (changing them later is destructive to existing
     * worlds).
     *
     * <p>The level itself cannot be asserted here: the GameTest harness
     * hardcodes the flat world preset and bakes its level stems from that
     * preset, so datapack dimensions never become levels in this harness. The
     * dimension loads on real servers and clients; that part is proven by the
     * user's in-game check.</p>
     */
    @GameTest(template = "empty")
    public static void dungeon_dimension_type_loads(GameTestHelper helper) {
        DimensionType type = helper.getLevel().getServer().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                .get(DungeonDimensions.TYPE);
        helper.assertTrue(type != null,
                "the dungeon dimension_type must load from the mod datapack");
        helper.assertTrue(type.fixedTime().isPresent(), "the dungeon must have a fixed time");
        helper.assertTrue(!type.hasCeiling(), "the dungeon must be open: no ceiling");
        helper.assertTrue(type.hasSkyLight(), "the dungeon must be open: skylight on");
        helper.assertTrue(type.minY() == -64, "min_y is locked at -64 by the plan");
        helper.assertTrue(type.height() == 2048, "height is locked at 2048 by the plan");
        helper.succeed();
    }
}
