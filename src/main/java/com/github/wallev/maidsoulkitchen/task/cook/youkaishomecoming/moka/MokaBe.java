package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.moka;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import dev.xkmc.youkaishomecoming.content.pot.moka.MokaMakerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
/** Source: 58ec08ec MokaBe.java (MIT), same native inventory/meal/container/heat responsibilities.
 * NeoForge Handler and current RecipeHolder lookup replace the old cast/accessor boundary.
 * Replaces beta TaskYhcMoka device operations; shares the original FdPotCookRule with FD. */
@TaskClassAnalyzer(TaskInfo.YHC_MOKA)
public class MokaBe extends CookBeBase<MokaMakerBlockEntity> {
    public MokaBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof MokaMakerBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return be.getInventory(); }
    @Override public int getIngredientSize() { return 4; }
    @Override public int getResultSlot() { return MokaMakerBlockEntity.OUTPUT_SLOT; }
    @Override public ItemStack getMeal() { return be.getMeal().copy(); }
    @Override public ItemStack getNeedContainer() { return be.getContainer().copy(); }
    @Override public int getContainerSlot() { return MokaMakerBlockEntity.CONTAINER_SLOT; }
    @Override public boolean recMatch() {
        return serverLevel.getRecipeManager().getRecipeFor(dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.MOKA_RT.get(), new RecipeWrapper(be.getInventory()), serverLevel).isPresent();
    }
    @Override public boolean cookStateMatch() { return be.isHeated(); }
    @Override public void markChanged() { defaultChanged(); }
}
