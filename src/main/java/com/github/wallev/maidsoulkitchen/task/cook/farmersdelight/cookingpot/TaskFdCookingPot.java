package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.FdPotCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModItems;

/** Source: 58ec08ec native device Task (MIT). Direct Be/Rule/RSM port. Legacy data key is read once by KitchenData migration; beta task execution is deleted. */
@TaskClassAnalyzer(TaskInfo.FD_COOK_POT)
public class TaskFdCookingPot extends ICookTask<CookingPotBlockEntity, CookingPotRecipe> {

    @Override
    protected CookBeBase<CookingPotBlockEntity> createCookBe(EntityMaid maid) {
        return new CookingPotBe(maid);
    }

    @Override
    protected AbstractCookRule<CookingPotBlockEntity, CookingPotRecipe> createCookRule() {
        return FdPotCookRule.getInstance();
    }

    @Override
    protected RecSerializerManager<CookingPotRecipe> createRecSerializerManager() {
        return CookingPotRecSerializerManager.getInstance();
    }

    @Override
    public ResourceLocation getUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.FD_COOK_POT.uid;
    }

    @Override
    public ItemStack getIcon() {
        return ModItems.COOKING_POT.get().getDefaultInstance();
    }
    @Override public boolean isCookBE(net.minecraft.world.level.block.entity.BlockEntity be) { return be instanceof CookingPotBlockEntity; }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.FD_COOK_POT; }
}
