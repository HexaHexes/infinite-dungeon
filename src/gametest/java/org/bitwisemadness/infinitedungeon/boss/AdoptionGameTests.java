package org.bitwisemadness.infinitedungeon.boss;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.lanes.LaneGrid;
import org.bitwisemadness.infinitedungeonapi.event.BossSlainEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * Boss challenge adoption end to end (issue #255, D40). One test owns the
 * {@link Adoptions} and {@link AdoptedDefeats} seams - static, and the batch
 * runs concurrently - so declaration, tagging, the credit rule, and the
 * notification are exercised and undone here alone. Everything runs
 * synchronously in the body (join and death events fire inline with
 * {@code addFreshEntity}/{@code hurt}), so the classic try/finally teardown
 * applies with no delayed legs to coordinate.
 *
 * <p>Wardens stand in as the adopted type: no other test spawns one, so no
 * cross-test adoption can fire (a shared type would let this declaration tag
 * another test's mobs - the seam race the recorded rules exist for). The
 * fight spawns at an explicit in-lane position so the layer tag is
 * deterministic regardless of where the harness sits. The collector watches
 * by <em>entity identity</em>, because the seal test's guardian deaths post
 * the same event type concurrently and must not be counted here.</p>
 *
 * <p>The profile registration mirrors the depth/Seal tests: adoption's layer
 * tag needs {@code isDungeonDimension} true for the harness, and the
 * start-premise re-establishes it after any sibling's teardown.</p>
 */
@GameTestHolder(InfiniteDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdoptionGameTests {

    private AdoptionGameTests() {
    }

    /** Distinct lane for the adopted fight's context; no other test uses it. */
    private static final int OWN_LANE = 96;

    /** This test's declaration identity - the defeat record's key. */
    private static final ResourceLocation DECLARATION =
            ResourceLocation.fromNamespaceAndPath(InfiniteDungeon.MOD_ID, "gametest_warden");

    @GameTest(template = "empty")
    public static void adopted_bosses_tag_on_spawn_and_stamp_only_on_credit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String harness = level.dimension().location().toString();
        // Start-premise: prior leftovers must not decide this run.
        Adoptions.clear();
        AdoptedDefeats.of(level).forget(DECLARATION);
        DimensionProfiles.register(DimensionProfile.forDimension(harness));
        var collector = new SlainCollector();
        NeoForge.EVENT_BUS.register(collector);
        Mob adopted = null;
        Mob ordinary = null;
        Mob credited = null;
        try {
            Adoptions.register(new Adoptions.Declaration(
                    DECLARATION, "minecraft:warden", null));

            // 1) Spawn: the declared type is tagged as its context's boss.
            adopted = spawnWatching(helper, level, EntityType.WARDEN,
                    new BlockPos(OWN_LANE * LaneGrid.laneStride() + 10, 64, 10), collector);
            helper.assertTrue(adopted.getTags().contains(AdoptionHooks.ADOPTED_TAG),
                    "a declared type must be tagged adopted on spawn; tags=" + adopted.getTags());
            helper.assertTrue(adopted.getTags().contains(
                            AdoptionHooks.ID_TAG_PREFIX + DECLARATION),
                    "the declaration id must be on the entity; tags=" + adopted.getTags());
            helper.assertTrue(adopted.getTags().contains(
                            AdoptionHooks.LAYER_TAG_PREFIX + OWN_LANE),
                    "the spawn layer must be on the entity; tags=" + adopted.getTags());

            // 2) An ordinary type never matches - ambient exclusion is the
            // declaration itself, not a filter.
            ordinary = spawnWatching(helper, level, EntityType.ZOMBIE,
                    new BlockPos(OWN_LANE * LaneGrid.laneStride() + 14, 64, 10), collector);
            helper.assertTrue(!ordinary.getTags().contains(AdoptionHooks.ADOPTED_TAG),
                    "an undeclared type must never be tagged adopted");

            // 3) Uncredited death: no stamp (the record stays clear, so the
            // source can fire again), but the layer is notified with the
            // documented unknown attribution.
            adopted.hurt(level.damageSources().generic(), 10000.0F);
            helper.assertTrue(!AdoptedDefeats.of(level).isDefeated(DECLARATION),
                    "an uncredited death must not stamp the defeat - re-arm");
            helper.assertTrue(collector.slains == 1,
                    "the death must notify its layer exactly once; got " + collector.slains);
            helper.assertTrue(collector.lastLayer == OWN_LANE,
                    "the notification must carry the spawn layer; got " + collector.lastLayer);
            helper.assertTrue(collector.lastKiller.isEmpty(),
                    "an uncredited death must forward the documented unknown; got "
                            + collector.lastKiller);

            // 4) Credited death: the stamp lands and the killer is carried -
            // the second fight proves the source could fire again after (3).
            credited = spawnWatching(helper, level, EntityType.WARDEN,
                    new BlockPos(OWN_LANE * LaneGrid.laneStride() + 10, 64, 14), collector);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            credited.hurt(level.damageSources().playerAttack(player), 10000.0F);
            helper.assertTrue(AdoptedDefeats.of(level).isDefeated(DECLARATION),
                    "a player kill must stamp the defeat");
            helper.assertTrue(collector.slains == 2,
                    "the second death must notify too; got " + collector.slains);
            helper.assertTrue(collector.lastKiller.isPresent()
                            && collector.lastKiller.get().equals(player.getUUID()),
                    "a player kill must carry the killer; got " + collector.lastKiller);

            helper.succeed();
        } finally {
            // Restore on EVERY path: probe mobs are try-scoped because a leaked mob in the shared
            // harness world outlives the run - the wardens die in-body on the happy path; failure
            // paths and the always-alive zombie would otherwise linger.
            NeoForge.EVENT_BUS.unregister(collector);
            if (adopted != null) {
                adopted.discard();
            }
            if (ordinary != null) {
                ordinary.discard();
            }
            if (credited != null) {
                credited.discard();
            }
            AdoptedDefeats.of(level).forget(DECLARATION);
            Adoptions.clear();
            DimensionProfiles.remove(harness);
        }
    }

    /** Spawns a probe mob at an absolute position and watches it for events. */
    private static Mob spawnWatching(GameTestHelper helper, ServerLevel level,
            EntityType<? extends Mob> type, BlockPos at, SlainCollector collector) {
        Mob mob = type.create(level);
        helper.assertTrue(mob != null, "the probe entity type must construct");
        mob.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(level.addFreshEntity(mob), "the probe mob must insert into the level");
        collector.watched.add(mob);
        return mob;
    }

    /**
     * Collects {@link BossSlainEvent}s for THIS test's entities only: the
     * seal test kills tagged guardians on the same bus mid-batch, and their
     * notifications are none of this assertion's business (the identity-filter
     * rule the recorded seam lore demands).
     */
    private static final class SlainCollector {

        final Set<LivingEntity> watched = new HashSet<>();
        int slains;
        int lastLayer = -1;
        Optional<UUID> lastKiller = Optional.empty();

        @SubscribeEvent
        public void onSlain(BossSlainEvent event) {
            if (!watched.contains(event.boss())) {
                return;
            }
            slains++;
            lastLayer = event.layer();
            lastKiller = event.killingPlayer();
        }
    }
}
