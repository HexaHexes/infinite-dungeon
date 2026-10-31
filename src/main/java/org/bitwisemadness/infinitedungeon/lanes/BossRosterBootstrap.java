package org.bitwisemadness.infinitedungeon.lanes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPools;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.stats.EntityPowerStats;
import org.bitwisemadness.infinitedungeonapi.stats.StatKind;

/**
 * The boss roster bootstrap (issue #60, plan section 6/13): turns the
 * profile's boss pool into the {@link GuardianSchedule} roster - the
 * {@code setRoster} seam #231 left as "the single seam". Runs on the first
 * dungeon level load: for every concrete id the pool yields (mod-gated, so a
 * pack without the boss mods contributes nothing), a TRANSIENT entity is
 * constructed, its EP read through the API's power stat service (providers
 * first, the documented attribute fallback otherwise - the service's own
 * contract), and discarded without ever being added to the world.
 *
 * <p>Tensura/NEB families carry {@code depthExempt=true} and a family id, but
 * that ranking belongs to the tensura integration module; the defaults here
 * are plain candidates ({@code depthExempt=false}, no family) so cross-family
 * interleaving is free exactly as the plan says. EP is the plan's default
 * power stat. Ids that fail to construct, or whose fallback reads as
 * non-positive, are skipped - the honest empty over an invented power.</p>
 *
 * <p>Interim contract (#231): an absent boss pool, an absent profile, or an
 * all-filtered pool yields an EMPTY roster, so nothing is assigned and every
 * transition stays open. The listener installs the roster before the seam
 * predicate is consulted, but the predicate reads the roster lazily on each
 * portal check, so registration order never matters.</p>
 */
public final class BossRosterBootstrap {

    private BossRosterBootstrap() {
    }

    /** Installs the roster when a dungeon level loads. */
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String dimensionId = level.dimension().location().toString();
        if (!DimensionProfiles.isDungeonDimension(dimensionId)) {
            return;
        }
        Optional<String> bossPool = DimensionProfiles.get(dimensionId)
                .flatMap(profile -> profile.pools())
                .flatMap(pools -> pools.boss());
        if (bossPool.isEmpty()) {
            // No boss pool named: the interim open-gate state stands.
            GuardianSchedule.setRoster(List.of());
            return;
        }
        GuardianSchedule.setRoster(derive(level, bossPool.get()));
    }

    /**
     * The roster for a pool: one transient entity per id, EP read, entity
     * discarded, never added. Public as the derivation entry - the load hook
     * calls it, and it is the seam a consumer (or test) uses to re-derive the
     * roster on demand, e.g. after a {@code /reload} changes the boss pool
     * (the hook itself runs at level load only, so a roster edited by reload
     * applies on the next load - documented staleness, matching the
     * datapack-first posture).
     */
    public static List<BossCandidate> derive(ServerLevel level, String poolId) {
        List<BossCandidate> candidates = new ArrayList<>();
        for (String id : SpawnPools.allIds(poolId)) {
            var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            // ENTITY_TYPE is a DefaultedRegistry: get() never returns null,
            // and allIds presence-checked the ids, so it resolves to itself.
            var created = type.create(level);
            if (!(created instanceof LivingEntity living)) {
                continue; // abstract/non-living/create refused: no invented power
            }
            double ep = EntityPowerStats.of(living, StatKind.EP);
            living.discard(); // never added: a flag-set, no world interaction
            if (ep > 0.0) {
                candidates.add(new BossCandidate(id, ep, false, Optional.empty()));
            }
        }
        return List.copyOf(candidates);
    }
}
