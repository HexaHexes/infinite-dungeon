package org.bitwisemadness.infinitedungeon.portal;

import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;
import org.bitwisemadness.infinitedungeonapi.profile.PortalPolicy;
import org.bitwisemadness.infinitedungeonapi.profile.PortalReturnMode;

/**
 * The effective portal rules for a dimension: the profile's
 * {@link PortalPolicy} when it declares one, the config defaults otherwise
 * (issue #163).
 *
 * <p>The three rules - where an exit sends the player, whether new frames may
 * ignite, whether a destroyed anchor re-forms - were config-only; a dimension
 * profile now overrides them wholesale. Resolution is per call: the registry
 * replaces its table on reload, so nothing caches a stale answer. Free of
 * Minecraft types, per the suite's unit-test rule.</p>
 */
public final class PortalRules {

    private PortalRules() {
    }

    /** The dimension's effective portal policy. */
    public static PortalPolicy forDimension(String dimensionId) {
        return DimensionProfiles.get(dimensionId)
                .flatMap(DimensionProfile::portal)
                .orElseGet(PortalRules::configDefaults);
    }

    /** The dimension's effective exit mode. */
    public static PortalReturnMode returnMode(String dimensionId) {
        return forDimension(dimensionId).returnMode();
    }

    /** Whether new frames may ignite in the dimension. */
    public static boolean allowConstruction(String dimensionId) {
        return forDimension(dimensionId).allowConstruction();
    }

    /** Whether a destroyed anchor frame re-forms at the anchor coordinates. */
    public static boolean reformMissingPortal(String dimensionId) {
        return forDimension(dimensionId).reformMissingPortal();
    }

    /** The config defaults as a policy - the no-profile fallback. */
    private static PortalPolicy configDefaults() {
        return new PortalPolicy(
                DungeonConfigs.portalReturnMode(),
                DungeonConfigs.portalAllowConstruction(),
                DungeonConfigs.reformMissingPortal());
    }
}
