package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;

/** Source: 58ec08ec NormalCookRule output -> insert lifecycle (MIT). KC requires whole-batch
 * output capacity and permits filling empty slots while other native slots cook. Those two device
 * differences retain the verified local behavior; only MaidCookManager owns plans and transactions.
 * Replaces independent MaidSteamerWorkTask and does not tick KC cooking a second time. */
public final class SteamerCookRule extends AbstractCookRule<SteamerBlockEntity, SteamerRecipe> {
    public static final SteamerCookRule INSTANCE = new SteamerCookRule();
    @Override public boolean canMoveTo(CookBeBase<SteamerBlockEntity> device, MaidCookManager<SteamerRecipe> cm) {
        var be = (SteamerBe) device;
        if (be.hasResult()) return cm.canAcceptNativeResults(be.snapshot().items());
        return be.cookStateMatch() && cm.hasMaidRecs(be);
    }
    @Override public void cookMake(CookBeBase<SteamerBlockEntity> device, MaidCookManager<SteamerRecipe> cm) {
        var be = (SteamerBe) device; boolean changed = false;
        if (be.hasResult()) {
            if (!be.extractResult(cm)) return;
            changed = true;
        }
        // Source: 58ec08ec NormalCookRule output -> input lifecycle, plus c9273ce5
        // TaskKcSteamer.workAt / SteamerAdapter.placeFoodFromSlot's native fill operation.
        // KC has no 1.20 Be: unlike a single-slot drying rack, one visit fills all 4/8
        // free slots. Preserve that batch with manager-owned physical one-item receipts,
        // committing only accepted MaidRecs; replaces the port's one-unit-per-visit regression.
        int freeSlots = be.snapshot().emptySlotCount();
        for (int slot = 0; slot < freeSlots && be.cookStateMatch(); slot++) {
            var work = cm.peekMaidRec(be);
            if (work == null || !be.insertInputs(work, cm)) break;
            changed = true;
            if (!cm.commitMaidRec(work)) break;
        }
        if (changed) { be.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
