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
 * Emits the depth-injection defaults (issue #52):
 * {@code infinite_dungeon/loot_injections/common.json}. Datagen-only.
 */
final class DungeonLootInjectionsProvider implements DataProvider {

    private final PackOutput.PathProvider injections;

    DungeonLootInjectionsProvider(PackOutput output) {
        this.injections = output.createPathProvider(PackOutput.Target.DATA_PACK,
                "infinite_dungeon/loot_injections");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject early = new JsonObject();
        early.addProperty("table", InfiniteDungeon.MOD_ID + ":loot/common");
        early.addProperty("min_layer", 1);
        early.addProperty("max_layer", 3);
        early.addProperty("chance", 0.5D);
        JsonObject deep = new JsonObject();
        deep.addProperty("table", InfiniteDungeon.MOD_ID + ":loot/treasure");
        deep.addProperty("min_layer", 4);
        deep.addProperty("max_layer", 99);
        deep.addProperty("chance", 0.25D);
        JsonArray list = new JsonArray();
        list.add(early);
        list.add(deep);
        JsonObject root = new JsonObject();
        root.add("injections", list);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                InfiniteDungeon.MOD_ID, "common");
        return DataProvider.saveStable(cache, root, this.injections.json(id));
    }

    @Override
    public String getName() {
        return "Dungeon loot injections";
    }
}
