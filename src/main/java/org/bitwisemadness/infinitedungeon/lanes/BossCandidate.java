package org.bitwisemadness.infinitedungeon.lanes;

import java.util.Optional;

/**
 * One guardian candidate the roster providers offer the schedule (issue #231,
 * plan section 6). Pure - no Minecraft - so the selection suite loads without
 * a runtime (the #200 rule).
 *
 * @param entityId    the boss entity's registry id
 * @param rawPower    its power metric (EP/AP/MP as the roster provider reads
 *                    it), strictly positive
 * @param depthExempt true when depth scaling does not apply at spawn (the
 *                    Tensura/NEB families), so raw power is its own metric
 * @param family      optional family id constraining same-family ordering
 *                    across depth (the plan's monotone-within-family rule);
 *                    absent means unconstrained and interleave freely
 */
public record BossCandidate(String entityId,
                            double rawPower,
                            boolean depthExempt,
                            Optional<String> family) {

    public BossCandidate {
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }
        if (!(rawPower > 0.0) || Double.isNaN(rawPower)) {
            throw new IllegalArgumentException(
                    "rawPower must be a positive number, got " + rawPower);
        }
        family = family == null ? Optional.empty() : family.filter(f -> !f.isBlank());
    }

    /**
     * The candidate's measurable power at a depth: raw for depth-exempt
     * families, raw times the difficulty value otherwise (plan section 6:
     * "filtered by effective power after depth scaling").
     */
    public double effectivePower(double difficulty) {
        return depthExempt ? rawPower : rawPower * difficulty;
    }
}
