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

    /**
     * Source: upstream InvUtil.extractItem / MaidCookManager.removeItemStacks and the validated
     * local CulinaryHubWorkStorage.prepareIngredientCount. Upstream and beta insert before extraction;
     * a source which refuses real extraction can duplicate items. Keep the local extract-first sequence,
     * report actual destination acceptance, and return un-restorable physical remainders to the owner.
     * This is a stateless 1.21 Handler boundary, replacing those unsafe transfer call sites.
     */
    public static TransferResult transfer(IItemHandler source, int slot, IItemHandler destination,
                                          int requested, Predicate<ItemStack> matches) {
        if (requested <= 0 || source == destination) return new TransferResult(0, 0, ItemStack.EMPTY);
        ItemStack preview = source.extractItem(slot, requested, true);
        if (preview.isEmpty() || !matches.test(preview)) return new TransferResult(0, 0, ItemStack.EMPTY);
        int capacity = preview.getCount() - ItemHandlerHelper.insertItemStacked(destination, preview.copy(), true).getCount();
        if (capacity <= 0) return new TransferResult(0, 0, ItemStack.EMPTY);
        ItemStack extracted = source.extractItem(slot, capacity, false);
        if (extracted.isEmpty()) return new TransferResult(0, 0, ItemStack.EMPTY);
        int extractedCount = extracted.getCount();
        ItemStack remainder = matches.test(extracted) && ItemStack.isSameItemSameComponents(preview, extracted)
                ? ItemHandlerHelper.insertItemStacked(destination, extracted.copy(), false) : extracted.copy();
        int inserted = extractedCount - remainder.getCount();
        if (!remainder.isEmpty()) remainder = source.insertItem(slot, remainder.copy(), false);
        if (!remainder.isEmpty()) remainder = ItemHandlerHelper.insertItemStacked(source, remainder.copy(), false);
        return new TransferResult(extractedCount, inserted, remainder);
    }

    public record TransferResult(int extracted, int inserted, ItemStack remainder) {
        public TransferResult { remainder = remainder.copy(); }
        @Override public ItemStack remainder() { return remainder.copy(); }
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
        long count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && matches.test(stack)) count += stack.getCount();
        }
        // Keep the bounded transaction API without overflowing on creative/infinity handlers.
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** Input chests may receive leftovers only when they already store this kind of item. */
    public static boolean containsItem(IItemHandler inventory, ItemStack item) {
        return !item.isEmpty() && count(inventory, stack -> stack.is(item.getItem())) > 0;
    }

    /** Simulates a whole batch together, respecting unstackable items and actual slot acceptance. */
    public static boolean canFitAll(IItemHandler inventory, List<ItemStack> incoming) {
        return canFitAll(List.of(inventory), incoming);
    }

    /** Same verified batch simulation across bound inventories. NeoForge capabilities need not
     * implement IItemHandlerModifiable; no setter or persistent warehouse snapshot is required. */
    public static boolean canFitAll(List<? extends IItemHandler> inventories, List<ItemStack> incoming) {
        List<ItemStack[]> snapshots = new java.util.ArrayList<>();
        for (IItemHandler inventory : inventories) {
            ItemStack[] virtual = new ItemStack[inventory.getSlots()];
            for (int slot = 0; slot < virtual.length; slot++) virtual[slot] = inventory.getStackInSlot(slot).copy();
            snapshots.add(virtual);
        }
        for (ItemStack stack : incoming) {
            int remaining = stack.getCount();
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int index = 0; index < inventories.size() && remaining > 0; index++) {
                    IItemHandler inventory = inventories.get(index);
                    ItemStack[] virtual = snapshots.get(index);
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
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
