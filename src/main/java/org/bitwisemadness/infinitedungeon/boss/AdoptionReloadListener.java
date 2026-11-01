package org.bitwisemadness.infinitedungeon.boss;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeonapi.data.InfiniteDungeonJsonReloadListener;
import org.slf4j.Logger;

/**
 * Loads {@code data/<namespace>/infinite_dungeon/adoptions/*.json} on server
 * start and every {@code /reload} (issue #255's ruling: a core datapack dir
 * mirroring gates/ and pools/).
 *
 * <p>One adoption per file: {@code {"entity": "minecraft:warden"}} with an
 * optional {@code "dimension"} scoping it (absent = every dimension). The
 * file's location is the declaration's identity - the defeat record's key and
 * the spawned entity's tag suffix - so renaming a file orphans its old
 * record, which is the honest signal that content was re-authored. Malformed
 * files skip with a warning, never fatal (the base listener's posture); a
 * blank entity names the file.</p>
 */
public final class AdoptionReloadListener extends InfiniteDungeonJsonReloadListener<Adoptions.Declaration> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public AdoptionReloadListener() {
        super(new Gson(), "adoptions");
    }

    /** Registers the listener; called from the mod constructor. */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AdoptionReloadListener());
    }

    @Override
    protected Adoptions.Declaration parse(ResourceLocation location, JsonElement json) {
        JsonObject adoption = json.getAsJsonObject();
        JsonElement entity = adoption.get("entity");
        if (entity == null || !entity.isJsonPrimitive() || entity.getAsString().isBlank()) {
            throw new JsonParseException("an adoption needs an entity registry id");
        }
        ResourceKey<Level> dimension = null;
        JsonElement declaredDimension = adoption.get("dimension");
        if (declaredDimension != null) {
            if (!declaredDimension.isJsonPrimitive()) {
                throw new JsonParseException("an adoption's dimension must be a resource location string");
            }
            dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(declaredDimension.getAsString()));
        }
        return new Adoptions.Declaration(location, entity.getAsString().trim(), dimension);
    }

    @Override
    protected void applyEntries(Map<ResourceLocation, Adoptions.Declaration> entries) {
        Map<ResourceKey<Level>, Map<String, Adoptions.Declaration>> scoped = new HashMap<>();
        Map<String, Adoptions.Declaration> global = new HashMap<>();
        entries.values().forEach(declaration -> {
            if (declaration.dimension() == null) {
                global.put(declaration.entityId(), declaration);
            } else {
                scoped.computeIfAbsent(declaration.dimension(), key -> new HashMap<>())
                        .put(declaration.entityId(), declaration);
            }
        });
        Adoptions.setDatapack(scoped, global);
        LOGGER.debug("Loaded {} adoption declaration(s) from datapacks", entries.size());
    }
}
