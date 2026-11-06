package org.bitwisemadness.infinitedungeon.loot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeonapi.data.InfiniteDungeonJsonReloadListener;
import org.slf4j.Logger;

/**
 * Loads {@code data/<namespace>/infinite_dungeon/loot_injections/*.json} on
 * server start and every {@code /reload} (issue #52). Files are parsed in
 * sorted location order and malformed entries are skipped with a warning -
 * the {@code SpawnPoolReloadListener} contract.
 */
public final class LootInjectionReloadListener
        extends InfiniteDungeonJsonReloadListener<List<LootInjection>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public LootInjectionReloadListener() {
        super(new Gson(), "loot_injections");
    }

    /** Registers the listener; called from the mod constructor. */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new LootInjectionReloadListener());
    }

    @Override
    protected List<LootInjection> parse(ResourceLocation location, JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        JsonElement injections = root.get("injections");
        if (injections == null || !injections.isJsonArray()) {
            throw new JsonParseException("loot_injections needs an injections array");
        }
        List<LootInjection> parsed = new ArrayList<>();
        for (JsonElement element : injections.getAsJsonArray()) {
            try {
                parsed.add(parseEntry(element));
            } catch (Exception e) {
                LOGGER.warn("Skipping malformed injection in {}: {}", location, element, e);
            }
        }
        return List.copyOf(parsed);
    }

    private static LootInjection parseEntry(JsonElement element) {
        JsonObject entry = element.getAsJsonObject();
        int minLayer = entry.has("min_layer") ? entry.get("min_layer").getAsInt() : 0;
        int maxLayer = entry.has("max_layer") ? entry.get("max_layer").getAsInt() : Integer.MAX_VALUE;
        double chance = entry.has("chance") ? entry.get("chance").getAsDouble() : 1.0D;
        Optional<String> requiredMod = entry.has("required_mod")
                && entry.get("required_mod").isJsonPrimitive()
                ? Optional.of(entry.get("required_mod").getAsString())
                : Optional.empty();
        return new LootInjection(read(entry, "table"), read(entry, "slot"),
                minLayer, maxLayer, chance, requiredMod);
    }

    private static Optional<String> read(JsonObject entry, String field) {
        JsonElement value = entry.get(field);
        return value == null || !value.isJsonPrimitive()
                ? Optional.empty()
                : Optional.of(value.getAsString());
    }

    @Override
    protected void applyEntries(Map<ResourceLocation, List<LootInjection>> entries) {
        List<LootInjection> all = new ArrayList<>();
        entries.values().forEach(all::addAll);
        LootInjections.setDatapack(all);
        LOGGER.debug("Loaded {} loot injection(s) from datapacks", all.size());
    }
}
