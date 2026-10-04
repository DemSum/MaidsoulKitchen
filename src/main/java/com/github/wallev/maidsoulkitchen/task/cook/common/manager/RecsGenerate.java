package com.github.wallev.maidsoulkitchen.task.cook.common.manager;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source: 58ec08ec task/cook/common/manager/RecsGenerate.java (MIT).
 * Keeps the upstream scanning/planning boundaries; NeoForge and RecipeInput replace 1.20 APIs.
 * Upstream MaidCookManager support; no independent work queue.
 */
public class RecsGenerate<R extends Recipe<? extends RecipeInput>> {
    public static final int TICK_SCAN_LIMIT = 10;
    protected Map<ItemDefinition, Long> available = new HashMap<>();
    protected List<MKRecipe<R>> rec = new ArrayList<>();
    protected List<MKRecipe<R>> currentRecs = new ArrayList<>();
    private int slots = 0;
    private int lastSlot = 0;

    public List<MKRecipe<R>> tickRun() {
        if (done()) return List.of();
        List<MKRecipe<R>> mkRecipes = currentRecs.subList(lastSlot, Math.min(lastSlot + TICK_SCAN_LIMIT, slots));
        lastSlot += TICK_SCAN_LIMIT;
        return mkRecipes;
    }

    public void markDone() {
        this.lastSlot = slots;
    }

    public boolean done() {
        return lastSlot >= slots;
    }

    public Map<ItemDefinition, Long> getAvailable() {
        return available;
    }

    public void setAvailable(Map<ItemDefinition, Long> available) {
        this.available = new HashMap<>(available);
    }

    public List<MKRecipe<R>> getRecs() {
        return rec;
    }

    public void setRecs(List<MKRecipe<R>> rec) {
        this.rec = List.copyOf(rec);
    }

    public List<MKRecipe<R>> getCurrentRecs() {
        return currentRecs;
    }

    public void setCurrentRecs(List<MKRecipe<R>> currentRecs) {
        this.currentRecs = new ArrayList<>(currentRecs);
        // Upstream counted the unfiltered catalog, then sliced the smaller filtered list.
        this.slots = currentRecs.size();
        this.lastSlot = 0;
    }

    public void clear() {
        this.currentRecs.clear();
        this.available.clear();
        this.lastSlot = 0;
        this.slots = 0;
    }
}
