package org.bitwisemadness.infinitedungeon.rooms;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;
import org.bitwisemadness.infinitedungeon.treasure.KeyItems;

/**
 * The room gate end to end (issue #252; #255 adds the trial source). One test
 * owns the {@link GateInstances} seam: it is static and vanilla runs batch
 * tests concurrently, so registration, every condition source, the
 * interaction, and the layer unlock flag are exercised - and cleaned up -
 * here alone, the pattern the protection and transition-seam tests
 * established.
 *
 * <p><b>The delayed legs own the finish.</b> An earlier version of this test
 * called {@code helper.succeed()} synchronously right after scheduling its
 * layer chain - and vanilla never runs a scheduled callback after success, so
 * the layer assertions were dead code that every green run had skipped (found
 * while grounding #255). The finish now rides a finalizer leg scheduled past
 * both chains, and the registry teardown rides that leg too: clearing the
 * registry at setup-return would pull the declarations out from under the
 * polls the delayed legs assert on. The start-premise at the head covers
 * failure paths, per the guardian test's convention.</p>
 *
 * <p>Lane 97 carries the {@code layer_defeated} gate so no other test's seal
 * state is touched, and it is locked first because harness saved data survives
 * between runs. Every probe drives the real block, the real block entity, and
 * a real mock player against a real level rather than asserting on mocks.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GateGameTests {

    private GateGameTests() {
    }

    /** Distinct lane used only here; no other test seals or unlocks it. */
    private static final int OWN_LANE = 97;

    @GameTest(template = "empty")
    public static void gate_opens_on_key_layer_and_trials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Start-premise: a prior failed run's leftovers (registrations, an
        // unlocked lane) must not decide this run's outcome.
        GateInstances.clear();
        LayerUnlocks.of(level).lock(OWN_LANE);

        probeKeySource(helper, level);           // synchronous, settles in-body
        net.minecraft.world.level.ChunkPos layerChunk =
                probeLayerDefeatedSource(helper, level); // schedules ticks 21 / 42
        probeTrialsSource(helper, level);        // schedules ticks 21 / 42 alongside

        // The finalizer runs after both chains' last legs (tick 43 > 42):
        // restore, release the forced chunk, then succeed. Nothing may succeed
        // before those legs run.
        helper.runAfterDelay(43, () -> {
            GateInstances.clear();
            LayerUnlocks.of(level).lock(OWN_LANE);
            level.setChunkForced(layerChunk.x, layerChunk.z, false);
            helper.succeed();
        });
    }

    /**
     * Places a gate in its closed state. The blockstate's default is open -
     * {@code BooleanProperty} orders its values {@code [true, false]}, so
     * {@code defaultBlockState()} is open - so every placement site must ask
     * for closed explicitly. The entity is the truth (D33) and a fresh entity
     * is closed; {@code GateBlockEntity.onLoad} snaps a stray open mirror shut
     * as defense, but that runs on the next tick, so placement is explicit.
     */
    private static void placeClosed(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, GateBlocks.GATE.get().defaultBlockState()
                .setValue(GateBlock.OPEN, false));
    }

    /** The key source: a click with the key consumes one and opens, once. */
    private static void probeKeySource(GameTestHelper helper, ServerLevel level) {
        // Relative coordinates throughout: setBlock, useBlock, and
        // assertBlockProperty each call absolutePos themselves, so handing them
        // a pre-absolute pos offsets twice (the gate-opens-without-a-key bug).
        BlockPos gatePos = new BlockPos(2, 4, 2);
        placeClosed(helper, gatePos);
        GateInstances.register(level.dimension(), helper.absolutePos(gatePos), GateCondition.KEY);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // No key in hand: the gate must stay closed and consume nothing.
        player.getInventory().setPickedItem(new ItemStack(Items.STONE));
        helper.useBlock(gatePos, player);
        helper.assertBlockProperty(gatePos, GateBlock.OPEN, open -> !open,
                "a click without the key must leave the gate closed");
        helper.assertTrue(player.getInventory().getSelected().is(Items.STONE),
                "a click without the key must consume nothing");

        // With the key: one is consumed and the gate opens.
        player.getInventory().setPickedItem(new ItemStack(KeyItems.KEY.get(), 2));
        helper.useBlock(gatePos, player);
        helper.assertBlockProperty(gatePos, GateBlock.OPEN, open -> open,
                "a click with the key must open the gate");
        helper.assertTrue(player.getInventory().getSelected().is(KeyItems.KEY.get())
                        && player.getInventory().getSelected().getCount() == 1,
                "the click must consume exactly one key; has "
                        + player.getInventory().getSelected());

        // The open flag is the entity's truth and it persists in the blockstate.
        var entity = helper.getBlockEntity(gatePos);
        helper.assertTrue(entity instanceof GateBlockEntity gate && gate.isOpen(),
                "the block entity must own the open truth");

        // This probe is synchronous, so its footprint goes now: an open gate
        // block left standing in the shared harness world outlives the run.
        helper.setBlock(gatePos, Blocks.AIR);
    }

    /**
     * The layer-defeat source: polls on the tick, opens once its lane falls.
     * Returns the gate's chunk so the finalizer can release the force.
     *
     * <p>The gate's lane-OWN_LANE coordinates are constructed far from the
     * harness structure, and an unforced chunk has no ticking ticket - no
     * block entity ticker, no polls, a gate that never opens. The seal test's
     * own precondition: force the chunk before placing, release it at
     * teardown.</p>
     */
    private static net.minecraft.world.level.ChunkPos probeLayerDefeatedSource(
            GameTestHelper helper, ServerLevel level) {
        int stride = LaneGrid.laneStride();
        // Lane OWN_LANE, arbitrary in-lane Z; the gate derives its lane from X.
        // The lane position must be absolute for the gate's own X-based lane
        // derivation, so this probe drives level.setBlock directly and asserts
        // through the level rather than the absolute-izing helper.
        BlockPos gatePos = new BlockPos(OWN_LANE * stride + 10, 64, 10);
        var gateChunk = new net.minecraft.world.level.ChunkPos(gatePos);
        level.setChunkForced(gateChunk.x, gateChunk.z, true); // ticker precondition
        level.setBlock(gatePos, GateBlocks.GATE.get().defaultBlockState()
                .setValue(GateBlock.OPEN, false), 3);
        GateInstances.register(level.dimension(), gatePos, GateCondition.LAYER_DEFEATED);

        // The guardian still stands: the gate must hold.
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(!level.getBlockState(gatePos).getValue(GateBlock.OPEN),
                    "a layer_defeated gate must stay closed while the guardian stands");

            // The guardian falls; the next poll (a further 20 ticks) opens it.
            LayerUnlocks.of(level).unlock(OWN_LANE);
            helper.runAfterDelay(21, () -> {
                helper.assertTrue(level.getBlockState(gatePos).getValue(GateBlock.OPEN),
                        "a layer_defeated gate must open once its lane's guardian falls");
                level.setBlock(gatePos, Blocks.AIR.defaultBlockState(), 3);
            });
        });
        return gateChunk;
    }

    /**
     * The trial source (#255): completion accumulates per spawner, so a
     * spawner that re-arms before its siblings finish still counts - the
     * all-at-once observation a multi-spawner room would otherwise demand is
     * exactly what a sticky stamp removes.
     *
     * <p><b>Synced to the poll.</b> The spawner's own ticker (verified:
     * {@code TrialSpawnerBlock} registers a server ticker) advances a
     * manually-set state almost immediately on fresh data
     * ({@code EJECTING -> COOLDOWN -> WAITING_FOR_PLAYERS}), so a state set
     * at an arbitrary tick is gone before the gate's next 20-tick poll -
     * phase luck decided whether the observation landed, which is exactly how
     * this test passed locally and failed on CI. Every set now lands on the
     * tick <em>immediately before</em> a poll: test callbacks run after the
     * level's block-entity phase (GameTestTicker sits past the level tick in
     * {@code tickChildren}), and the gate is placed before the spawners in
     * the same chunk, so at the poll tick the gate observes the set state -
     * or, should the spawner tick first, its one-step successor
     * ({@code COOLDOWN}, also a completing state). Either observation
     * stamps; the later {@code COOLDOWN -> WAITING} death needs a second
     * level phase, which cannot precede the poll.</p>
     */
    private static void probeTrialsSource(GameTestHelper helper, ServerLevel level) {
        // Relative coordinates: these positions carry no lane derivation.
        BlockPos gateRel = new BlockPos(10, 4, 6);
        BlockPos firstRel = new BlockPos(12, 4, 6);
        BlockPos secondRel = new BlockPos(14, 4, 6);
        // Gate placed FIRST: same-chunk block entities tick in placement
        // order, so the gate's poll runs before the spawners' advance.
        placeClosed(helper, gateRel);
        helper.setBlock(firstRel, Blocks.TRIAL_SPAWNER);
        helper.setBlock(secondRel, Blocks.TRIAL_SPAWNER);
        BlockPos gateAbs = helper.absolutePos(gateRel);
        BlockPos firstAbs = helper.absolutePos(firstRel);
        BlockPos secondAbs = helper.absolutePos(secondRel);
        GateInstances.register(level.dimension(), gateAbs,
                new GateDeclaration(GateCondition.TRIALS_COMPLETE, List.of(firstAbs, secondAbs)));

        TrialSpawnerBlockEntity first = helper.getBlockEntity(firstRel);
        TrialSpawnerBlockEntity second = helper.getBlockEntity(secondRel);
        helper.assertTrue(first != null && second != null,
                "the declared trial spawners must place with block entities");

        // Leg A lands on the tick before a poll: set the first spawner's
        // reward state, and the very next poll must stamp it.
        runJustBeforeNextPoll(helper, level, () -> {
            first.setState(level, TrialSpawnerState.EJECTING_REWARD);

            helper.runAfterDelay(1, () -> {
                // Leg B (on a poll tick, after its level phase): one spawner
                // stamped, the second never completed - still closed.
                helper.assertBlockProperty(gateRel, GateBlock.OPEN, open -> !open,
                        "a trials gate must stay closed until every declared spawner rewards");

                // Leg C, one poll cycle later: the first re-arms BEFORE the
                // second ever completes - its stamp must still count.
                runJustBeforeNextPoll(helper, level, () -> {
                    first.setState(level, TrialSpawnerState.WAITING_FOR_PLAYERS);
                    second.setState(level, TrialSpawnerState.EJECTING_REWARD);

                    helper.runAfterDelay(2, () -> {
                        helper.assertBlockProperty(gateRel, GateBlock.OPEN, open -> open,
                                "a trials gate must open once every spawner has rewarded, stamps sticky");
                        level.setBlock(gateAbs, Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(firstAbs, Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(secondAbs, Blocks.AIR.defaultBlockState(), 3);
                    });
                });
            });
        });
    }

    /**
     * Schedules the action on the tick immediately before the gate's next
     * poll (the gate ticks when {@code gameTime % 20 == 0}), so a state set
     * there is observed by that poll before the spawner's ticker can advance
     * it out of the completing states.
     */
    private static void runJustBeforeNextPoll(
            GameTestHelper helper, ServerLevel level, Runnable action) {
        long phase = level.getGameTime() % 20L;
        long delta = (19L - phase + 20L) % 20L;
        if (delta == 0L) {
            delta = 20L; // already on a pre-poll tick: aim at the next cycle
        }
        helper.runAfterDelay(delta, action);
    }
}
