package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CulinaryHubWorkStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Thin view of the existing hub/backpack transaction boundary; owns no inventories or cached items. */
final class StockpotWorkStorage {
    private final EntityMaid maid;
    private final CulinaryHubWorkStorage hub;
    final IItemHandlerModifiable inputs;
    final IItemHandlerModifiable returns;
    final IItemHandler outputs;

    StockpotWorkStorage(EntityMaid maid) {
        this.maid = maid;
        hub = CulinaryHubWorkStorage.open(maid).orElse(null);
        var backpack = maid.getAvailableBackpackInv();
        returns = hub == null ? backpack : hub.ingredients();
        inputs = hub == null ? backpack : new CombinedInvWrapper(hub.ingredients(), backpack);
        outputs = hub == null ? backpack : hub.outputs();
    }

    List<ItemStack> available() {
        List<ItemStack> result = hub == null ? new ArrayList<>() : new ArrayList<>(hub.availableInputs());
        var backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (!stack.isEmpty()) result.add(stack.copy());
        }
        return result;
    }

    boolean prepare(List<ItemStack> requirements) {
        for (ItemStack required : aggregate(requirements)) {
            Predicate<ItemStack> same = stack -> ItemStack.isSameItemSameComponents(required, stack);
            int local = CookInventoryTransactions.count(inputs, same);
            if (local >= required.getCount()) continue;
            if (hub == null) return false;
            int backpack = CookInventoryTransactions.count(maid.getAvailableBackpackInv(), same);
            if (!hub.prepareIngredientCount(same, required.getCount() - backpack)) return false;
        }
        return true;
    }

    ItemStack take(ItemStack expected) {
        return take(stack -> ItemStack.isSameItemSameComponents(expected, stack));
    }

    ItemStack take(Predicate<ItemStack> matches) {
        for (int slot = 0; slot < inputs.getSlots(); slot++) {
            ItemStack stack = inputs.getStackInSlot(slot);
            if (!stack.isEmpty() && matches.test(stack)) return inputs.extractItem(slot, 1, false);
        }
        return ItemStack.EMPTY;
    }

    boolean canReturn(ItemStack stack) { return CookInventoryTransactions.canInsertAll(returns, stack); }

    boolean canOutput(ItemStack stack) {
        return CookInventoryTransactions.canFitAll(outputs, List.of(stack))
                && (hub == null || !hub.hasOutputBindings() || hub.canAcceptOutputs(List.of(stack)));
    }

    void returnInput(ItemStack stack) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(returns, stack, false);
        CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), remainder, maid);
    }

    void storeOutput(ItemStack stack) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(outputs, stack, false);
        CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), remainder, maid);
    }

    void flush() { if (hub != null) hub.flushOutputs(); }
    void sync() { if (hub != null) hub.sync(); }
    void storeUnused(Predicate<ItemStack> retain) { if (hub != null) hub.storeUnusedInputs(retain); }

    private static List<ItemStack> aggregate(List<ItemStack> requirements) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : requirements) {
            if (stack.isEmpty()) continue;
            var existing = result.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).findFirst();
            if (existing.isPresent()) existing.get().grow(stack.getCount());
            else result.add(stack.copy());
        }
        return result; // Quantities only; aggregated stacks never inserted/extracted as physical items.
    }
}
