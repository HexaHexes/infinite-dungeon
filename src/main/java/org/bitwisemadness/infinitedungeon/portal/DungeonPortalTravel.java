package org.bitwisemadness.infinitedungeon.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.bitwisemadness.infinitedungeon.dimension.DungeonDimensions;

/**
 * Walking into a dungeon portal: bind or read the anchor, then teleport.
 *
 * <p>The two trips, per section 4: entering from any dimension stores the
 * portal you used as your anchor and drops you at the dungeon entrance;
 * walking out of the dungeon returns you to your own anchor, so a party that
 * entered through separate overworld portals each exits at their own. A
 * portal cooldown after every teleport stops ping-pong between two facing
 * portals. A player inside the dungeon with no anchor is left alone - the
 * missing-anchor fallback belongs to its own issue.</p>
 */
public final class DungeonPortalTravel {

    private DungeonPortalTravel() {
    }

    /** One portal step, from the portal block the entity is standing in. */
    public static void handle(Level level, BlockPos portalPos, Entity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.isOnPortalCooldown() || !player.isAlive() || player.isPassenger()) {
            return;
        }
        boolean inDungeon = DungeonDimensions.LEVEL.equals(serverLevel.dimension());
        TravelPolicy.Action action = TravelPolicy.decide(inDungeon, EntryAnchors.has(player));

        switch (action) {
            case BIND_AND_ENTER -> {
                EntryAnchors.bind(player, serverLevel.dimension(), portalPos);
                ServerLevel dungeon = serverLevel.getServer().getLevel(DungeonDimensions.LEVEL);
                if (dungeon == null) {
                    return;
                }
                teleport(player, dungeon, DungeonEntrance.ensure(dungeon));
            }
            case RETURN_TO_ANCHOR -> {
                EntryAnchor anchor = EntryAnchors.get(player).orElseThrow();
                ServerLevel home = serverLevel.getServer().getLevel(anchor.dimension());
                if (home == null) {
                    // The anchor's dimension no longer exists: do nothing rather
                    // than strand the player; the fallback path owns this case.
                    return;
                }
                teleport(player, home, anchor.position());
            }
            case STAY_PUT -> {
                // Missing anchor: the anchor-handling issue owns the fallback.
            }
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel target, BlockPos destination) {
        player.teleportTo(target,
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.setPortalCooldown();
    }
}
