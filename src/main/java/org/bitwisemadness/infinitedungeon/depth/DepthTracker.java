package org.bitwisemadness.infinitedungeon.depth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.bitwisemadness.infinitedungeonapi.event.FloorEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.ProfileAnnouncements;

/**
 * The player depth tracker (issue #238): observes every dungeon player's
 * derived (layer, floor) on a cadence and turns MOVEMENT into the API's
 * transition events - the production poster both {@link FloorEnteredEvent}
 * (issue #41's raid trigger) and {@link LayerEnteredEvent} lacked - plus the
 * profile's entry/layer announcements, whose keys it resolves against THIS
 * mod's language file (ProfileAnnouncements' contract: keys, not literals).
 *
 * <p>Announcements are opt-in through the profile: no profile, no keys, no
 * messages - shipping a default profile naming them belongs to the profile
 * defaults work (#162 family), not here. Events post unconditionally (their
 * contract is notification-only); the cadence (every 10 ticks) is a
 * documented choice, not a pinned value. Leaving the dungeon (or void)
 * forgets the player so the next crossing announces ENTRY again.</p>
 */
public final class DepthTracker {

    /** Per-player last in-dungeon (layer, floor); server thread only. */
    private static final Map<UUID, int[]> LAST = new HashMap<>();

    /** Ticks between observations (documented choice). */
    private static final int CADENCE = 10;

    private DepthTracker() {
    }

    /** The level-tick scan; dungeon dimensions only, every {@link #CADENCE} ticks. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!DimensionProfiles.isDungeonDimension(level.dimension().location().toString())) {
            return;
        }
        if (level.getGameTime() % CADENCE != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            step(level, player);
        }
    }

    /** Forgets a departing player so the next crossing announces again. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    /**
     * One player's observation: classify, act, record - and return what it
     * decided, the gametest's observable (the consider()/onApproach seam
     * pattern).
     */
    static DepthTransition.Kind step(ServerLevel level, ServerPlayer player) {
        var parameters = LayoutParameters.forServer(level);
        boolean inside = DungeonLayout.inDungeon(parameters, player.getBlockX());
        int layer = -1;
        int floor = -1;
        if (inside) {
            var depth = DungeonLayout.derive(parameters, player.getBlockX(), player.getBlockY());
            layer = depth.layerIndex();
            floor = depth.floorInLayer();
        }
        UUID playerId = player.getUUID();
        DepthTransition.Kind kind = DepthTransition.classify(LAST.get(playerId), inside, layer, floor);
        switch (kind) {
            case OUTSIDE -> LAST.remove(playerId);
            case ENTRY -> {
                record(playerId, layer, floor);
                NeoForge.EVENT_BUS.post(new LayerEnteredEvent(level.dimension(), playerId, layer));
                announceEntry(level, player);
            }
            case LAYER -> {
                record(playerId, layer, floor);
                NeoForge.EVENT_BUS.post(new LayerEnteredEvent(level.dimension(), playerId, layer));
                announceLayer(level, player, layer);
            }
            case FLOOR -> {
                record(playerId, layer, floor);
                NeoForge.EVENT_BUS.post(new FloorEnteredEvent(level.dimension(), playerId, layer, floor));
            }
            case SAME -> {
                // nothing moved
            }
        }
        return kind;
    }

    private static void record(UUID playerId, int layer, int floor) {
        LAST.put(playerId, new int[] {layer, floor});
    }

    private static void announceEntry(ServerLevel level, ServerPlayer player) {
        key(level, ProfileAnnouncements::entry).ifPresent(key ->
                player.displayClientMessage(Component.translatable(key), false));
    }

    private static void announceLayer(ServerLevel level, ServerPlayer player, int layer) {
        key(level, ProfileAnnouncements::layer).ifPresent(key ->
                player.displayClientMessage(Component.translatable(key, layer), false));
    }

    /** The profile's non-empty announcement key, when a profile names one. */
    private static Optional<String> key(ServerLevel level,
            Function<ProfileAnnouncements, String> field) {
        return DimensionProfiles.get(level.dimension().location().toString())
                .flatMap(profile -> profile.announcements())
                .map(field)
                .filter(value -> value != null && !value.isEmpty());
    }

    /** Clears the observation state (test premise; no production caller). */
    static void reset() {
        LAST.clear();
    }
}
