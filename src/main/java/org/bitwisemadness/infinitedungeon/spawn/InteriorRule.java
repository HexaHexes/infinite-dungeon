package org.bitwisemadness.infinitedungeon.spawn;

/**
 * The interior-bounds rule as pure math (issue #254).
 *
 * <p>In a dimension whose only terrain is structures over void, a position
 * at or above its column's surface is exterior - a roof, a void cell, a band
 * gap, a ledge top. Exposure is the surface-heightmap comparison (not the
 * light engine's sky visibility): heightmaps update synchronously with the
 * block that caused them, so the answer never races a light update. The rule
 * is exposure plus a support guard; the caller supplies every fact, and this
 * class never reads the world, the config, or the dimension itself.</p>
 *
 * <p>Only natural spawns in the mod's own dimension are ever denied
 * (event spawns are code-positioned at designed interiors, and the overworld
 * keeps vanilla behaviour - its outdoors are supposed to be sky-visible).</p>
 */
public final class InteriorRule {

    private InteriorRule() {
    }

    /**
     * Whether this spawn is denied for sitting outside an interior.
     *
     * @param interiorOnly    the config toggle
     * @param naturalSpawn    whether the spawn is NATURAL
     * @param inDungeonDimension whether the level is the mod's dungeon dimension
     * @param exposed         whether the position sits at or above its column's
     *                        surface heightmap (nothing above it)
     * @param supported       whether a sturdy floor lies below the position
     */
    public static boolean denies(boolean interiorOnly,
                                 boolean naturalSpawn,
                                 boolean inDungeonDimension,
                                 boolean exposed,
                                 boolean supported) {
        if (!interiorOnly || !naturalSpawn || !inDungeonDimension) {
            return false;
        }
        return exposed || !supported;
    }
}
