package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.GatherResult;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.*;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.List;

/** Source: 58ec08ec CookBeBase/FdPot device actions (MIT), with verified KC TaskKcStockpot
 * load/collect/clearIngredients moved here. No source stockpot existed. Keep native lid, soup,
 * component inputs, safety events and portions; manager owns every physical inventory transaction.
 * Replaces beta workAt and its Plan/Storage actions. There are no saved recipe/output caches. */
public final class StockpotBe extends CookBeBase<StockpotBlockEntity> {
    public StockpotBe(EntityMaid maid) { super(maid); }
    public StockpotAdapter.Snapshot snapshot() { return StockpotAdapter.inspect(be, serverLevel).orElseThrow(); }
    @Override public boolean isCookBe(BlockEntity device) { return StockpotAdapter.supports(device); }
    @Override public IItemHandlerModifiable getInv() { throw new UnsupportedOperationException("KC stockpot uses native interactions"); }
    @Override public int getIngredientSize() { return 9; }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("KC stockpot exposes native portions"); }
    @Override public boolean hasInputs() { return snapshot().hasIngredients(); }
    @Override public boolean hasResult() { return snapshot().status() == IStockpot.FINISHED && snapshot().portions() > 0; }
    @Override public ItemStack getResult() { return snapshot().result().copyWithCount(1); }
    @Override public boolean recMatch() { return StockpotRecSerializerManager.INSTANCE.hasAllowedCompletion(snapshot(), serverLevel, TaskKcStockpot.settings(maid)); }
    @Override public boolean cookStateMatch() { return snapshot().heated(); }
    @Override public boolean isAwaitingNativeCooking() {
        var state = snapshot();
        return state.status() == IStockpot.COOKING && state.covered() && state.heated();
    }
    @Override public void markChanged() { defaultChanged(); }
    static boolean isBoundTo(MaidRec work, BlockEntity device) {
        var p = work.parameters();
        return device != null && p.getOrDefault("x", Integer.MIN_VALUE) == device.getBlockPos().getX()
                && p.getOrDefault("y", Integer.MIN_VALUE) == device.getBlockPos().getY()
                && p.getOrDefault("z", Integer.MIN_VALUE) == device.getBlockPos().getZ()
                && p.getOrDefault("device", 0) == System.identityHashCode(device);
    }
    /** Exact component/count conditions belong to MaidRec. A player's edit of a half pot revokes
     * the stale unit rather than replenishing or clearing it using a second planner snapshot. */
    public boolean matchesWork(MaidRec work) {
        if (!isBoundTo(work, be) || be.isRemoved()) return false;
        var state = snapshot(); var fixed = work.maidItems().stream().filter(item -> item.role() == MaidItem.Role.DEVICE_INPUT).toList();
        var inputs = state.inputs();
        if (state.status() != work.parameters().get("status") || (state.covered() ? 1 : 0) != work.parameters().get("covered")
                || fixed.size() != inputs.size() + (state.covered() ? 1 : 0)) return false;
        for (int slot = 0; slot < inputs.size(); slot++)
            if (!fixed.get(slot).item().is(inputs.get(slot)) || fixed.get(slot).count() != inputs.get(slot).getCount()) return false;
        if (state.covered() && (!fixed.getLast().item().is(state.lid()) || fixed.getLast().count() != state.lid().getCount())) return false;
        var recipe = work.recipe().value();
        var soup = recipe instanceof com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe ordinary ? ordinary.soupBase()
                : ((com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe) recipe).soupBase();
        return state.status() == IStockpot.PUT_SOUP_BASE || soup.equals(state.soupBase());
    }
    public boolean canReturnLid(MaidCookManager<?> cm) {
        return !snapshot().covered() || CookInventoryTransactions.canInsertAll(cm.getInputInv(), StockpotAdapter.lidToReturn(snapshot()));
    }
    private boolean uncover(MaidCookManager<?> cm, MaidRec work) {
        return !snapshot().covered() || canReturnLid(cm) && cm.useNativeItem(GatherResult.FAIL, cm.getInputInv(), work, false,
                stack -> StockpotAdapter.uncover(be, serverLevel, maid));
    }
    public boolean cover(MaidCookManager<?> cm, MaidRec work) {
        var lid = cm.getItem(StockpotAdapter::isLid);
        return !lid.isFail() && cm.useNativeItem(lid, cm.getInputInv(), work, false, stack -> StockpotAdapter.cover(be, serverLevel, maid, stack));
    }
    @Override public boolean insertInputs(MaidRec work, MaidCookManager<?> cm) {
        if (!matchesWork(work) || !cookStateMatch() || !cm.hasMaterials(work) || !canReturnLid(cm)
                || !cm.canAcceptNativeResults(List.of(work.result().copyWithCount(1)))) return false;
        boolean complete = false;
        try {
            if (!uncover(cm, work)) return false;
            for (var material : work.maidItems()) {
                if (material.role() != MaidItem.Role.FLUID && material.role() != MaidItem.Role.INGREDIENT) continue;
                for (int unit = 0; unit < material.count(); unit++) {
                    var source = cm.getItem(material.item()::is); if (source.isFail()) return false;
                    var before = snapshot();
                    if (!cm.useNativeItem(source, cm.getInputInv(), work, false, stack -> material.role() == MaidItem.Role.FLUID
                            ? StockpotAdapter.addSoupBase(be, serverLevel, maid, stack) : StockpotAdapter.addIngredient(be, serverLevel, maid, stack))) return false;
                    var after = snapshot();
                    if (material.role() == MaidItem.Role.FLUID ? after.status() != IStockpot.PUT_INGREDIENT
                            : after.inputs().stream().filter(stack -> !stack.isEmpty()).count() != before.inputs().stream().filter(stack -> !stack.isEmpty()).count() + 1) return false;
                }
            }
            if (!cookStateMatch() || !StockpotRecSerializerManager.INSTANCE.permitsCompleted(snapshot(), serverLevel, TaskKcStockpot.settings(maid))) return false;
            complete = cover(cm, work); return complete;
        } finally { if (!complete) cm.clear(); }
    }
    @Override public boolean extractResult(MaidCookManager<?> cm) {
        var carrier = StockpotAdapter.carrier(be, serverLevel).orElse(null);
        if (!hasResult() || carrier == null || !cm.canAcceptNativeResults(List.of(getResult())) || !canReturnLid(cm)
                || !carrier.isEmpty() && cm.getItem(carrier::test).isFail()) return false;
        if (!uncover(cm, null)) return false;
        boolean changed = false;
        for (int portion = 0; portion < 9 && hasResult(); portion++) {
            if (!cm.canAcceptNativeResults(List.of(getResult()))) break;
            var source = carrier.isEmpty() ? GatherResult.FAIL : cm.getItem(carrier::test);
            if (!carrier.isEmpty() && source.isFail()) break;
            int before = snapshot().portions();
            if (!cm.useNativeItem(source, cm.getOutputInv(), null, false, stack -> StockpotAdapter.takeOne(be, serverLevel, maid, stack))) break;
            if (snapshot().portions() != before - 1) break;
            changed = true; cm.itemOutput2Chest();
        }
        if (changed && snapshot().status() == IStockpot.PUT_SOUP_BASE) cm.cookingCycleCompleted();
        return changed;
    }
    public boolean canTakeInputs(MaidCookManager<?> cm) {
        if (!hasInputs() || recMatch() || !StockpotAdapter.canRetrieveIngredients(be, maid) || !canReturnLid(cm)) return false;
        ItemStack last = lastIngredient(snapshot()); var container = StockpotAdapter.ingredientContainer(last);
        return CookInventoryTransactions.canInsertAll(cm.getInputInv(), last)
                && (container.isEmpty() || !cm.getItem(stack -> stack.is(container.getItem())).isFail());
    }
    @Override public boolean takeInputs(MaidCookManager<?> cm) {
        if (!canTakeInputs(cm) || !uncover(cm, null)) return false;
        boolean changed = false;
        for (int i = 0; i < 9 && hasInputs() && StockpotAdapter.canRetrieveIngredients(be, maid); i++) {
            ItemStack last = lastIngredient(snapshot()); var container = StockpotAdapter.ingredientContainer(last);
            if (!CookInventoryTransactions.canInsertAll(cm.getInputInv(), last)) break;
            var source = container.isEmpty() ? GatherResult.FAIL : cm.getItem(stack -> stack.is(container.getItem()));
            if (!container.isEmpty() && source.isFail()) break;
            if (!cm.useNativeItem(source, cm.getInputInv(), null, true, stack -> StockpotAdapter.removeIngredient(be, serverLevel, maid))) break;
            changed = true;
        }
        return changed;
    }
    private static ItemStack lastIngredient(StockpotAdapter.Snapshot state) {
        var inputs = state.inputs(); for (int index = inputs.size() - 1; index >= 0; index--) if (!inputs.get(index).isEmpty()) return inputs.get(index);
        return ItemStack.EMPTY;
    }
}
