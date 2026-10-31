package org.bitwisemadness.infinitedungeon.lanes;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.GuardianRules;

/**
 * The schedule's production wiring (issue #231): on the first dungeon level
 * load - the same push-at-load pattern the #37 config toggle established,
 * config values being unreadable at mod construction - install the guardian
 * predicate behind {@link LayerUnlocks}' single seam.
 *
 * <p>The band percent resolves profile-over-config: a dimension profile's
 * {@code guardian.powerBandPercent} wins, {@code boss.powerBandPercent} is
 * the default - the first core consumer of a resolved profile setting (#162
 * tracks generalizing the read). The predicate closes over the world seed and
 * the roster as it stands; an empty roster (interim until #60 and the family
 * modules feed it) assigns nothing, so every transition stays open exactly as
 * #31's contract promises. Only dungeon dimensions install - the harness
 * never does, so gametests keep whatever seam state they set themselves.</p>
 */
public final class GuardianScheduleHook {

    private GuardianScheduleHook() {
    }

    /** Installs the seam predicate when a dungeon level loads. */
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String dimensionId = level.dimension().location().toString();
        if (!DimensionProfiles.isDungeonDimension(dimensionId)) {
            return;
        }
        double bandPercent = DimensionProfiles.get(dimensionId)
                .flatMap(profile -> profile.guardian())
                .map(GuardianRules::powerBandPercent)
                .orElseGet(DungeonConfigs::bossPowerBandPercent);
        long worldSeed = level.getSeed();
        double base = DungeonConfigs.difficultyBase();
        double linear = DungeonConfigs.difficultyLinearPerFloor();
        double quadratic = DungeonConfigs.difficultyQuadraticPerLayer();
        LayerUnlocks.setGuardianAssigned(layer -> GuardianSchedule.select(
                layer, worldSeed, base, linear, quadratic, bandPercent).isPresent());
    }
}
