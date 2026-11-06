package org.bitwisemadness.infinitedungeon.gametest;

import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.portal.PortalRules;
import org.bitwisemadness.infinitedungeon.portal.TravelPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.PortalPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode;

/**
 * The per-profile portal acceptance (issue #163): two registered profiles
 * yield two independent destinations and return behaviours - the exit mode,
 * the construction gate and the anchor re-form rule resolve per dimension,
 * and the travel decision matrix routes them apart. Registrations are
 * removed in finally-style tails so the suite's other tests never see the
 * second profile.
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProfilePortalGameTests {

    private static final String SECOND = "infinite_dungeon:gametest_portal";

    private ProfilePortalGameTests() {
    }

    /** A profile's PortalPolicy overrides the config rules for its dimension. */
    @GameTest(template = "empty")
    public static void portal_rules_resolve_per_profile(GameTestHelper helper) {
        try {
            DimensionProfiles.register(new DimensionProfile(SECOND,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(),
                    Optional.of(new PortalPolicy(PortalReturnMode.WORLD_SPAWN, false, false)),
                    Optional.empty(), Optional.empty(), Optional.empty()).sanitised());
            helper.assertTrue(PortalRules.returnMode(SECOND) == PortalReturnMode.WORLD_SPAWN,
                    "the second profile's exit mode must win");
            helper.assertTrue(!PortalRules.allowConstruction(SECOND),
                    "the second profile's construction refusal must win");
            helper.assertTrue(!PortalRules.reformMissingPortal(SECOND),
                    "the second profile's reform refusal must win");
            // The core dimension keeps its own shipped profile's rules.
            helper.assertTrue(PortalRules.returnMode("infinite_dungeon:dungeon")
                    == PortalReturnMode.ENTRY_PORTAL,
                    "the core dimension must keep its entry-portal exit");
        } finally {
            DimensionProfiles.remove(SECOND);
        }
        helper.succeed();
    }

    /** The travel decision matrix routes the two profiles' exits apart. */
    @GameTest(template = "empty")
    public static void travel_decision_matrix_second_profile(GameTestHelper helper) {
        try {
            DimensionProfiles.register(new DimensionProfile(SECOND,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(),
                    Optional.of(new PortalPolicy(PortalReturnMode.WORLD_SPAWN, false, false)),
                    Optional.empty(), Optional.empty(), Optional.empty()).sanitised());
            // The core profile (entry-portal exit): a anchored exit returns.
            helper.assertTrue(TravelPolicy.decide(true, true,
                    PortalRules.returnMode("infinite_dungeon:dungeon"))
                    == TravelPolicy.Action.RETURN_TO_ANCHOR,
                    "the core dimension's exit returns to the anchor");
            // The second profile (world-spawn exit): the same walk exits to spawn.
            helper.assertTrue(TravelPolicy.decide(true, true,
                    PortalRules.returnMode(SECOND))
                    == TravelPolicy.Action.WORLD_SPAWN_BY_CONFIG,
                    "the second profile's exit goes to world spawn");
            // The re-form rule per profile: the second profile refuses.
            helper.assertTrue(TravelPolicy.afterReformAttempt(
                    PortalRules.reformMissingPortal(SECOND), true)
                    == TravelPolicy.Action.WORLD_SPAWN_DESTROYED,
                    "the second profile must not re-form a destroyed anchor");
            helper.assertTrue(TravelPolicy.afterReformAttempt(
                    PortalRules.reformMissingPortal("infinite_dungeon:dungeon"), true)
                    == TravelPolicy.Action.RETURN_TO_ANCHOR,
                    "the core dimension still re-forms a destroyed anchor");
        } finally {
            DimensionProfiles.remove(SECOND);
        }
        helper.succeed();
    }
}
