package org.bitwisemadness.infinitedungeon.portal;

/**
 * The dungeon portal's tint colour, as pure logic: parse and validate the
 * config's hex form without touching Minecraft types, so the wording of the
 * rule is unit-testable.
 *
 * <p>Accepted forms: six hex digits with an optional leading {@code #}, any
 * case - {@code FF88CC}, {@code #ff88cc}. Anything else is not a colour, and
 * {@link #parse} falls back to {@link #DEFAULT} (white: the vanilla nether
 * portal texture rendered exactly as it ships, which is the plan's rule that
 * portals keep the nether texture).</p>
 *
 * @param rgb the 24-bit RGB value
 */
public record TintColour(int rgb) {

    /** The shipped default: white - the texture, untouched. */
    public static final TintColour DEFAULT = new TintColour(0xFFFFFF);

    public TintColour {
        rgb = rgb & 0xFFFFFF;
    }

    /** Whether the config value is a well-formed colour. */
    public static boolean isValidFormat(String raw) {
        if (raw == null) {
            return false;
        }
        String digits = raw.startsWith("#") ? raw.substring(1) : raw;
        if (digits.length() != 6) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** Parses a config value; anything unrecognised is the default colour. */
    public static TintColour parse(String raw) {
        if (!isValidFormat(raw)) {
            return DEFAULT;
        }
        String digits = raw.startsWith("#") ? raw.substring(1) : raw;
        return new TintColour(Integer.parseInt(digits, 16));
    }

    /** The canonical six-digit uppercase form, for round-tripping. */
    public static String format(TintColour colour) {
        return String.format(java.util.Locale.ROOT, "%06X", colour.rgb());
    }
}
