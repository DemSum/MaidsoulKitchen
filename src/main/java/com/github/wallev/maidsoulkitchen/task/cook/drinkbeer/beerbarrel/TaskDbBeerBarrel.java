package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.NormalCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;
import lekavar.lma.drinkbeer.recipes.BrewingRecipe;
import lekavar.lma.drinkbeer.registries.BlockRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Source: 58ec08ec native device Task (MIT). Direct Be/Rule/RSM port. Legacy data key is read once by KitchenData migration; beta task execution is deleted. */
@TaskClassAnalyzer(TaskInfo.DB_BEER)
public class TaskDbBeerBarrel extends ICookTask<BeerBarrelBlockEntity, BrewingRecipe> {
    @Override
    protected AbstractCookRule<BeerBarrelBlockEntity, BrewingRecipe> createCookRule() {
        return new BeerBarrelCookRule();
    }

    @Override
    protected RecSerializerManager<BrewingRecipe> createRecSerializerManager() {
        return BeerBarrelRecSerializerManager.getInstance();
    }

    @Override
    protected CookBeBase<BeerBarrelBlockEntity> createCookBe(EntityMaid maid) {
        return new BeerBarrelBe(maid);
    }

    @Override
    public ResourceLocation getUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.DB_BEER.uid;
    }

    @Override
    public ItemStack getIcon() {
        return BlockRegistry.BEER_BARREL.get().asItem().getDefaultInstance();
    }
    @Override public boolean isCookBE(net.minecraft.world.level.block.entity.BlockEntity be) { return be instanceof BeerBarrelBlockEntity; }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() { return com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister.DB_BEER; }
}
