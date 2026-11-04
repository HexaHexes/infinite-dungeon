package org.bitwisemadness.infinitedungeon.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The quantity half of depth-driven loot (issue #51): scales a rolled entry's
 * count by {@code 1 + loot.quantityPerPoint * D} at the loot origin, clamped to
 * the stack's max size. Outside the dungeon the count is untouched.
 */
public final class DepthScaledFunction extends LootItemConditionalFunction {

    public static final MapCodec<DepthScaledFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> commonFields(instance).apply(instance, DepthScaledFunction::new));

    /** Public for the datagen builder's method reference (the codec's factory). */
    public DepthScaledFunction(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType<? extends LootItemConditionalFunction> getType() {
        return LootFunctions.DEPTH_SCALED.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        double difficulty = LootOrigin.difficultyAt(context);
        if (Double.isNaN(difficulty)) {
            return stack;
        }
        stack.setCount(LootScaling.scaledCount(stack.getCount(), difficulty,
                DungeonConfigs.lootQuantityPerPoint(), stack.getMaxStackSize()));
        return stack;
    }
}
