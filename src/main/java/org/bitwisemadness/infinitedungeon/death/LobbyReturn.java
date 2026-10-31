package org.bitwisemadness.infinitedungeon.death;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalBlock;
import org.bitwisemadness.infinitedungeon.portal.DungeonPortalTravel;
import org.bitwisemadness.infinitedungeon.portal.EntryAnchors;
import org.bitwisemadness.infinitedungeon.portal.PortalFrameBuilder;

/**
 * Death in the dungeon returns the player to the lobby - the first layer
 * (section 5). Since issue #246 (Reading B, the api#54 ruling) the lobby is
 * the <em>Overworld entrance site</em>, so the return leads back to the
 * entrance the player descended through, not to a spot inside the dungeon
 * dimension.
 *
 * <p>A death inside the dungeon sets a marker on the dying player; the marker
 * rides through respawn (it is copied on death, like any persisted
 * attachment), and the respawn handler consumes it: teleport to the anchor
 * entrance, and clear the marker. The spawn-point invariant is untouched -
 * this is an explicit teleport, not a respawn point, and no dungeon respawn
 * ever exists.</p>
 *
 * <p>The anchor is the lobby's address, and it survives the death on purpose
 * ({@link EntryAnchors} copies it on death) precisely so this return can
 * never strand the player. When the anchor or its dimension is gone - or the
 * entrance's frame has been destroyed and re-formation is refused - the
 * shared world-spawn fallback applies, announced exactly as the walk-out
 * announces it ({@link DungeonPortalTravel#worldSpawn}).</p>
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
        // Issue #246: the lobby is the Overworld entrance site - the anchor
        // the player descended through. Resolve that site, else fall back.
        var anchor = EntryAnchors.get(player);
        ServerLevel lobby = anchor.isPresent()
                ? server.getLevel(anchor.get().dimension()) : null;
        if (lobby == null) {
            // No anchor, or its dimension no longer exists: the same
            // world-spawn fallback the walk-out uses, announced.
            DungeonPortalTravel.worldSpawn(player, true);
            return;
        }
        BlockPos spot = anchor.get().position();
        if (!(lobby.getBlockState(spot).getBlock() instanceof DungeonPortalBlock)
                && !(DungeonConfigs.reformMissingPortal()
                        && PortalFrameBuilder.build(lobby, spot, Direction.Axis.X))) {
            // The entrance's frame was destroyed and re-formation is refused
            // or blocked: fall back rather than drop the player into whatever
            // now stands there. The anchor stores only a position, so
            // re-formed portals use the X axis (PortalFrameBuilder's
            // contract, as the walk-out path assumes).
            DungeonPortalTravel.worldSpawn(player, true);
            return;
        }
        // Land at the anchor portal itself - the same inside-the-plane
        // landing every lane hop and the descent use, guarded by the portal
        // cooldown set below.
        player.teleportTo(lobby,
                spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setPortalCooldown();
    }
}
