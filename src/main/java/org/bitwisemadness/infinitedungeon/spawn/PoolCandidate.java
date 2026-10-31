package org.bitwisemadness.infinitedungeon.spawn;

/**
 * One concrete spawn candidate: an entity id that survived layer band,
 * required-mod, and reference expansion, carrying the weight of the entry it
 * came from (a tag entry contributes one candidate per member, each with the
 * entry's weight). Pure - no Minecraft types.
 */
public record PoolCandidate(String entityId, int weight) {

    public PoolCandidate {
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be at least 1, got " + weight);
        }
    }
}
