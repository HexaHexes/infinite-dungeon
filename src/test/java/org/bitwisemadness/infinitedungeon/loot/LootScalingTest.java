package org.bitwisemadness.infinitedungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The depth-scaling truth table (issue #51). */
class LootScalingTest {

    @Test
    void theCountScalesWithDifficulty() {
        assertEquals(1, LootScaling.scaledCount(1, 0.0D, 0.05D, 64),
                "D=0 leaves the base count untouched");
        assertEquals(2, LootScaling.scaledCount(1, 20.0D, 0.05D, 64),
                "1 * (1 + 0.05*20) = 2");
        assertEquals(3, LootScaling.scaledCount(2, 10.0D, 0.05D, 64),
                "2 * (1 + 0.05*10) = 3");
    }

    @Test
    void theCountClampsAtOneAndMaxStack() {
        assertEquals(1, LootScaling.scaledCount(1, 0.0D, 10.0D, 64),
                "a count never drops below 1");
        assertEquals(64, LootScaling.scaledCount(1, 100000.0D, 0.05D, 64),
                "a count never exceeds the stack's max size");
    }

    @Test
    void theRareChanceGrowsWithDifficultyAndClamps() {
        assertEquals(0.0D, LootScaling.rareChance(0.0D, 0.02D), 1.0E-9D,
                "D=0 never draws the rare sub-pool");
        assertEquals(0.4D, LootScaling.rareChance(20.0D, 0.02D), 1.0E-9D,
                "0.02 * 20 = 0.4");
        assertEquals(1.0D, LootScaling.rareChance(100000.0D, 0.02D), 1.0E-9D,
                "the chance clamps at 1");
    }
}
