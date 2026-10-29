package org.bitwisemadness.infinitedungeon.portal;

/**
 * Whether a portal may be ignited here, as pure logic: the admin construction
 * toggle and the dimension gate must both allow it.
 *
 * <p>With `portal.allowConstruction` off, ignition is refused everywhere while
 * already-formed portals keep working - the "entrance is an admin-placed
 * fixture" mode of section 4. The two refusal reasons share one announcement,
 * so this predicate is the whole decision.</p>
 */
public final class ConstructionPolicy {

    private ConstructionPolicy() {
    }

    /** Whether an ignition attempt may proceed. */
    public static boolean mayIgnite(boolean allowConstruction, boolean dimensionAllowed) {
        return allowConstruction && dimensionAllowed;
    }
}
