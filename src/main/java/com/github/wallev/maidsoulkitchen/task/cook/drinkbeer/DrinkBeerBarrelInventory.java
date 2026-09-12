package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import com.mojang.datafixers.util.Pair;
import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;
import lekavar.lma.drinkbeer.registries.ItemRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Native DrinkBeer slot rules for the MSK beer-barrel task.
 *
 * <p>Adapted from MaidSoul Brewery Public commits {@code ca84cf4},
 * {@code 0a0a46b}, and {@code f5e0fa9}. Keeping the rules here avoids a
 * Mixin that replaces methods on MSK's own task class.</p>
 */
final class DrinkBeerBarrelInventory {
    private DrinkBeerBarrelInventory() {
    }

    static boolean needsCups(Container container) {
        if (!isValidSlot(container, DrinkBeerBarrelRules.CUP_SLOT)) {
            return false;
        }

        ItemStack cups = container.getItem(DrinkBeerBarrelRules.CUP_SLOT);
        return DrinkBeerBarrelRules.needsCups(
                cups.getCount(), cups.isEmpty(), cups.is(ItemRegistry.EMPTY_BEER_MUG.get()));
    }

    static boolean hasEmptyBeerMug(IItemHandlerModifiable source) {
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(ItemRegistry.EMPTY_BEER_MUG.get())) {
                return true;
            }
        }
        return false;
    }

    static boolean fillCups(Container container, IItemHandlerModifiable source) {
        if (!needsCups(container)) {
            return false;
        }

        ItemStack originalCups = container.getItem(DrinkBeerBarrelRules.CUP_SLOT);
        int originalCount = originalCups.isEmpty() ? 0 : originalCups.getCount();
        int missing = DrinkBeerBarrelRules.REQUIRED_CUPS - originalCount;
        for (int slot = 0; slot < source.getSlots() && missing > 0; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || !stack.is(ItemRegistry.EMPTY_BEER_MUG.get())) {
                continue;
            }

            ItemStack extracted = source.extractItem(slot, missing, false);
            if (extracted.isEmpty()) {
                continue;
            }

            ItemStack current = container.getItem(DrinkBeerBarrelRules.CUP_SLOT);
            if (current.isEmpty()) {
                container.setItem(DrinkBeerBarrelRules.CUP_SLOT, extracted.copy());
            } else {
                ItemStack merged = current.copy();
                merged.grow(extracted.getCount());
                container.setItem(DrinkBeerBarrelRules.CUP_SLOT, merged);
            }
            missing -= extracted.getCount();
        }

        return missing < DrinkBeerBarrelRules.REQUIRED_CUPS - originalCount;
    }

    static boolean hasReturnedBucket(Container container) {
        int checkedSlots = Math.min(DrinkBeerBarrelRules.INGREDIENT_SLOTS, container.getContainerSize());
        for (int slot = 0; slot < checkedSlots; slot++) {
            if (container.getItem(slot).is(Items.BUCKET)) {
                return true;
            }
        }
        return false;
    }

    static void extractReturnedBuckets(
            Container container,
            IItemHandlerModifiable destination,
            BlockEntity blockEntity
    ) {
        int checkedSlots = Math.min(DrinkBeerBarrelRules.INGREDIENT_SLOTS, container.getContainerSize());
        boolean changed = false;
        for (int slot = 0; slot < checkedSlots; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !stack.is(Items.BUCKET)) {
                continue;
            }

            ItemStack remaining = ItemHandlerHelper.insertItemStacked(destination, stack.copy(), false);
            int moved = stack.getCount() - remaining.getCount();
            if (moved > 0) {
                container.removeItem(slot, moved);
                changed = true;
            }
        }
        if (changed) {
            markChanged(blockEntity);
        }
    }

    static void insertRecipeInputs(
            Container container,
            IItemHandlerModifiable source,
            BlockEntity blockEntity,
            Pair<List<Integer>, List<List<ItemStack>>> recipeIngredient
    ) {
        List<Integer> amounts = recipeIngredient.getFirst();
        List<List<ItemStack>> ingredients = recipeIngredient.getSecond();
        int entries = Math.min(amounts.size(), ingredients.size());

        for (int index = 0; index < entries; index++) {
            if (!isValidSlot(container, index)) {
                return;
            }

            int required = DrinkBeerBarrelRules.targetAmount(index, amounts.get(index));
            int missing = missingAmount(container, index, required, ingredients.get(index));
            if (missing < 0 || !hasEnoughAvailable(missing, ingredients.get(index))) {
                return;
            }
        }

        boolean changed = false;
        for (int index = 0; index < entries; index++) {
            int required = DrinkBeerBarrelRules.targetAmount(index, amounts.get(index));
            int missing = missingAmount(container, index, required, ingredients.get(index));
            if (missing > 0) {
                insertAndShrink(container, ingredients.get(index), index, missing);
                changed = true;
            }
        }

        if (changed) {
            markChanged(blockEntity);
        }
    }

    private static boolean isValidSlot(Container container, int slot) {
        return slot >= 0 && slot < container.getContainerSize();
    }

    private static int missingAmount(
            Container container,
            int targetSlot,
            int required,
            List<ItemStack> availableStacks
    ) {
        ItemStack current = container.getItem(targetSlot);
        if (current.isEmpty()) {
            return required;
        }
        if (matchesAny(current, availableStacks)) {
            return Math.max(0, required - current.getCount());
        }
        return -1;
    }

    private static boolean hasEnoughAvailable(int required, List<ItemStack> availableStacks) {
        int remaining = required;
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty()) {
                remaining -= stack.getCount();
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return remaining <= 0;
    }

    private static void insertAndShrink(
            Container container,
            List<ItemStack> availableStacks,
            int targetSlot,
            int amount
    ) {
        int remaining = amount;
        for (ItemStack stack : availableStacks) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack current = container.getItem(targetSlot);
            int moved = Math.min(remaining, stack.getCount());
            int newCount = (current.isEmpty() ? 0 : current.getCount()) + moved;
            container.setItem(targetSlot, stack.copyWithCount(newCount));
            stack.shrink(moved);
            remaining -= moved;
            if (remaining <= 0) {
                return;
            }
        }
    }

    private static boolean matchesAny(ItemStack current, List<ItemStack> availableStacks) {
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)) {
                return true;
            }
        }
        return false;
    }

    private static void markChanged(BlockEntity blockEntity) {
        if (blockEntity instanceof BeerBarrelBlockEntity barrel) {
            DrinkBeerBarrelAdapter.markChanged(barrel);
        } else {
            blockEntity.setChanged();
        }
    }
}
