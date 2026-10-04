package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source: 58ec08ec NormalCookRule.java (MIT). Keeps output -> invalid-input cleanup -> insertion.
 * Handler receipts replace live-stack shrinking; commit follows complete device acceptance instead
 * of the upstream poll-before-insert defect. Replaces beta INormalCook execution on migrated devices. */
public class NormalCookRule<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends AbstractCookRule<B, R> {
    private static final NormalCookRule<?, ?> INSTANCE = new NormalCookRule<>();
    @SuppressWarnings("unchecked") public static <B extends BlockEntity, R extends Recipe<? extends RecipeInput>> NormalCookRule<B, R> getInstance() {
        return (NormalCookRule<B, R>) INSTANCE;
    }
    @Override public boolean canMoveTo(CookBeBase<B> cookBe, MaidCookManager<R> cm) {
        if (cookBe.canTakeResult() && cookBe.hasResult() && cm.canTakeResult(cookBe.getResult())) return true;
        if (cookBe.hasInputs() && cm.getCookInv().hasInputAvailableSlot() && !cookBe.recMatch()) return true;
        return cookBe.cookStateMatch() && !cookBe.hasInputs() && cm.hasMaidRecs();
    }
    @Override public void cookMake(CookBeBase<B> cookBe, MaidCookManager<R> cm) {
        boolean changed = false;
        if (cookBe.canTakeResult() && cookBe.hasResult()) {
            changed = cookBe.extractResult(cm);
            if (changed) cookBe.awardExp();
        }
        if (cookBe.hasInputs() && !cookBe.recMatch()) changed |= cookBe.takeInputs(cm);
        if (cookBe.cookStateMatch() && !cookBe.hasInputs()) {
            var rec = cm.peekMaidRec();
            if (rec != null && cookBe.insertInputs(rec, cm)) changed |= cm.commitMaidRec(rec);
        }
        if (changed) { cookBe.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
