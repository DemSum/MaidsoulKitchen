package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Source: 58ec08ec DryingRackBe native-placeFood boundary (MIT). KC has no source device;
 * retains existing validated adapter snapshots/heat/1-4 layers and native clocks. Its API has
 * no Handler result slot. Actual extraction/hand/drop receipts belong solely to MaidCookManager.
 * Replaces TaskKcSteamer and SteamerWorkStorage inventory/execution responsibilities. */
public final class SteamerBe extends CookBeBase<SteamerBlockEntity> {
    public SteamerBe(EntityMaid maid) { super(maid); }
    public SteamerAdapter.Snapshot snapshot() { return SteamerAdapter.inspect(be, serverLevel).orElseThrow(); }
    @Override public boolean isCookBe(BlockEntity device) { return SteamerAdapter.supports(device); }
    @Override public IItemHandlerModifiable getInv() { throw new UnsupportedOperationException("KC steamer uses native placeFood/takeFood"); }
    @Override public int getIngredientSize() { return snapshot().items().size(); }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("KC steamer has native batch outputs"); }
    @Override public boolean hasInputs() { return snapshot().items().stream().anyMatch(stack -> !stack.isEmpty()); }
    @Override public boolean hasResult() { return snapshot().canTakeFood(); }
    @Override public boolean recMatch() { return hasInputs(); }
    @Override public boolean cookStateMatch() {
        var state = snapshot(); return state.accessible() && state.covered() && state.hasHeatSource() && state.hasEmptySlot();
    }
    @Override public boolean insertInputs(MaidRec work, MaidCookManager<?> cm) {
        if (!cookStateMatch()) return false;
        return cm.insertInputs(work, stack -> SteamerAdapter.placeFood(be, serverLevel, maid, stack, work.recipeId()::equals));
    }
    @Override public boolean extractResult(MaidCookManager<?> cm) {
        var state = snapshot();
        return state.canTakeFood() && cm.takeNativeOutput(state.items(), () -> ((ISteamer) be).takeFood(serverLevel, maid));
    }
    @Override public int[] getInteractionHeightOffsets() { return SteamerAdapter.interactionHeightOffsets(); }
    @Override public int getVerticalSearchRange() { return SteamerBlockEntity.MAX_LIT_LEVEL; }
    @Override public BlockPos getWorkAreaFloorAnchor(BlockPos walkPos) { return SteamerAdapter.findHeatSourcePosition(be, serverLevel).orElse(getPos()); }
    @Override public void markChanged() { defaultChanged(); }
}
