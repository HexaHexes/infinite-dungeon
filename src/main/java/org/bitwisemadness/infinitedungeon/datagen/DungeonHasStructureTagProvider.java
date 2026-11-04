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
 * Emits the additive Dungeon Crawl structure gate (issue #130):
 * {@code data/dungeoncrawl/tags/worldgen/biome/has_structure/dungeon.json}. The
 * tag lives in another namespace, so a mod-namespaced {@code TagsProvider}
 * cannot write it - a small {@code saveStable} writer is the house pattern for
 * it. Datagen-only.
 */
final class DungeonHasStructureTagProvider implements DataProvider {

    /** The namespace the tag belongs to (the structure's owner). */
    private static final String DUNGEONCRAWL = "dungeoncrawl";

    private final PackOutput.PathProvider tags;

    DungeonHasStructureTagProvider(PackOutput output) {
        this.tags = output.createPathProvider(PackOutput.Target.DATA_PACK,
                "tags/worldgen/biome/has_structure");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonArray values = new JsonArray();
        values.add(InfiniteDungeon.MOD_ID + ":dungeon");
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DUNGEONCRAWL, "dungeon");
        return DataProvider.saveStable(cache, tag, this.tags.json(id));
    }

    @Override
    public String getName() {
        return "Dungeon Crawl has_structure tag";
    }
}
