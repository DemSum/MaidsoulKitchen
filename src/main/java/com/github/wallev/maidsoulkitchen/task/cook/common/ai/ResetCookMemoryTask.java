package com.github.wallev.maidsoulkitchen.task.cook.common.ai;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.crafting.*;
import java.util.Map;
/** Source: 58ec08ec ResetCookMemoryTask.java (MIT). Uses the existing dual-memory cleanup,
 * releases locks and clears the manager's pending work instead of retaining stale plans. */
public class ResetCookMemoryTask<R extends Recipe<? extends RecipeInput>> extends Behavior<EntityMaid> {
    private final MaidCookManager<R> cm;
    public ResetCookMemoryTask(MaidCookManager<R> cm) { super(Map.of()); this.cm = cm; }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) { return !maid.canBrainMoving(); }
    @Override protected void start(ServerLevel level, EntityMaid maid, long time) { CookTargetMemory.clear(maid); cm.clear(); }
}
