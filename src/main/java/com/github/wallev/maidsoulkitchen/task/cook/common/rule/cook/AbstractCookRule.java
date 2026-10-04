package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source: 58ec08ec AbstractCookRule.java (MIT). RecipeInput adapts 1.21;
 * empty tick hooks are removed: actual ticking rules implement TickCookRule's contract. */
public abstract class AbstractCookRule<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> {
    public abstract boolean canMoveTo(CookBeBase<B> cookBe, MaidCookManager<R> cm);
    public abstract void cookMake(CookBeBase<B> cookBe, MaidCookManager<R> cm);
    public AbstractCookRule<B, R> getOrCreate() { return this; }
}
