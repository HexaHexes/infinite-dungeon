package org.bitwisemadness.infinitedungeon.datagen;

import java.util.Set;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The datagen entry point (issue #130): every provider that writes the mod's
 * internal datapack to {@code src/generated/resources}. Registered on the mod
 * bus, so {@code ./gradlew runData} gathers them; the CI drift gate regenerates
 * and fails on any difference.
 */
@EventBusSubscriber(modid = InfiniteDungeon.MOD_ID)
public final class DungeonDataGenerators {

    private DungeonDataGenerators() {
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        if (!event.includeServer()) {
            return;
        }
        var generator = event.getGenerator();
        generator.addProvider(true, new DatapackBuiltinEntriesProvider(
                generator.getPackOutput(),
                event.getLookupProvider(),
                registrySet(),
                Set.of(InfiniteDungeon.MOD_ID)));
        generator.addProvider(true, new DungeonProfileProvider(generator.getPackOutput()));
        generator.addProvider(true, new DungeonPoolsProvider(generator.getPackOutput()));
        generator.addProvider(true, new DungeonHasStructureTagProvider(generator.getPackOutput()));
        generator.addProvider(true,
                new DungeonLootProvider(generator.getPackOutput(), event.getLookupProvider()));
        generator.addProvider(true, new DungeonLootInjectionsProvider(generator.getPackOutput()));
        generator.addProvider(true, new DungeonLootSlotsProvider(generator.getPackOutput()));
        generator.addProvider(true,
                new DungeonLootModifierProvider(generator.getPackOutput(), event.getLookupProvider()));
    }

    /** The dynamic-registry entries test-datapack generation bootstraps. */
    private static RegistrySetBuilder registrySet() {
        return new RegistrySetBuilder()
                .add(Registries.DIMENSION_TYPE, DungeonWorldgen::dimensionTypes)
                .add(Registries.BIOME, DungeonWorldgen::biomes)
                .add(Registries.CONFIGURED_FEATURE, DungeonWorldgen::configuredFeatures)
                .add(Registries.PLACED_FEATURE, DungeonWorldgen::placedFeatures)
                .add(Registries.STRUCTURE_SET, DungeonWorldgen::structureSets)
                .add(Registries.LEVEL_STEM, DungeonWorldgen::levelStems);
    }
}
