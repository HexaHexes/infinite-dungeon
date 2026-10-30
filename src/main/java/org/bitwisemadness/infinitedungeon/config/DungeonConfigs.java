package org.bitwisemadness.infinitedungeon.config;

import java.util.HashSet;
import java.util.List;
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
    private static final ModConfigSpec.ConfigValue<String> PORTAL_NOT_ALLOWED;
    private static final ModConfigSpec.ConfigValue<String> LANE_SEALED;
    private static final ModConfigSpec.BooleanValue TIME_FLOWS;
    private static final ModConfigSpec.IntValue TIME_FIXED_TICK;
    private static final ModConfigSpec.ConfigValue<String> PORTAL_FRAME_BLOCK;
    private static final ModConfigSpec.ConfigValue<String> PORTAL_RETURN_MODE;
    private static final ModConfigSpec.BooleanValue PORTAL_REFORM_MISSING;
    private static final ModConfigSpec.ConfigValue<String> PORTAL_BUILDABLE_MODE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> PORTAL_BUILDABLE_LIST;
    private static final ModConfigSpec.BooleanValue PORTAL_ALLOW_CONSTRUCTION;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ADVENTURE_BREAKABLE;
    private static final ModConfigSpec.BooleanValue CORPSE_DESPAWN_WHEN_EMPTY;
    private static final ModConfigSpec.IntValue FLOORS_PER_LAYER_MIN;
    private static final ModConfigSpec.IntValue FLOORS_PER_LAYER_MAX;

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
        PORTAL_NOT_ALLOWED = builder
                .comment("Shown when a portal may not be built here: the dimension gate")
                .comment("or the allowConstruction toggle refused it.")
                .define("portalNotAllowed", AnnouncementTexts.PORTAL_NOT_ALLOWED);
        LANE_SEALED = builder
                .comment("Shown when a layer transition portal refuses passage:")
                .comment("the layer's guardian has not fallen yet (boss gating).")
                .define("laneSealed", AnnouncementTexts.LANE_SEALED);
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
        PORTAL_BUILDABLE_MODE = builder
                .comment("Where dungeon portals may be built: 'whitelist' allows only the")
                .comment("listed dimensions, 'blacklist' allows every dimension except them.")
                .comment("Construction only: travel through an already-formed portal and the")
                .comment("mod's own dungeon entrance are never gated.")
                .define("buildableDimensions.mode", "whitelist",
                        value -> "whitelist".equals(value) || "blacklist".equals(value));
        PORTAL_BUILDABLE_LIST = builder
                .comment("The dimensions involved. The default keeps entrances to the")
                .comment("overworld and the dungeon itself, so nothing sprouts in the")
                .comment("Nether, the End, or another mod's dimension.")
                .defineListAllowEmpty("buildableDimensions.list",
                        List.of("minecraft:overworld",
                                org.bitwisemadness.infinitedungeon.spawn.SpawnPolicy.DUNGEON_DIMENSION),
                        () -> "minecraft:overworld",
                        value -> value instanceof String);
        PORTAL_ALLOW_CONSTRUCTION = builder
                .comment("When off, ignition attempts are refused with a message while")
                .comment("already-formed portals keep working: the 'entrance is an")
                .comment("admin-placed fixture' server mode.")
                .define("allowConstruction", true);
        builder.pop();
        builder.push("adventureLock");
        ADVENTURE_BREAKABLE = builder
                .comment("Blocks that may still be broken inside the dungeon while the")
                .comment("adventure lock holds. The default breaks nothing.")
                .defineListAllowEmpty("breakableBlocks", List.of(),
                        () -> "minecraft:stone",
                        value -> value instanceof String text
                                && org.bitwisemadness.infinitedungeonapi.data.ResourceRef
                                        .isValidLocation(text.trim()));
        builder.pop();
        // Root-level keys, named exactly as the plan (section 3): the band per
        // layer is at most 2048 blocks tall, so a floor is never forced under
        // 32 blocks by the cap.
        FLOORS_PER_LAYER_MIN = builder
                .comment("The fewest floors a dungeon layer may field. The exact count")
                .comment("per layer is derived from the world seed and layer index")
                .comment("within [floorsPerLayerMin, floorsPerLayerMax].")
                .defineInRange("floorsPerLayerMin", 5, 1, 64);
        FLOORS_PER_LAYER_MAX = builder
                .comment("The most floors a dungeon layer may field (see floorsPerLayerMin).")
                .comment("The first layer is the fixed-construction lobby and ignores both.")
                .defineInRange("floorsPerLayerMax", 10, 1, 64);
        builder.push("integrations");
        builder.push("corpse");
        CORPSE_DESPAWN_WHEN_EMPTY = builder
                .comment("In the dungeon, remove a corpse once its inventory has been")
                .comment("fully reclaimed (Corpse's isEmpty), so emptied corpses do not")
                .comment("litter the floors. Off leaves every corpse in place.")
                .define("despawnWhenEmpty", true);
        builder.pop();
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

    /** Which dimensions a dungeon portal may be built in. */
    public static org.bitwisemadness.infinitedungeon.portal.BuildableDimensions portalBuildableDimensions() {
        return new org.bitwisemadness.infinitedungeon.portal.BuildableDimensions(
                org.bitwisemadness.infinitedungeon.portal.BuildableDimensions.parseMode(PORTAL_BUILDABLE_MODE.get()),
                new HashSet<>(PORTAL_BUILDABLE_LIST.get()));
    }

    /** The message a player sees when a portal may not be built here. */
    public static String portalNotAllowed() {
        return PORTAL_NOT_ALLOWED.get();
    }

    /** The message a player sees when a sealed layer transition refuses them. */
    public static String laneSealed() {
        return LANE_SEALED.get();
    }

    /** Whether new portal ignition is allowed at all (admin-fixture mode). */
    public static boolean portalAllowConstruction() {
        return PORTAL_ALLOW_CONSTRUCTION.get();
    }

    /** Blocks still breakable inside the dungeon under the adventure lock. */
    public static java.util.Set<String> adventureLockBreakableBlocks() {
        return new HashSet<>(ADVENTURE_BREAKABLE.get());
    }

    /** Whether a fully reclaimed corpse is swept from the dungeon (section 5). */
    public static boolean corpseDespawnWhenEmpty() {
        return CORPSE_DESPAWN_WHEN_EMPTY.get();
    }

    /** The fewest floors a layer may field (plan section 3; default 5). */
    public static int floorsPerLayerMin() {
        return FLOORS_PER_LAYER_MIN.get();
    }

    /** The most floors a layer may field (plan section 3; default 10). */
    public static int floorsPerLayerMax() {
        return FLOORS_PER_LAYER_MAX.get();
    }
}
