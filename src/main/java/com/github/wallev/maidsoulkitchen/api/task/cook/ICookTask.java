package com.github.wallev.maidsoulkitchen.api.task.cook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.List;

/**
 * Source: 58ec08ec api/task/cook/ICookTask.java (MIT). Retains per-maid Be/Rule/manager creation
 * and common Collect/Generate/Move/Make/Pathing dispatch. Direct TLM BehaviorControl replaces V shims;
 * the duplicate upstream Collect registration and empty builder/ride hooks are omitted.
 * The v1 interface supplies only the existing GUI/registration signatures during P3-P7 migration;
 * its execution entry points below delegate one way to Be/Rule, owning no queues or state.
 */
public abstract class ICookTask<B extends BlockEntity, R extends Recipe<? extends RecipeInput>>
        implements com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask<B, R> {
    public static final float MOVE_SPEED = 0.5f;
    public static final int VERTICAL_SEARCH_RANGE = 2;
    public final AbstractCookRule<B, R> cookRule;
    public final RecSerializerManager<R> recSerializerManager;
    protected ICookTask() {
        cookRule = createCookRule(); recSerializerManager = createRecSerializerManager();
    }
    protected abstract AbstractCookRule<B, R> createCookRule();
    protected abstract RecSerializerManager<R> createRecSerializerManager();
    protected abstract CookBeBase<B> createCookBe(EntityMaid maid);
    @Override public final RecSerializerManager<R> getRecSerializerManager() { return recSerializerManager; }
    @Override public RecipeType<R> getRecipeType() { return recSerializerManager.getRecipeType(); }
    @Override public final List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        if (maid.level().isClientSide()) return List.of();
        CookTargetMemory.clear(maid);
        CookBeBase<B> cookBe = createCookBe(maid);
        AbstractCookRule<B, R> rule = cookRule.getOrCreate();
        MaidCookManager<R> cm = createRecipesManager(maid, cookBe);
        cm.checkAndInit();
        // TLM 1.5.3 appends its common goals to the returned upstream task list.
        return new java.util.ArrayList<>(List.of(Pair.of(0, new ResetCookMemoryTask<>(cm)),
                Pair.of(3, new CollectChestIngredientsTask<>(cm)), Pair.of(4, new GenerateRecsTask<>(cm)),
                Pair.of(5, new CookMoveTask<>(this, cm, rule, cookBe)),
                Pair.of(6, new CookMakeTask<>(this, cm, rule, cookBe)),
                Pair.of(7, new CookMakePathingTask<>(cookBe))));
    }
    protected MaidCookManager<R> createRecipesManager(EntityMaid maid, CookBeBase<B> cookBe) {
        return new MaidCookManager<>(recSerializerManager, maid, this);
    }
    @Override public final boolean shouldMoveTo(ServerLevel level, EntityMaid maid, B be, MaidCookManager<R> cm) {
        CookBeBase<B> cookBe = createCookBe(maid); cookBe.setBe(be);
        return cookRule.canMoveTo(cookBe, cm);
    }
    @Override public final void processCookMake(ServerLevel level, EntityMaid maid, B be, MaidCookManager<R> cm) {
        CookBeBase<B> cookBe = createCookBe(maid); cookBe.setBe(be);
        cookRule.getOrCreate().cookMake(cookBe, cm); cm.syncInv();
    }
}
