package org.bitwisemadness.infinitedungeon.loot;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * Registration for the depth-injection serializer (issue #52). The modifier is
 * activated by its datapack JSON ({@code data/infinite_dungeon/loot_modifiers/}
 * {@code depth_injection.json}), which datagen ships.
 */
public final class LootModifiers {

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    InfiniteDungeon.MOD_ID);

    /** The {@code infinite_dungeon:depth_injection} modifier serializer. */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<DepthInjectionModifier>> DEPTH_INJECTION =
            SERIALIZERS.register("depth_injection", () -> DepthInjectionModifier.CODEC);

    private LootModifiers() {
    }

    /** Registers the serializer on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
