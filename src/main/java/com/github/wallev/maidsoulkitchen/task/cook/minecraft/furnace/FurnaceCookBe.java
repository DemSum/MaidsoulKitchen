package com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.cbaccessor.IAbstractFurnaceAccessor;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
/** Source: 58ec08ec FurnaceCookBe.java (MIT). InvWrapper replaces cast-only inventory mixins;
 * SingleRecipeInput and the existing recipe-type accessor adapt 1.21 matching. Replaces beta furnace Be operations. */
public class FurnaceCookBe extends CookBeBase<AbstractFurnaceBlockEntity> {
    public FurnaceCookBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof AbstractFurnaceBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return new InvWrapper(be); }
    @Override public int getIngredientSize() { return 1; }
    public int activeItemSlot() { return 1; }
    @Override public int getResultSlot() { return 2; }
    @Override public boolean recMatch() {
        return serverLevel.getRecipeManager().getRecipeFor(((IAbstractFurnaceAccessor) be).tlmk$getRecipeType(),
                new SingleRecipeInput(be.getItem(0)), serverLevel).isPresent();
    }
    @Override public boolean cookStateMatch() {
        return be.getBlockState().getValue(AbstractFurnaceBlock.LIT) || AbstractFurnaceBlockEntity.isFuel(be.getItem(activeItemSlot()));
    }
    @Override public void markChanged() { defaultChanged(); }
}
