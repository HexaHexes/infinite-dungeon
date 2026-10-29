package org.bitwisemadness.infinitedungeon.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.infinitedungeon.portal.TintColour;

/**
 * The portal tint, as a CLIENT config: it colours the dungeon portal's
 * rendering only, so it lives in the client config file and never affects a
 * server. The default is white - the vanilla nether portal texture rendered
 * exactly as it ships, per the plan's rule that portals keep the nether
 * texture - and a pack sets a hex colour to tint them.
 */
public final class PortalTint {

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> TINT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("portal");
        TINT = builder
                .comment("Tint applied to the dungeon portal's texture: six hex digits,")
                .comment("with an optional # (for example 88CCFF). FFFFFF leaves the vanilla")
                .comment("nether portal texture untouched.")
                .define("tint", TintColour.format(TintColour.DEFAULT),
                        value -> value instanceof String text && TintColour.isValidFormat(text));
        builder.pop();
        SPEC = builder.build();
    }

    private PortalTint() {
    }

    /** Registers the client config; called from the mod constructor. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /** The configured tint as a 24-bit RGB value, for the colour handler. */
    public static int colour() {
        return TintColour.parse(TINT.get()).rgb();
    }
}
