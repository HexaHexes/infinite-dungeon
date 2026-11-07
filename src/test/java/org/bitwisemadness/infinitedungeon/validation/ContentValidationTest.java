package org.bitwisemadness.infinitedungeon.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The reference-resolution message form (issue #257). */
class ContentValidationTest {

    @Test
    void aLoadedModReferenceThatNamesNothingIsAProblem() {
        var problem = ContentValidation.unresolved(
                "pool infinite_dungeon:fodder", "entity", "some_mod:typo", true, false);
        assertTrue(problem.isPresent());
        assertEquals(
                "[reference-resolution] pool infinite_dungeon:fodder: "
                        + "entity 'some_mod:typo' is in a loaded mod but names nothing",
                problem.get().format());
    }

    @Test
    void anAbsentModsReferenceIsALegitimateSkip() {
        assertTrue(ContentValidation.unresolved("u", "entity", "gone_mod:x", false, false).isEmpty(),
                "a reference to an absent mod is the required_mod contract, not a problem");
    }

    @Test
    void aResolvingReferenceIsClean() {
        assertTrue(ContentValidation.unresolved("u", "entity", "minecraft:zombie", true, true).isEmpty());
    }

    @Test
    void namespaceDefaultsToMinecraft() {
        assertEquals("minecraft", ContentValidation.namespaceOf("zombie"));
        assertEquals("some_mod", ContentValidation.namespaceOf("some_mod:x"));
    }
}
