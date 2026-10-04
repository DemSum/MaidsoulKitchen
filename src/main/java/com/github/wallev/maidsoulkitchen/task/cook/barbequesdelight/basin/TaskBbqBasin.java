package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.basin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.mao.barbequesdelight.content.block.BasinBlockEntity;
import com.mao.barbequesdelight.content.recipe.SkeweringRecipe;
import com.mao.barbequesdelight.init.registrate.BBQDBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Source: 58ec08ec TaskBbqBasin (MIT), direct unified Be/Rule/RSM port. Deletes beta TaskBdBasin/Make chain; legacy data migrate once. */
@TaskClassAnalyzer(TaskInfo.BD_BASIN)
public class TaskBbqBasin extends ICookTask<BasinBlockEntity, SkeweringRecipe<?>> {
    @Override
    protected AbstractCookRule<BasinBlockEntity, SkeweringRecipe<?>> createCookRule() {
        return BasinCookRule.getInstance();
    }

    @Override
    protected RecSerializerManager<SkeweringRecipe<?>> createRecSerializerManager() {
        return SkeweringRecSerializerManager.getInstance();
    }

    @Override
    protected CookBeBase<BasinBlockEntity> createCookBe(EntityMaid maid) {
        return new BasinBe(maid);
    }

    @Override
    public ResourceLocation getUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.BD_BASIN.uid;
    }

    @Override
    public ItemStack getIcon() {
        return BBQDBlocks.BASIN.asStack();
    }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.BD_BASIN; }
}
