package org.bitwisemadness.infinitedungeon.death;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;
import org.bitwisemadness.infinitedungeon.portal.DungeonEntrance;

/**
 * Death in the dungeon returns the player to the lobby - the first layer
 * (section 5, and the user's ruling that the lobby <em>is</em> the first
 * layer, of fixed construction).
 *
 * <p>A death inside the dungeon sets a marker on the dying player; the marker
 * rides through respawn (it is copied on death, like any persisted
 * attachment), and the respawn handler consumes it: teleport to the dungeon,
 * land at the lobby spot beside the entrance portal, and clear the marker. The
 * spawn-point invariant is untouched - this is an explicit teleport, not a
 * respawn point, and no dungeon respawn ever exists.</p>
 *
 * <p><strong>Guard point for "final death only":</strong> PlayerRevive is
 * deferred indefinitely, so every death is final and every dungeon death sets
 * the marker. When that integration lands, narrowing to real deaths happens
 * here - in {@link #onDeath} - not by redesign.</p>
 */
public final class LobbyReturn {

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, InfiniteDungeon.MOD_ID);

    public static final Supplier<AttachmentType<Boolean>> DIED_IN_DUNGEON =
            TYPES.register("died_in_dungeon", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    private LobbyReturn() {
    }

    /** Registers the marker attachment on the mod bus; called from the constructor. */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** Records a dungeon death so the respawn can continue at the lobby. */
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (DeathPolicy.returnsToLobby(player.level().dimension().location().toString())) {
            player.setData(DIED_IN_DUNGEON.get(), Boolean.TRUE);
        }
    }

    /** After respawn: a dungeon death continues at the first layer. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasData(DIED_IN_DUNGEON.get())) {
            return;
        }
        player.removeData(DIED_IN_DUNGEON.get());
        teleportToLobby(player);
    }

    /** Whether this player carries an unconsumed dungeon-death marker. */
    public static boolean holdsMarker(ServerPlayer player) {
        return player.hasData(DIED_IN_DUNGEON.get());
    }

    /** Marks a dungeon death directly; the death listener and the test use it. */
    public static void mark(ServerPlayer player) {
        player.setData(DIED_IN_DUNGEON.get(), Boolean.TRUE);
    }

    /** Consumes the marker without teleporting; used after the lobby trip. */
    public static void clear(ServerPlayer player) {
        player.removeData(DIED_IN_DUNGEON.get());
    }

    private static void teleportToLobby(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ServerLevel dungeon = server.getLevel(DungeonDimensions.LEVEL);
        if (dungeon == null) {
            return;
        }
        BlockPos lobby = DungeonEntrance.lobby(dungeon);
        if (lobby == null) {
            // Blocked entrance: stay at the ordinary respawn point rather than
            // drop the player into the void.
            return;
        }
        player.teleportTo(dungeon,
                lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        // Land beside the exit portal's plane; the cooldown covers any
        // accidental re-entry while the player reorients.
        player.setPortalCooldown();
    }
}
