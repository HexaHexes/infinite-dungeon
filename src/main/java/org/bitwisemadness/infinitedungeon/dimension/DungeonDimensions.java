package org.bitwisemadness.infinitedungeon.dimension;

import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfile;
import org.bitwisemadness.infinitedungeonapi.profile.DimensionProfiles;

/**
 * The resource keys of the dungeon dimension.
 *
 * <p>The dimension and its type ship as datapack JSON under
 * {@code data/infinite_dungeon/} - data-driven over code, per the org's scope
 * doctrine - so there is nothing to register here. These keys are the
 * Java-side handles the later waves travel by and resolve against.</p>
 *
 * <p>Since the profile program (issue #162), dungeon membership is profile
 * driven: the consumers ask {@link #isDungeon(Level)}, which resolves through
 * the {@link DimensionProfiles} registry - the core dimension ships its own
 * profile as datapack data, and addon profiles register alongside it. The
 * hard {@link #LEVEL} equality stays only in the portal travel/ignition path
 * (issue #163's scope) and this class.</p>
 */
public final class DungeonDimensions {

    /** The dungeon dimension's id. */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("infinite_dungeon", "dungeon");

    /** The dungeon level. */
    public static final ResourceKey<Level> LEVEL =
            ResourceKey.create(Registries.DIMENSION, ID);

    /** The dungeon's dimension type. */
    public static final ResourceKey<DimensionType> TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, ID);

    private DungeonDimensions() {
    }

    /** Whether a level is a dungeon dimension per the profile registry. */
    public static boolean isDungeon(Level level) {
        return DimensionProfiles.isDungeonDimension(level.dimension().location().toString());
    }

    /** The level's dimension profile, when one is registered. */
    public static Optional<DimensionProfile> profileOf(Level level) {
        return DimensionProfiles.get(level.dimension().location().toString());
    }
}
