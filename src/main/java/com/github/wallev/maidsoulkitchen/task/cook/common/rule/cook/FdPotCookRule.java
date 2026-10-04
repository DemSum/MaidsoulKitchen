package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.crafting.RecipeInput;

/** Source: 58ec08ec FdPotCookRule.java (MIT). Keeps meal/container -> output -> cleanup ->
 * input ordering. Shared native meal/container access keeps the upstream FD/Moka rule contract.
 * Matching containers stay in place while a meal exists; slot receipts and commit-after-acceptance
 * fix upstream live-stack mutation/early consumption. Replaces beta IFdPotCook execution. */
public class FdPotCookRule<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends AbstractCookRule<B, R> {
    private static final FdPotCookRule<?, ?> INSTANCE = new FdPotCookRule<>();
    @SuppressWarnings("unchecked") public static <B extends BlockEntity, R extends Recipe<? extends RecipeInput>> FdPotCookRule<B, R> getInstance() { return (FdPotCookRule<B, R>) INSTANCE; }
    @Override public boolean canMoveTo(CookBeBase<B> cookBe, MaidCookManager<R> cm) {
        CookBeBase<B> pot = cookBe;
        if (pot.canTakeResult() && pot.hasResult() && cm.canTakeResult(pot.getResult())) return true;
        ItemStack container = pot.getNowContainer();
        ItemStack needed = pot.getNeedContainer();
        if (pot.hasMeal()) {
            if (!cm.getCookInv().hasOutputAvailableSlot())
                cm.reportWorkFeedback("chat_bubble.maidsoulkitchen.cook.output_full");
            else if (!needed.isEmpty() && (container.isEmpty() || !container.is(needed.getItem()))
                    && cm.getItem(stack -> stack.is(needed.getItem())).isFail())
                cm.reportMissingRequirement(needed);
        }
        if (pot.hasMeal() && cm.getCookInv().hasOutputAvailableSlot() && !needed.isEmpty()
                && (container.isEmpty() || !container.is(needed.getItem()))
                && !cm.getItem(stack -> stack.is(needed.getItem())).isFail()) return true;
        if (pot.hasInputs() && cm.getCookInv().hasInputAvailableSlot() && !pot.recMatch()) return true;
        if (!container.isEmpty() && !pot.hasMeal() && !pot.hasInputs() && cm.getCookInv().hasInputAvailableSlot()) return true;
        return pot.cookStateMatch() && !pot.hasMeal() && !pot.hasInputs() && cm.hasMaidRecs();
    }
    @Override public void cookMake(CookBeBase<B> cookBe, MaidCookManager<R> cm) {
        CookBeBase<B> pot = cookBe;
        boolean changed = false;
        if (pot.hasMeal() && cm.getCookInv().hasOutputAvailableSlot()) {
            ItemStack needed = pot.getNeedContainer();
            ItemStack current = pot.getNowContainer();
            if (!current.isEmpty() && !current.is(needed.getItem()))
                changed |= cm.takeItem(pot.getInv(), pot.getContainerSlot(), cm.getInputInv(), stack -> true) > 0;
            if (!needed.isEmpty() && pot.getNowContainer().isEmpty()) {
                var source = cm.getItem(stack -> stack.is(needed.getItem()));
                changed |= cm.insertItem(source, pot.getInv(), pot.getContainerSlot(), pot.getMeal().getCount()) > 0;
            }
        }
        if (pot.canTakeResult() && pot.hasResult()) {
            boolean taken = pot.extractResult(cm);
            if (taken) pot.awardExp();
            changed |= taken;
        }
        if (pot.hasInputs() && !pot.recMatch()) changed |= pot.takeInputs(cm);
        if (!pot.getNowContainer().isEmpty() && !pot.hasMeal() && !pot.hasInputs())
            changed |= cm.takeItem(pot.getInv(), pot.getContainerSlot(), cm.getInputInv(), stack -> true) > 0;
        if (pot.cookStateMatch() && !pot.hasInputs() && !pot.hasMeal()) {
            var rec = cm.peekMaidRec();
            if (rec != null && pot.insertInputs(rec, cm)) changed |= cm.commitMaidRec(rec);
        }
        if (changed) { pot.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
