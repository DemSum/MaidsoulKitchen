package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.cbaccessor.IFdCbeAccessor;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

/** Source: 58ec08ec CookingPotBe.java (MIT). Direct device port; current Holder-aware
 * FD accessor and NeoForge Handler replace upstream accessor/cast APIs. Replaces TaskFdCookPot's Be methods. */
@TaskClassAnalyzer(TaskInfo.FD_COOK_POT)
public class CookingPotBe extends CookBeBase<CookingPotBlockEntity> {
    public CookingPotBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof CookingPotBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return be.getInventory(); }
    @Override public int getIngredientSize() { return 6; }
    @Override public int getResultSlot() { return CookingPotBlockEntity.OUTPUT_SLOT; }
    public ItemStack getMeal() { return be.getMeal().copy(); }
    public boolean hasMeal() { return !getMeal().isEmpty(); }
    public ItemStack getNeedContainer() { return be.getContainer().copy(); }
    public int getContainerSlot() { return CookingPotBlockEntity.CONTAINER_SLOT; }
    public ItemStack getNowContainer() { return getInv().getStackInSlot(getContainerSlot()).copy(); }
    @Override @SuppressWarnings("unchecked") public boolean recMatch() {
        var accessor = (IFdCbeAccessor<CookingPotRecipe>) be;
        return accessor.tlmk$getMatchingRecipe(new RecipeWrapper(be.getInventory())).isPresent();
    }
    @Override public boolean cookStateMatch() { return be.isHeated(); }
    /** Source: upstream FdPotCookRule meal/container ordering. FD keeps cooked meals separately
     * and transfers them to OUTPUT_SLOT on its native tick. A correctly supplied meal waiting
     * for that transfer is also busy; the generic input-only check cannot represent this API. */
    @Override public boolean isAwaitingNativeCooking() {
        if (hasResult()) return false;
        if (hasMeal()) {
            var needed = getNeedContainer();
            var container = getNowContainer();
            return needed.isEmpty() || !container.isEmpty() && container.is(needed.getItem());
        }
        return super.isAwaitingNativeCooking();
    }
    @Override public void markChanged() { defaultChanged(); }
}
