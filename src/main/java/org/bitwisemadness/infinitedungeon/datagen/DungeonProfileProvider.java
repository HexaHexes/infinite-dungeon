package org.bitwisemadness.infinitedungeon.datagen;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * Emits the dungeon's dimension profile (issue #130):
 * {@code infinite_dungeon/dimension_profile/dungeon.json}, the theme entry the
 * API's {@code DimensionProfileReloadListener} reads. Datagen-only.
 */
final class DungeonProfileProvider implements DataProvider {

    private final PackOutput.PathProvider profiles;

    DungeonProfileProvider(PackOutput output) {
        this.profiles = output.createPathProvider(PackOutput.Target.DATA_PACK,
                "infinite_dungeon/dimension_profile");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject pools = new JsonObject();
        pools.addProperty("fodder", InfiniteDungeon.MOD_ID + ":fodder");
        pools.addProperty("elite", InfiniteDungeon.MOD_ID + ":elite");
        pools.addProperty("boss", InfiniteDungeon.MOD_ID + ":boss");
        JsonObject announcements = new JsonObject();
        announcements.addProperty("entry", "dungeon.entry");
        announcements.addProperty("layer", "dungeon.layer");
        JsonObject profile = new JsonObject();
        profile.addProperty("dimension", InfiniteDungeon.MOD_ID + ":dungeon");
        profile.add("pools", pools);
        profile.add("announcements", announcements);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                InfiniteDungeon.MOD_ID, "dungeon");
        return DataProvider.saveStable(cache, profile, this.profiles.json(id));
    }

    @Override
    public String getName() {
        return "Dungeon dimension profile";
    }
}
