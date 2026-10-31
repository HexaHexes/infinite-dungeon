package org.bitwisemadness.infinitedungeon.spawn;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.lanes.BossRosterBootstrap;
import org.bitwisemadness.infinitedungeon.lanes.GuardianSchedule;
import org.bitwisemadness.infinitedungeon.treasure.KeyItems;
import org.bitwisemadness.infinitedungeonapi.event.FloorEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.ProfilePools;
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

    /**
     * The boss roster bootstrap end to end (issue #60): against the REAL
     * listener, the shipped boss.json bytes, and the public derive seam - the
     * shipped pool parses with its exact jar-verified entry count and, because
     * every entry is mod-gated while the harness has no boss mods, derives an
     * EMPTY roster (the interim open-gate contract on production data); a test
     * pool with a vanilla zombie derives a single candidate whose EP is the
     * documented fallback metric (positive on a real mob), proving the
     * transient-entity read path. Self-contained: the pool table is reloaded
     * from the shipped bytes inside the test, so a sibling finally that reset
     * the datapack cannot shape the verdict; `finally` restores roster and
     * table.
     */
    @GameTest(template = "empty")
    public static void boss_roster_bootstrap_reads_the_shipped_pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var listener = new SpawnPoolReloadListener();
        GuardianSchedule.setRoster(List.of());
        try {
            // The shipped boss pool: exact verified entry count, all gated.
            String path = "/data/infinite_dungeon/infinite_dungeon/pools/boss.json";
            var stream = SpawnPoolReloadListener.class.getResourceAsStream(path);
            helper.assertTrue(stream != null, "the shipped boss pool must exist: " + path);
            ResourceLocation shipped = ResourceLocation.fromNamespaceAndPath(
                    InfiniteDungeon.MOD_ID, "infinite_dungeon/pools/boss");
            List<SpawnPoolEntry> parsedShipped = listener.parse(shipped,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            helper.assertTrue(parsedShipped.size() == 59,
                    "the shipped boss pool must carry its jar-verified entries: got "
                            + parsedShipped.size() + ", want 59");
            listener.applyEntries(Map.of(shipped, parsedShipped));
            helper.assertTrue(SpawnPools.allIds(InfiniteDungeon.MOD_ID + ":boss").isEmpty(),
                    "every shipped boss entry is mod-gated; without the mods the roster must "
                            + "stay empty (interim contract): got "
                            + SpawnPools.allIds(InfiniteDungeon.MOD_ID + ":boss"));
            helper.assertTrue(BossRosterBootstrap.derive(
                            level, InfiniteDungeon.MOD_ID + ":boss").isEmpty(),
                    "an all-gated pool must derive an empty roster");

            // A test pool with a present entity: one candidate, positive EP.
            ResourceLocation testPool = ResourceLocation.fromNamespaceAndPath(
                    InfiniteDungeon.MOD_ID, "infinite_dungeon/pools/roster_smoke");
            List<SpawnPoolEntry> testEntries = listener.parse(testPool, JsonParser.parseString(
                    "{\"entries\": [{\"entity\": \"minecraft:zombie\", \"weight\": 1}]}"));
            listener.applyEntries(Map.of(testPool, testEntries));
            var derived = BossRosterBootstrap.derive(
                    level, InfiniteDungeon.MOD_ID + ":roster_smoke");
            helper.assertTrue(derived.size() == 1,
                    "the test pool must derive exactly one candidate: got " + derived);
            var candidate = derived.get(0);
            helper.assertTrue(candidate.entityId().equals("minecraft:zombie"),
                    "the candidate must be the pool's entity: got " + candidate.entityId());
            helper.assertTrue(candidate.rawPower() > 0.0,
                    "the fallback EP metric must be positive on a real mob: got "
                            + candidate.rawPower());
            helper.assertTrue(!candidate.depthExempt() && candidate.family().isEmpty(),
                    "defaults are plain candidates; family ranking is the tensura module's job");
            helper.succeed();
        } finally {
            GuardianSchedule.setRoster(List.of());
            SpawnPools.setDatapack(Map.of());
        }
    }

    /**
     * The miniboss raid end to end (issue #41): against the real listener,
     * pool registry, SavedData, and drop - an assigned floor fields the
     * elite-pool miniboss beside the entrant with the stat multiplier stacked
     * last (fresh join: #37 declines in the harness, so the multiplier is
     * exactly observable), the singleton holds, its death stamps the floor and
     * drops the key, a re-entry stays quiet, and an unassigned floor never
     * triggers. Production trigger = the depth tracker (#238); the event is
     * posted here exactly as #42's was pre-#43.
     */
    @GameTest(template = "empty")
    public static void miniboss_floors_spawn_slain_and_drop_the_key(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String harnessDimension = level.dimension().location().toString();

        var poolListener = new SpawnPoolReloadListener();
        apply(poolListener, ResourceLocation.fromNamespaceAndPath(
                        InfiniteDungeon.MOD_ID, "infinite_dungeon/pools/elite_smoke"),
                """
                {"entries": [{"entity": "minecraft:zombie", "weight": 1}]}""");
        DimensionProfiles.register(new DimensionProfile(
                harnessDimension,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ProfilePools(
                        Optional.empty(), Optional.of(InfiniteDungeon.MOD_ID + ":elite_smoke"), Optional.empty())),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        MinibossSlain slay = MinibossSlain.of(level);
        slay.forget(3, 3); // harness world data survives runs: force the premise
        var player = helper.makeMockServerPlayerInLevel();
        // PathfinderMob.checkSpawnRules is a WALK-VALUE rule: monsters refuse
        // bright ground, and the harness platform is open sky (the guardian
        // test passes because its interior is deep and dark). Give the entrant
        // a dark pocket - walls, roof, air inside - exactly the harness
        // manipulation the block-clearing precedent uses; the raid then finds
        // shade within its first candidate ring, as it would in a real room.
        int px = (int) Math.floor(player.getX());
        int py = (int) Math.floor(player.getY());
        int pz = (int) Math.floor(player.getZ());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(new BlockPos(px + dx, py + 3, pz + dz),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int edge = -3; edge <= 3; edge++) {
            level.setBlock(new BlockPos(px + edge, py, pz - 3), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(px + edge, py, pz + 3), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(px - 3, py, pz + edge), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(px + 3, py, pz + edge), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(new BlockPos(px + dx, py + dy, pz + dz),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        player.setPos(px + 0.5, py, pz + 0.5);
        try {
            // The listener's thin resolution path runs for an unassigned floor
            // (best-effort glue; the seam below carries the observable logic).
            NeoForge.EVENT_BUS.post(new FloorEnteredEvent(
                    level.dimension(), player.getUUID(), 3, 1));
            helper.assertTrue(Minibosses.onApproach(level, player, 3, 1)
                            == Minibosses.Outcome.NOT_ASSIGNED,
                    "an unassigned floor must never field a miniboss; got "
                            + Minibosses.onApproach(level, player, 3, 1));

            var first = Minibosses.onApproach(level, player, 3, 3);
            helper.assertTrue(first == Minibosses.Outcome.SPAWNED,
                    "the assigned floor must field the elite-pool miniboss beside the entrant; got "
                            + first);
            var miniboss = findMiniboss(level, 3, 3);
            helper.assertTrue(miniboss != null,
                    "the spawn payload must be findable beside the entrant");
            helper.assertTrue(miniboss.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 40.0,
                    "the miniboss multiplier must stack last (20 base * 2.0): got "
                            + miniboss.getAttribute(Attributes.MAX_HEALTH).getBaseValue());

            helper.assertTrue(Minibosses.onApproach(level, player, 3, 3)
                            == Minibosses.Outcome.ALIVE,
                    "the floor must stay a singleton on re-entry; got "
                            + Minibosses.onApproach(level, player, 3, 3));

            NeoForge.EVENT_BUS.post(new LivingDeathEvent(miniboss, level.damageSources().generic()));
            miniboss.discard(); // the real death removes the body too
            helper.assertTrue(slay.isSlain(3, 3),
                    "the death must stamp the floor as slain");
            helper.assertTrue(hasKeyDrop(level),
                    "the death must drop the dungeon key");

            helper.assertTrue(Minibosses.onApproach(level, player, 3, 3)
                            == Minibosses.Outcome.SLAIN,
                    "a slain floor must stay quiet on re-entry; got "
                            + Minibosses.onApproach(level, player, 3, 3));
            helper.succeed();
        } finally {
            var survivor = findMiniboss(level, 3, 3);
            if (survivor != null) {
                survivor.discard();
            }
            dropKeyCleanup(level);
            slay.forget(3, 3);
            SpawnPools.setDatapack(Map.of());
            DimensionProfiles.remove(harnessDimension);
        }
    }
    /**
     * The SHIPPED roster defaults parse and stay gated (issue #61): reads the
     * real resources off the classpath through the real listener - exact
     * entry counts pin the shipped content - and, because every entry carries
     * a verified required_mod while the harness has no such mods, the pools
     * answer EMPTY candidates at every layer: the "data for optional content"
     * contract the schema exists for, proven on production bytes.
     */
    @GameTest(template = "empty")
    public static void shipped_roster_pools_parse_and_stay_gated(GameTestHelper helper) {
        var listener = new SpawnPoolReloadListener();
        try {
            int expectedFodder = 15;
            int expectedElite = 10;
            for (Object[] pool : new Object[][] {
                    {"fodder", expectedFodder}, {"elite", expectedElite}}) {
                String name = (String) pool[0];
                int expected = (Integer) pool[1];
                String path = "/data/infinite_dungeon/infinite_dungeon/pools/" + name + ".json";
                var stream = SpawnPoolReloadListener.class.getResourceAsStream(path);
                helper.assertTrue(stream != null, "the shipped " + name + " pool must exist: " + path);
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        InfiniteDungeon.MOD_ID, "infinite_dungeon/pools/" + name);
                List<SpawnPoolEntry> parsed = listener.parse(location, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                helper.assertTrue(parsed.size() == expected,
                        "the shipped " + name + " pool must carry its verified entries: got "
                                + parsed.size() + ", want " + expected);
                listener.applyEntries(Map.of(location, parsed));
                String poolId = InfiniteDungeon.MOD_ID + ":" + name;
                helper.assertTrue(SpawnPools.exists(poolId),
                        "the pool id must be the location minus the data directory");
                for (int layer : new int[] {0, 5, 10}) {
                    helper.assertTrue(SpawnPools.candidates(poolId, layer).isEmpty(),
                            name + " must stay gated without its mods (layer " + layer
                                    + "): got " + SpawnPools.candidates(poolId, layer));
                }
            }
        } finally {
            SpawnPools.setDatapack(Map.of());
        }
        helper.succeed();
    }
    /**
     * The shipped default profile resolves WITHOUT test registration (issue
     * #241): this is the regression the issue exists to prevent - no profile
     * meant every profile-gated Wave-4 feature read absent and stayed silent
     * in a fresh pack. The pool NAMES are asserted from the static JSON; pool
     * RUNTIME state is deliberately not (other tests' finallys reset the
     * datapack, and this profile is production data, not test fixture).
     */
    @GameTest(template = "empty")
    public static void default_profile_ships_and_resolves(GameTestHelper helper) {
        String dungeon = "infinite_dungeon:dungeon";
        var profile = DimensionProfiles.get(dungeon);
        helper.assertTrue(profile.isPresent(),
                "the shipped default profile must resolve with no test-side register");
        helper.assertTrue(DimensionProfiles.isDungeonDimension(dungeon),
                "the shipped profile must make the dungeon dimension known");
        var pools = profile.get().pools();
        helper.assertTrue(pools.isPresent()
                        && pools.get().fodder().equals(Optional.of("infinite_dungeon:fodder"))
                        && pools.get().elite().equals(Optional.of("infinite_dungeon:elite"))
                        && pools.get().boss().equals(Optional.of("infinite_dungeon:boss")),
                "the profile must name the three roster pools: " + pools);
        var announcements = profile.get().announcements();
        helper.assertTrue(announcements.isPresent()
                        && announcements.get().entry().equals("dungeon.entry")
                        && announcements.get().layer().equals("dungeon.layer"),
                "the profile must name the shipped announcement keys: " + announcements);
        helper.succeed();
    }
    /** The live tagged miniboss of a floor, or null (visible sections only). */
    private static Mob findMiniboss(ServerLevel level, int layer, int floor) {
        String tag = "infinite_dungeon_miniboss_layer_" + layer + "_" + floor;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.getTags().contains(tag)) {
                return mob;
            }
        }
        return null;
    }

    private static int countMinibosses(ServerLevel level, int layer, int floor) {
        return findMiniboss(level, layer, floor) == null ? 0 : 1;
    }

    private static boolean hasKeyDrop(ServerLevel level) {
        boolean found = false;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item
                    && item.getItem().is(KeyItems.KEY.get())) {
                found = true;
            }
        }
        return found;
    }

    private static void dropKeyCleanup(ServerLevel level) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item
                    && item.getItem().is(KeyItems.KEY.get())) {
                entity.discard();
            }
        }
    }
}
