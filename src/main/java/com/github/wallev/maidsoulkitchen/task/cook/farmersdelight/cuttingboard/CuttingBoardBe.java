package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
/** Source: 58ec08ec CuttingBoardBe.java (MIT). The actual current FD inventory replaces the
 * upstream null getInv stub; board output is spawned by its native processing API, not an output slot.
 * Rule uses the native item/tool checks instead of upstream false state stubs. */
public class CuttingBoardBe extends CookBeBase<CuttingBoardBlockEntity> {
    public CuttingBoardBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof CuttingBoardBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return (IItemHandlerModifiable) be.getInventory(); }
    @Override public int getIngredientSize() { return 1; }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("Native cutting produces item entities"); }
    @Override public boolean hasResult() { return false; }
    @Override public boolean recMatch() { return !be.getStoredItem().isEmpty(); }
    @Override public boolean cookStateMatch() { return be.getStoredItem().isEmpty(); }
    @Override public void markChanged() { defaultChanged(); }
}
