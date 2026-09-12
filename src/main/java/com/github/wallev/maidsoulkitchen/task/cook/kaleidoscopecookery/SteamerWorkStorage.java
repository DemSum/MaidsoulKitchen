package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CulinaryHubWorkStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;
import java.util.function.Predicate;

interface SteamerWorkStorage {
    IItemHandlerModifiable ingredients();

    IItemHandler outputDestination();

    boolean canSupplyIngredient(Predicate<ItemStack> predicate);

    boolean prepareIngredient(Predicate<ItemStack> predicate, int maximumCount);

    boolean canAcceptOutputs(List<ItemStack> outputs);

    void flushOutputs();

    void sync();

    static SteamerWorkStorage forMaid(EntityMaid maid) {
        return CulinaryHubWorkStorage.open(maid)
                .<SteamerWorkStorage>map(HubStorage::new)
                .orElseGet(() -> new MaidStorage(maid.getAvailableBackpackInv()));
    }

    final class MaidStorage implements SteamerWorkStorage {
        private final IItemHandlerModifiable inventory;

        private MaidStorage(IItemHandlerModifiable inventory) {
            this.inventory = inventory;
        }

        @Override
        public IItemHandlerModifiable ingredients() {
            return inventory;
        }

        @Override
        public IItemHandler outputDestination() {
            return inventory;
        }

        @Override
        public boolean canSupplyIngredient(Predicate<ItemStack> predicate) {
            return hasMatchingStack(inventory, predicate);
        }

        @Override
        public boolean prepareIngredient(Predicate<ItemStack> predicate, int maximumCount) {
            return canSupplyIngredient(predicate);
        }

        @Override
        public boolean canAcceptOutputs(List<ItemStack> outputs) {
            return SteamerAdapter.canFitAll(inventory, outputs);
        }

        @Override
        public void flushOutputs() {
        }

        @Override
        public void sync() {
        }
    }

    final class HubStorage implements SteamerWorkStorage {
        private final CulinaryHubWorkStorage storage;

        private HubStorage(CulinaryHubWorkStorage storage) {
            this.storage = storage;
        }

        @Override
        public IItemHandlerModifiable ingredients() {
            return storage.ingredients();
        }

        @Override
        public IItemHandler outputDestination() {
            return storage.outputs();
        }

        @Override
        public boolean canSupplyIngredient(Predicate<ItemStack> predicate) {
            return storage.hasIngredient(predicate);
        }

        @Override
        public boolean prepareIngredient(Predicate<ItemStack> predicate, int maximumCount) {
            return storage.prepareIngredient(predicate, maximumCount);
        }

        @Override
        public boolean canAcceptOutputs(List<ItemStack> outputs) {
            return storage.canAcceptOutputs(outputs)
                    && SteamerAdapter.canFitAll(storage.outputs(), outputs);
        }

        @Override
        public void flushOutputs() {
            storage.flushOutputs();
        }

        @Override
        public void sync() {
            storage.sync();
        }
    }

    private static boolean hasMatchingStack(IItemHandler inventory, Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return true;
            }
        }
        return false;
    }
}
