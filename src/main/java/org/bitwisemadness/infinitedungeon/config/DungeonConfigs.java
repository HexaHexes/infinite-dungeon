package org.bitwisemadness.infinitedungeon.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.infinitedungeon.announce.AnnouncementTexts;

/**
 * The core mod's config file, starting with the announcement texts.
 *
 * <p>The texts are config-driven so a server operator rewords them without
 * touching data; the shipped defaults live in {@link AnnouncementTexts} and are
 * guarded by tests to stay ASCII-only. Feature PRs add their own entries here
 * as they need them.</p>
 *
 * <p>Registered as a COMMON config: the texts are pack-wide server behaviour,
 * readable on either distribution, and loaded at mod construction so no
 * world-load ordering can leave it unattached.</p>
 */
public final class DungeonConfigs {

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> ADVENTURE_LOCK;
    private static final ModConfigSpec.ConfigValue<String> ANCHOR_FALLBACK;
    private static final ModConfigSpec.ConfigValue<String> BOSS_AWAKENING;
    private static final ModConfigSpec.ConfigValue<String> ROOM_EVENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("announcements");
        ADVENTURE_LOCK = builder
                .comment("Sent to a player when the adventure lock is applied to them.")
                .define("adventureLock", AnnouncementTexts.ADVENTURE_LOCK);
        ANCHOR_FALLBACK = builder
                .comment("Sent to a player when their destroyed anchor falls back to the world spawn.")
                .define("anchorFallback", AnnouncementTexts.ANCHOR_FALLBACK);
        BOSS_AWAKENING = builder
                .comment("Broadcast to a level when a floor's guardian awakens.")
                .define("bossAwakening", AnnouncementTexts.BOSS_AWAKENING);
        ROOM_EVENT = builder
                .comment("Broadcast to a level when a room event begins.")
                .define("roomEvent", AnnouncementTexts.ROOM_EVENT);
        builder.pop();
        SPEC = builder.build();
    }

    private DungeonConfigs() {
    }

    /** Registers the config file; called from the mod constructor. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    /** The configured adventure lock text. */
    public static String adventureLock() {
        return ADVENTURE_LOCK.get();
    }

    /** The configured anchor fallback text. */
    public static String anchorFallback() {
        return ANCHOR_FALLBACK.get();
    }

    /** The configured boss awakening text. */
    public static String bossAwakening() {
        return BOSS_AWAKENING.get();
    }

    /** The configured room event text. */
    public static String roomEvent() {
        return ROOM_EVENT.get();
    }
}
