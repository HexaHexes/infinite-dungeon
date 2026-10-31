package org.bitwisemadness.infinitedungeon.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeonapi.data.InfiniteDungeonJsonReloadListener;
import org.bitwisemadness.infinitedungeonapi.data.ResourceRef;
import org.slf4j.Logger;

/**
 * Loads {@code data/<namespace>/infinite_dungeon/pools/*.json} on server start
 * and on every {@code /reload} (issue #39, plan section 10).
 *
 * <p>The pool id is the file's location with the data directory stripped:
 * {@code data/mymod/infinite_dungeon/pools/fodder.json} becomes
 * {@code mymod:fodder} - the plain-id contract ProfilePools validates for.
 * The base listener parses in sorted location order and skips malformed
 * files, never fatal; here individual malformed entries are also skipped
 * (a bad reference or band drops the entry with a warning, the file
 * survives). Entry parsing is deliberately permissive so a pack can carry
 * entries for optional content.</p>
 */
public final class SpawnPoolReloadListener extends InfiniteDungeonJsonReloadListener<List<SpawnPoolEntry>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The data directory stripped from file locations to form pool ids. */
    private static final String POOL_DIRECTORY = "infinite_dungeon/pools/";

    public SpawnPoolReloadListener() {
        super(new Gson(), "pools");
    }

    /** Registers the listener; called from the mod constructor. */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SpawnPoolReloadListener());
    }

    @Override
    protected List<SpawnPoolEntry> parse(ResourceLocation location, JsonElement json) {
        JsonObject pool = json.getAsJsonObject();
        JsonElement entries = pool.get("entries");
        if (entries == null || !entries.isJsonArray()) {
            throw new JsonParseException("pool needs an entries array");
        }
        List<SpawnPoolEntry> parsed = new ArrayList<>();
        for (JsonElement element : entries.getAsJsonArray()) {
            try {
                parsed.add(parseEntry(element));
            } catch (Exception e) {
                LOGGER.warn("Skipping malformed entry in spawn pool {}: {}", location, element, e);
            }
        }
        return List.copyOf(parsed);
    }

    private static SpawnPoolEntry parseEntry(JsonElement element) throws Exception {
        JsonObject entry = element.getAsJsonObject();
        JsonElement entity = entry.get("entity");
        if (entity == null) {
            throw new JsonParseException("entry needs an entity reference");
        }
        ResourceRef ref = ResourceRef.parse(entity.getAsString());
        int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
        int minLayer = entry.has("minLayer") ? entry.get("minLayer").getAsInt() : 0;
        int maxLayer = entry.has("maxLayer") ? entry.get("maxLayer").getAsInt() : Integer.MAX_VALUE;
        Optional<String> requiredMod = entry.has("required_mod")
                && entry.get("required_mod").isJsonPrimitive()
                ? Optional.of(entry.get("required_mod").getAsString())
                : Optional.empty();
        return new SpawnPoolEntry(ref, weight, minLayer, maxLayer, requiredMod);
    }

    @Override
    protected void applyEntries(Map<ResourceLocation, List<SpawnPoolEntry>> entries) {
        Map<String, List<SpawnPoolEntry>> pools = new HashMap<>();
        entries.forEach((location, pool) -> pools.put(toPoolId(location), pool));
        SpawnPools.setDatapack(pools);
        LOGGER.debug("Loaded {} spawn pool(s) from datapacks", pools.size());
    }

    /** {@code ns:infinite_dungeon/pools/foo} -> {@code ns:foo}. */
    static String toPoolId(ResourceLocation location) {
        String path = location.getPath();
        if (path.startsWith(POOL_DIRECTORY)) {
            String id = path.substring(POOL_DIRECTORY.length());
            return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), id).toString();
        }
        // listResources only yields files under the directory; be tolerant
        // anyway rather than producing an id no profile would ever name.
        return location.toString();
    }
}
