package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.mao.barbequesdelight.content.block.GrillBlockEntity;
import com.mao.barbequesdelight.content.recipe.GrillingRecipe;
import com.mao.barbequesdelight.init.registrate.BBQDBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Source: 58ec08ec TaskBbqGrill (MIT), direct unified Be/Rule/RSM port. Deletes beta TaskBdGrill/Make chain; legacy data migrate once. */
@TaskClassAnalyzer(TaskInfo.BD_GRILL)
public class TaskBbqGrill extends ICookTask<GrillBlockEntity, GrillingRecipe<?>> {
    @Override
    protected AbstractCookRule<GrillBlockEntity, GrillingRecipe<?>> createCookRule() {
        return GrillCookRule.getInstance();
    }

    @Override
    protected RecSerializerManager<GrillingRecipe<?>> createRecSerializerManager() {
        return GrillingRecSerializerManager.getInstance();
    }

    @Override
    protected CookBeBase<GrillBlockEntity> createCookBe(EntityMaid maid) {
        return new GrillBe(maid);
    }

    @Override
    public ResourceLocation getUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.BD_GRILL.uid;
    }

    @Override
    public ItemStack getIcon() {
        return BBQDBlocks.GRILL.asStack();
    }
    @Override public boolean isCookBE(net.minecraft.world.level.block.entity.BlockEntity entity) { return entity instanceof GrillBlockEntity; }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.BD_GRILL; }
}
