package org.bitwisemadness.infinitedungeon.loot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeonapi.data.InfiniteDungeonJsonReloadListener;
import org.slf4j.Logger;

/**
 * Loads {@code data/<namespace>/infinite_dungeon/loot_slots/*.json} on server
 * start and every {@code /reload} (issue #256). Files parse in sorted location
 * order and malformed entries are skipped with a warning - the
 * {@code SpawnPoolReloadListener} contract.
 */
public final class LootSlotReloadListener
        extends InfiniteDungeonJsonReloadListener<List<LootSlotMapping>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public LootSlotReloadListener() {
        super(new Gson(), "loot_slots");
    }

    /** Registers the listener; called from the mod constructor. */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new LootSlotReloadListener());
    }

    @Override
    protected List<LootSlotMapping> parse(ResourceLocation location, JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        JsonElement mappings = root.get("mappings");
        if (mappings == null || !mappings.isJsonArray()) {
            throw new JsonParseException("loot_slots needs a mappings array");
        }
        List<LootSlotMapping> parsed = new ArrayList<>();
        for (JsonElement element : mappings.getAsJsonArray()) {
            try {
                parsed.add(parseEntry(element));
            } catch (Exception e) {
                LOGGER.warn("Skipping malformed slot mapping in {}: {}", location, element, e);
            }
        }
        return List.copyOf(parsed);
    }

    private static LootSlotMapping parseEntry(JsonElement element) {
        JsonObject entry = element.getAsJsonObject();
        String slot = require(entry, "slot");
        String table = require(entry, "table");
        String theme = entry.has("theme") ? entry.get("theme").getAsString() : LootSlotMapping.ANY_THEME;
        int minLayer = entry.has("min_layer") ? entry.get("min_layer").getAsInt() : 0;
        int maxLayer = entry.has("max_layer") ? entry.get("max_layer").getAsInt() : Integer.MAX_VALUE;
        return new LootSlotMapping(slot, theme, minLayer, maxLayer, table);
    }

    private static String require(JsonObject entry, String field) {
        JsonElement value = entry.get(field);
        if (value == null) {
            throw new JsonParseException("slot mapping needs a " + field);
        }
        return value.getAsString();
    }

    @Override
    protected void applyEntries(Map<ResourceLocation, List<LootSlotMapping>> entries) {
        List<LootSlotMapping> all = new ArrayList<>();
        entries.values().forEach(all::addAll);
        LootSlots.setDatapack(all);
        LOGGER.debug("Loaded {} loot slot mapping(s) from datapacks", all.size());
    }
}
