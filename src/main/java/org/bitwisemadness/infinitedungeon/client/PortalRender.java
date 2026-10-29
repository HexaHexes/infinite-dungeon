package org.bitwisemadness.infinitedungeon.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
import org.bitwisemadness.infinitedungeon.portal.PortalBlocks;

/**
 * Client rendering for the dungeon portal: applies the configured tint to the
 * portal block, so the colour comes from config multiplied over the vanilla
 * nether portal texture - no new texture is generated, per issue #67.
 *
 * <p>The tint only reaches the model because the block's model declares
 * {@code tintindex 0} on its faces; without a block colour handler registered
 * on this dist, the texture ships unmultiplied. Registered with
 * {@code Dist.CLIENT}, so a dedicated server never loads rendering code.</p>
 */
@EventBusSubscriber(modid = InfiniteDungeon.MOD_ID, value = Dist.CLIENT)
public final class PortalRender {

    private PortalRender() {
    }

    /** Registers the dungeon portal's colour handler on the client. */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> PortalTint.colour(),
                PortalBlocks.DUNGEON_PORTAL.get());
    }
}
