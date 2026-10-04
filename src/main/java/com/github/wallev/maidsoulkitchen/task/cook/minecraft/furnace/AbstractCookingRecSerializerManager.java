package com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import java.util.*;
/** Source: 58ec08ec AbstractCookingRecSerializerManager.java (MIT). Preserves the combined
 * smelting/smoking/blasting catalog and native fuel list. Holder snapshots make all three families
 * reloadable; vanilla/NeoForge item fuel APIs replace ForgeRegistries/ForgeHooks. */
public class AbstractCookingRecSerializerManager extends RecSerializerManager<AbstractCookingRecipe> {
    private static final AbstractCookingRecSerializerManager INSTANCE = new AbstractCookingRecSerializerManager();
    @SuppressWarnings({"unchecked", "rawtypes"}) protected AbstractCookingRecSerializerManager() { super((RecipeType) RecipeType.SMELTING); }
    public static AbstractCookingRecSerializerManager getInstance() { return INSTANCE; }
    @Override @SuppressWarnings({"unchecked", "rawtypes"}) protected List<RecipeHolder<AbstractCookingRecipe>> getRecsFromRm(Level level) {
        List<RecipeHolder<AbstractCookingRecipe>> recipes = new ArrayList<>();
        recipes.addAll((List) level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
        recipes.addAll((List) level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING));
        recipes.addAll((List) level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING));
        return recipes;
    }
    @Override public List<ItemStack> getFuels() {
        if (fuels == null) fuels = BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance()).filter(AbstractFurnaceBlockEntity::isFuel).toList();
        return fuels.stream().map(ItemStack::copy).toList();
    }
}
