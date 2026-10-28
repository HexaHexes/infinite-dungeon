package org.bitwisemadness.infinitedungeon.spawn;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Enforces the spawn-point invariant: the player's actual spawn point is never
 * settable inside the dungeon dimension.
 *
 * <p>Three guards cover the reachable paths. Beds: {@link CanPlayerSleepEvent}
 * is posted at the top of vanilla's sleep entry point, before the
 * respawn-setting path can run, so denying sleep keeps beds decorative even
 * with the clock flowing. The spawn-point command: NeoForge's cancellable
 * {@link CommandEvent} refuses it while the source stands in the dungeon, which
 * also covers execute-in redirection. The safety net: on death, a respawn
 * target stored in the dungeon is cleared, so nothing set out-of-band can ever
 * be used. Respawn anchors need no guard - the shipped dimension type refuses
 * them outright.</p>
 */
public final class DungeonSpawnGuard {

    private DungeonSpawnGuard() {
    }

    /**
     * Denies sleep inside the dungeon. Vanilla sets the respawn position only
     * once a player actually starts sleeping, so this gate keeps beds as
     * non-explosive decoration in every configuration.
     */
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (SpawnPolicy.deniesSleep(dimensionId(event.getEntity().level()))) {
            event.setProblem(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
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
     * The safety net: a respawn target stored in the dungeon is cleared the
     * moment the player dies, whatever set it, so no respawn can ever land in
     * the dungeon.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> respawnDimension = player.getRespawnDimension();
        String dimensionId = respawnDimension == null
                ? null
                : respawnDimension.location().toString();
        if (SpawnPolicy.clearsRespawn(dimensionId)) {
            player.setRespawnPosition(null, null, 0.0F, false, false);
        }
    }

    private static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }
}
