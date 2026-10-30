package org.bitwisemadness.infinitedungeon.spawn;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.depth.DifficultyCurve;
import org.bitwisemadness.infinitedungeon.depth.DungeonLayout;
import org.bitwisemadness.infinitedungeon.depth.DungeonPosition;
import org.bitwisemadness.infinitedungeon.depth.LayoutParameters;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeonapi.magicule.AmbientMagicule;
import org.bitwisemadness.infinitedungeonapi.party.PartySizeResolver;
import org.bitwisemadness.infinitedungeonapi.stats.AttributeScaling;

/**
 * Spawn attribute scaling (plan sections 7/9, issue #37): fresh mob joins
 * inside the dungeon get their whitelisted attributes multiplied by
 * {@link SpawnFactor} - the attribute's keyed curve (D or M, api#67 rows)
 * at the spawn's position, times the party multiplier (api#65).
 *
 * <p>The hook choice is spike #82's recorded "or": a SINGLE
 * {@link EntityJoinLevelEvent} listener with a fresh-join guard
 * ({@code tickCount == 0}) rather than PositionCheck + a marker - one hook
 * cannot double-apply, and the freshness guard excludes chunk-load re-adds,
 * which are not spawns. Spawner, command and egg spawns are all fresh joins;
 * natural spawns reach here too (their PositionCheck path is irrelevant to
 * this listener). Players are never scaled; outside the dungeon the listener
 * is a no-op, which is exactly what the harness gametest pins.</p>
 *
 * <p>The whitelist is the API's scaling list: this class registers the
 * default vanilla rows once ({@link #registerDefaultRows}), Tensura's rows
 * come from api#67's enumeration, and packs opt through the same list - core
 * configures, the API holds (I32).</p>
 */
public final class SpawnAttributeScaling {

    /**
     * The shipped default scaling set: the core combat/mob quartet, keyed to
     * DIFFICULTY. The plan says "all whitelisted attributes" without an
     * enumeration; this is the whitelist v1, extended through the API list
     * (a datapack-tag surface is the flagged follow-up if packs ask).
     */
    private static final String[] DEFAULT_ROWS = {
        "minecraft:generic.max_health",
        "minecraft:generic.attack_damage",
        "minecraft:generic.armor",
        "minecraft:generic.movement_speed"
    };

    private SpawnAttributeScaling() {
    }

    /** Registers the default scaling rows; called once at mod construction. */
    public static void registerDefaultRows() {
        for (String id : DEFAULT_ROWS) {
            AttributeScaling.register(ResourceLocation.parse(id), AttributeScaling.Curve.DIFFICULTY);
        }
    }

    /**
     * The plan's magicule toggle is core config; the keying flag is API state
     * (I32: core config drives API machinery) - so core pushes the value at
     * the first level load: mod construction runs too early (the gate caught
     * exactly that: "Cannot get config value before config is loaded"), while
     * world load is guaranteed past config load. The push is idempotent, and
     * restart-to-apply is the house posture for config values (the same rule
     * the elevation sources document). The event class exists in the merged
     * jar - a loader-artifact event type deliberately does not.
     */
    public static void onLevelLoad(LevelEvent.Load event) {
        AttributeScaling.setMagiculeKeyedScaling(DungeonConfigs.magiculeTensuraAttributeScaling());
    }

    /** The fresh-join hook: scale in-dungeon mob spawns, decline everywhere else. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        if (!DungeonDimensions.LEVEL.equals(level.dimension())) {
            return;
        }
        if (living.tickCount != 0) {
            // Fresh joins only: a chunk-load re-add carries its saved tick
            // count and is not a spawn - scaling it would drift every reload.
            return;
        }
        apply(level, living);
    }

    private static void apply(ServerLevel level, LivingEntity living) {
        var position = living.blockPosition();
        var parameters = LayoutParameters.forServer(level);
        if (!DungeonLayout.inDungeon(parameters, position.getX())) {
            // Void positions west of lane 0 have no depth; the derivation
            // refuses them loudly by contract, so decline before it.
            return;
        }
        DungeonPosition depth = DungeonLayout.derive(parameters, position.getX(), position.getY());
        double difficulty = DifficultyCurve.difficulty(depth.layerIndex(), depth.floorInLayer(),
                DungeonConfigs.difficultyBase(),
                DungeonConfigs.difficultyLinearPerFloor(),
                DungeonConfigs.difficultyQuadraticPerLayer());
        double magicule = AmbientMagicule.query(level, position);
        double party = PartySizeResolver.multiplier(level, position,
                DungeonConfigs.partyRadius(), DungeonConfigs.partyPerMemberMultiplier());
        double perPoint = DungeonConfigs.difficultyStatMultiplierPerPoint();

        for (AttributeScaling.ScaledAttribute row : AttributeScaling.entries()) {
            var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(row.attribute());
            if (attribute.isEmpty()) {
                // The row names an attribute this game does not know.
                continue;
            }
            AttributeInstance instance = living.getAttribute(attribute.get());
            if (instance == null) {
                // The entity simply does not carry this attribute.
                continue;
            }
            instance.setBaseValue(instance.getBaseValue()
                    * SpawnFactor.of(difficulty, magicule, perPoint, party, row.curve()));
        }
    }
}
