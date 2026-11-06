package org.bitwisemadness.infinitedungeon.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * Emits the slot-to-table mappings (issue #256):
 * {@code infinite_dungeon/loot_slots/default.json}. Ships the dungeon theme's
 * mapping plus one alternate theme, so the theme axis is exercised. Datagen-only.
 */
final class DungeonLootSlotsProvider implements DataProvider {

    /** The demonstration alternate theme. */
    private static final String EXAMPLE_THEME = InfiniteDungeon.MOD_ID + ":example_theme";
    private static final String DUNGEON_THEME = InfiniteDungeon.MOD_ID + ":dungeon";

    private final PackOutput.PathProvider slots;

    DungeonLootSlotsProvider(PackOutput output) {
        this.slots = output.createPathProvider(PackOutput.Target.DATA_PACK,
                "infinite_dungeon/loot_slots");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonArray mappings = new JsonArray();
        mappings.add(mapping("common", DUNGEON_THEME, 1, 3, InfiniteDungeon.MOD_ID + ":loot/common"));
        mappings.add(mapping("treasure", DUNGEON_THEME, 4, 99,
                InfiniteDungeon.MOD_ID + ":loot/treasure"));
        mappings.add(mapping("common", EXAMPLE_THEME, 1, 3,
                InfiniteDungeon.MOD_ID + ":loot/treasure"));
        JsonObject root = new JsonObject();
        root.add("mappings", mappings);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                InfiniteDungeon.MOD_ID, "default");
        return DataProvider.saveStable(cache, root, this.slots.json(id));
    }

    private static JsonObject mapping(String slotPath, String theme, int minLayer, int maxLayer,
            String table) {
        JsonObject mapping = new JsonObject();
        mapping.addProperty("slot", InfiniteDungeon.MOD_ID + ":" + slotPath);
        mapping.addProperty("theme", theme);
        mapping.addProperty("min_layer", minLayer);
        mapping.addProperty("max_layer", maxLayer);
        mapping.addProperty("table", table);
        return mapping;
    }

    @Override
    public String getName() {
        return "Dungeon loot slots";
    }
}
