package org.bitwisemadness.infinitedungeon.announce;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the config-driven announcements.
 *
 * <p>One entry point for every later feature that speaks: a message is rendered
 * from its config text (with {@code {token}} placeholders) and delivered either
 * to one player or broadcast to every player in a level.</p>
 */
public final class DungeonAnnouncements {

    private DungeonAnnouncements() {
    }

    /** Renders one announcement's text with the given tokens. */
    public static Component render(Announcement announcement, Map<String, String> tokens) {
        return Component.literal(AnnouncementText.render(announcement.text(), tokens));
    }

    /** Sends one announcement to a player as chat. */
    public static void send(ServerPlayer player, Announcement announcement, Map<String, String> tokens) {
        player.displayClientMessage(render(announcement, tokens), false);
    }

    /** Sends one announcement to a player, with no tokens. */
    public static void send(ServerPlayer player, Announcement announcement) {
        send(player, announcement, Map.of());
    }

    /** Broadcasts one announcement to every player in the level. */
    public static void broadcast(ServerLevel level, Announcement announcement, Map<String, String> tokens) {
        Component component = render(announcement, tokens);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(component, false);
        }
    }

    /** Broadcasts one announcement to every player in the level, with no tokens. */
    public static void broadcast(ServerLevel level, Announcement announcement) {
        broadcast(level, announcement, Map.of());
    }
}
