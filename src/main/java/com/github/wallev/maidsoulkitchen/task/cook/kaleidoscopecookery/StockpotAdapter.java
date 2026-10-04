package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.wallev.maidsoulkitchen.init.MkEffects;
import com.github.wallev.maidsoulkitchen.init.MkItems;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.serializer.StockpotRecipeSerializer;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.FluidSoupBase;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import com.github.ysbbbbbb.kaleidoscopecookery.compat.tetra.TetraCompat;
import com.github.ysbbbbbb.kaleidoscopecookery.util.ItemUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Optional;

/** KC 1.4.1 API boundary. Structure informed by the MIT Public StockpotAdapter. */
@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.KC_STOCKPOT)
public final class StockpotAdapter {
    private StockpotAdapter() { }

    public static void verifyApi() {
        if (!IStockpot.class.isAssignableFrom(StockpotBlockEntity.class)) {
            throw new IllegalStateException("KC stockpot no longer implements IStockpot");
        }
    }

    public static List<RecipeOption> getRecipeOptions(Level level, boolean includeFlex) {
        return StockpotRecipePlanner.options(level, includeFlex);
    }

    public static boolean supports(BlockEntity entity) {
        return entity instanceof StockpotBlockEntity;
    }

    public static Optional<Snapshot> inspect(BlockEntity entity, Level level) {
        StockpotBlockEntity pot = current(entity, level);
        if (pot == null) return Optional.empty();
        return Optional.of(new Snapshot(pot.getStatus(), pot.hasHeatSource(level), pot.hasLid(),
                pot.getSoupBaseId(), pot.getInputs(), pot.getLidItem(), pot.getResult(), pot.getTakeoutCount()));
    }

    static StockpotBlockEntity current(BlockEntity entity, Level level) {
        return entity instanceof StockpotBlockEntity pot && !pot.isRemoved()
                && level.isLoaded(pot.getBlockPos()) && level.getBlockEntity(pot.getBlockPos()) == pot
                ? pot : null;
    }

    public static Optional<ResourceLocation> soupBaseFor(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        // Native iteration order matters when multiple soup predicates accept the same item.
        return SoupBaseManager.getAllSoupBases().entrySet().stream()
                .filter(entry -> entry.getValue().isSoupBase(stack)).map(java.util.Map.Entry::getKey).findFirst();
    }

    public static boolean isLid(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.STOCKPOT_LID.get());
    }

    public static boolean canPlaceIngredient(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(TagMod.INGREDIENT_BLOCKLIST) && !TetraCompat.isModularItem(stack);
    }

    static ItemStack lidToReturn(Snapshot snapshot) {
        // Capacity preview of KC's legacy fallback; only onLitClick actually returns the item.
        return snapshot.lid().isEmpty() && snapshot.covered()
                ? ModItems.STOCKPOT_LID.get().getDefaultInstance() : snapshot.lid();
    }

    public static ItemStack ingredientContainer(ItemStack stack) {
        var item = ItemUtils.getContainerItem(stack);
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    public static Optional<Ingredient> carrier(BlockEntity entity, Level level) {
        StockpotBlockEntity pot = current(entity, level);
        if (pot == null) return Optional.empty();
        // Read native saved identity only at collection; never load it back or write private state.
        String saved = pot.saveWithoutMetadata(level.registryAccess()).getString("RecipeId");
        ResourceLocation id = ResourceLocation.tryParse(saved);
        if (id == null) return Optional.empty();
        var recipe = level.getRecipeManager().byKey(id).map(holder -> holder.value()).orElse(null);
        if (recipe instanceof StockpotRecipe ordinary) return Optional.of(ordinary.carrier());
        if (recipe instanceof FlexStockpotRecipe flex) return Optional.of(flex.carrier());
        return Optional.of(StockpotRecipeSerializer.DEFAULT_CARRIER);
    }

    public static boolean addSoupBase(BlockEntity entity, Level level, LivingEntity user, ItemStack stack) {
        var pot = current(entity, level);
        return pot != null && !pot.hasLid() && pot.getStatus() == IStockpot.PUT_SOUP_BASE
                && pot.addSoupBase(level, user, stack);
    }

    public static boolean addIngredient(BlockEntity entity, Level level, LivingEntity user, ItemStack stack) {
        var pot = current(entity, level);
        return pot != null && !pot.hasLid() && pot.getStatus() == IStockpot.PUT_INGREDIENT
                && pot.addIngredient(level, user, stack);
    }

    public static boolean cover(BlockEntity entity, Level level, LivingEntity user, ItemStack lid) {
        var pot = current(entity, level);
        return pot != null && !pot.hasLid() && isLid(lid)
                && pot.onLitClick(level, user, lid) && pot.hasLid();
    }

    public static boolean uncover(BlockEntity entity, Level level, LivingEntity user) {
        var pot = current(entity, level);
        return pot != null && pot.hasLid() && user.getMainHandItem().isEmpty()
                && pot.onLitClick(level, user, ItemStack.EMPTY) && !pot.hasLid();
    }

    public static boolean removeIngredient(BlockEntity entity, Level level, EntityMaid maid) {
        var pot = current(entity, level);
        return pot != null && !pot.hasLid() && pot.getStatus() == IStockpot.PUT_INGREDIENT
                && canRetrieveIngredients(pot, maid) && pot.removeIngredient(level, maid);
    }

    public static boolean takeOne(BlockEntity entity, Level level, LivingEntity user, ItemStack container) {
        var pot = current(entity, level);
        return pot != null && !pot.hasLid() && pot.getStatus() == IStockpot.FINISHED
                && pot.takeOutProduct(level, user, container);
    }

    public static boolean canRetrieveIngredients(BlockEntity entity, EntityMaid maid) {
        if (!(entity instanceof StockpotBlockEntity pot)) return false;
        if (!(pot.getSoupBase() instanceof FluidSoupBase fluid)
                || fluid.getFluid().getFluidType().getTemperature() <= 500) return true;
        if (maid.hasEffect(MobEffects.FIRE_RESISTANCE) || maid.hasEffect(MkEffects.BURN_PROTECT)) return true;
        var baubles = maid.getMaidBauble();
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (!stack.isEmpty() && (stack.is(InitItems.FIRE_PROTECT_BAUBLE.get())
                    || stack.is(MkItems.BURN_PROTECT_BAUBLE.get()))
                    && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage())) return true;
        }
        return false; // Let existing damage/bauble events do all actual durability charging.
    }

    public record Snapshot(int status, boolean heated, boolean covered, ResourceLocation soupBase,
                           List<ItemStack> inputs, ItemStack lid, ItemStack result, int portions) {
        public Snapshot {
            inputs = inputs.stream().map(ItemStack::copy).toList();
            lid = lid.copy();
            result = result.copy();
        }
        @Override public List<ItemStack> inputs() { return inputs.stream().map(ItemStack::copy).toList(); }
        @Override public ItemStack lid() { return lid.copy(); }
        @Override public ItemStack result() { return result.copy(); }
        public boolean hasIngredients() { return inputs.stream().anyMatch(stack -> !stack.isEmpty()); }
    }
}
