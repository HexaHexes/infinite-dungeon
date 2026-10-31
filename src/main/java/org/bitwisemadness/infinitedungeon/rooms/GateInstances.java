package org.bitwisemadness.infinitedungeon.rooms;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where gates are configured (issue #252): the merged view of datapack
 * instances ({@code data/<ns>/infinite_dungeon/gates/*.json}) and code
 * registration, read LIVE on every interaction and tick - never cached by the
 * block - so a {@code /reload} takes effect on already-loaded gates exactly
 * like it does for spawn pools.
 *
 * <p>A declaration may name a dimension; one that does applies there and only
 * there (the lobby and a dungeon could share coordinates), one that does not
 * is global - it applies in every dimension. Precedence: datapack beats code
 * (the profile registry's rule), and within a source the dimension-scoped
 * declaration beats the global one. A position with no declaration is an
 * unconfigured gate: closed, inert, never opening - configuration is
 * explicit, never assumed (the C3 posture applied to doors).</p>
 */
public final class GateInstances {

    private static Map<ResourceKey<Level>, Map<BlockPos, GateCondition>> scopedCode = new HashMap<>();
    private static Map<BlockPos, GateCondition> globalCode = new HashMap<>();
    private static Map<ResourceKey<Level>, Map<BlockPos, GateCondition>> scopedDatapack = new HashMap<>();
    private static Map<BlockPos, GateCondition> globalDatapack = new HashMap<>();

    private GateInstances() {
    }

    /** Declares a gate for one dimension (the content-authored path, for #47/#49). */
    public static void register(ResourceKey<Level> dimension, BlockPos pos, GateCondition condition) {
        scopedCode.computeIfAbsent(dimension, key -> new HashMap<>()).put(pos.immutable(), condition);
    }

    /** Declares a gate in every dimension (the dimension-less form). */
    public static void registerGlobal(BlockPos pos, GateCondition condition) {
        globalCode.put(pos.immutable(), condition);
    }

    /**
     * The counterpart: forgets a code-registered gate. The test teardowns use
     * it for the same reason the seals have one - harness state outlives a
     * run, so a fixture must undo its own registration. Content has no
     * unregister need yet; when a reload path grows a code-side diff, this is
     * the seam it will call.
     */
    public static void remove(ResourceKey<Level> dimension, BlockPos pos) {
        Map<BlockPos, GateCondition> gates = scopedCode.get(dimension);
        if (gates != null) {
            gates.remove(pos);
        }
    }

    /** The dimension-less counterpart of {@link #remove}. */
    public static void removeGlobal(BlockPos pos) {
        globalCode.remove(pos);
    }

    /** Replaces every datapack instance (the reload path; clears on reload). */
    public static void setDatapack(
            Map<ResourceKey<Level>, Map<BlockPos, GateCondition>> scoped,
            Map<BlockPos, GateCondition> global) {
        scopedDatapack = new HashMap<>();
        scoped.forEach((dimension, gates) -> scopedDatapack.put(dimension, Map.copyOf(gates)));
        globalDatapack = Map.copyOf(global);
    }

    /** The condition declared for the position, empty when unconfigured. */
    public static GateCondition conditionAt(Level level, BlockPos pos) {
        GateCondition hit = lookup(scopedDatapack, level.dimension(), pos);
        if (hit != null) {
            return hit;
        }
        hit = globalDatapack.get(pos);
        if (hit != null) {
            return hit;
        }
        hit = lookup(scopedCode, level.dimension(), pos);
        return hit != null ? hit : globalCode.get(pos);
    }

    private static GateCondition lookup(
            Map<ResourceKey<Level>, Map<BlockPos, GateCondition>> source,
            ResourceKey<Level> dimension,
            BlockPos pos) {
        Map<BlockPos, GateCondition> gates = source.get(dimension);
        return gates != null ? gates.get(pos) : null;
    }

    /** Clears every source (test teardown; no production caller). */
    public static void clear() {
        scopedCode = new HashMap<>();
        globalCode = new HashMap<>();
        scopedDatapack = new HashMap<>();
        globalDatapack = new HashMap<>();
    }
}
