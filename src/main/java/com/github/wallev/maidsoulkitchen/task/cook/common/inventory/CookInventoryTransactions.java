package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;
import java.util.function.Predicate;

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

    public static int count(IItemHandler inventory, Predicate<ItemStack> matches) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && matches.test(stack)) count += stack.getCount();
        }
        return count;
    }

    /** Input chests may receive leftovers only when they already store this kind of item. */
    public static boolean containsItem(IItemHandler inventory, ItemStack item) {
        return !item.isEmpty() && count(inventory, stack -> stack.is(item.getItem())) > 0;
    }

    /** Simulates a whole batch together, respecting unstackable items and actual slot acceptance. */
    public static boolean canFitAll(IItemHandler inventory, List<ItemStack> incoming) {
        ItemStack[] virtual = new ItemStack[inventory.getSlots()];
        for (int slot = 0; slot < virtual.length; slot++) virtual[slot] = inventory.getStackInSlot(slot).copy();
        for (ItemStack stack : incoming) {
            int remaining = stack.getCount();
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int slot = 0; slot < virtual.length && remaining > 0; slot++) {
                    ItemStack present = virtual[slot];
                    if ((pass == 0 && present.isEmpty()) || (pass == 1 && !present.isEmpty())) continue;
                    if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, stack)) continue;
                    if (!inventory.isItemValid(slot, stack)) continue;
                    int limit = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
                    int capacity = Math.max(0, limit - present.getCount());
                    int accepted = remaining - inventory.insertItem(slot, stack.copyWithCount(remaining), true).getCount();
                    int moved = Math.min(capacity, Math.max(0, accepted));
                    if (moved == 0) continue;
                    virtual[slot] = stack.copyWithCount(present.getCount() + moved);
                    remaining -= moved;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
