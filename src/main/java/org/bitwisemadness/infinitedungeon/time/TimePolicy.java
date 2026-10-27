package org.bitwisemadness.infinitedungeon.time;

/**
 * The dungeon clock policy: whether time flows, and the tick it is held at
 * when it does not.
 *
 * <p>Pure, so the bounds and defaults are unit-testable without a game
 * runtime. The dimension type itself carries no fixed time - it is baked into
 * a frozen registry object and cannot be toggled at runtime - so the mod holds
 * the clock through this policy instead, and the shipped default keeps today's
 * frozen-noon experience.</p>
 *
 * @param flows     whether the dungeon clock runs
 * @param fixedTick the tick the clock is held at while {@code flows} is false
 */
public record TimePolicy(boolean flows, int fixedTick) {

    /** The vanilla day length in ticks. */
    public static final int DAY_LENGTH = 24000;

    /** The shipped default: frozen at noon. */
    public static final TimePolicy DEFAULT = new TimePolicy(false, 6000);

    public TimePolicy {
        fixedTick = Math.floorMod(fixedTick, DAY_LENGTH);
    }
}
