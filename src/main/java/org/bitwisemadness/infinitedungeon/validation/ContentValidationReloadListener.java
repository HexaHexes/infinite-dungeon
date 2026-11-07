package org.bitwisemadness.infinitedungeon.validation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.bitwisemadness.infinitedungeon.loot.LootInjection;
import org.bitwisemadness.infinitedungeon.loot.LootInjections;
import org.bitwisemadness.infinitedungeon.loot.LootSlotMapping;
import org.bitwisemadness.infinitedungeon.loot.LootSlots;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPoolEntry;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPools;
import org.bitwisemadness.infinitedungeonapi.data.ResourceRef;
import org.slf4j.Logger;

/**
 * The authoring-time validation pass (issue #257): after the content listeners
 * load, it checks references and logs one named, rule-tagged line per problem.
 * Registered last, so the schemas it reads are already loaded.
 *
 * <p>Covers the reference-resolution rule for spawn-pool refs (the entity
 * registry + tags), loot-slot mappings and loot-injection refs (the loot-table
 * resource, plus slot resolution against {@link LootSlots}). Loot-table refs
 * are checked against the resource manager, not the loot registry, so the pass
 * carries no reload-ordering risk. The room rules arrive with #78.</p>
 */
public final class ContentValidationReloadListener extends SimplePreparableReloadListener<Boolean> {

    /** The datapack directory loot tables live under. */
    private static final String LOOT_TABLE_DIRECTORY = "loot_table";

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
        List<ContentProblem> problems = poolProblems();
        problems.addAll(lootProblems(manager));
        report(problems);
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
                unresolved(problems, "pool " + pool.getKey() + " entry " + index,
                        "entity", ref.serialize(), modLoaded, resolves(ref));
            }
        }
        return problems;
    }

    /** The reference-resolution problems across the loot injections and slots. */
    private static List<ContentProblem> lootProblems(ResourceManager manager) {
        List<ContentProblem> problems = new ArrayList<>();
        for (LootInjection injection : LootInjections.all()) {
            String unit = "loot_injection "
                    + injection.slot().orElseGet(() -> injection.table().orElse("?"));
            injection.table().ifPresent(table ->
                    unresolved(problems, unit, "table", table, tableResolves(manager, table)));
            injection.slot().ifPresent(slot ->
                    unresolved(problems, unit, "slot", slot, slotResolves(slot)));
        }
        int index = 0;
        for (LootSlotMapping mapping : LootSlots.all()) {
            unresolved(problems, "loot_slot mapping " + index++, "table", mapping.table(),
                    tableResolves(manager, mapping.table()));
        }
        return problems;
    }

    private static void unresolved(List<ContentProblem> problems, String unit, String field,
            String reference, boolean resolves) {
        unresolved(problems, unit, field, reference,
                ModList.get().isLoaded(ContentValidation.namespaceOf(reference)), resolves);
    }

    private static void unresolved(List<ContentProblem> problems, String unit, String field,
            String reference, boolean modLoaded, boolean resolves) {
        ContentValidation.unresolved(unit, field, reference, modLoaded, resolves)
                .ifPresent(problems::add);
    }

    /** A loot table id resolves when its datapack resource exists. */
    private static boolean tableResolves(ResourceManager manager, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return false;
        }
        ResourceLocation resource = FileToIdConverter.json(LOOT_TABLE_DIRECTORY)
                .idToFile(location);
        return manager.getResource(resource).isPresent();
    }

    /** A slot id resolves when any loaded mapping serves it. */
    private static boolean slotResolves(String slot) {
        return LootSlots.all().stream().anyMatch(mapping -> mapping.slot().equals(slot));
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
