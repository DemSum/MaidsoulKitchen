package com.github.wallev.maidsoulkitchen.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Source: 58ec08ec MaidCookManager binding/chest scan, plus NeoForge 1.21.1 menu-close API.
 * The upstream backpack retry gate cannot notice external bound-chest refills. Adapt that
 * boundary after the menu's stopOpen, notifying existing maid-owned managers only. No tick
 * listener, manager registry, inventory cache or work queue; replaces one-second polling. */
public final class CookingIngredientEvents {
    private CookingIngredientEvents() { }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        Set<Container> containers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IItemHandler> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var slot : event.getContainer().slots) {
            if (slot instanceof SlotItemHandler handlerSlot) {
                IItemHandler handler = handlerSlot.getItemHandler();
                handlers.add(handler);
                // Vanilla capability lookups create fresh wrappers; compare the underlying
                // container rather than requiring identical wrapper objects across lookups.
                if (handler instanceof net.neoforged.neoforge.items.wrapper.InvWrapper wrapper)
                    containers.add(wrapper.getInv());
            }
            else if (slot.container != event.getEntity().getInventory()) containers.add(slot.container);
        }
        if (containers.isEmpty() && handlers.isEmpty()) return;
        for (EntityMaid maid : level.getEntities(EntityTypeTest.forClass(EntityMaid.class), EntityMaid::isAlive)) {
            var manager = ((IAddonMaid) maid).tlmk$getCookManager();
            if (manager != null) manager.boundIngredientContainerClosed(containers, handlers);
        }
    }
}
