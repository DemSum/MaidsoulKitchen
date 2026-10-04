package com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.cbaccessor.IAbstractFurnaceAccessor;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidConditionCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
/** Source: 58ec08ec MaidFurnaceCookManager.java (MIT). Same recipe-type condition matcher,
 * inheriting the sole MaidRec queue; no upstream consumable index queues or empty override. */
public class MaidFurnaceCookManager extends MaidConditionCookManager<AbstractCookingRecipe, RecipeType<?>> {
    public MaidFurnaceCookManager(RecSerializerManager<AbstractCookingRecipe> rsm, EntityMaid maid,
                                ICookTask<?, AbstractCookingRecipe> task, CookBeBase<?> cookBe) { super(rsm, maid, task, cookBe); }
    @Override protected RecipeType<?> getRecipeCondition(AbstractCookingRecipe recipe) { return recipe.getType(); }
    @Override protected RecipeType<?> getBeCondition(BlockEntity be) { return ((IAbstractFurnaceAccessor) be).tlmk$getRecipeType(); }
    @Override protected boolean isValid(RecipeType<?> beCondition, RecipeType<?> recipeCondition) { return beCondition.equals(recipeCondition); }
}
