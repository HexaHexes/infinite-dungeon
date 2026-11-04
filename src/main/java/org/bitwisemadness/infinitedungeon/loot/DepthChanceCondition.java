package org.bitwisemadness.infinitedungeon.loot;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The rare-entry gate (issue #51): passes with probability
 * {@code loot.qualityPerPoint * D} at the loot origin. Tables attach it to their
 * rare entries, so those entries' share of the pool grows with depth while
 * common entries are unaffected.
 */
public final class DepthChanceCondition implements LootItemCondition {

    /** The single stateless instance the codec serializes. */
    public static final DepthChanceCondition INSTANCE = new DepthChanceCondition();

    /** No fields; the JSON is just {@code {"condition": ".../depth_chance"}}. */
    public static final MapCodec<DepthChanceCondition> CODEC = MapCodec.unit(INSTANCE);

    private DepthChanceCondition() {
    }

    @Override
    public LootItemConditionType getType() {
        return LootFunctions.DEPTH_CHANCE.get();
    }

    @Override
    public boolean test(LootContext context) {
        double difficulty = LootOrigin.difficultyAt(context);
        if (Double.isNaN(difficulty)) {
            return false;
        }
        return context.getRandom().nextDouble()
                < LootScaling.rareChance(difficulty, DungeonConfigs.lootQualityPerPoint());
    }
}
