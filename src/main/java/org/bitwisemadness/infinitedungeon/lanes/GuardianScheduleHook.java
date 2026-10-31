package org.bitwisemadness.infinitedungeon.lanes;

import java.util.Optional;
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
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return;
        }
        LayerUnlocks.setGuardianAssigned(layer -> guardianIdFor(level, layer).isPresent());
    }

    /**
     * The layer's guardian id for a live level: THE single resolution path -
     * the seam predicate (above) and #42's kill reaction both ask here, so
     * they can never disagree about who the guardian is. Profile-over-config
     * band percent, difficulty coefficients and world seed resolve together.
     * Config reads happen at call time (portal checks, kill reactions, tests),
     * never at mod construction.
     */
    public static Optional<String> guardianIdFor(ServerLevel level, int layer) {
        String dimensionId = level.dimension().location().toString();
        double bandPercent = DimensionProfiles.get(dimensionId)
                .flatMap(profile -> profile.guardian())
                .map(GuardianRules::powerBandPercent)
                .orElseGet(DungeonConfigs::bossPowerBandPercent);
        return GuardianSchedule.select(layer, level.getSeed(),
                DungeonConfigs.difficultyBase(),
                DungeonConfigs.difficultyLinearPerFloor(),
                DungeonConfigs.difficultyQuadraticPerLayer(),
                bandPercent);
    }
}
