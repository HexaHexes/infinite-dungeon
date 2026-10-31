package org.bitwisemadness.infinitedungeon.depth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeonapi.event.FloorEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.ProfileAnnouncements;

/**
 * The depth tracker end to end (issue #238): against the real seam, events,
 * and announcement path - outside declines silently, first observation posts
 * the layer event (ENTRY), repeats stay SAME, a lane crossing posts again,
 * and a floor move posts the floor event. Positions are chosen for
 * determinism: lane membership comes from X (lane 0 vs lane 1 anchors), and
 * the floor-difference position is found by asking the derivation itself, so
 * the test never hardcodes floor math. Same package as the tracker for the
 * step/reset seams; `finally` restores roster-free state and the profile.
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DepthTrackerGameTests {

    private DepthTrackerGameTests() {
    }

    /** Records the events the tracker posts; registered around the test. */
    public static final class Collector {
        int layerCount;
        int floorCount;
        int lastLayer = -1;
        int lastFloor = -1;

        @SubscribeEvent
        public void onLayer(LayerEnteredEvent event) {
            layerCount++;
            lastLayer = event.layer();
        }

        @SubscribeEvent
        public void onFloor(FloorEnteredEvent event) {
            floorCount++;
            lastLayer = event.layer();
            lastFloor = event.floor();
        }
    }

    @GameTest(template = "empty")
    public static void depth_tracker_posts_transitions_and_stays_silent_outside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String harnessDimension = level.dimension().location().toString();
        // Registered so the announce path builds real components (shipped lang
        // keys); messages themselves are delivery, not asserted here.
        DimensionProfiles.register(new DimensionProfile(
                harnessDimension,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.of(new ProfileAnnouncements("dungeon.entry", "dungeon.layer")),
                Optional.empty()));
        DepthTracker.reset();
        var collector = new Collector();
        NeoForge.EVENT_BUS.register(collector);
        var player = helper.makeMockServerPlayerInLevel();
        try {
            // Outside: negative X is west of lane 0 - void, whatever the Y.
            player.setPos(-1000.0, -60.0, 0.0);
            var outcome1 = DepthTracker.step(level, player);
            helper.assertTrue(outcome1 == DepthTransition.Kind.OUTSIDE, "transition must read as asserted; got " + outcome1);
            helper.assertTrue(collector.layerCount == 0 && collector.floorCount == 0,
                    "outside must post no events");

            // Lane 0 is the lobby's home, and since #246 the lobby is the
            // Overworld entrance site - a player standing in lane 0 is
            // OUTSIDE the dungeon, so it stays silent just like the void.
            player.setPos(100.0, -60.0, 0.0);
            var outcomeLane0 = DepthTracker.step(level, player);
            helper.assertTrue(outcomeLane0 == DepthTransition.Kind.OUTSIDE,
                    "lane 0 must read as OUTSIDE since #246; got " + outcomeLane0);
            helper.assertTrue(collector.layerCount == 0 && collector.floorCount == 0,
                    "lane 0 must post no events - the lobby is Overworld-resident");

            // First observation inside the dungeon (layer 1): ENTRY posts the
            // layer event.
            int stride = LaneGrid.laneStride();
            player.setPos(stride + 100.0, -60.0, 0.0);
            var outcome2 = DepthTracker.step(level, player);
            helper.assertTrue(outcome2 == DepthTransition.Kind.ENTRY, "transition must read as asserted; got " + outcome2);
            helper.assertTrue(collector.layerCount == 1 && collector.lastLayer == 1,
                    "ENTRY must post exactly the layer-1 event; got " + collector.layerCount
                            + " layers, last " + collector.lastLayer);

            // Wobble within the same lane and Y: SAME, no new events.
            player.setPos(stride + 110.0, -60.0, 0.0);
            var outcome3 = DepthTracker.step(level, player);
            helper.assertTrue(outcome3 == DepthTransition.Kind.SAME, "transition must read as asserted; got " + outcome3);
            helper.assertTrue(collector.layerCount == 1 && collector.floorCount == 0,
                    "SAME must post nothing new");

            // Lane crossing (into layer 2): LAYER posts again.
            player.setPos(2 * stride + 100.0, -60.0, 0.0);
            var outcome4 = DepthTracker.step(level, player);
            helper.assertTrue(outcome4 == DepthTransition.Kind.LAYER, "transition must read as asserted; got " + outcome4);
            helper.assertTrue(collector.layerCount == 2,
                    "LAYER must post the layer event; got " + collector.layerCount);

            // A different floor in the SAME lane: ask the derivation for a Y
            // whose floor differs, so the test never hardcodes floor math.
            var parameters = LayoutParameters.forServer(level);
            int laneX = 2 * stride + 100;
            int baseY = -60;
            int otherY = baseY;
            int baseFloor = DungeonLayout.derive(parameters, laneX, baseY).floorInLayer();
            for (int probe = -63; probe < level.getMaxBuildHeight(); probe++) {
                if (DungeonLayout.derive(parameters, laneX, probe).floorInLayer() != baseFloor) {
                    otherY = probe;
                    break;
                }
            }
            helper.assertTrue(otherY != baseY, "the derivation must offer a second floor to move to");
            player.setPos(laneX, otherY, 0.0);
            var outcome5 = DepthTracker.step(level, player);
            helper.assertTrue(outcome5 == DepthTransition.Kind.FLOOR, "transition must read as asserted; got " + outcome5);
            helper.assertTrue(collector.floorCount == 1,
                    "FLOOR must post exactly the floor event; got " + collector.floorCount);
            helper.assertTrue(collector.lastFloor
                            == DungeonLayout.derive(parameters, laneX, otherY).floorInLayer(),
                    "the floor event must carry the derived floor index; got " + collector.lastFloor);
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(collector);
            DepthTracker.reset();
            DimensionProfiles.remove(harnessDimension);
        }
    }
}
