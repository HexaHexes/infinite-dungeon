package org.bitwisemadness.infinitedungeon.spawn;

import com.mojang.brigadier.ParseResults;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Enforces the spawn-point invariant: the player's actual spawn point is never
 * settable inside the dungeon dimension - while still letting players nap.
 *
 * <p>Vanilla hard-wires the respawn write to sleep-start: the moment a player
 * actually lies down, the respawn position becomes the bed. So the guard
 * snapshots the player's stored respawn before that write (on
 * {@link CanPlayerSleepEvent}, which fires first) and restores it the instant
 * the dungeon-bed target appears - on waking, on the very next player tick, or
 * at death. A player who had no respawn point gets the dungeon target cleared
 * instead. Entries are dropped on restore and on logout, so the snapshot map
 * stays bounded. The spawn-point command is refused outright, and respawn
 * anchors are already refused by the dimension type.</p>
 */
public final class DungeonSpawnGuard {

    /** The respawn target a player had before a dungeon nap overwrote it. */
    private record StoredRespawn(ResourceKey<Level> dimension,
                                 BlockPos position,
                                 float angle,
                                 boolean forced) {
    }

    private static final Map<UUID, StoredRespawn> PRE_SLEEP_RESPAWN = new HashMap<>();

    private DungeonSpawnGuard() {
    }

    /**
     * Allows the nap and preserves the spawn point. Sleep is no longer denied:
     * instead the current respawn target is snapshotted here, before vanilla
     * overwrites it, and restored once the dungeon-bed target appears.
     */
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (!SpawnPolicy.preservesRespawn(dimensionId(player.level()))) {
            return;
        }
        PRE_SLEEP_RESPAWN.put(player.getUUID(), new StoredRespawn(
                player.getRespawnDimension(),
                player.getRespawnPosition(),
                player.getRespawnAngle(),
                player.isRespawnForced()));
    }

    /** Restores the snapshot when the nap ends. */
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreIfOverwritten(player);
        }
    }

    /**
     * The one-tick net: vanilla writes the dungeon-bed respawn during the
     * click, so the very next player tick puts the stored target back.
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreIfOverwritten(player);
        }
    }

    /** Drops the snapshot on logout, restoring first if the nap is still pending. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreIfOverwritten(player);
        }
    }

    /** Refuses the spawn-point command while its source stands in the dungeon. */
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (parse.getContext().getNodes().isEmpty()) {
            return;
        }
        String command = parse.getContext().getNodes().get(0).getNode().getName();
        if (!"spawnpoint".equals(command)) {
            return;
        }
        CommandSourceStack source = parse.getContext().getSource();
        if (SpawnPolicy.refusesSpawnpointCommand(dimensionId(source.getLevel()))) {
            event.setCanceled(true);
            source.sendFailure(Component.literal(
                    "The dungeon forbids setting a spawn point."));
        }
    }

    /**
     * The safety net: a respawn target stored in the dungeon is put back the
     * way it was - or cleared if the player had none - the moment the player
     * dies, whatever set it, so no respawn can ever land in the dungeon.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!SpawnPolicy.clearsRespawn(respawnDimensionId(player))) {
            return;
        }
        StoredRespawn stored = PRE_SLEEP_RESPAWN.remove(player.getUUID());
        if (stored == null) {
            player.setRespawnPosition(null, null, 0.0F, false, false);
            return;
        }
        player.setRespawnPosition(stored.dimension(), stored.position(),
                stored.angle(), stored.forced(), false);
    }

    /**
     * Puts the stored target back whenever the player's respawn currently
     * points into the dungeon, and drops the snapshot either way.
     */
    private static void restoreIfOverwritten(ServerPlayer player) {
        StoredRespawn stored = PRE_SLEEP_RESPAWN.get(player.getUUID());
        if (stored == null) {
            return;
        }
        PRE_SLEEP_RESPAWN.remove(player.getUUID());
        if (SpawnPolicy.clearsRespawn(respawnDimensionId(player))) {
            player.setRespawnPosition(stored.dimension(), stored.position(),
                    stored.angle(), stored.forced(), false);
        }
    }

    private static String respawnDimensionId(ServerPlayer player) {
        ResourceKey<Level> respawnDimension = player.getRespawnDimension();
        return respawnDimension == null ? null : respawnDimension.location().toString();
    }

    private static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }
}
