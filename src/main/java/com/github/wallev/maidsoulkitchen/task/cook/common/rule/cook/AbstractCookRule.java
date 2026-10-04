package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source: 58ec08ec AbstractCookRule.java (MIT). RecipeInput adapts 1.21;
 * empty tick hooks are removed: actual ticking rules implement TickCookRule's contract. */
public abstract class AbstractCookRule<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> {
    /** Direct source fluid-container lookup; derived from the RSM's native catalog, no work state. */
    protected boolean hasFluidContainers(net.minecraft.world.level.material.Fluid fluid, MaidCookManager<R> cm) {
        return !getFluidContainers(fluid, cm).isFail();
    }
    protected com.github.wallev.maidsoulkitchen.task.cook.common.manager.GatherResult getFluidContainers(net.minecraft.world.level.material.Fluid fluid, MaidCookManager<R> cm) {
        var containers = ((com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.FluidRecSerializerManager<R>) cm.getRecSerializerManager()).fluidContainer(fluid);
        return cm.getItem(stack -> containers.stream().anyMatch(container -> container.is(stack.getItem())));
    }
    public abstract boolean canMoveTo(CookBeBase<B> cookBe, MaidCookManager<R> cm);
    public abstract void cookMake(CookBeBase<B> cookBe, MaidCookManager<R> cm);
    public AbstractCookRule<B, R> getOrCreate() { return this; }
}
