package com.github.wallev.maidsoulkitchen.task.cook.common.cook.be;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Source: 58ec08ec common/cook/be/CookBeBase.java (MIT).
 * Retains device binding, input/result slots, recipe/state checks and mutation notifications.
 * NeoForge IItemHandler replaces the upstream cast-only IInvHandler mixins. Inventory mutations
 * go through the sole MaidCookManager: upstream live-stack shrinking and unconditional success
 * could lose a partially inserted plan. Empty optional fluid/tool hooks are not ported.
 * Replaces beta task-owned device inventory operations as each device migrates.
 */
public abstract class CookBeBase<B extends BlockEntity> {
    protected final EntityMaid maid;
    protected final ServerLevel serverLevel;
    protected B be;

    public CookBeBase(EntityMaid maid) {
        this.maid = maid;
        this.serverLevel = (ServerLevel) maid.level();
    }
    public boolean hasResult() { return !getResult().isEmpty(); }
    public boolean extractResult(MaidCookManager<?> cm) {
        return cm.takeItem(getResultInv(), getResultSlot(), cm.getOutputInv(), stack -> true) > 0;
    }
    public boolean hasInputs() {
        for (int slot = getIngredientSlotStart(); slot < getIngredientSlotStart() + getIngredientSize(); slot++)
            if (!getIngredientInv().getStackInSlot(slot).isEmpty()) return true;
        return false;
    }
    public boolean takeInputs(MaidCookManager<?> cm) {
        boolean changed = false;
        for (int slot = getIngredientSlotStart(); slot < getIngredientSlotStart() + getIngredientSize(); slot++)
            changed |= cm.takeItem(getIngredientInv(), slot, cm.getInputInv(), stack -> true) > 0;
        return changed;
    }
    public boolean insertInputs(MaidRec rec, MaidCookManager<?> cm) {
        return cm.insertInputs(rec, getIngredientInv(), getIngredientSlotStart(), getIngredientSize());
    }
    public abstract boolean isCookBe(BlockEntity be);
    public abstract IItemHandlerModifiable getInv();
    public IItemHandlerModifiable getIngredientInv() { return getInv(); }
    public int getIngredientSlotStart() { return 0; }
    public abstract int getIngredientSize();
    public IItemHandlerModifiable getResultInv() { return getInv(); }
    public ItemStack getResult() { return getResultInv().getStackInSlot(getResultSlot()).copy(); }
    public abstract int getResultSlot();
    /** Source: CookBeBase meal/container contract, required by the shared upstream FdPotCookRule.
     * Devices using that rule implement these native accessors. Unsupported devices fail explicitly
     * instead of retaining the source's empty optional hooks or adding a duplicate pot abstraction. */
    public ItemStack getMeal() { throw new UnsupportedOperationException("Device has no meal slot"); }
    public boolean hasMeal() { return !getMeal().isEmpty(); }
    public ItemStack getNeedContainer() { throw new UnsupportedOperationException("Device has no meal container"); }
    public int getContainerSlot() { throw new UnsupportedOperationException("Device has no container slot"); }
    public ItemStack getNowContainer() { return getInv().getStackInSlot(getContainerSlot()).copy(); }
    public abstract boolean recMatch();
    public abstract boolean cookStateMatch();
    public boolean canTakeResult() { return true; }
    public void awardExp() { ICookTask.awardExperience(be, maid); }
    public abstract void markChanged();
    protected final void defaultChanged() { MaidCookManager.makeChanged(be); }
    public B getBe() { return be; }
    public void setBe(B be) { this.be = be; }
    @SuppressWarnings("unchecked")
    public void setBlockEntity(BlockEntity be) {
        if (!isCookBe(be)) throw new IllegalArgumentException("Wrong cooking device");
        setBe((B) be);
    }
    public BlockPos getPos() { return be.getBlockPos(); }
    /** Source: c9273ce5 side search, extended by the verified KC steamer stack search.
     * Single-block devices, including KC stockpots and future KC woks/teapots, use these
     * defaults; only multi-layer steamers extend the heights. Common Move performs one TLM BFS. */
    public int[] getInteractionHeightOffsets() { return new int[]{0, 1}; }
    public int getVerticalSearchRange() { return ICookTask.VERTICAL_SEARCH_RANGE; }
    /** Existing floor recovery anchor; stacked devices override it with their actual heat base. */
    public BlockPos getWorkAreaFloorAnchor(BlockPos walkPos) { return walkPos; }
    public void clear() { be = null; }
    public EntityMaid getMaid() { return maid; }
}
