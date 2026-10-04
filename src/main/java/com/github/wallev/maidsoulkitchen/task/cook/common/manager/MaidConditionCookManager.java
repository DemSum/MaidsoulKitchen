package com.github.wallev.maidsoulkitchen.task.cook.common.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Source: 58ec08ec MaidConditionCookManager.java (MIT), same device-condition ownership.
 * Upstream tempIngredients was a second consumable index queue and poll left consumed MaidRec
 * entries in the list. Select and remove identities in the sole inherited queue instead.
 * Condition collection no longer overwrites/clears the active Be while probing appliances.
 * Replaces the beta furnace's live per-slot recipe search; no extra plans or resource state. */
public abstract class MaidConditionCookManager<R extends Recipe<? extends RecipeInput>, C> extends MaidCookManager<R> {
    protected final int extraTryTimeMax = 20;
    protected int extraTryTime;
    private final CookBeBase<?> cookBe;
    public MaidConditionCookManager(RecSerializerManager<R> rsm, EntityMaid maid, ICookTask<?, R> task, CookBeBase<?> cookBe) {
        super(rsm, maid, task); this.cookBe = cookBe;
    }
    @Override protected List<MKRecipe<R>> getRecs() {
        Set<C> conditions = collectValidConditions();
        return super.getRecs().stream().filter(recipe -> conditions.contains(getRecipeCondition(recipe.rec()))).toList();
    }
    @Override public MaidRec peekMaidRec(CookBeBase<?> cookBe) {
        C condition = getBeCondition(cookBe.getBe());
        MaidRec rec = peekMaidRec(recipe -> isValid(condition, getRecipeCondition(castRecipe(recipe))));
        if (rec != null) extraTryTime = 0;
        else if (extraTryTime++ > extraTryTimeMax) { extraTryTime = 0; invalidate(); }
        return rec;
    }
    @SuppressWarnings("unchecked") private R castRecipe(MaidRec recipe) { return (R) recipe.recipe().value(); }
    protected Set<C> collectValidConditions() { return collectConditions(); }
    protected abstract C getRecipeCondition(R recipe);
    protected abstract C getBeCondition(BlockEntity be);
    protected abstract boolean isValid(C beCondition, C recipeCondition);
    protected final Set<C> collectConditions() {
        Set<C> conditions = new HashSet<>();
        BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition().below();
        int range = (int) maid.getRestrictRadius();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Direct upstream bounded appliance scan. Reachability remains one TLM BFS in common Move.
        for (int y = 0; y <= ICookTask.VERTICAL_SEARCH_RANGE; y = y > 0 ? -y : 1 - y)
            for (int i = 0; i < range; ++i)
                for (int x = 0; x <= i; x = x > 0 ? -x : 1 - x)
                    for (int z = x < i && x > -i ? i : 0; z <= i; z = z > 0 ? -z : 1 - z) {
                        pos.setWithOffset(center, x, y + 1, z);
                        if (!maid.isWithinRestriction(pos) || !level.isLoaded(pos)) continue;
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null && cookBe.isCookBe(be)) conditions.add(getBeCondition(be));
                    }
        return conditions;
    }
}
