package org.bitwisemadness.infinitedungeon.lobby;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The tower-entrance building's placement feature (issue #199, Piece 1). The
 * building itself is the datagen-authored {@code StructureTemplate}
 * ({@code data/infinite_dungeon/structure/tower_entrance.nbt}) - this feature
 * loads it through the server's structure manager and places it at the origin,
 * so the runtime path reads exactly the committed content the village
 * injection (Piece 2) will reference. Gated by the
 * {@code towerEntrance.enabled} toggle; the register's rules (fixed, minimal
 * terrain alteration, no exterior windows) are authored into the template.
 */
public final class TowerEntranceFeature extends Feature<NoneFeatureConfiguration> {

    /** The authored structure template this feature places. */
    public static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "tower_entrance");

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, InfiniteDungeon.MOD_ID);

    /** The registered feature: {@code infinite_dungeon:tower_entrance}. */
    public static final Supplier<TowerEntranceFeature> TOWER_ENTRANCE =
            FEATURES.register("tower_entrance", TowerEntranceFeature::new);

    private TowerEntranceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Registers the feature on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!enabled()) {
            return false;
        }
        return build(context.level(), context.origin(), context.random());
    }

    /** The toggle gate (the {@code towerEntrance.enabled} config). */
    public static boolean enabled() {
        return DungeonConfigs.towerEntranceEnabled();
    }

    /** Loads the authored template and places it at the origin; the testable path. */
    static boolean build(ServerLevelAccessor level, BlockPos origin, RandomSource random) {
        var template = level.getServer().getStructureManager().getOrCreate(TEMPLATE_ID);
        return template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setIgnoreEntities(false), random, 2);
    }
}
