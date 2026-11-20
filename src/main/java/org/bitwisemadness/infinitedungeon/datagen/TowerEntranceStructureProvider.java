package org.bitwisemadness.infinitedungeon.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 * The tower-entrance building (issue #199, Piece 1): the fixed, predefined
 * structure the register's global entrance rules bind - minimal terrain
 * alteration, no exterior windows - authored as a datagen {@code StructureTemplate}
 * ({@code data/infinite_dungeon/structure/tower_entrance.nbt}) plus its start
 * template pool, so the village injection (Piece 2) and the runtime code
 * feature both read the same committed content. The portal shell is the
 * obsidian frame inside; the portal treatment rides the API entrance policy
 * (api#58, Piece 3).
 */
final class TowerEntranceStructureProvider implements DataProvider {

    /** The building's fixed dimensions: 7 wide, 8 tall, 7 deep. */
    static final int WIDTH = 7;
    static final int HEIGHT = 8;
    static final int DEPTH = 7;

    private static final ResourceLocation STRUCTURE_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "tower_entrance");
    private static final ResourceLocation START_POOL_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "tower_entrance/start");

    private static final String STONE_BRICKS = "minecraft:stone_bricks";
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String JIGSAW = "minecraft:jigsaw";

    /** The current data version (1.21.1), stamped so the loader's DFU skips no-ops. */
    private static final int DATA_VERSION = 4189;

    private final PackOutput.PathProvider structures;
    private final PackOutput.PathProvider pools;

    TowerEntranceStructureProvider(PackOutput output) {
        this.structures = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
        this.pools = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/template_pool");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, Integer> palette = new LinkedHashMap<>();
        List<int[]> positions = new ArrayList<>();
        List<Integer> stateIndices = new ArrayList<>();
        List<CompoundTag> blockNbt = new ArrayList<>();

        // Floor and walls in one pass; the doorway and the portal center are
        // absent (air), so the template only stores placed blocks.
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    String state = stateAt(x, y, z);
                    if (state == null) {
                        continue;
                    }
                    int index = palette.computeIfAbsent(state, key -> palette.size());
                    positions.add(new int[] {x, y, z});
                    stateIndices.add(index);
                    blockNbt.add(state.equals(JIGSAW) ? jigsawNbt() : null);
                }
            }
        }

        ListTag paletteTag = new ListTag();
        palette.forEach((name, index) -> {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            paletteTag.add(state);
        });
        ListTag blocksTag = new ListTag();
        for (int i = 0; i < positions.size(); i++) {
            CompoundTag block = new CompoundTag();
            // 1.21.1 stores pos and size as integer LISTS (newIntegerList), not
            // int arrays - the loader reads them via getList(type 3).
            block.put("pos", intList(positions.get(i)));
            block.putInt("state", stateIndices.get(i));
            CompoundTag nbt = blockNbt.get(i);
            if (nbt != null) {
                block.put("nbt", nbt);
            }
            blocksTag.add(block);
        }

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", DATA_VERSION);
        root.put("size", intList(WIDTH, HEIGHT, DEPTH));
        root.put("palette", paletteTag);
        root.put("blocks", blocksTag);
        root.put("entities", new ListTag());

        JsonObject pool = new JsonObject();
        pool.addProperty("name", START_POOL_ID.toString());
        pool.addProperty("fallback", "minecraft:empty");
        JsonArray elements = new JsonArray();
        JsonObject element = new JsonObject();
        element.addProperty("weight", 1);
        JsonObject inner = new JsonObject();
        inner.addProperty("element_type", "minecraft:single_pool_element");
        inner.addProperty("location", STRUCTURE_ID.toString());
        inner.addProperty("projection", "rigid");
        inner.addProperty("processors", "minecraft:empty");
        element.add("element", inner);
        elements.add(element);
        pool.add("elements", elements);

        // Raw compressed bytes through CachedOutput (tracked, never cleaned) - the
// CompoundTag.CODEC path re-encodes integer lists as arrays the loader
// cannot read.
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            byte[] bytes = out.toByteArray();
            java.nio.file.Path nbtPath = this.structures.file(STRUCTURE_ID, "nbt");
            cache.writeIfNeeded(nbtPath, bytes, com.google.common.hash.Hashing.sha1().hashBytes(bytes));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return DataProvider.saveStable(cache, pool, this.pools.json(START_POOL_ID));
    }

    /**
     * The block state name at a local cell, or null for an open cell. The
     * building: a hollow stone-brick tower with a front doorway, the obsidian
     * portal shell on the back interior wall, and the jigsaw connection block
     * on the door threshold. No exterior windows (register rule).
     */
    private static String stateAt(int x, int y, int z) {
        if (x == 3 && z == DEPTH - 1 && y == 0) {
            return JIGSAW; // the door threshold connection
        }
        boolean roof = y == HEIGHT - 1;
        boolean floor = y == 0;
        boolean frontDoor = z == DEPTH - 1 && x >= 2 && x <= 4 && y <= 2;
        if (floor || roof) {
            return frontDoor ? null : STONE_BRICKS;
        }
        boolean perimeter = x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
        if (perimeter) {
            return frontDoor ? null : STONE_BRICKS;
        }
        // The interior: the obsidian portal shell against the back wall.
        if (z == 1 && x >= 2 && x <= 4 && y >= 1 && y <= 4) {
            boolean bottom = y == 1;
            boolean top = y == 4;
            boolean side = x == 2 || x == 4;
            return bottom || top || side ? OBSIDIAN : null; // the center stays open
        }
        return null;
    }

    /** A coordinate list in the 1.21.1 template format: a ListTag of IntTags. */
    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        return list;
    }

    /** The jigsaw block entity: the start connection to the tower pool. */
    private static CompoundTag jigsawNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "infinite_dungeon:tower_entrance");
        tag.putString("target", START_POOL_ID.toString());
        tag.putString("pool", "minecraft:empty");
        tag.putString("final_state", "minecraft:air");
        tag.putString("joint", "rollable");
        return tag;
    }

    @Override
    public String getName() {
        return "Tower-entrance structure";
    }
}
