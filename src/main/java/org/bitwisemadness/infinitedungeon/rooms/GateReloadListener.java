package org.bitwisemadness.infinitedungeon.rooms;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeonapi.data.InfiniteDungeonJsonReloadListener;
import org.slf4j.Logger;

/**
 * Loads {@code data/<namespace>/infinite_dungeon/gates/*.json} on server start
 * and every {@code /reload} (issue #252).
 *
 * <p>One gate per file: {@code {"pos": [x, y, z], "condition": "key"}} with an
 * optional {@code "dimension"} - present scopes the gate to that dimension
 * (the lobby and a dungeon could share coordinates), absent makes it apply
 * everywhere. The position is absolute, which the datapack author controls
 * and a datagen step can emit. Malformed files skip with a warning, never
 * fatal - the base listener's posture; the condition word itself names the
 * offending value ({@link GateCondition#parse}).</p>
 */
public final class GateReloadListener extends InfiniteDungeonJsonReloadListener<GateReloadListener.Instance> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The parsed instance: where the gate sits, and how it opens. */
    public record Instance(BlockPos pos, GateCondition condition, ResourceKey<Level> dimension) {
    }

    public GateReloadListener() {
        super(new Gson(), "gates");
    }

    /** Registers the listener; called from the mod constructor. */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GateReloadListener());
    }

    @Override
    protected Instance parse(ResourceLocation location, JsonElement json) {
        JsonObject gate = json.getAsJsonObject();
        JsonElement pos = gate.get("pos");
        if (pos == null || !pos.isJsonArray() || pos.getAsJsonArray().size() != 3) {
            throw new JsonParseException("a gate needs a pos array of three integers");
        }
        JsonElement condition = gate.get("condition");
        if (condition == null || !condition.isJsonPrimitive()) {
            throw new JsonParseException("a gate needs a condition");
        }
        BlockPos parsed = new BlockPos(
                pos.getAsJsonArray().get(0).getAsInt(),
                pos.getAsJsonArray().get(1).getAsInt(),
                pos.getAsJsonArray().get(2).getAsInt());
        GateCondition declared = GateCondition.parse(condition.getAsString(), location.toString());
        ResourceKey<Level> dimension = null;
        JsonElement declaredDimension = gate.get("dimension");
        if (declaredDimension != null) {
            if (!declaredDimension.isJsonPrimitive()) {
                throw new JsonParseException("a gate's dimension must be a resource location string");
            }
            dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(declaredDimension.getAsString()));
        }
        return new Instance(parsed, declared, dimension);
    }

    @Override
    protected void applyEntries(Map<ResourceLocation, Instance> entries) {
        Map<ResourceKey<Level>, Map<BlockPos, GateCondition>> scoped = new HashMap<>();
        Map<BlockPos, GateCondition> global = new HashMap<>();
        entries.forEach((location, instance) -> {
            if (instance.dimension() == null) {
                global.put(instance.pos(), instance.condition());
            } else {
                scoped.computeIfAbsent(instance.dimension(), key -> new HashMap<>())
                        .put(instance.pos(), instance.condition());
            }
        });
        GateInstances.setDatapack(scoped, global);
        LOGGER.debug("Loaded {} gate instance(s) from datapacks", global.size()
                + scoped.values().stream().mapToInt(Map::size).sum());
    }
}
