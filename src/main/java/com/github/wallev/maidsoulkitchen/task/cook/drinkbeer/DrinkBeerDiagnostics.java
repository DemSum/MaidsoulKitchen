package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookSearchDiagnostics;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Map;

/**
 * Temporary, opt-in DrinkBeer chain diagnostics.
 *
 * <p>Kept in one class so it can be removed after the live-world investigation.
 * It is completely silent unless {@code -Dmaidsoulkitchen.debugCookingChain=true}
 * is present in the JVM arguments.</p>
 */
final class DrinkBeerDiagnostics {
    private static final int MAX_LISTED_STACKS = 24;

    private DrinkBeerDiagnostics() {
    }

    static void evaluation(EntityMaid maid, BeerBarrelBlockEntity barrel,
                           MaidRecipesManager<?> manager, boolean actionable, String reason,
                           boolean canModify, boolean brewing, boolean outputReady,
                           boolean needsCups, boolean hasMugs, boolean returnedBucket) {
        if (!CookSearchDiagnostics.enabled()) return;
        MaidsoulKitchen.LOGGER.info(
                "Beer debug eval maid={} pos={} actionable={} reason={} can_modify={} brewing={} "
                        + "output_ready={} needs_cups={} input_has_mugs={} returned_bucket={} plans={} "
                        + "barrel={} hub_input={}",
                maid.getUUID(), barrel.getBlockPos(), actionable, reason, canModify, brewing,
                outputReady, needsCups, hasMugs, returnedBucket,
                manager.getRecipesIngredients().size(),
                containerContents(barrel.getBrewingInventory()),
                handlerContents(manager.getInputInv()));
    }

    static Snapshot snapshot(EntityMaid maid, BeerBarrelBlockEntity barrel,
                             MaidRecipesManager<?> manager) {
        if (!CookSearchDiagnostics.enabled()) return Snapshot.DISABLED;
        return new Snapshot(
                state(barrel),
                containerContents(barrel.getBrewingInventory()),
                handlerContents(manager.getInputInv()),
                handlerContents(manager.getOutputInv()),
                handlerContents(maid.getAvailableInv(true))
        );
    }

    static void action(EntityMaid maid, BeerBarrelBlockEntity barrel,
                       Snapshot before, Snapshot after) {
        if (!CookSearchDiagnostics.enabled()) return;
        MaidsoulKitchen.LOGGER.info(
                "Beer debug action maid={} pos={} before_state={} after_state={} "
                        + "before_barrel={} after_barrel={} before_hub_input={} after_hub_input={} "
                        + "before_hub_output={} after_hub_output={} before_maid_inv={} after_maid_inv={}",
                maid.getUUID(), barrel.getBlockPos(), before.state(), after.state(),
                before.barrel(), after.barrel(), before.hubInput(), after.hubInput(),
                before.hubOutput(), after.hubOutput(), before.maidInventory(), after.maidInventory());
    }

    static String available(Map<Item, Integer> available) {
        if (!CookSearchDiagnostics.enabled()) return "disabled";
        StringBuilder result = new StringBuilder("[");
        int listed = 0;
        for (Map.Entry<Item, Integer> entry : available.entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (listed > 0) result.append(',');
            result.append(BuiltInRegistries.ITEM.getKey(entry.getKey()))
                    .append('x').append(entry.getValue());
            listed++;
            if (listed >= MAX_LISTED_STACKS) {
                result.append(",...");
                break;
            }
        }
        return result.append(']').toString();
    }

    static void recipePlan(ItemStack beerCup, int ingredientCount, boolean valid,
                           int maxCount, List<Integer> plannedAmounts,
                           String availableBefore, Map<Item, Integer> availableAfter) {
        if (!CookSearchDiagnostics.enabled()) return;
        MaidsoulKitchen.LOGGER.info(
                "Beer debug plan cup={} ingredient_count={} valid={} max_count={} amounts={} "
                        + "available_before={} available_after={}",
                stack(beerCup), ingredientCount, valid, maxCount, plannedAmounts,
                availableBefore, available(availableAfter));
    }

    private static String state(BeerBarrelBlockEntity barrel) {
        if (DrinkBeerBarrelAdapter.isOutputReady(barrel)) return "ready";
        if (!DrinkBeerBarrelAdapter.canModifyInputs(barrel)) return "brewing";
        return "idle";
    }

    private static String containerContents(Container container) {
        StringBuilder result = new StringBuilder("[");
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (slot > 0) result.append(',');
            result.append(slot).append('=').append(stack(container.getItem(slot)));
        }
        return result.append(']').toString();
    }

    private static String handlerContents(IItemHandler inventory) {
        if (inventory == null) return "null";
        StringBuilder result = new StringBuilder("[");
        int listed = 0;
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            total += stack.getCount();
            if (stack.isEmpty()) continue;
            if (listed < MAX_LISTED_STACKS) {
                if (listed > 0) result.append(',');
                result.append(slot).append('=').append(stack(stack));
            }
            listed++;
        }
        if (listed > MAX_LISTED_STACKS) {
            result.append(",...+").append(listed - MAX_LISTED_STACKS).append("stacks");
        }
        return result.append(";total=").append(total).append(']').toString();
    }

    private static String stack(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    record Snapshot(String state, String barrel, String hubInput,
                    String hubOutput, String maidInventory) {
        private static final Snapshot DISABLED = new Snapshot("disabled", "", "", "", "");
    }
}
