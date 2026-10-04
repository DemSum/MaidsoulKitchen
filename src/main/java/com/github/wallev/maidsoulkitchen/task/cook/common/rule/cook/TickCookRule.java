package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source: 58ec08ec TickCookRule.java (MIT). Per-maid start/tick/stop state is retained.
 * Unsafe live-stack tool swapping is omitted until the corresponding device ports use the manager's
 * Handler transactions; the verified local fake-player API is retained at those device boundaries. */
public abstract class TickCookRule<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends AbstractCookRule<B, R> {
    protected EntityMaid maid;
    protected B be;
    protected BlockPos pos;
    private boolean end;
    protected int tick;
    public boolean tickCan(CookBeBase<B> cookBe, MaidCookManager<R> cm) { return be != null && !end; }
    public abstract void tickCookMake(CookBeBase<B> cookBe, MaidCookManager<R> cm);
    public void tickStop(CookBeBase<B> cookBe, MaidCookManager<R> cm) { clear(cookBe, cm); }
    @Override public final TickCookRule<B, R> getOrCreate() { return create(); }
    protected abstract TickCookRule<B, R> create();
    protected void init(CookBeBase<B> cookBe, MaidCookManager<R> cm) {
        maid = cookBe.getMaid(); be = cookBe.getBe(); pos = be.getBlockPos();
    }
    protected void clear(CookBeBase<B> cookBe, MaidCookManager<R> cm) {
        maid = null; be = null; pos = null; end = false; tick = 0;
    }
    protected void stop() { end = true; }
}
