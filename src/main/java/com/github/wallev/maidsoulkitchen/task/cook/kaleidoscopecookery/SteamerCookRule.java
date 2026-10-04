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
        if (be.hasResult()) return CookInventoryTransactions.canFitAll(cm.getOutputInv(), be.snapshot().items());
        return be.cookStateMatch() && cm.hasMaidRecs(be);
    }
    @Override public void cookMake(CookBeBase<SteamerBlockEntity> device, MaidCookManager<SteamerRecipe> cm) {
        var be = (SteamerBe) device; boolean changed = false;
        if (be.hasResult()) changed = be.extractResult(cm);
        if (be.cookStateMatch()) {
            var work = cm.peekMaidRec(be);
            if (work != null && be.insertInputs(work, cm)) changed |= cm.commitMaidRec(work);
        }
        if (changed) { be.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
