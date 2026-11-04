package org.bitwisemadness.infinitedungeon.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.loot.DepthChanceCondition;
import org.bitwisemadness.infinitedungeon.loot.DepthScaledFunction;

/**
 * The depth-driven loot tables (issue #51), datagen'd into
 * {@code src/generated/resources/data/infinite_dungeon/loot_table/}. Every
 * entry carries the {@code depth_scaled} function; the rarer entries also carry
 * the {@code depth_chance} gate, so their share grows with depth. The slot
 * layer (#256) later re-points these.
 */
final class DungeonLootProvider extends LootTableProvider {

    private static final ResourceKey<LootTable> COMMON = table("loot/common");
    private static final ResourceKey<LootTable> TREASURE = table("loot/treasure");

    /** Scales a rolled count by depth (issue #51). */
    private static final LootItemFunction.Builder DEPTH_SCALED =
            LootItemConditionalFunction.simpleBuilder(DepthScaledFunction::new);

    /** Gates a rare entry by {@code qualityPerPoint * D} (issue #51). */
    private static final LootItemCondition.Builder DEPTH_CHANCE =
            () -> DepthChanceCondition.INSTANCE;

    DungeonLootProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.of(COMMON, TREASURE),
                List.of(new SubProviderEntry(provider -> new Tables(), LootContextParamSets.CHEST)),
                registries);
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, path));
    }

    /** A common entry: base count, depth-scaled, always eligible. */
    private static LootPoolSingletonContainer.Builder<?> common(net.minecraft.world.level.ItemLike item,
            int baseCount) {
        return LootItem.lootTableItem(item)
                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(baseCount)))
                .apply(DEPTH_SCALED);
    }

    /** A rare entry: base count, depth-scaled, gated by the depth chance. */
    private static LootPoolSingletonContainer.Builder<?> rare(net.minecraft.world.level.ItemLike item,
            int baseCount) {
        return LootItem.lootTableItem(item)
                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(baseCount)))
                .apply(DEPTH_SCALED)
                .when(DEPTH_CHANCE);
    }

    private static final class Tables implements LootTableSubProvider {

        @Override
        public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
            output.accept(COMMON, LootTable.lootTable()
                    .withPool(builder()
                            .setRolls(UniformGenerator.between(1.0F, 2.0F))
                            .add(common(Items.IRON_INGOT, 2))
                            .add(common(Items.GOLD_INGOT, 1))
                            .add(common(Items.COAL, 3))
                            .add(rare(Items.DIAMOND, 1))
                            .add(rare(Items.EMERALD, 2))));
            output.accept(TREASURE, LootTable.lootTable()
                    .withPool(builder()
                            .setRolls(ConstantValue.exactly(1.0F))
                            .add(common(Items.GOLDEN_APPLE, 1))
                            .add(rare(Items.DIAMOND, 3))
                            .add(rare(Items.NETHERITE_SCRAP, 1))
                            .add(rare(Items.EMERALD_BLOCK, 1))));
        }

        private static net.minecraft.world.level.storage.loot.LootPool.Builder builder() {
            return net.minecraft.world.level.storage.loot.LootPool.lootPool();
        }
    }
}
