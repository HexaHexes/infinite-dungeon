package org.bitwisemadness.infinitedungeon.config;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
    private static final ModConfigSpec.ConfigValue<String> PORTAL_FRAME_BLOCK;
    private static final ModConfigSpec.ConfigValue<String> PORTAL_RETURN_MODE;
    private static final ModConfigSpec.BooleanValue PORTAL_REFORM_MISSING;

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
                .comment("Whether the dungeon clock runs. The default is a plain day/night")
                .comment("cycle that dims skylight at night; off holds the time of day at fixedTick.")
                .comment("Either way: sleeping is flavour only and never sets a spawn point in the")
                .comment("dungeon.")
                .define("flows", TimePolicy.DEFAULT.flows());
        TIME_FIXED_TICK = builder
                .comment("The tick the clock is held at while flows is off. 6000 is noon,")
                .comment("13000 is around dusk, 18000 is midnight.")
                .defineInRange("fixedTick", TimePolicy.DEFAULT.fixedTick(), 0, TimePolicy.DAY_LENGTH - 1);
        builder.pop();
        builder.push("portal");
        PORTAL_FRAME_BLOCK = builder
                .comment("The block the dungeon portal's frame is built from. Any")
                .comment("nether-portal-shaped frame of this block ignites into a")
                .comment("dungeon portal; a nether portal needs obsidian.")
                .define("frameBlock", "minecraft:stone_bricks");
        PORTAL_RETURN_MODE = builder
                .comment("Where an exit portal sends you: 'entry_portal' returns to the")
                .comment("portal you entered through, 'world_spawn' always exits at the")
                .comment("overworld world spawn.")
                .define("returnMode", "entry_portal",
                        value -> "entry_portal".equals(value) || "world_spawn".equals(value));
        PORTAL_REFORM_MISSING = builder
                .comment("Re-form your anchor's portal at its old coordinates when the")
                .comment("frame has been destroyed (nether behaviour). When off, a")
                .comment("destroyed anchor falls back to the world spawn with a message.")
                .define("reformMissingPortal", true);
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

    /**
     * The configured portal frame block, resolved against the block registry.
     * A malformed or unknown id falls back to the stone bricks default, never
     * to air.
     */
    public static Block frameBlock() {
        String raw = PORTAL_FRAME_BLOCK.get();
        ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw.trim());
        if (id == null) {
            return Blocks.STONE_BRICKS;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == null || block == Blocks.AIR ? Blocks.STONE_BRICKS : block;
    }

    /** Where an exit portal sends the player (`entry_portal` or `world_spawn`). */
    public static org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode portalReturnMode() {
        return org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode.parse(PORTAL_RETURN_MODE.get());
    }

    /** Whether a destroyed anchor frame is re-formed at its old coordinates. */
    public static boolean reformMissingPortal() {
        return PORTAL_REFORM_MISSING.get();
    }
}
