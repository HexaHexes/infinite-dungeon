package org.bitwisemadness.infinitedungeon.loot;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * Registration for the depth-driven loot pieces (issue #51): the
 * {@code depth_scaled} function and the {@code depth_chance} condition.
 */
public final class LootFunctions {

    private static final DeferredRegister<LootItemFunctionType<?>> FUNCTION_TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, InfiniteDungeon.MOD_ID);
    private static final DeferredRegister<LootItemConditionType> CONDITION_TYPES =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, InfiniteDungeon.MOD_ID);

    /** The {@code infinite_dungeon:depth_scaled} loot function. */
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<DepthScaledFunction>>
            DEPTH_SCALED = FUNCTION_TYPES.register("depth_scaled",
                    () -> new LootItemFunctionType<>(DepthScaledFunction.CODEC));

    /** The {@code infinite_dungeon:depth_chance} loot condition. */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType>
            DEPTH_CHANCE = CONDITION_TYPES.register("depth_chance",
                    () -> new LootItemConditionType(DepthChanceCondition.CODEC));

    private LootFunctions() {
    }

    /** Registers both types on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        FUNCTION_TYPES.register(modBus);
        CONDITION_TYPES.register(modBus);
    }
}
