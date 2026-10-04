package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.dryingrack;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.NormalCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity;
import dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackRecipe;
import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Source: 58ec08ec drying rack Task (MIT), direct Be/NormalRule/RSM port; beta task execution is deleted. */
@TaskClassAnalyzer(TaskInfo.YHC_DRYING_RACK)
public class TaskYhcDryingRack extends ICookTask<DryingRackBlockEntity, DryingRackRecipe> {
    @Override
    protected AbstractCookRule<DryingRackBlockEntity, DryingRackRecipe> createCookRule() {
        return NormalCookRule.getInstance();
    }

    @Override
    protected RecSerializerManager<DryingRackRecipe> createRecSerializerManager() {
        return DryingRackRecSerializerManager.getInstance();
    }

    @Override
    protected CookBeBase<DryingRackBlockEntity> createCookBe(EntityMaid maid) {
        return new DryingRackBe(maid);
    }

    @Override
    public ResourceLocation getUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.YHC_DRYING_RACK.uid;
    }

    @Override
    public ItemStack getIcon() {
        return YHBlocks.RACK.asStack();
    }
    @Override public boolean isCookBE(net.minecraft.world.level.block.entity.BlockEntity be) { return be instanceof DryingRackBlockEntity; }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.YHC_DRYING_RACK; }
}
