package org.bitwisemadness.infinitedungeon.gametest;

import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.death.DeathPolicy;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.depth.ProfileCurves;
import org.bitwisemadness.infinitedungeon.depth.ProfileFloors;
import org.bitwisemadness.infinitedungeon.lanes.FloorSchedule;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.CurveCoefficients;
import org.bitwisemadness.infinitedungeonapi.profile.CurveOverrides;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.FloorsPerLayer;

/**
 * The second-profile acceptance (issue #162): a dimension with a registered
 * profile - any addon's future dimension - is treated as a dungeon dimension
 * by every string-seam consumer, and its curve overrides resolve over the
 * config defaults. The level-tick consumers funnel through the same registry
 * query ({@code DungeonDimensions.isDungeon}), so the registry answer IS the
 * gate; the tick behaviours themselves stay covered by the core-dimension
 * suite. The registration is removed in a finally-style tail so the suite's
 * other tests never see the second dimension.
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProfileDimensionsGameTests {

    private static final String SECOND = "infinite_dungeon:gametest_second";

    private ProfileDimensionsGameTests() {
    }

    /** A registered second profile engages every consumer seam, then is removed. */
    @GameTest(template = "empty")
    public static void second_profile_engages_all_consumers(GameTestHelper helper) {
        try {
            DimensionProfiles.register(DimensionProfile.forDimension(SECOND)
                    .sanitised());
            helper.assertTrue(DimensionProfiles.isDungeonDimension(SECOND),
                    "the registered profile must resolve");
            helper.assertTrue(SpawnPolicy.isDungeon(SECOND),
                    "spawn policy must treat the second profile as a dungeon dimension");
            helper.assertTrue(DeathPolicy.returnsToLobby(SECOND),
                    "death policy must route the second profile to the lobby");
            helper.assertTrue(SpawnPolicy.refusesSpawnpointCommand(SECOND),
                    "the spawn-point command must be refused in the second profile");
            // The core dimension keeps its own shipped profile alongside.
            helper.assertTrue(SpawnPolicy.isDungeon("infinite_dungeon:dungeon"),
                    "the core dimension's profile must survive a second registration");
        } finally {
            DimensionProfiles.remove(SECOND);
        }
        helper.assertTrue(!SpawnPolicy.isDungeon(SECOND),
                "the removed profile must disengage every consumer again");
        helper.succeed();
    }

    /** Profile curve overrides resolve over the config defaults, per dimension. */
    @GameTest(template = "empty")
    public static void profile_curve_overrides_resolve(GameTestHelper helper) {
        double[] coreDefaults = ProfileCurves.magicule("infinite_dungeon:dungeon");
        try {
            DimensionProfiles.register(new DimensionProfile(SECOND,
                    java.util.Optional.empty(), java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.of(new CurveOverrides(
                            java.util.Optional.empty(),
                            java.util.Optional.of(new CurveCoefficients(500.0, 7.5, 0.25)),
                            java.util.Optional.empty())),
                    java.util.Optional.empty(), java.util.Optional.empty(),
                    java.util.Optional.empty(), java.util.Optional.empty(),
                    java.util.Optional.empty()).sanitised());
            double[] overridden = ProfileCurves.magicule(SECOND);
            helper.assertTrue(Math.abs(overridden[0] - 500.0) < 1.0e-9
                    && Math.abs(overridden[1] - 7.5) < 1.0e-9
                    && Math.abs(overridden[2] - 0.25) < 1.0e-9,
                    "the profile's magicule coefficients must win: " + overridden[0]);
            helper.assertTrue(Math.abs(coreDefaults[0] - 500.0) > 1.0e-9,
                    "the core dimension must keep its own coefficients");
        } finally {
            DimensionProfiles.remove(SECOND);
        }
        helper.succeed();
    }

    /** Floor bounds resolve profile-over-config: absent falls to the config, a declared pair wins (issue #81). */
    @GameTest(template = "empty")
    public static void profile_floors_override_the_config_defaults(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int configMin = DungeonConfigs.floorsPerLayerMin();
        int configMax = DungeonConfigs.floorsPerLayerMax();
        helper.assertTrue(ProfileFloors.of(SECOND).min() == configMin
                        && ProfileFloors.of(SECOND).max() == configMax,
                "without a profile the config pair must supply the bounds, got "
                        + ProfileFloors.of(SECOND).describe());
        LayoutParameters defaulted = LayoutParameters.forServer(level);
        helper.assertTrue(defaulted.floorsMin() == configMin && defaulted.floorsMax() == configMax,
                "the level read must fall to the config bounds too, got ["
                        + defaulted.floorsMin() + ", " + defaulted.floorsMax() + "]");
        try {
            DimensionProfiles.register(new DimensionProfile(SECOND,
                    Optional.empty(), Optional.of(new FloorsPerLayer(3, 4)),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty()).sanitised());
            FloorsPerLayer overridden = ProfileFloors.of(SECOND);
            helper.assertTrue(overridden.min() == 3 && overridden.max() == 4,
                    "the profile's floor pair must win, got " + overridden.describe());
            helper.assertTrue(ProfileFloors.floorsInLayer(SECOND, level.getSeed(), 1)
                            == FloorSchedule.floorsInLayer(level.getSeed(), 1, 3, 4),
                    "the layer derivation must run on the profile's bounds");
        } finally {
            DimensionProfiles.remove(SECOND);
        }
        helper.assertTrue(ProfileFloors.of(SECOND).min() == configMin,
                "removing the profile must restore the config default");
        helper.succeed();
    }
}
