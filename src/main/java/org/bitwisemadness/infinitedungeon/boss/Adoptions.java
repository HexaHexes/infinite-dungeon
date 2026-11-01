package org.bitwisemadness.infinitedungeon.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Where content declares adopted bosses (issue #255, D40; home chosen by the
 * recorded ruling): {@code data/<ns>/infinite_dungeon/adoptions/*.json} with a
 * code seam beside it - the {@link GateInstances} merge posture. A declared
 * entity type appearing anywhere the declaration reaches is that context's
 * boss: tagged on spawn, presented with a bar, its defeats tracked credit-by
 * credit.
 *
 * <p>A declaration may name a dimension (scoped beats global), datapack beats
 * code, and lookup walks those four buckets in exactly that precedence. An
 * entity type no declaration covers is an ordinary mob - the join listener's
 * match <em>is</em> the gate, so ambient spawns are excluded structurally.
 * Reads are live, so {@code /reload} reconfigures what future spawns adopt.</p>
 */
public final class Adoptions {

    /**
     * One adoption: the declaration's identity (the datapack file's location,
     * or a code id), the entity type it adopts, and its optional dimension
     * (null = every dimension).
     *
     * @param id        stable identity - the defeat record's key and the tag's suffix
     * @param entityId  the adopted entity type's registry id
     * @param dimension the dimension this applies to, null for all of them
     */
    public record Declaration(ResourceLocation id, String entityId, ResourceKey<Level> dimension) {
    }

    private static Map<ResourceKey<Level>, Map<String, Declaration>> scopedCode = new HashMap<>();
    private static Map<String, Declaration> globalCode = new HashMap<>();
    private static Map<ResourceKey<Level>, Map<String, Declaration>> scopedDatapack = new HashMap<>();
    private static Map<String, Declaration> globalDatapack = new HashMap<>();

    private Adoptions() {
    }

    /** Declares an adopted type in code (the seam tests and future content use). */
    public static void register(Declaration declaration) {
        if (declaration.dimension() == null) {
            globalCode.put(declaration.entityId(), declaration);
        } else {
            scopedCode.computeIfAbsent(declaration.dimension(), key -> new HashMap<>())
                    .put(declaration.entityId(), declaration);
        }
    }

    /** Forgets a code declaration by identity (test teardown; no production caller). */
    public static void remove(ResourceLocation id) {
        globalCode.values().removeIf(declaration -> declaration.id().equals(id));
        scopedCode.values().forEach(bucket -> bucket.values().removeIf(d -> d.id().equals(id)));
    }

    /** Replaces every datapack declaration (the reload path; clears on reload). */
    public static void setDatapack(
            Map<ResourceKey<Level>, Map<String, Declaration>> scoped,
            Map<String, Declaration> global) {
        scopedDatapack = new HashMap<>();
        scoped.forEach((dimension, declarations) ->
                scopedDatapack.put(dimension, Map.copyOf(declarations)));
        globalDatapack = Map.copyOf(global);
    }

    /** The declaration covering this entity type here, empty when ordinary. */
    public static Optional<Declaration> find(ResourceKey<Level> dimension, String entityId) {
        Declaration hit = lookup(scopedDatapack, dimension, entityId);
        if (hit != null) {
            return Optional.of(hit);
        }
        hit = globalDatapack.get(entityId);
        if (hit != null) {
            return Optional.of(hit);
        }
        hit = lookup(scopedCode, dimension, entityId);
        return hit != null ? Optional.of(hit) : Optional.ofNullable(globalCode.get(entityId));
    }

    private static Declaration lookup(
            Map<ResourceKey<Level>, Map<String, Declaration>> source,
            ResourceKey<Level> dimension,
            String entityId) {
        Map<String, Declaration> declarations = source.get(dimension);
        return declarations == null ? null : declarations.get(entityId);
    }

    /** Clears every source (test teardown; no production caller). */
    public static void clear() {
        scopedCode = new HashMap<>();
        globalCode = new HashMap<>();
        scopedDatapack = new HashMap<>();
        globalDatapack = new HashMap<>();
    }
}
