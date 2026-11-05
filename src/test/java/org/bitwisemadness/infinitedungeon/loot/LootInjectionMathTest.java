package org.bitwisemadness.infinitedungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The injection-selection truth table (issue #52). */
class LootInjectionMathTest {

    private static LootInjection injection(int minLayer, int maxLayer, double chance) {
        return new LootInjection("test:table", minLayer, maxLayer, chance, Optional.empty());
    }

    @Test
    void theBandFilterKeepsOnlyTheServingLayer() {
        List<LootInjection> all = List.of(
                injection(1, 3, 1.0D),
                injection(4, 9, 1.0D),
                injection(2, 5, 1.0D));
        assertEquals(2, LootInjectionMath.filterByLayer(all, 2).size(),
                "bands 1-3 and 2-5 both serve layer 2");
        assertEquals(1, LootInjectionMath.filterByLayer(all, 7).size(),
                "only band 4-9 serves layer 7");
        assertEquals(0, LootInjectionMath.filterByLayer(all, 0).size(),
                "no band serves layer 0");
    }

    @Test
    void theChanceIsAStrictThreshold() {
        assertTrue(LootInjectionMath.passesChance(1.0D, 0.0D), "chance 1 always fires");
        assertTrue(LootInjectionMath.passesChance(0.5D, 0.49D), "a draw below the chance fires");
        assertFalse(LootInjectionMath.passesChance(0.5D, 0.5D), "a draw at the chance does not fire");
        assertFalse(LootInjectionMath.passesChance(0.0D, 0.0D), "chance 0 never fires");
    }
}
