package com.github.wallev.maidsoulkitchen.task.cook.common.task;

import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTargetTask;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.TaskBdBasin;
import com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.TaskBdGrill;
import com.github.wallev.maidsoulkitchen.task.cook.cuisine.TaskCdCuisineSkillet;
import com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel.TaskDbBeerBarrel;
import com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.TaskFdCookingPot;
import com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.TaskFdCuttingBoard;
import com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.TaskFdSkillet;
import com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.TaskFurnace;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.TaskKcSteamer;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.TaskKcStockpot;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.TaskYhcDryingRack;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.TaskYhcFermentationTank;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.TaskYhcMoka;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.TaskYhcTeaKettle;
/**
 * Source: 58ec08ec task/cook/common/task/CookTaskManager.java (MIT).
 * Keeps UID lookup and ordered task index. Current NeoForge canLoad/signature gates replace upstream Mods;
 * the catalog contains only currently registered devices, including KC devices absent from the old implementation.
 * Replaces the cooking constructor branches in TaskRegister; does not own per-maid plans or execution state.
 * ICookTargetTask is the temporary consumer type until Be/Rule consumers migrate in P3-P6.
 */
public final class CookTaskManager {
    private static Map<ResourceLocation, ICookTargetTask> taskMap = Map.of();
    private static long recipeGeneration;
    /** NeoForge reload boundary for the shared recipe catalog, not per-maid work state. */
    public static void recipesReloaded() { recipeGeneration++; }
    public static long getRecipeGeneration() { return recipeGeneration; }
    private CookTaskManager() { }
    public static void init() {
        Map<ResourceLocation, ICookTargetTask> tasks = new LinkedHashMap<>();
        if (TaskInfo.FURNACE.canLoad()) {
            add(tasks, new TaskFurnace());
        }

        if (TaskInfo.FD_COOK_POT.canLoad()) {
            add(tasks, new TaskFdCookingPot());
        }
        if (TaskInfo.FD_CUTTING_BOARD.canLoad()) {
            add(tasks, new TaskFdCuttingBoard());
        }
        if (TaskInfo.FD_SKILLET.canLoad()) {
            add(tasks, new TaskFdSkillet());
        }
        if (TaskInfo.CD_CUISINE_SKILLET.canLoad()) {
            add(tasks, new TaskCdCuisineSkillet());
        }
        if (TaskInfo.BD_BASIN.canLoad()) {
            add(tasks, new TaskBdBasin());
        }
        if (TaskInfo.BD_GRILL.canLoad()) {
            add(tasks, new TaskBdGrill());
        }
        if (TaskInfo.YHC_MOKA.canLoad()) {
            add(tasks, new TaskYhcMoka());
        }
        if (TaskInfo.YHC_TEA_KETTLE.canLoad()) {
            add(tasks, new TaskYhcTeaKettle());
        }
        if (TaskInfo.YHC_DRYING_RACK.canLoad()) {
            add(tasks, new TaskYhcDryingRack());
        }
        if (TaskInfo.YHC_FERMENTATION_TANK.canLoad()) {
            add(tasks, new TaskYhcFermentationTank());
        }

        if (TaskInfo.DB_BEER.canLoad()) {
            add(tasks, new TaskDbBeerBarrel());
        }
        if (TaskInfo.KC_STEAMER.canLoad()) {
            add(tasks, new TaskKcSteamer());
        }
        if (TaskInfo.KC_STOCKPOT.canLoad()) {
            add(tasks, new TaskKcStockpot());
        }

        taskMap = java.util.Collections.unmodifiableMap(tasks);
    }
    private static void add(Map<ResourceLocation, ICookTargetTask> tasks, ICookTargetTask task) {
        if (tasks.putIfAbsent(task.getUid(), task) != null) throw new IllegalStateException("Duplicate cooking task " + task.getUid());
    }
    public static Optional<ICookTargetTask> findTask(ResourceLocation uid) { return Optional.ofNullable(taskMap.get(uid)); }
    public static Map<ResourceLocation, ICookTargetTask> getTaskMap() { return taskMap; }
    public static List<ICookTargetTask> getTaskIndex() { return List.copyOf(taskMap.values()); }
}
