package org.bitwisemadness.infinitedungeon.time;

/**
 * The dungeon clock policy: whether time flows, and the tick it is held at
 * when it does not.
 *
 * <p>Pure, so the bounds and defaults are unit-testable without a game
 * runtime. The dimension type itself carries no fixed time - it is baked into
 * a frozen registry object and cannot be toggled at runtime - so the mod owns
 * the clock through this policy instead, and the shipped default lets the
 * dungeon run a plain day/night cycle.</p>
 *
 * @param flows     whether the dungeon clock runs
 * @param fixedTick the tick the clock is held at while {@code flows} is false
 */
public record TimePolicy(boolean flows, int fixedTick) {

    /** The vanilla day length in ticks. */
    public static final int DAY_LENGTH = 24000;

    /** The shipped default: the clock runs; fixedTick applies only when held. */
    public static final TimePolicy DEFAULT = new TimePolicy(true, 6000);

    public TimePolicy {
        fixedTick = Math.floorMod(fixedTick, DAY_LENGTH);
    }
}
