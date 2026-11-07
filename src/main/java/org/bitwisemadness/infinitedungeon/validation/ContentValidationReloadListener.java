package org.bitwisemadness.infinitedungeon.validation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPoolEntry;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPools;
import org.bitwisemadness.infinitedungeonapi.data.ResourceRef;
import org.slf4j.Logger;

/**
 * The authoring-time validation pass (issue #257): after the content listeners
 * load, it checks references and logs one named, rule-tagged line per problem.
 * Registered last, so the schemas it reads are already loaded.
 *
 * <p>Phase 1 covers the reference-resolution rule for spawn-pool entity refs.
 * The loot-table/slot rules extend this pass once it reads the loot registry
 * (its accessor is {@code ReloadableServerResources.fullRegistries()}).</p>
 */
public final class ContentValidationReloadListener extends SimplePreparableReloadListener<Boolean> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Registers the pass; called from the mod constructor, after the content listeners. */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ContentValidationReloadListener());
    }

    @Override
    protected Boolean prepare(ResourceManager manager, ProfilerFiller profiler) {
        return Boolean.TRUE; // nothing to prepare; a non-null sentinel (SpotBugs)
    }

    @Override
    protected void apply(Boolean prepared, ResourceManager manager, ProfilerFiller profiler) {
        report(poolProblems());
    }

    /** The reference-resolution problems across every loaded pool. */
    private static List<ContentProblem> poolProblems() {
        List<ContentProblem> problems = new ArrayList<>();
        for (Map.Entry<String, List<SpawnPoolEntry>> pool : SpawnPools.entries().entrySet()) {
            List<SpawnPoolEntry> entries = pool.getValue();
            for (int index = 0; index < entries.size(); index++) {
                SpawnPoolEntry entry = entries.get(index);
                ResourceRef ref = entry.ref();
                boolean modLoaded = entry.requiredMod()
                        .map(id -> ModList.get().isLoaded(id))
                        .orElseGet(() -> ModList.get().isLoaded(
                                ContentValidation.namespaceOf(ref.location())));
                ContentValidation.unresolved("pool " + pool.getKey() + " entry " + index,
                        "entity", ref.serialize(), modLoaded, resolves(ref))
                        .ifPresent(problems::add);
            }
        }
        return problems;
    }

    private static boolean resolves(ResourceRef ref) {
        ResourceLocation id = ResourceLocation.tryParse(ref.location());
        if (id == null) {
            return false;
        }
        if (ref.tag()) {
            return BuiltInRegistries.ENTITY_TYPE
                    .getTag(TagKey.create(Registries.ENTITY_TYPE, id)).isPresent();
        }
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    private static void report(List<ContentProblem> problems) {
        if (problems.isEmpty()) {
            LOGGER.debug("Content validation: no reference problems");
            return;
        }
        LOGGER.warn("Content validation found {} problem(s):", problems.size());
        for (ContentProblem problem : problems) {
            LOGGER.warn("  {}", problem.format());
        }
    }
}
