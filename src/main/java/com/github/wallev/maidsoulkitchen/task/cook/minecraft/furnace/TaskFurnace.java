package com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.FuelCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Source: 58ec08ec TaskFurnace.java (MIT). Direct Be/FuelRule/condition-manager port; beta per-slot cookable search is deleted. */
@TaskClassAnalyzer(TaskInfo.FURNACE)
public class TaskFurnace extends ICookTask<AbstractFurnaceBlockEntity, AbstractCookingRecipe> {

    @Override
    protected CookBeBase<AbstractFurnaceBlockEntity> createCookBe(EntityMaid maid) {
        return new FurnaceCookBe(maid);
    }

    @Override
    protected AbstractCookRule<AbstractFurnaceBlockEntity, AbstractCookingRecipe> createCookRule() {
        return FuelCookRule.getInstance();
    }

    @Override
    protected RecSerializerManager<AbstractCookingRecipe> createRecSerializerManager() {
        return AbstractCookingRecSerializerManager.getInstance();
    }

    @Override
    protected MaidCookManager<AbstractCookingRecipe> createRecipesManager(EntityMaid maid, CookBeBase<AbstractFurnaceBlockEntity> cookBe) {
        return new MaidFurnaceCookManager(recSerializerManager, maid, this, cookBe);
    }

    @Override
    public ResourceLocation getUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.FURNACE.uid;
    }

    @Override
    public ItemStack getIcon() {
        return Items.FURNACE.getDefaultInstance();
    }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.MC_FURNACE; }
    @Override public java.util.List<net.minecraft.world.item.crafting.RecipeHolder<AbstractCookingRecipe>> getRecipeHolders(net.minecraft.world.level.Level level) { return recSerializerManager.getRecipes(level).stream().map(description -> description.holder()).toList(); }
}
