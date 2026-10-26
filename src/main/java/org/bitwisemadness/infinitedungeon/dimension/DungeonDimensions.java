package org.bitwisemadness.infinitedungeon.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * The resource keys of the dungeon dimension.
 *
 * <p>The dimension and its type ship as datapack JSON under
 * {@code data/infinite_dungeon/} - data-driven over code, per the org's scope
 * doctrine - so there is nothing to register here. These keys are the
 * Java-side handles the later waves travel by and resolve against.</p>
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
}
