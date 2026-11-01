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
 * and every {@code /reload} (issue #252; #255 adds the trial declaration).
 *
 * <p>One gate per file: {@code {"pos": [x, y, z], "condition": "key"}} with an
 * optional {@code "dimension"} - present scopes the gate to that dimension
 * (the lobby and a dungeon could share coordinates), absent makes it apply
 * everywhere - and, for {@code trials_complete}, an optional
 * {@code "trial_spawners": [[x, y, z], ...]} naming the gate's context. The
 * positions are absolute, which the datapack author controls and a datagen
 * step can emit. Malformed files skip with a warning, never fatal - the base
 * listener's posture; the condition word itself names the offending value
 * ({@link GateCondition#parse}).</p>
 */
public final class GateReloadListener extends InfiniteDungeonJsonReloadListener<GateReloadListener.Instance> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The parsed instance: where the gate sits, what it waits on, and its scope. */
    public record Instance(BlockPos pos, GateDeclaration declaration, ResourceKey<Level> dimension) {
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
        java.util.List<BlockPos> trials = new java.util.ArrayList<>();
        JsonElement trialSpawners = gate.get("trial_spawners");
        if (trialSpawners != null) {
            if (!trialSpawners.isJsonArray()) {
                throw new JsonParseException(
                        "a gate's trial_spawners must be an array of [x, y, z] arrays");
            }
            for (JsonElement entry : trialSpawners.getAsJsonArray()) {
                if (!entry.isJsonArray() || entry.getAsJsonArray().size() != 3) {
                    throw new JsonParseException(
                            "every trial_spawners entry must be an array of three integers");
                }
                trials.add(new BlockPos(
                        entry.getAsJsonArray().get(0).getAsInt(),
                        entry.getAsJsonArray().get(1).getAsInt(),
                        entry.getAsJsonArray().get(2).getAsInt()));
            }
        }
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
        return new Instance(parsed, new GateDeclaration(declared, trials), dimension);
    }

    @Override
    protected void applyEntries(Map<ResourceLocation, Instance> entries) {
        Map<ResourceKey<Level>, Map<BlockPos, GateDeclaration>> scoped = new HashMap<>();
        Map<BlockPos, GateDeclaration> global = new HashMap<>();
        entries.forEach((location, instance) -> {
            if (instance.dimension() == null) {
                global.put(instance.pos(), instance.declaration());
            } else {
                scoped.computeIfAbsent(instance.dimension(), key -> new HashMap<>())
                        .put(instance.pos(), instance.declaration());
            }
        });
        GateInstances.setDatapack(scoped, global);
        LOGGER.debug("Loaded {} gate instance(s) from datapacks", global.size()
                + scoped.values().stream().mapToInt(Map::size).sum());
    }
}
