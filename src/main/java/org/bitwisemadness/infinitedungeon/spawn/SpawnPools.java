package org.bitwisemadness.infinitedungeon.spawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import org.bitwisemadness.infinitedungeonapi.data.RequiredModFilter;
import org.bitwisemadness.infinitedungeonapi.data.TagExpansion;

/**
 * The core module's spawn pool registry (issue #39): pools loaded from
 * {@code data/<ns>/infinite_dungeon/pools/*.json} by
 * {@link SpawnPoolReloadListener}, queried by the spawn consumers (#40 and
 * the roster defaults of #60/#61). The API cannot own this - ProfilePools
 * says plainly that resolving pool ids against a registry belongs to the
 * consumer that owns that registry - but every mechanism under the registry
 * is borrowed from the API's data package (I32).
 *
 * <p>Filtering is a runtime pipeline over the raw parsed entries: layer band,
 * then required-mod (the API's {@link RequiredModFilter} with FML's mod list
 * at the boundary), then reference expansion (the API's
 * {@link TagExpansion}, which drops absent ids SILENTLY - the issue's
 * contract, and the reason this schema exists instead of plain tags: a tag
 * naming an absent mod's entity fails datapack validation, ours never
 * does).</p>
 */
public final class SpawnPools {

    private static volatile Map<String, List<SpawnPoolEntry>> datapack = Map.of();

    private SpawnPools() {
    }

    /** Replaces the pool table; called with the complete state on every load. */
    static void setDatapack(Map<String, List<SpawnPoolEntry>> pools) {
        datapack = Map.copyOf(pools);
    }

    /** Whether the current datapack state declares this pool id. */
    public static boolean exists(String poolId) {
        return datapack.containsKey(poolId);
    }

    /**
     * The concrete candidates for a pool at a layer: the filter pipeline in
     * order - band, required mod, expansion - with one candidate per entity a
     * reference contributes. An unknown pool is an empty answer, never an
     * error: profiles may name pools a pack has not shipped yet.
     */
    public static List<PoolCandidate> candidates(String poolId, int layer) {
        List<SpawnPoolEntry> entries = datapack.get(poolId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<SpawnPoolEntry> inLayer = PoolMath.filterByLayer(entries, layer);
        List<SpawnPoolEntry> allowed = RequiredModFilter.filter(
                inLayer, SpawnPoolEntry::requiredMod, modId -> ModList.get().isLoaded(modId));
        if (allowed.isEmpty()) {
            return List.of();
        }
        List<PoolCandidate> candidates = new ArrayList<>();
        for (SpawnPoolEntry entry : allowed) {
            for (String id : TagExpansion.expand(entry.ref(), SpawnPools::entityExists,
                    SpawnPools::tagMembers)) {
                candidates.add(new PoolCandidate(id, entry.weight()));
            }
        }
        return List.copyOf(candidates);
    }

    /** The weighted pick for one spawn attempt (plan section 9). */
    public static Optional<PoolCandidate> pick(String poolId, int layer, RandomSource random) {
        return PoolMath.pick(candidates(poolId, layer), random.nextDouble());
    }

    /**
     * Every concrete entity id in a pool, IGNORING layer bands: the boss
     * roster derivation (issue #60) needs the whole pool because the guardian
     * schedule does its own per-layer band fitting on raw power - the pool
     * data stays dumb (plan section 10). Mod-gated and tag-expanded exactly
     * like {@link #candidates}; duplicates collapse (two entries naming one
     * entity are one roster member). Unknown pool = empty answer.
     */
    public static List<String> allIds(String poolId) {
        List<SpawnPoolEntry> entries = datapack.get(poolId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<SpawnPoolEntry> allowed = RequiredModFilter.filter(
                entries, SpawnPoolEntry::requiredMod, modId -> ModList.get().isLoaded(modId));
        Set<String> ids = new LinkedHashSet<>();
        for (SpawnPoolEntry entry : allowed) {
            ids.addAll(TagExpansion.expand(entry.ref(), SpawnPools::entityExists,
                    SpawnPools::tagMembers));
        }
        return List.copyOf(ids);
    }

    private static boolean entityExists(String id) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id));
    }

    private static Collection<String> tagMembers(String tagId) {
        var holders = BuiltInRegistries.ENTITY_TYPE.getTag(
                TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(tagId)));
        if (holders.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (var holder : holders.get()) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(holder.value());
            if (key != null) {
                ids.add(key.toString());
            }
        }
        return ids;
    }
}
