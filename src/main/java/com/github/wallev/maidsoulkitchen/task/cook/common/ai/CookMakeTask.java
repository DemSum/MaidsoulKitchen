package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.TickCookRule;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source: 58ec08ec CookMakeTask.java (MIT). Keeps Rule start/tick/stop and sync lifecycle.
 * Existing dual coordinates/locks validate every action, including replacement at the same position.
 * Tick methods are called only on actual TickCookRule implementations, avoiding upstream empty hooks.
 * Finally cleanup releases assignments after failure, task change, destruction, displacement or death.
 * Replaces MaidCookMakeTask for migrated tasks. */
public class CookMakeTask<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends Behavior<EntityMaid> {
    private final ICookTask<B, R> task;
    private final MaidCookManager<R> cm;
    private final AbstractCookRule<B, R> rule;
    private final CookBeBase<B> cookBe;
    public CookMakeTask(ICookTask<B, R> task, MaidCookManager<R> cm, AbstractCookRule<B, R> rule, CookBeBase<B> cookBe) {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT));
        this.task = task; this.cm = cm; this.rule = rule; this.cookBe = cookBe;
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid != cm.getMaid() || !cm.checkAndInit()) { CookTargetMemory.clear(maid); return false; }
        var state = CookTargetMemory.evaluateStart(level, maid, cookBe::isCookBe, task.getCloseEnoughDist());
        if (state == CookTargetState.StartState.CLEAR_INVALID) { cookBe.clear(); CookTargetMemory.clear(maid); }
        if (state != CookTargetState.StartState.READY) return false;
        BlockEntity target = level.getBlockEntity(CookTargetMemory.getWorkPos(maid).orElseThrow().currentBlockPosition());
        // Move binds the final selected entity. Replacement at the same position revokes it.
        if (cookBe.getBe() == null || cookBe.getBe() != target || target.isRemoved()) {
            cookBe.clear(); CookTargetMemory.clear(maid); return false;
        }
        return true;
    }
    @Override protected void start(ServerLevel level, EntityMaid maid, long time) {
        try { rule.cookMake(cookBe, cm); sync(); }
        catch (RuntimeException | Error failure) { cleanup(maid); throw failure; }
    }
    @Override protected void tick(ServerLevel level, EntityMaid maid, long time) {
        if (cookBe.getBe() == null || maid.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(cookBe.getPos()))
                > task.getCloseEnoughDist() * task.getCloseEnoughDist()) return;
        if (rule instanceof TickCookRule<B, R> tickRule) {
            try { tickRule.tickCookMake(cookBe, cm); sync(); }
            catch (RuntimeException | Error failure) { cleanup(maid); throw failure; }
        }
    }
    private void sync() {
        if (cookBe.getBe() != null && !cookBe.getBe().isRemoved()) cookBe.markChanged();
        cm.syncInv(); cm.itemOutput2Chest();
    }
    @Override protected boolean canStillUse(ServerLevel level, EntityMaid maid, long time) {
        if (!maid.isAlive() || !maid.canBrainMoving() || !cm.checkAndInit() || !CookTargetMemory.hasValidWorkTarget(level, maid, cookBe::isCookBe)
                || cookBe.getBe() == null || level.getBlockEntity(cookBe.getPos()) != cookBe.getBe()) return false;
        return rule instanceof TickCookRule<B, R> tickRule && tickRule.tickCan(cookBe, cm);
    }
    @Override protected void stop(ServerLevel level, EntityMaid maid, long time) { cleanup(maid); }
    private void cleanup(EntityMaid maid) {
        try {
            if (rule instanceof TickCookRule<B, R> tickRule) tickRule.tickStop(cookBe, cm);
            sync();
        } finally { cookBe.clear(); CookTargetMemory.clear(maid); }
    }
    @Override protected boolean timedOut(long time) { return false; }
}
