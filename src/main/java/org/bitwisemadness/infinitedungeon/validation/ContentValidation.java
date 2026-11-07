package org.bitwisemadness.infinitedungeon.validation;

import java.util.Optional;

/**
 * The authoring-time validation posture (issue #257): bad content fails with a
 * message that names the unit, never silently in-world.
 *
 * <p>Pure - the caller supplies whether a reference's mod is loaded and whether
 * the reference resolves, so every rule and its exact message are unit-testable
 * without a runtime (the {@code SpawnPoolEntry} rule).</p>
 */
public final class ContentValidation {

    /** The rule tag for a reference that names nothing. */
    public static final String REFERENCE_RULE = "reference-resolution";

    private ContentValidation() {
    }

    /**
     * A problem when a reference is gated by a <em>loaded</em> mod yet resolves
     * to nothing - a typo, or dead content. A reference whose mod is absent is a
     * legitimate skip (the {@code required_mod} contract), never a problem.
     *
     * @param unit     the content unit, named
     * @param field    the declaring field ({@code entity}, {@code table}, …)
     * @param reference the reference as written
     * @param modLoaded whether the reference's mod is loaded
     * @param resolves  whether the reference names an existing entry
     */
    public static Optional<ContentProblem> unresolved(String unit, String field,
            String reference, boolean modLoaded, boolean resolves) {
        if (modLoaded && !resolves) {
            return Optional.of(new ContentProblem(REFERENCE_RULE, unit,
                    field + " '" + reference + "' is in a loaded mod but names nothing"));
        }
        return Optional.empty();
    }

    /** The namespace of an id, defaulting to {@code minecraft} when unqualified. */
    public static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }
}
