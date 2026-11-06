package org.bitwisemadness.infinitedungeon.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.death.DeathPolicy;
import org.bitwisemadness.infinitedungeon.depth.ProfileCurves;
import org.bitwisemadness.infinitedungeon.spawn.SpawnPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.CurveCoefficients;
import org.bitwisemadness.infinitedungeonapi.profile.CurveOverrides;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

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
}
