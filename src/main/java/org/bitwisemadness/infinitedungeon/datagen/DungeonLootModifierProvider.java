package org.bitwisemadness.infinitedungeon.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.loot.DepthInjectionModifier;

/**
 * Emits the depth-injection modifier's activation JSON (issue #52):
 * {@code data/infinite_dungeon/loot_modifiers/depth_injection.json}. Datagen-only.
 */
final class DungeonLootModifierProvider extends GlobalLootModifierProvider {

    DungeonLootModifierProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, InfiniteDungeon.MOD_ID);
    }

    @Override
    protected void start() {
        add("depth_injection", new DepthInjectionModifier(new LootItemCondition[0]));
    }
}
