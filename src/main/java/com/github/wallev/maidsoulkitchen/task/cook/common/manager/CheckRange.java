package com.github.wallev.maidsoulkitchen.task.cook.common.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * Source: 58ec08ec task/cook/common/manager/CheckRange.java (MIT).
 * Keeps the upstream scanning/planning boundaries; NeoForge and RecipeInput replace 1.20 APIs.
 * Upstream MaidCookManager support; no independent work queue.
 */
public class CheckRange {
    private int maxCheckRate = 20;
    private int nextCheckTickCount;

    public boolean checkExtraStartConditions(EntityMaid maid) {
        if (this.nextCheckTickCount > 0) {
            --this.nextCheckTickCount;
            return false;
        }
        this.nextCheckTickCount = maxCheckRate + maid.getRandom().nextInt(maxCheckRate);
        return true;
    }

    public void setMaxCheckRate(int maxCheckRate) {
        this.maxCheckRate = maxCheckRate;
    }

    public void setNextCheckTickCount(int nextCheckTickCount) {
        this.nextCheckTickCount = nextCheckTickCount;
    }
}
