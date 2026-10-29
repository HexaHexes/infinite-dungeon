package org.bitwisemadness.infinitedungeon.portal;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;

/**
 * The per-player entry anchor: bound when a player walks into a dungeon
 * portal, read when they walk back out.
 *
 * <p>A NeoForge data attachment with a codec serializer, so the anchor is
 * saved in the player's own NBT and survives sessions - section 4's "anchors
 * persist with the player". {@link #has} distinguishes "never entered" from
 * "entered", which is what the travel policy keys on; the missing-anchor case
 * belongs to the anchor-handling issue.</p>
 */
public final class EntryAnchors {

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, InfiniteDungeon.MOD_ID);

    public static final Supplier<AttachmentType<EntryAnchor>> ENTRY_ANCHOR =
            TYPES.register("entry_anchor", () -> AttachmentType.builder(EntryAnchor::empty)
                    .serialize(EntryAnchor.CODEC)
                    .build());

    private EntryAnchors() {
    }

    /** Registers the attachment types on the mod bus; called from the constructor. */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** Binds (or rebinds) a player's anchor to the portal they walked in through. */
    public static void bind(ServerPlayer player, ResourceKey<Level> dimension, BlockPos portalPos) {
        player.setData(ENTRY_ANCHOR.get(),
                new EntryAnchor(dimension, portalPos.getX(), portalPos.getY(), portalPos.getZ()));
    }

    /** Whether this player has ever entered the dungeon through a portal. */
    public static boolean has(ServerPlayer player) {
        return player.hasData(ENTRY_ANCHOR.get());
    }

    /** The player's anchor, when they have one. */
    public static Optional<EntryAnchor> get(ServerPlayer player) {
        return has(player) ? Optional.of(player.getData(ENTRY_ANCHOR.get())) : Optional.empty();
    }

    /** Forgets the anchor; the anchor-handling issue may use it. */
    public static void clear(ServerPlayer player) {
        player.removeData(ENTRY_ANCHOR.get());
    }
}
