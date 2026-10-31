package org.bitwisemadness.infinitedungeon.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.bitwisemadness.infinitedungeonapi.data.ResourceRef;
import org.junit.jupiter.api.Test;

/**
 * The pool selection math (issue #39): layer bands with their default edges,
 * and the weighted pick's cumulative-draw rule including its clamps - pure
 * arithmetic, no Minecraft runtime (the rule the #200 gate made structural).
 */
public class PoolMathTest {

    private static SpawnPoolEntry entry(int min, int max) {
        return new SpawnPoolEntry(ResourceRef.parse("minecraft:zombie"), 1, min, max, Optional.empty());
    }

    @Test
    void layerBandIsInclusiveOnBothEnds() {
        SpawnPoolEntry band = entry(2, 4);
        assertTrue(!band.inLayer(1));
        assertTrue(band.inLayer(2));
        assertTrue(band.inLayer(3));
        assertTrue(band.inLayer(4));
        assertTrue(!band.inLayer(5));
    }

    @Test
    void defaultBandServesEveryLayer() {
        SpawnPoolEntry open = new SpawnPoolEntry(ResourceRef.parse("minecraft:zombie"), 1,
                0, Integer.MAX_VALUE, Optional.empty());
        assertTrue(open.inLayer(0));
        assertTrue(open.inLayer(9999));
        assertTrue(!open.inLayer(-1));
    }

    @Test
    void filterPreservesInputOrderAndDropsOutsiders() {
        SpawnPoolEntry early = new SpawnPoolEntry(ResourceRef.parse("minecraft:zombie"), 1, 0, 1, Optional.empty());
        SpawnPoolEntry late = new SpawnPoolEntry(ResourceRef.parse("minecraft:creeper"), 1, 5, 9, Optional.empty());
        List<SpawnPoolEntry> kept = PoolMath.filterByLayer(List.of(early, late), 0);
        assertEquals(1, kept.size());
        assertEquals(early, kept.get(0));
        assertEquals(0, PoolMath.filterByLayer(List.of(early, late), 3).size());
        assertEquals(0, PoolMath.filterByLayer(List.of(), 0).size());
    }

    @Test
    void entryValidationRefusesImpossibleShapes() {
        assertThrows(IllegalArgumentException.class, () -> new SpawnPoolEntry(
                ResourceRef.parse("minecraft:zombie"), 0, 0, 1, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new SpawnPoolEntry(
                ResourceRef.parse("minecraft:zombie"), 1, 4, 2, Optional.empty()));
    }

    @Test
    void pickOfNothingIsNothing() {
        assertEquals(Optional.empty(), PoolMath.pick(List.of(), 0.5));
    }

    @Test
    void pickFollowsCumulativeWeights() {
        PoolCandidate small = new PoolCandidate("minecraft:zombie", 1);
        PoolCandidate large = new PoolCandidate("minecraft:creeper", 3);
        List<PoolCandidate> both = List.of(small, large);
        // Total 4: the boundary sits at cumulative weight 1 -> 0.25.
        assertEquals(small, PoolMath.pick(both, 0.0).orElseThrow());
        assertEquals(small, PoolMath.pick(both, 0.24).orElseThrow());
        assertEquals(large, PoolMath.pick(both, 0.25).orElseThrow());
        assertEquals(large, PoolMath.pick(both, 0.999).orElseThrow());
        // Clamps: at/above the total -> last; negative -> first.
        assertEquals(large, PoolMath.pick(both, 1.0).orElseThrow());
        assertEquals(small, PoolMath.pick(both, -1.0).orElseThrow());
    }

    @Test
    void pickWalksThreeCandidates() {
        PoolCandidate a = new PoolCandidate("a", 1);
        PoolCandidate b = new PoolCandidate("b", 2);
        PoolCandidate c = new PoolCandidate("c", 1);
        List<PoolCandidate> three = List.of(a, b, c);
        assertEquals(a, PoolMath.pick(three, 0.0).orElseThrow());
        assertEquals(b, PoolMath.pick(three, 0.3).orElseThrow());
        assertEquals(b, PoolMath.pick(three, 0.74).orElseThrow());
        assertEquals(c, PoolMath.pick(three, 0.75).orElseThrow());
        assertEquals(b, PoolMath.pick(three, 0.5).orElseThrow());
    }
}
