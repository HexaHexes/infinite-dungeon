package org.bitwisemadness.infinitedungeon.validation;

/**
 * One authoring-time problem (issue #257): the rule it broke, the unit that
 * broke it, and why. Pure, so the failure text itself is pinnable by the unit
 * suite.
 *
 * @param rule    the rule tag (see {@link ContentValidation})
 * @param unit    the content unit, named so an author can find it
 * @param message what is wrong
 */
public record ContentProblem(String rule, String unit, String message) {

    /** The one-line report form: {@code [rule] unit: message}. */
    public String format() {
        return "[" + rule + "] " + unit + ": " + message;
    }
}
