package org.bitwisemadness.infinitedungeon.rooms;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * What a gate declaration says (issue #255): its condition plus everything
 * that condition needs to evaluate. {@link GateCondition#KEY} and
 * {@link GateCondition#LAYER_DEFEATED} need only the condition; a
 * {@link GateCondition#TRIALS_COMPLETE} gate names the trial spawners of its
 * context - explicit, never assumed, so an undeclared position is never
 * scoured from the world to answer a seal question.
 *
 * <p>Positions are absolute (the datapack author controls them and a datagen
 * step can emit them), mirroring how the gate's own position is read.</p>
 *
 * @param condition      how the gate opens
 * @param trialSpawners  the trial spawners this gate waits on; empty for
 *                       conditions that do not consult them
 */
public record GateDeclaration(GateCondition condition, List<BlockPos> trialSpawners) {

    public GateDeclaration {
        if (condition == null) {
            throw new IllegalArgumentException("a gate declaration needs a condition");
        }
        trialSpawners = trialSpawners == null ? List.of() : List.copyOf(trialSpawners);
    }

    /** A declaration for conditions that need nothing beyond themselves. */
    public static GateDeclaration of(GateCondition condition) {
        return new GateDeclaration(condition, List.of());
    }
}
