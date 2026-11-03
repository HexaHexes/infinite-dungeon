package org.bitwisemadness.infinitedungeon.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The interior-bounds truth table (issue #254). */
class InteriorRuleTest {

    @Test
    void anExposedNaturalSpawnInTheDungeonIsDenied() {
        assertTrue(InteriorRule.denies(true, true, true, true, true),
                "a roof spawn sits at the column surface and must be denied");
        assertTrue(InteriorRule.denies(true, true, true, true, false),
                "exposure alone denies, supported or not");
    }

    @Test
    void anUnsupportedSpawnIsDeniedEvenWhenCovered() {
        assertTrue(InteriorRule.denies(true, true, true, false, false),
                "no sturdy floor below cannot be an interior");
    }

    @Test
    void aCoveredSupportedNaturalSpawnInTheDungeonPasses() {
        assertFalse(InteriorRule.denies(true, true, true, false, true),
                "sky-hidden plus sturdy floor is the interior definition");
    }

    @Test
    void theToggleOffAllowsEverything() {
        assertFalse(InteriorRule.denies(false, true, true, true, false),
                "off restores exact vanilla behaviour");
    }

    @Test
    void eventSpawnsAreNeverDenied() {
        assertFalse(InteriorRule.denies(true, false, true, true, false),
                "scripted raids are code-positioned at designed interiors");
    }

    @Test
    void otherDimensionsAreNeverDenied() {
        assertFalse(InteriorRule.denies(true, true, false, true, false),
                "the overworld's outdoors are supposed to be sky-visible");
    }
}
