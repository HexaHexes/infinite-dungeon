package org.bitwisemadness.infinitedungeon.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a player entered the dungeon from: the dimension and portal position
 * they walked in through, per player.
 *
 * <p>Stored as a NeoForge data attachment so it rides in the player's own NBT
 * and persists with them across sessions. Section 4's guarantee: walking back
 * out through any exit portal returns the player to their own anchor, so a
 * party entering through separate overworld portals each exits at their own.</p>
 *
 * @param dimension the dimension the anchor lives in
 * @param x         the anchor portal's block x
 * @param y         the anchor portal's block y
 * @param z         the anchor portal's block z
 */
public record EntryAnchor(ResourceKey<Level> dimension, int x, int y, int z) {

    public static final Codec<EntryAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(EntryAnchor::dimension),
            Codec.INT.fieldOf("x").forGetter(EntryAnchor::x),
            Codec.INT.fieldOf("y").forGetter(EntryAnchor::y),
            Codec.INT.fieldOf("z").forGetter(EntryAnchor::z)
    ).apply(instance, EntryAnchor::new));

    /** The default the attachment type needs; {@code hasData} tells unset from set. */
    public static EntryAnchor empty() {
        return new EntryAnchor(Level.OVERWORLD, 0, 64, 0);
    }

    /** The anchor's position as a block position. */
    public net.minecraft.core.BlockPos position() {
        return new net.minecraft.core.BlockPos(x, y, z);
    }
}
