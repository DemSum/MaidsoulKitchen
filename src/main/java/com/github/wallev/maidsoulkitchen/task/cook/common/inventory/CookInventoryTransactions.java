package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Small transaction helpers for device inventories that cannot expose an atomic item handler. */
public final class CookInventoryTransactions {
    private CookInventoryTransactions() {
    }

    public static boolean canInsertAll(IItemHandler destination, ItemStack stack) {
        return stack.isEmpty() || ItemHandlerHelper.insertItemStacked(destination, stack.copy(), true).isEmpty();
    }

    public static boolean insertAll(IItemHandler destination, ItemStack stack) {
        return stack.isEmpty() || ItemHandlerHelper.insertItemStacked(destination, stack.copy(), false).isEmpty();
    }

    public static void returnOrDrop(IItemHandler destination, ItemStack stack, EntityMaid maid) {
        if (stack.isEmpty()) return;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, stack.copy(), false);
        if (!remainder.isEmpty()) maid.spawnAtLocation(remainder);
    }
}
