package com.github.wallev.maidsoulkitchen.task.cook.common.inv.itemdown;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.ItemAmount;

import java.util.Map;

/** Source: MSK 1.20.1-1.0-dev (58ec08ec), same relative class. Holder/RecipeInput and defensive component value copies are the only platform changes; beta consumers migrate in P2. */
public class RecDataUse {
    private Map<ItemDefinition, ItemAmount> itemUse;
    private int recipeRepeat;

    public RecDataUse() {
    }

    public Map<ItemDefinition, ItemAmount> getItemUse() {
        return itemUse;
    }

    protected void setItemUse(Map<ItemDefinition, ItemAmount> itemUse) {
        this.itemUse = itemUse;
    }

    public void set(Map<ItemDefinition, ItemAmount> itemUse, int repeat) {
        this.setItemUse(itemUse);
        this.setRecipeRepeat(repeat);
    }

    public int getRecipeRepeat() {
        return recipeRepeat;
    }

    protected void setRecipeRepeat(int recipeRepeat) {
        this.recipeRepeat = recipeRepeat;
    }
}
