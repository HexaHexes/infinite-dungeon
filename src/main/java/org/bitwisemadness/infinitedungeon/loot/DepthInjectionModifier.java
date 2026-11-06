package org.bitwisemadness.infinitedungeon.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeonapi.data.RequiredModFilter;

/**
 * The depth-injection global loot modifier (issue #52): on any loot-table roll
 * inside the dungeon dimension it injects the layer's configured tables
 * ({@link LootInjections}), so Dungeon Crawl's containers - which we do not own
 * - carry our depth-driven loot.
 *
 * <p>Injected tables are filled with {@code getRandomItemsRaw} so the modifier
 * never re-enters itself. Outside the dungeon dimension, or west of the
 * dungeon, it is a no-op.</p>
 */
public final class DepthInjectionModifier extends LootModifier {

    public static final MapCodec<DepthInjectionModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, DepthInjectionModifier::new));

    public DepthInjectionModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    // getRandomItemsRaw is deprecated because the non-raw path re-applies
    // global loot modifiers - exactly the re-entry this modifier must avoid.
    @SuppressWarnings("deprecation")
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ServerLevel level = context.getLevel();
        if (!DungeonDimensions.LEVEL.equals(level.dimension())) {
            return loot;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return loot;
        }
        BlockPos position = BlockPos.containing(origin);
        LayoutParameters parameters = LayoutParameters.forServer(level);
        if (!DungeonLayout.inDungeon(parameters, position.getX())) {
            return loot;
        }
        int layer = DungeonLayout.derive(parameters, position.getX(), position.getY()).layerIndex();
        List<LootInjection> allowed = RequiredModFilter.filter(
                LootInjectionMath.filterByLayer(LootInjections.all(), layer),
                LootInjection::requiredMod,
                modId -> ModList.get().isLoaded(modId));
        String theme = level.dimension().location().toString();
        for (LootInjection injection : allowed) {
            if (!LootInjectionMath.passesChance(injection.chance(), context.getRandom().nextDouble())) {
                continue;
            }
            Optional<String> tableId = injection.table().isPresent()
                    ? injection.table()
                    : LootSlots.resolve(injection.slot().orElseThrow(), theme, layer);
            if (tableId.isEmpty()) {
                continue;
            }
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.parse(tableId.get()));
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            if (table != null) {
                table.getRandomItemsRaw(context, loot::add);
            }
        }
        return loot;
    }
}
