package org.bitwisemadness.infinitedungeon.lock;

import java.util.HashSet;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.announce.Announcement;
import org.bitwisemadness.infinitedungeon.announce.DungeonAnnouncements;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;

/**
 * The adventure-mode lock in the dungeon (section 5).
 *
 * <p>Entering the dungeon swaps the player to adventure and remembers what
 * they were in a data attachment (persisted with them, like the entry anchor);
 * leaving - through a portal, a command, or death - restores it and forgets it.
 * Logging in already inside the dungeon re-applies the lock if something
 * slipped. Block breaking is refused inside except for the config whitelist,
 * so the dungeon is never mined apart.</p>
 *
 * <p>The lock is anchored to the dungeon dimension only, so it cannot leak
 * into a player's ordinary world even if a later feature lets them skip out.</p>
 */
public final class DungeonLock {

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, InfiniteDungeon.MOD_ID);

    public static final Supplier<AttachmentType<GameType>> PREVIOUS_GAME_TYPE =
            TYPES.register("previous_game_type", () -> AttachmentType.builder(() -> GameType.SURVIVAL)
                    .serialize(GameType.CODEC)
                    .build());

    private DungeonLock() {
    }

    /** Registers the attachment types on the mod bus; called from the constructor. */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** Swaps a player into adventure for the dungeon, remembering what they were. */
    public static void apply(ServerPlayer player) {
        if (!player.hasData(PREVIOUS_GAME_TYPE.get())) {
            player.setData(PREVIOUS_GAME_TYPE.get(), player.gameMode.getGameModeForPlayer());
        }
        player.setGameMode(GameType.ADVENTURE);
        DungeonAnnouncements.send(player, Announcement.ADVENTURE_LOCK);
    }

    /** Restores the gamemode the player had before the dungeon swapped it. */
    public static void release(ServerPlayer player) {
        if (player.hasData(PREVIOUS_GAME_TYPE.get())) {
            player.setGameMode(player.getData(PREVIOUS_GAME_TYPE.get()));
            player.removeData(PREVIOUS_GAME_TYPE.get());
        }
    }

    /** Whether this player's swap is still stored (the lock is holding them). */
    public static boolean holdsSnapshot(ServerPlayer player) {
        return player.hasData(PREVIOUS_GAME_TYPE.get());
    }

    /** Entry swaps to adventure; exit restores. */
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean fromDungeon = AdventureLock.applies(event.getFrom().location().toString());
        boolean toDungeon = AdventureLock.applies(event.getTo().location().toString());
        if (toDungeon && !fromDungeon) {
            apply(player);
        } else if (fromDungeon && !toDungeon) {
            release(player);
        }
    }

    /** Logging in already inside the dungeon: re-apply if the lock slipped. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (AdventureLock.applies(player.level().dimension().location().toString())
                && player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            apply(player);
        }
    }

    /** Block breaking is refused inside, except for the config whitelist. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        if (!AdventureLock.applies(level.dimension().location().toString())) {
            return;
        }
        String blockId = String.valueOf(
                BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()));
        if (!AdventureLock.isBreakable(blockId, DungeonConfigs.adventureLockBreakableBlocks())) {
            event.setCanceled(true);
        }
    }
}
