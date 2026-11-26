package org.bitwisemadness.infinitedungeon.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The fixed-construction lobby building (issue #131): the five lobby floors as
 * one datagen {@code StructureTemplate}
 * ({@code data/infinite_dungeon/structure/lobby_entrance.nbt}) so the
 * room-content issues (#138 reception, #139-#140 shops, #141 waystone room)
 * and the EasyNPC slot table all read the same committed geometry. The floor
 * roles are the {@code LobbyFloors} contract, top-anchored: floor 0 the guild
 * reception (counter, task board, decorative lodging), floor 4 the waystone
 * room (the clear pedestal the layer-entrance waystone stands on).
 *
 * <p>Fixed construction per the register: stone-brick shell, no exterior
 * windows, a straight staircase through the floors, and the beds are
 * decoration only (the no-spawn-point invariant is enforced elsewhere).</p>
 */
final class LobbyBuildingStructureProvider implements DataProvider {

    /** The building's fixed dimensions: 7 wide, 21 tall (5 floors), 7 deep. */
    static final int WIDTH = 7;
    static final int HEIGHT = 21;
    static final int DEPTH = 7;

    /** One floor's height inside the shell. */
    static final int FLOOR_HEIGHT = 4;

    /** The lobby floor count, pinned to the LobbyFloors contract. */
    static final int FLOORS = 5;

    /** The reception counter's local cells (floor 0). */
    static final int[] COUNTER = {2, 3, 4};
    /** The task board's local cell (floor 0). */
    static final int[] TASK_BOARD = {1, 2};
    /** The lodging beds' foot cells (floor 0). */
    static final int[][] BED_FOOTS = {{2, 5}, {3, 5}, {4, 5}, {5, 5}};
    /** The waystone pedestal's local cell (floor 4). */
    static final int[] WAYSTONE_PEDESTAL = {3, 3};

    private static final ResourceLocation STRUCTURE_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "lobby_entrance");

    private static final String STONE_BRICKS = "minecraft:stone_bricks";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String BARREL = "minecraft:barrel";
    private static final String PEDESTAL = "minecraft:polished_andesite";

    private static final int DATA_VERSION = 4189;

    private final PackOutput.PathProvider structures;

    LobbyBuildingStructureProvider(PackOutput output) {
        this.structures = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, Integer> palette = new LinkedHashMap<>();
        List<int[]> positions = new ArrayList<>();
        List<Integer> stateIndices = new ArrayList<>();

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    State state = stateAt(x, y, z);
                    if (state == null) {
                        continue;
                    }
                    String key = state.paletteKey();
                    int index = palette.computeIfAbsent(key, k -> palette.size());
                    positions.add(new int[] {x, y, z});
                    stateIndices.add(index);
                }
            }
        }

        ListTag paletteTag = new ListTag();
        palette.forEach((key, index) -> paletteTag.add(State.fromKey(key).tag()));
        ListTag blocksTag = new ListTag();
        for (int i = 0; i < positions.size(); i++) {
            CompoundTag block = new CompoundTag();
            block.put("pos", intList(positions.get(i)));
            block.putInt("state", stateIndices.get(i));
            blocksTag.add(block);
        }

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", DATA_VERSION);
        root.put("size", intList(WIDTH, HEIGHT, DEPTH));
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", new ListTag());

        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            byte[] bytes = out.toByteArray();
            java.nio.file.Path nbtPath = this.structures.file(STRUCTURE_ID, "nbt");
            cache.writeIfNeeded(nbtPath, bytes,
                    com.google.common.hash.Hashing.sha256().hashBytes(bytes));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * The block state at a local cell, or null for open air. The shell:
     * stone-brick perimeter and floor slabs, a front doorway, a straight
     * staircase along the back, the reception content on floor 0 (counter,
     * task board, decorative beds), and the waystone pedestal on floor 4.
     */
    private static State stateAt(int x, int y, int z) {
        int floor = y / FLOOR_HEIGHT;
        int inFloor = y % FLOOR_HEIGHT;
        if (floor >= FLOORS) {
            return y == HEIGHT - 1 ? State.of(STONE_BRICKS) : null; // the roof slab
        }
        boolean slab = inFloor == 0;
        boolean frontDoor = z == DEPTH - 1 && x >= 2 && x <= 4 && y <= 1;
        boolean perimeter = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
        if (perimeter) {
            return frontDoor ? null : State.of(STONE_BRICKS);
        }
        // The waystone pedestal replaces the floor-4 slab (issue #141).
        if (floor == FLOORS - 1 && x == WAYSTONE_PEDESTAL[0]
                && z == WAYSTONE_PEDESTAL[1] && inFloor == 0) {
            return State.of(PEDESTAL);
        }
        if (slab) {
            return State.of(STONE_BRICKS);
        }
        // The straight staircase along the back (z=1), one step per cell
        // ascending along +z toward the next floor's slab.
        if (x == 1 && z == inFloor && inFloor >= 1) {
            return State.stairs();
        }
        if (floor == 0) {
            return floor0Content(x, inFloor, z);
        }
        if (floor == FLOORS - 1) {
            return floor4Content(x, inFloor, z);
        }
        return null; // the shop floors (issues #139-#140) are empty shells here
    }

    private static State floor0Content(int x, int inFloor, int z) {
        if (inFloor == 1) {
            if (z == 2 && x >= COUNTER[0] && x <= COUNTER[COUNTER.length - 1]) {
                return State.of(OAK_PLANKS); // the reception counter
            }
            if (x == TASK_BOARD[0] && z == TASK_BOARD[1]) {
                return State.of(BARREL); // the task board
            }
            for (int[] bed : BED_FOOTS) {
                if (x == bed[0] && z == bed[1]) {
                    return State.bedFoot();
                }
                if (x == bed[0] && z == bed[1] - 1) {
                    return State.bedHead();
                }
            }
        }
        return null;
    }

    private static State floor4Content(int x, int inFloor, int z) {
        if (x == WAYSTONE_PEDESTAL[0] && z == WAYSTONE_PEDESTAL[1]) {
            if (inFloor == 0) {
                return State.of(PEDESTAL); // the waystone pedestal
            }
            return null; // the clear column the waystone stands in
        }
        return null;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        return list;
    }

    /** One template block state: a name plus optional block properties. */
    private static final class State {

        private final String name;
        private final Map<String, String> properties;

        private State(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = properties;
        }

        static State of(String name) {
            return new State(name, Map.of());
        }

        static State stairs() {
            return new State("minecraft:stone_brick_stairs",
                    Map.of("facing", "north", "half", "bottom"));
        }

        static State bedHead() {
            return new State("minecraft:red_bed",
                    Map.of("facing", "north", "part", "head"));
        }

        static State bedFoot() {
            return new State("minecraft:red_bed",
                    Map.of("facing", "north", "part", "foot"));
        }

        String paletteKey() {
            return properties.isEmpty() ? name : name + properties;
        }

        static State fromKey(String key) {
            if (key.equals(stairs().paletteKey())) {
                return stairs();
            }
            if (key.equals(bedHead().paletteKey())) {
                return bedHead();
            }
            if (key.equals(bedFoot().paletteKey())) {
                return bedFoot();
            }
            return State.of(key);
        }

        CompoundTag tag() {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            if (!properties.isEmpty()) {
                CompoundTag props = new CompoundTag();
                properties.forEach(props::putString);
                state.put("Properties", props);
            }
            return state;
        }
    }

    @Override
    public String getName() {
        return "Lobby-entrance building";
    }
}
