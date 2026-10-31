package org.bitwisemadness.infinitedungeon.treasure;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.bitwisemadness.infinitedungeon.InfiniteDungeon;
/**
 * The core mod's items - starting with the miniboss key (issue #41): dropped
 * by minibosses, consumed by sealed treasure rooms (#46 reads
 * {@code treasure.requireKey}).
 *
 * <p>Rendered through vanilla's own tripwire-hook texture: the plan's texture
 * rule (no new textures - the same rule PortalBlocks follows), so the model
 * below points at a vanilla sheet and no PNG ships.</p>
 */
public final class KeyItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(InfiniteDungeon.MOD_ID);

    public static final DeferredItem<Item> KEY =
            ITEMS.registerItem("key", Item::new,
                    new Item.Properties().stacksTo(16));

    private KeyItems() {
    }

    /** Registers the items on the mod bus; called from the mod constructor. */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
