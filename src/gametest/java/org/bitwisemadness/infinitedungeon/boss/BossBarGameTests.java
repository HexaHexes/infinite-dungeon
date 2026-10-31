package org.bitwisemadness.infinitedungeon.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * Boss bars end to end (issue #253). One test owns the {@link BossBars}
 * seam: it is static and vanilla runs batch tests concurrently, so the
 * tracked-map, the audience, and the config toggle are exercised - and
 * cleaned up - here alone, the pattern the protection and gate seams
 * established (start-premise re-establishes state on a failed run, the
 * guardian test's happy-path-restore convention).
 *
 * <p>C creepers stand in as fight entities: never sunburn (unlike zombies,
 * whose daylight damage would make the health mirror unstable), noAi so the
 * platform keeps them put, and no tags - so their death also proves the
 * ambient-death handlers ignore them. Every probe drives real entities, a
 * real mock player placed through the real join path, and the real config
 * value rather than asserting on mocks.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BossBarGameTests {

    private BossBarGameTests() {
    }

    @GameTest(template = "empty")
    public static void boss_bars_follow_fights_and_never_ambient(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Start-premise: a prior failed run's leftovers (tracked bars, an
        // off toggle) must not decide this run's outcome.
        BossBars.clear();
        DungeonConfigs.setBossBarEnabled(true);
        try {
            // The fight entity: tracked, and the bar carries its own name.
            BlockPos fightCell = new BlockPos(4, 4, 4);
            helper.setBlock(fightCell.below(), Blocks.STONE); // it must keep standing
            Mob fight = spawnStill(helper, level, fightCell);
            BossBars.track(level, fight);
            helper.assertTrue(BossBars.isTracked(fight), "a tracked fight must be tracked");
            var bar = BossBars.barFor(fight);
            helper.assertTrue(bar.isPresent(), "a tracked fight must have a bar");
            helper.assertTrue(bar.get().getName().getString().equals(fight.getDisplayName().getString()),
                    "the bar must carry the entity's own name; got " + bar.get().getName().getString()
                            + ", entity " + fight.getDisplayName().getString());

            // The ambient spawn: never called track, so never barred -
            // structural, not a tag filter that could misfire.
            Mob ambient = spawnStill(helper, level, new BlockPos(6, 4, 8));
            helper.assertTrue(!BossBars.isTracked(ambient),
                    "an ambient spawn must have no bar");

            // The off switch, through the real config value: refused at the door.
            DungeonConfigs.setBossBarEnabled(false);
            Mob refused = spawnStill(helper, level, new BlockPos(2, 4, 10));
            BossBars.track(level, refused);
            helper.assertTrue(!BossBars.isTracked(refused),
                    "bossBar=false must refuse new bars");
            DungeonConfigs.setBossBarEnabled(true);

            // One real damage event: the next ticks must mirror it.
            fight.hurt(level.damageSources().generic(), 5.0F);

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.runAfterDelay(2, () -> {
                var live = BossBars.barFor(fight);
                helper.assertTrue(live.isPresent(), "the fight's bar must survive to the tick");
                float expected = fight.getHealth() / fight.getMaxHealth();
                helper.assertTrue(Math.abs(live.get().getProgress() - expected) < 0.001F,
                        "progress must mirror health; bar " + live.get().getProgress()
                                + " vs health ratio " + expected);
                helper.assertTrue(live.get().getPlayers().contains(player),
                        "a player in the level must see the fight's bar");
                helper.assertTrue(!BossBars.isTracked(ambient),
                        "the ambient mob must stay unbarred across ticks");

                // Death reaps: the bar must vanish without a death listener,
                // because the tick reaps anything no longer walking the level.
                fight.hurt(level.damageSources().generic(), 999.0F);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(!BossBars.isTracked(fight),
                            "death must reap the bar");

                    // Happy-path restore (start-premise covers failures).
                    fight.discard();
                    ambient.discard();
                    refused.discard();
                    BossBars.clear();
                    DungeonConfigs.setBossBarEnabled(true);
                    helper.succeed();
                });
            });
        } finally {
            // Synchronous legs only; the delayed legs restore in their own
            // final block. A failure before them leaves state for the next
            // run's start-premise, per the guardian test's convention.
            DungeonConfigs.setBossBarEnabled(true);
        }
    }

    /** A creeper that stands where it is put: no AI wander, no sunburn. */
    private static Mob spawnStill(GameTestHelper helper, ServerLevel level, BlockPos relative) {
        Mob mob = EntityType.CREEPER.create(level);
        helper.assertTrue(mob != null, "the creeper entity type must construct");
        mob.setNoAi(true);
        mob.setPersistenceRequired(); // batch runs keep no player nearby; never despawn
        BlockPos at = helper.absolutePos(relative);
        mob.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(level.addFreshEntity(mob), "the probe mob must insert into the level");
        return mob;
    }
}
