package org.bitwisemadness.infinitedungeon.spawn;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * Spawn pool filtering and reload (issue #39; plan section: "spawn pool
 * filtering" and "pool JSON reload filtering" are gametest items). Same
 * package as the pool classes, so the tests drive the REAL listener steps -
 * parse plus applyEntries - exactly as the base listener invokes them; only
 * the base's sorted-file orchestration glue sits outside the harness's reach.
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpawnPoolGameTests {

    private static final ResourceLocation FILTER_FILE = ResourceLocation.fromNamespaceAndPath(
            InfiniteDungeon.MOD_ID, "infinite_dungeon/pools/filter_smoke");
    private static final String FILTER_POOL = InfiniteDungeon.MOD_ID + ":filter_smoke";
    private static final ResourceLocation RELOAD_FILE = ResourceLocation.fromNamespaceAndPath(
            InfiniteDungeon.MOD_ID, "infinite_dungeon/pools/reload_smoke");
    private static final String RELOAD_POOL = InfiniteDungeon.MOD_ID + ":reload_smoke";

    private SpawnPoolGameTests() {
    }

    private static void apply(SpawnPoolReloadListener listener, ResourceLocation location, String json) {
        List<SpawnPoolEntry> parsed = listener.parse(location, JsonParser.parseString(json));
        listener.applyEntries(Map.of(location, parsed));
    }

    /**
     * The filter pipeline end-to-end: layer band, required-mod, and reference
     * expansion over a real tag - absent registry ids and absent mods must
     * never surface (the issue's silent-skip contract), and the tag entry
     * must contribute one candidate per member with its weight.
     */
    @GameTest(template = "empty")
    public static void spawn_pool_filtering(GameTestHelper helper) {
        var listener = new SpawnPoolReloadListener();
        apply(listener, FILTER_FILE, """
                {"entries": [
                  {"entity": "minecraft:zombie", "weight": 2},
                  {"entity": "#infinite_dungeon:pool_smoke", "weight": 3, "minLayer": 2, "maxLayer": 4},
                  {"entity": "minecraft:not_a_real_entity", "weight": 9},
                  {"entity": "minecraft:arrow", "weight": 9, "required_mod": "no_such_mod_here"}
                ]}""");

        helper.assertTrue(SpawnPools.exists(FILTER_POOL),
                "the pool id must be the file location minus the data directory");

        List<PoolCandidate> layer0 = SpawnPools.candidates(FILTER_POOL, 0);
        helper.assertTrue(layer0.size() == 1 && layer0.get(0).entityId().equals("minecraft:zombie"),
                "layer 0 serves only the un-banded zombie (band excludes the tag entry, "
                        + "absent entity and absent mod dropped): got " + layer0);

        List<PoolCandidate> layer3 = SpawnPools.candidates(FILTER_POOL, 3);
        helper.assertTrue(layer3.size() == 3,
                "layer 3 must hold zombie(w2) plus the tag's members zombie(w3)+creeper(w3): got " + layer3);
        helper.assertTrue(layer3.stream().noneMatch(c -> c.entityId().equals("minecraft:arrow")),
                "an entry for an absent mod must never surface");
        helper.assertTrue(layer3.stream().anyMatch(c -> c.entityId().equals("minecraft:creeper")),
                "the pack tag must expand to its members");

        helper.assertTrue(SpawnPools.candidates(InfiniteDungeon.MOD_ID + ":never_declared", 0).isEmpty(),
                "an unknown pool is an empty answer, never an error");
        helper.succeed();
    }

    /**
     * Reload semantics: the table is rebuilt from scratch each load, so a
     * reload that drops a file drops what it declared; a reload that brings
     * the file back with a malformed entry keeps the good half (the base
     * skips malformed files, this level skips malformed entries).
     */
    @GameTest(template = "empty")
    public static void spawn_pool_reload_filtering(GameTestHelper helper) {
        var listener = new SpawnPoolReloadListener();
        apply(listener, RELOAD_FILE, """
                {"entries": [{"entity": "minecraft:zombie"}]}""");
        helper.assertTrue(!SpawnPools.candidates(RELOAD_POOL, 0).isEmpty(),
                "the first load must serve the pool");

        // What the base hands applyEntries when a /reload finds no files.
        listener.applyEntries(Map.of());
        helper.assertTrue(!SpawnPools.exists(RELOAD_POOL),
                "a reload that drops the file must drop what it declared");
        helper.assertTrue(SpawnPools.candidates(RELOAD_POOL, 0).isEmpty(),
                "the dropped pool must answer empty");

        apply(listener, RELOAD_FILE, """
                {"entries": [
                  {"entity": "!!not a reference!!"},
                  {"entity": "minecraft:creeper", "weight": 5}
                ]}""");
        List<PoolCandidate> revived = SpawnPools.candidates(RELOAD_POOL, 0);
        helper.assertTrue(revived.size() == 1 && revived.get(0).entityId().equals("minecraft:creeper"),
                "the malformed entry must drop while the valid half survives: got " + revived);
        helper.succeed();
    }

    /**
     * The ambient driver's decline path (issue #40): outside a dungeon
     * dimension the cycle answers zero before any other check - the harness
     * is the overworld, so a tick through the driver must spawn nothing. The
     * positive path cannot run here (the harness never hosts the dungeon
     * dimension - the #37 finding), so the count/phase arithmetic is JUnit
     * and the in-dimension spawn route is pack-verified at wiring.
     */
    @GameTest(template = "empty")
    public static void ambient_spawner_declines_outside_the_dungeon(GameTestHelper helper) {
        int spawned = AmbientSpawner.tick(helper.getLevel());
        helper.assertTrue(spawned == 0,
                "outside the dungeon the driver must spawn nothing, spawned " + spawned);
        helper.succeed();
    }
}
