package org.bitwisemadness.infinitedungeon.spawn;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.treasure.KeyItems;
import org.bitwisemadness.infinitedungeonapi.event.FloorEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.MinibossSlainEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.stats.AttributeScaling;

/**
 * The miniboss raid (issue #41, plan sections 6/7): assigned floors field a
 * miniboss when entered, it re-appears until slain, and its fall stamps the
 * floor and drops the key the sealed treasure rooms (#46) demand.
 *
 * <p><b>Trigger</b>: {@link FloorEnteredEvent} - declared and tested by the
 * API but with no production poster yet (the depth tracker, #238, becomes
 * it); this listener ships testable-by-posting, exactly the #42-before-#43
 * pattern. The event carries a player UUID rather than a position, so the
 * entering player resolves first: no player, no raid (the next entry
 * retries). The spawn sits AT the player's own standing spot - a spot
 * vanilla's obstruction sanity accepts by construction.</p>
 *
 * <p><b>Identity (the design's flagged choice (a))</b>: the profile's ELITE
 * pool via the #39 registry - no API surface change; if the pack ships no
 * elite pool, the floor stays quiet (empty answer, never an error).</p>
 *
 * <p><b>Stats</b>: the miniboss multiplier stacks LAST (plan section 7) -
 * the spawn is a fresh join, so #37's depth-and party factor has already
 * applied by the time {@link MinibossFloors#stacked} multiplies the current
 * base by {@code miniboss.statMultiplier}.</p>
 *
 * <p><b>Death</b>: the floor stamps {@link MinibossSlain} and the key drops
 * unconditionally - the treasure toggle governs REQUIRING keys, never
 * earning them - and the fall posts {@code MinibossSlainEvent} (api#83):
 * layer and floor straight from the tag that proved identity, the boss
 * valid at fire, and the attributable killer (ADR-0003 - a player source
 * only, documented unknown otherwise).</p>
 */
public final class Minibosses {

    /** Marks a miniboss entity; the per-floor tag carries the coordinates. */
    static final String MINIBOSS_TAG = "infinite_dungeon_miniboss";

    private static final String FLOOR_TAG_PREFIX = "infinite_dungeon_miniboss_layer_";

    private Minibosses() {
    }

    /** What an approach decided - the gametest's observable (the consider() pattern). */
    enum Outcome {
        /** Below the start layer, floor 0, or not an every-N floor. */
        NOT_ASSIGNED,
        /** The floor earned its peace on the first kill. */
        SLAIN,
        /** The floor already fields its miniboss (singleton). */
        ALIVE,
        /** No elite pool shipped, or every candidate filtered away. */
        NO_POOL,
        /** Identity or entity creation refused the spawn. */
        REFUSED,
        REFUSED_RULES,
        REFUSED_INSERT,
        /** The miniboss spawned beside the entrant, tagged and stacked. */
        SPAWNED
    }

    /** Floor entry: thin resolution - server, level, player - then the seam. */
    public static void onFloorEntered(FloorEnteredEvent event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(event.dimension());
        if (level == null
                || !DimensionProfiles.isDungeonDimension(event.dimension().location().toString())) {
            return;
        }
        Player player = server.getPlayerList().getPlayer(event.playerId());
        if (player == null || !level.equals(player.level())) {
            return; // logged off (or moved on): the next entry retries
        }
        onApproach(level, player, event.layer(), event.floor());
    }

    /**
     * The complete decision beside an entrant - package-visible so the
     * harness drives the real logic with a helper-made player (no server
     * resolution in the test path), every branch observable as an outcome.
     */
    static Outcome onApproach(ServerLevel level, Player player, int layer, int floor) {
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return Outcome.NOT_ASSIGNED;
        }
        if (!MinibossFloors.assigned(layer, floor,
                DungeonConfigs.minibossStartLayer(), DungeonConfigs.minibossEveryNFloors())) {
            return Outcome.NOT_ASSIGNED;
        }
        if (MinibossSlain.of(level).isSlain(layer, floor)) {
            return Outcome.SLAIN;
        }
        if (guardianOfKindAlive(level, floorTag(layer, floor))) {
            return Outcome.ALIVE;
        }
        Optional<String> elitePool = DimensionProfiles.get(level.dimension().location().toString())
                .flatMap(profile -> profile.pools())
                .flatMap(pools -> pools.elite());
        if (elitePool.isEmpty()) {
            return Outcome.NO_POOL;
        }
        var picked = SpawnPools.pick(elitePool.get(), layer, level.random);
        if (picked.isEmpty()) {
            return Outcome.NO_POOL;
        }
        return spawnBeside(level, player, picked.get().entityId(), layer, floor);
    }
    private static Outcome spawnBeside(ServerLevel level, Player player,
            String entityId, int layer, int floor) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityId));
        var created = type.create(level);
        if (!(created instanceof Mob mob)) {
            return Outcome.REFUSED;
        }
        // Beside, not ON the entrant (their body fails obstruction), on the
        // ENTRANT'S FLOOR PLANE - and outward: PathfinderMob.checkSpawnRules
        // is a WALK-VALUE rule, so monsters refuse bright ground; the wider
        // ring seeks shade when the room has any, and REFUSED_RULES stays the
        // honest answer for a floor lit corner to corner (documented on the
        // class: the raid declines there rather than lying about vanilla).
        double[][] offsets = {
            {2.0D, 0.0D}, {0.0D, 2.0D}, {-2.0D, 0.0D}, {0.0D, -2.0D},
            {2.0D, 2.0D}, {-2.0D, 2.0D}, {2.0D, -2.0D}, {-2.0D, -2.0D},
            {4.0D, 0.0D}, {0.0D, 4.0D}, {-4.0D, 0.0D}, {0.0D, -4.0D}
        };
        boolean placed = false;
        for (double[] offset : offsets) {
            mob.setPos(player.getX() + offset[0], player.getY(), player.getZ() + offset[1]);
            if (EventSpawnSanity.passed(mob, level, MobSpawnType.EVENT)) {
                placed = true;
                break;
            }
        }
        if (!placed) {
            return Outcome.REFUSED_RULES; // every candidate failed vanilla sanity
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT, null);
        if (!level.addFreshEntity(mob)) {
            return Outcome.REFUSED_INSERT;
        }
        // #37's factor already sits on the base (fresh join, same tick) - the
        // miniboss multiplier stacks last, plan section 7, as one product.
        double statMultiplier = DungeonConfigs.minibossStatMultiplier();
        for (AttributeScaling.ScaledAttribute row : AttributeScaling.entries()) {
            var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(row.attribute());
            if (attribute.isEmpty()) {
                continue;
            }
            var instance = mob.getAttribute(attribute.get());
            if (instance != null) {
                instance.setBaseValue(MinibossFloors.stacked(instance.getBaseValue(), statMultiplier));
            }
        }
        mob.addTag(MINIBOSS_TAG);
        mob.addTag(floorTag(layer, floor));

        return Outcome.SPAWNED;
    }

    /**
     * The death side: stamp the floor, drop the key, announce the raid.
     * The tag is the authority for identity and coordinates; the announcement
     * rides the same payload the stamp used.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().getTags().contains(MINIBOSS_TAG)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return;
        }
        int[] coordinates = floorOf(event.getEntity());
        if (coordinates == null) {
            return; // foreign/corrupt tag: never stamp on a guess
        }
        MinibossSlain.of(level).slay(coordinates[0], coordinates[1]);
        event.getEntity().spawnAtLocation(new ItemStack(KeyItems.KEY.get()));
        NeoForge.EVENT_BUS.post(new MinibossSlainEvent(level.dimension(),
                coordinates[0], coordinates[1], event.getEntity(), killerId(event.getSource())));
    }

    /**
     * ADR-0003: the killing player where the kill is attributable to one;
     * {@code null} is the documented unknown (environmental or mob kills -
     * presence must never be used to invent a killer). The indirect entity
     * wins first so a player's projectile still credits its shooter.
     */
    private static UUID killerId(DamageSource source) {
        Entity killer = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        return killer instanceof Player player ? player.getUUID() : null;
    }

    private static boolean guardianOfKindAlive(ServerLevel level, String tag) {
        for (var entity : level.getAllEntities()) {
            if (entity.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static String floorTag(int layer, int floor) {
        return FLOOR_TAG_PREFIX + layer + "_" + floor;
    }

    private static int[] floorOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(FLOOR_TAG_PREFIX)) {
                String[] parts = tag.substring(FLOOR_TAG_PREFIX.length()).split("_");
                if (parts.length != 2) {
                    return null;
                }
                try {
                    return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
