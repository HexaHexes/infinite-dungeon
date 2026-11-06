package org.bitwisemadness.infinitedungeon.depth;

import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import org.bitwisemadness.infinitedungeon.config.DungeonConfigs;
import org.bitwisemadness.infinitedungeonapi.profile.CurveCoefficients;
import org.bitwisemadness.infinitedungeonapi.profile.CurveOverrides;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * The effective curve coefficients for a dimension: the profile's
 * {@link CurveOverrides} layered over the config defaults (issue #162).
 *
 * <p>The three plan curves (difficulty, magicule, density) keep their
 * config-tuned coefficients for the core dimension; a dimension profile may
 * override any subset per curve, and every absent override falls back to the
 * config. Resolution is per call - the registry replaces its table wholesale
 * on reload, so nothing here may cache a stale answer.</p>
 *
 * <p>Keyed by dimension id and free of Minecraft types, per the suite's
 * unit-test rule: the callers derive the id from their level and keep passing
 * their own depth coordinates to the pure curve math, which stays untouched.</p>
 */
public final class ProfileCurves {

    private ProfileCurves() {
    }

    /** Effective difficulty coefficients for a dimension: {base, linear, quadratic}. */
    public static double[] difficulty(String dimensionId) {
        return resolve(dimensionId, CurveOverrides::difficulty,
                DungeonConfigs::difficultyBase,
                DungeonConfigs::difficultyLinearPerFloor,
                DungeonConfigs::difficultyQuadraticPerLayer);
    }

    /** Effective magicule coefficients for a dimension: {base, linear, quadratic}. */
    public static double[] magicule(String dimensionId) {
        return resolve(dimensionId, CurveOverrides::magicule,
                DungeonConfigs::magiculeBase,
                DungeonConfigs::magiculeLinearPerFloor,
                DungeonConfigs::magiculeQuadraticPerLayer);
    }

    /** Effective density coefficients for a dimension: {base, linear, quadratic}. */
    public static double[] density(String dimensionId) {
        return resolve(dimensionId, CurveOverrides::density,
                DungeonConfigs::densityBase,
                DungeonConfigs::densityLinearPerFloor,
                DungeonConfigs::densityQuadraticPerLayer);
    }

    private static double[] resolve(String dimensionId,
            Function<CurveOverrides, Optional<CurveCoefficients>> selector,
            DoubleSupplier configBase, DoubleSupplier configLinear,
            DoubleSupplier configQuadratic) {
        Optional<CurveCoefficients> override = dimensionId == null ? Optional.empty()
                : DimensionProfiles.get(dimensionId)
                        .flatMap(DimensionProfile::curves)
                        .flatMap(selector);
        return new double[] {
                override.map(CurveCoefficients::base).orElseGet(configBase::getAsDouble),
                override.map(CurveCoefficients::linearPerFloor).orElseGet(configLinear::getAsDouble),
                override.map(CurveCoefficients::quadraticPerLayer).orElseGet(configQuadratic::getAsDouble),
        };
    }
}
