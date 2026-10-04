package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.skillet;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
/** No 1.20 FD skillet descriptor existed. Direct existing upstream RecSerializerManager native
 * campfire catalog adaptation; no new recipe map or planner. Replaces beta task descriptor loop. */
public class SkilletRecSerializerManager extends RecSerializerManager<CampfireCookingRecipe> {
    private static final SkilletRecSerializerManager INSTANCE = new SkilletRecSerializerManager();
    protected SkilletRecSerializerManager() { super(RecipeType.CAMPFIRE_COOKING); }
    public static SkilletRecSerializerManager getInstance() { return INSTANCE; }
}
