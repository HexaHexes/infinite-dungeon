package org.bitwisemadness.infinitedungeon.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The tower-entrance village injection (issue #199, Piece 2): the datapack
 * override of the plains village houses pool - the vanilla elements verbatim,
 * plus the tower element. The vanilla houses are a flat list, so an override
 * must replicate them; this table is pinned to the 1.21.1 vanilla pool (a
 * deliberate, committed snapshot - the CI drift gate keeps the generated tree
 * current). The toggle gates the tower's standalone feature (Piece 1); this
 * declaration ships the tower as a village house.
 */
final class VillageInjectionProvider implements DataProvider {

    private static final ResourceLocation HOUSES =
            ResourceLocation.withDefaultNamespace("village/plains/houses");
    private static final ResourceLocation TOWER =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "tower_entrance");

    private static final String MOSSIFY_10 = "minecraft:mossify_10_percent";
    private static final String MOSSIFY_70 = "minecraft:mossify_70_percent";
    private static final String FARM_PLAINS = "minecraft:farm_plains";

    /** A vanilla house: location, weight, processor id (null = the inline empty list). */
    private record House(String location, int weight, String processor) {
    }

    private static final List<House> VANILLA = List.of(
            new House("minecraft:village/plains/houses/plains_small_house_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_2", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_3", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_4", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_5", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_6", 1, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_7", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_small_house_8", 3, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_medium_house_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_medium_house_2", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_big_house_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_butcher_shop_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_butcher_shop_2", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_tool_smith_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_fletcher_house_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_shepherds_house_1", 2, null),
            new House("minecraft:village/plains/houses/plains_armorer_house_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_fisher_cottage_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_tannery_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_cartographer_1", 1, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_library_1", 5, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_library_2", 1, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_masons_house_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_weaponsmith_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_temple_3", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_temple_4", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_stable_1", 2, MOSSIFY_10),
            new House("minecraft:village/plains/houses/plains_stable_2", 2, null),
            new House("minecraft:village/plains/houses/plains_large_farm_1", 4, FARM_PLAINS),
            new House("minecraft:village/plains/houses/plains_small_farm_1", 4, FARM_PLAINS),
            new House("minecraft:village/plains/houses/plains_animal_pen_1", 1, null),
            new House("minecraft:village/plains/houses/plains_animal_pen_2", 1, null),
            new House("minecraft:village/plains/houses/plains_animal_pen_3", 5, null),
            new House("minecraft:village/plains/houses/plains_accessory_1", 1, null),
            new House("minecraft:village/plains/houses/plains_meeting_point_4", 3, MOSSIFY_70),
            new House("minecraft:village/plains/houses/plains_meeting_point_5", 1, null));

    private final PackOutput.PathProvider pools;

    VillageInjectionProvider(PackOutput output) {
        this.pools = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/template_pool");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonArray elements = new JsonArray();
        for (House house : VANILLA) {
            elements.add(houseElement(house));
        }
        elements.add(emptyPoolElement());
        elements.add(towerElement());
        JsonObject root = new JsonObject();
        root.add("elements", elements);
        root.addProperty("fallback", "minecraft:village/plains/terminators");
        return DataProvider.saveStable(cache, root, this.pools.json(HOUSES));
    }

    private static JsonObject houseElement(House house) {
        JsonObject element = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("element_type", "minecraft:legacy_single_pool_element");
        inner.addProperty("location", house.location());
        if (house.processor() == null) {
            JsonObject empty = new JsonObject();
            empty.add("processors", new JsonArray());
            inner.add("processors", empty);
        } else {
            inner.addProperty("processors", house.processor());
        }
        inner.addProperty("projection", "rigid");
        element.add("element", inner);
        element.addProperty("weight", house.weight());
        return element;
    }

    private static JsonObject emptyPoolElement() {
        JsonObject element = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("element_type", "minecraft:empty_pool_element");
        element.add("element", inner);
        element.addProperty("weight", 10);
        return element;
    }

    /** The tower as a village house: the fixed template, rigid, weight 3. */
    private static JsonObject towerElement() {
        JsonObject element = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("element_type", "minecraft:single_pool_element");
        inner.addProperty("location", TOWER.toString());
        inner.addProperty("processors", "minecraft:empty");
        inner.addProperty("projection", "rigid");
        element.add("element", inner);
        element.addProperty("weight", 3);
        return element;
    }

    @Override
    public String getName() {
        return "Village injection (plains houses)";
    }
}
