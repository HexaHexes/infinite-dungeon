package org.bitwisemadness.infinitedungeon.datagen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.lanes.FallbackHallFeature;
import org.bitwisemadness.infinitedungeon.lanes.TransitionFramesFeature;
import org.bitwisemadness.infinitedungeon.lanes.WaystoneEntranceFeature;

/**
 * The dungeon's worldgen registry entries as Java tables (issue #130): the
 * dimension type and its level stem, the biome, the three lane features and
 * their placements, and the lane structure set. Datagen turns these into the
 * committed datapack; the runtime never reads this class.
 *
 * <p>Mirrors what shipped hand-authored under
 * {@code data/infinite_dungeon/} before the datagen migration, so the emitted
 * tree is not byte-identical to the old files but is semantically the same.</p>
 */
final class DungeonWorldgen {

    /** The lane structure set, shared by the dimension's flat generator. */
    private static final ResourceKey<StructureSet> LANES =
            key(Registries.STRUCTURE_SET, "lanes");

    /** The dungeon's level stem (the dimension itself). */
    static final ResourceKey<LevelStem> LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, DungeonDimensions.ID);

    private static final ResourceKey<Biome> BIOME = key(Registries.BIOME, "dungeon");

    private static final ResourceKey<ConfiguredFeature<?, ?>> FALLBACK_HALL =
            key(Registries.CONFIGURED_FEATURE, "fallback_hall");
    private static final ResourceKey<ConfiguredFeature<?, ?>> TRANSITION_FRAMES =
            key(Registries.CONFIGURED_FEATURE, "transition_frames");
    private static final ResourceKey<ConfiguredFeature<?, ?>> WAYSTONE_ENTRANCE =
            key(Registries.CONFIGURED_FEATURE, "waystone_entrance");

    private static final ResourceKey<PlacedFeature> FALLBACK_HALL_PLACED =
            key(Registries.PLACED_FEATURE, "fallback_hall");
    private static final ResourceKey<PlacedFeature> TRANSITION_FRAMES_PLACED =
            key(Registries.PLACED_FEATURE, "transition_frames");
    private static final ResourceKey<PlacedFeature> WAYSTONE_ENTRANCE_PLACED =
            key(Registries.PLACED_FEATURE, "waystone_entrance");

    /** The structure the lane set places - Dungeon Crawl's, resolved by reference. */
    private static final ResourceKey<Structure> DUNGEON =
            ResourceKey.create(Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath("dungeoncrawl", "dungeon"));

    private DungeonWorldgen() {
    }

    static void dimensionTypes(BootstrapContext<DimensionType> context) {
        context.register(DungeonDimensions.TYPE, new DimensionType(
                OptionalLong.empty(),
                true,
                false,
                false,
                false,
                1.0D,
                true,
                false,
                -64,
                2048,
                2048,
                BlockTags.INFINIBURN_END,
                ResourceLocation.withDefaultNamespace("the_end"),
                0.0F,
                new DimensionType.MonsterSettings(false, false, UniformInt.of(0, 7), 0)));
    }

    static void biomes(BootstrapContext<Biome> context) {
        HolderGetter<PlacedFeature> placed = context.lookup(Registries.PLACED_FEATURE);
        // One list per decoration step (the PlainBuilder would trim trailing
        // empties; the hand-authored file carried all of them, so keep the
        // shape identical). The three lane features ride SURFACE_STRUCTURES.
        List<HolderSet<PlacedFeature>> features = new ArrayList<>(
                Collections.nCopies(GenerationStep.Decoration.values().length, HolderSet.direct()));
        features.set(GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal(), HolderSet.direct(
                placed.getOrThrow(FALLBACK_HALL_PLACED),
                placed.getOrThrow(TRANSITION_FRAMES_PLACED),
                placed.getOrThrow(WAYSTONE_ENTRANCE_PLACED)));
        BiomeGenerationSettings generation = new BiomeGenerationSettings(Map.of(), features);
        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .fogColor(10518688)
                .skyColor(0)
                .waterColor(4159204)
                .waterFogColor(329011)
                .ambientMoodSound(new AmbientMoodSettings(SoundEvents.AMBIENT_CAVE, 6000, 8, 2.0D))
                .build();
        context.register(BIOME, new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(effects)
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(generation)
                .build());
    }

    static void configuredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        context.register(FALLBACK_HALL, new ConfiguredFeature<>(
                FallbackHallFeature.FALLBACK_HALL.get(), NoneFeatureConfiguration.INSTANCE));
        context.register(TRANSITION_FRAMES, new ConfiguredFeature<>(
                TransitionFramesFeature.TRANSITION_FRAMES.get(), NoneFeatureConfiguration.INSTANCE));
        context.register(WAYSTONE_ENTRANCE, new ConfiguredFeature<>(
                WaystoneEntranceFeature.WAYSTONE_ENTRANCE.get(), NoneFeatureConfiguration.INSTANCE));
    }

    static void placedFeatures(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configured =
                context.lookup(Registries.CONFIGURED_FEATURE);
        // BiomeFilter.biome() last, as vanilla's datagen validator requires; the
        // old hand-authored files omitted it (a latent flaw datagen surfaced).
        List<PlacementModifier> placement = List.of(BiomeFilter.biome());
        context.register(FALLBACK_HALL_PLACED,
                new PlacedFeature(configured.getOrThrow(FALLBACK_HALL), placement));
        context.register(TRANSITION_FRAMES_PLACED,
                new PlacedFeature(configured.getOrThrow(TRANSITION_FRAMES), placement));
        context.register(WAYSTONE_ENTRANCE_PLACED,
                new PlacedFeature(configured.getOrThrow(WAYSTONE_ENTRANCE), placement));
    }

    static void structureSets(BootstrapContext<StructureSet> context) {
        HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);
        context.register(LANES, new StructureSet(
                List.of(StructureSet.entry(structures.getOrThrow(DUNGEON), 1)),
                new RandomSpreadStructurePlacement(32, 12, RandomSpreadType.LINEAR, 10387313)));
    }

    static void levelStems(BootstrapContext<LevelStem> context) {
        HolderGetter<DimensionType> types = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<StructureSet> sets = context.lookup(Registries.STRUCTURE_SET);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.of(HolderSet.direct(sets.getOrThrow(LANES))),
                biomes.getOrThrow(BIOME),
                List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        settings.updateLayers();
        settings.setDecoration();
        context.register(LEVEL_STEM, new LevelStem(
                types.getOrThrow(DungeonDimensions.TYPE), new FlatLevelSource(settings)));
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry,
                ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, path));
    }
}
