package org.bitwisemadness.infinitedungeon.lanes;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.infinitedungeonapi.event.BossSlainEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerCompletedEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * The gating reaction (issue #42, plan section 6): a guardian's fall opens
 * its layer's transition, permanently, in world saved data.
 *
 * <p>The listener consumes the API's {@link BossSlainEvent} - which already
 * carries dimension, layer, and the boss - so no position derivation is
 * needed: dimension gate, then the layer's guardian id through
 * {@link GuardianScheduleHook#guardianIdFor} (the SAME resolver the seam
 * predicate uses, so install and reaction can never disagree), then id
 * equality against the slain entity. {@link LayerUnlocks#unlock} is
 * idempotent and only ever opens (the plan's decision record: "the unlock
 * persists once a guardian falls" - respawned guardians of #43 never re-seal).</p>
 *
 * <p><b>C7a (via issue #79, landing here):</b> the flags are world saved
 * data - one set per world - so a kill by any party member reads as open for
 * every other player the instant it lands, a superset of the ruled
 * team-share. No PartySizeResolver call belongs at unlock time: precedence
 * resolves per-player READS, and the gate read is not one. A future
 * per-player flag model would revisit this note (recorded on #42's design).</p>
 *
 * <p><b>Poster chain (issue #249):</b> {@code BossSlainEvent} is posted by
 * the guardian chamber's death side (the #43 spawn loop), and THIS listener
 * is the {@code LayerCompletedEvent} poster: the first validated guardian
 * fall per layer per world announces completion exactly where the unlock
 * transition proves it - respawn deaths never re-fire. The chain carries no
 * player-presence guard, so an environmental death with zero players present
 * still completes the layer; consumers treat that as unknown attribution
 * rather than player credit (U-10, HexaHexes/tensura-quests#7). The
 * completion forwards this fall's attributable killer from the
 * {@code BossSlainEvent} payload (the API's attribution, added by
 * HexaHexes/infinite-dungeon-api#86 and carried since {@code
 * api_version=1.0.0-alpha.13}); an environmental fall forwards the
 * documented unknown.</p>
 */
public final class GuardianGating {

    private GuardianGating() {
    }

    /** One boss death: unlock its layer when the boss was the guardian. */
    public static void onBossSlain(BossSlainEvent event) {
        if (!DimensionProfiles.isDungeonDimension(event.dimension().location().toString())) {
            return;
        }
        if (!(event.boss().level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(event.dimension())) {
            return; // payload and boss disagree: never unlock on a mismatch
        }
        Optional<String> guardian = GuardianScheduleHook.guardianIdFor(level, event.layer());
        if (guardian.isEmpty()) {
            return; // no roster, no guardian: no kill gates anything
        }
        String slain = BuiltInRegistries.ENTITY_TYPE.getKey(event.boss().getType()).toString();
        if (!guardian.get().equals(slain)) {
            return; // a boss, but not THIS layer's guardian
        }
        boolean firstFall = LayerUnlocks.of(level).unlock(event.layer());
        if (firstFall) {
            // The completion announcement: first validated guardian fall per
            // layer per world (the unlock transition proves "first").
            // Completer = this fall's attributable killer forwarded from the
            // BossSlainEvent payload (api#86); empty = environmental fall,
            // ADR-0003's documented unknown.
            NeoForge.EVENT_BUS.post(new LayerCompletedEvent(event.dimension(), event.layer(),
                    event.killingPlayer().orElse(null)));
        }
    }
}
