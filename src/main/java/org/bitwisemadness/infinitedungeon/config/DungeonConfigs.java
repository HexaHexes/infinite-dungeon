package org.bitwisemadness.infinitedungeon.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.infinitedungeon.announce.AnnouncementTexts;
import org.bitwisemadness.infinitedungeon.time.TimePolicy;

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
    private static final ModConfigSpec.BooleanValue TIME_FLOWS;
    private static final ModConfigSpec.IntValue TIME_FIXED_TICK;

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
        builder.push("time");
        TIME_FLOWS = builder
                .comment("Whether the dungeon clock runs. Off holds the time of day at")
                .comment("fixedTick, which is the default experience; on gives a plain day/night")
                .comment("cycle that dims skylight at night. Purely atmospheric either way: beds")
                .comment("never set a respawn point inside the dimension.")
                .define("flows", TimePolicy.DEFAULT.flows());
        TIME_FIXED_TICK = builder
                .comment("The tick the clock is held at while flows is off. 6000 is noon,")
                .comment("13000 is around dusk, 18000 is midnight.")
                .defineInRange("fixedTick", TimePolicy.DEFAULT.fixedTick(), 0, TimePolicy.DAY_LENGTH - 1);
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

    /** The dungeon clock policy as the pure record. */
    public static TimePolicy timePolicy() {
        return new TimePolicy(TIME_FLOWS.get(), TIME_FIXED_TICK.get());
    }
}
