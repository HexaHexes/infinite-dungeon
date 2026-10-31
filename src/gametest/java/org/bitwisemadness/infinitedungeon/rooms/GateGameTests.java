package org.bitwisemadness.infinitedungeon.rooms;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeon.lanes.LayerUnlocks;
import org.bitwisemadness.infinitedungeon.treasure.KeyItems;

/**
 * The room gate end to end (issue #252). One test owns the
 * {@link GateInstances} seam: it is static and vanilla runs batch tests
 * concurrently, so registration, both condition sources, the interaction, and
 * the layer unlock flag are exercised - and cleaned up - here alone, the
 * pattern the protection and transition-seam tests established.
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
    public static void gate_opens_on_key_and_on_layer_defeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GateInstances.clear();
        LayerUnlocks.of(level).lock(OWN_LANE);
        try {
            probeKeySource(helper, level);
            probeLayerDefeatedSource(helper, level);
            helper.succeed();
        } finally {
            GateInstances.clear();
            LayerUnlocks.of(level).lock(OWN_LANE);
        }
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
    }

    /** The layer-defeat source: polls on the tick, opens once its lane falls. */
    private static void probeLayerDefeatedSource(GameTestHelper helper, ServerLevel level) {
        int stride = LaneGrid.laneStride();
        // Lane OWN_LANE, arbitrary in-lane Z; the gate derives its lane from X.
        // The lane position must be absolute for the gate's own X-based lane
        // derivation, so this probe drives level.setBlock directly and asserts
        // through the level rather than the absolute-izing helper.
        BlockPos gatePos = new BlockPos(OWN_LANE * stride + 10, 64, 10);
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
    }
}
