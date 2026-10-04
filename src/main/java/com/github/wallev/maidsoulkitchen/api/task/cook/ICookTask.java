package com.github.wallev.maidsoulkitchen.api.task.cook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.List;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.github.wallev.maidsoulkitchen.api.IMaidsoulKitchenTask;
import com.github.wallev.maidsoulkitchen.api.TaskBookEntryType;
import com.github.wallev.maidsoulkitchen.api.event.MaidMkTaskEnableEvent;
import com.github.wallev.maidsoulkitchen.api.task.IDataTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.CookConfigContainer;
import com.github.wallev.maidsoulkitchen.inventory.tooltip.AmountTooltip;
import com.github.wallev.maidsoulkitchen.task.cook.common.cbaccessor.IRecipeExperinceAward;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;


/**
 * Source: 58ec08ec api/task/cook/ICookTask.java (MIT). Retains per-maid Be/Rule/manager creation
 * and common Collect/Generate/Move/Make/Pathing dispatch. Direct TLM BehaviorControl replaces V shims;
 * the duplicate upstream Collect registration and empty builder/ride hooks are omitted.
 * Sound, favour and menu methods retain the upstream contract with current NeoForge events.
 * Replaces the beta v1/v2 execution APIs; all devices now dispatch only Be/Rule and common goals.
 */
public abstract class ICookTask<B extends BlockEntity, R extends Recipe<? extends RecipeInput>>
        implements ICookTargetTask, com.github.wallev.maidsoulkitchen.api.task.IDataTask<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> {
    public static final float MOVE_SPEED = 0.5f;
    public static final int VERTICAL_SEARCH_RANGE = 2;
    public final AbstractCookRule<B, R> cookRule;
    public final RecSerializerManager<R> recSerializerManager;
    protected ICookTask() {
        cookRule = createCookRule(); recSerializerManager = createRecSerializerManager();
    }
    protected abstract AbstractCookRule<B, R> createCookRule();
    protected abstract RecSerializerManager<R> createRecSerializerManager();
    protected abstract CookBeBase<B> createCookBe(EntityMaid maid);
    public final RecSerializerManager<R> getRecSerializerManager() { return recSerializerManager; }
    public RecipeType<R> getRecipeType() { return recSerializerManager.getRecipeType(); }
    /** Source: upstream BubbleUtil recipe overview/commit presentation. Explicit local acceptance
     * policy temporarily excludes the new KC devices from quantity bubbles. A constant device
     * capability changes presentation only; it cannot change planning, transactions or the queue. */
    public boolean showRecipeAmountBubbles() { return true; }
    /** Source getRecipes delegates to the RSM catalog. Holder is the 1.21 identity boundary;
     * GUI and server filter validation read this same catalog without building work. */
    public List<RecipeHolder<R>> getRecipeHolders(Level level) {
        return recSerializerManager.getRecipes(level).stream().map(recipe -> recipe.holder()).toList();
    }
    @Override public final List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        if (maid.level().isClientSide()) return List.of();
        var owner = (com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid;
        if (owner.tlmk$getCookManager() != null) owner.tlmk$getCookManager().retire();
        CookTargetMemory.clear(maid);
        CookBeBase<B> cookBe = createCookBe(maid);
        AbstractCookRule<B, R> rule = cookRule.getOrCreate();
        MaidCookManager<R> cm = createRecipesManager(maid, cookBe);
        owner.tlmk$setCookManager(cm);
        cm.checkAndInit();
        // TLM 1.5.3 appends its common goals to the returned upstream task list.
        return new java.util.ArrayList<>(List.of(Pair.of(0, new ResetCookMemoryTask<>(cm)),
                Pair.of(3, new CollectChestIngredientsTask<>(cm)), Pair.of(4, new GenerateRecsTask<>(cm)),
                Pair.of(5, new CookMoveTask<>(this, cm, rule, cookBe)),
                Pair.of(6, new CookMakeTask<>(this, cm, rule, cookBe)),
                Pair.of(7, new CookMakePathingTask<>(cookBe))));
    }
    protected MaidCookManager<R> createRecipesManager(EntityMaid maid, CookBeBase<B> cookBe) {
        return new MaidCookManager<>(recSerializerManager, maid, this);
    }
    /** Source one-manager-per-brain contract. TLM lacks a task-context accessor, so native/GUI
     * consumers reuse its maid-owned reference; replaces the beta getter's fresh queue owner. */
    @SuppressWarnings("unchecked")
    public final MaidCookManager<R> getRecipesManager(EntityMaid maid) {
        var owner = (com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid;
        var active = owner.tlmk$getCookManager();
        if (active != null && active.getTaskUid().equals(getUid())) return (MaidCookManager<R>) active;
        if (active != null) active.retire();
        var manager = createRecipesManager(maid, createCookBe(maid));
        owner.tlmk$setCookManager(manager);
        return manager;
    }
    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_FURNACE.get(), 0.5f);
    }

    public double getCloseEnoughDist() {
        return 3.2;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        MaidMkTaskEnableEvent maidMkTaskEnableEvent = new MaidMkTaskEnableEvent(maid, this);
        NeoForge.EVENT_BUS.post(maidMkTaskEnableEvent);
        if (!maidMkTaskEnableEvent.isEnable()) {
            return maidMkTaskEnableEvent.getEnableConditionDesc();
        }

        return Lists.newArrayList(Pair.of("has_enough_favor", this::hasEnoughFavor));
    }

    @Override
    public boolean isEnable(EntityMaid maid) {
        MaidMkTaskEnableEvent maidMkTaskEnableEvent = new MaidMkTaskEnableEvent(maid, this);
        NeoForge.EVENT_BUS.post(maidMkTaskEnableEvent);
        if (!maidMkTaskEnableEvent.isEnable()) {
            return false;
        }

        return hasEnoughFavor(maid);
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        final int entityId = maid.getId();
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Maid Cook Config Container2");
            }

            @Override
            public AbstractContainerMenu createMenu(int index, Inventory playerInventory, Player player) {
                return new CookConfigContainer(index, playerInventory, entityId);
            }

            @Override
            public boolean shouldTriggerClientSideContainerClosingOnOpen() {
                return false;
            }
        };
    }

    public boolean hasEnoughFavor(EntityMaid maid) {
        return maid.getFavorabilityManager().getLevel() >= 1;
    }






    @Override
    public TaskBookEntryType getBookEntryType() {
        return TaskBookEntryType.COOK;
    }

    @Override
    public CookData getDefaultData() {
        return new CookData();
    }

    @Override
    public CookData getTaskData(EntityMaid maid) {
        return com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData.get(maid, this);
    }
    public NonNullList<Ingredient> getIngredients(Recipe<?> recipe) {
        return recipe.getIngredients();
    }

    public ItemStack getResultItem(Recipe<?> recipe, RegistryAccess pRegistryAccess) {
        return recipe.getResultItem(pRegistryAccess);
    }

    @OnlyIn(Dist.CLIENT)
    public Optional<TooltipComponent> getRecClientAmountTooltip(Recipe<?> recipe, boolean modeRandom, boolean overSize, CookData cookData) {
        List<Ingredient> ingres = this.getIngredients(recipe);
        return ingres.isEmpty() ? Optional.empty() : Optional.of(new AmountTooltip(getRecipeId(recipe), ingres, modeRandom, overSize, cookData));
    }

    @OnlyIn(Dist.CLIENT)
    public String getRecipeId(Recipe<?> recipe) {
        Optional<RecipeHolder<R>> recipeHolder = this.getRecipeHolders(Minecraft.getInstance().level).stream().filter(r -> r.value().equals(recipe)).findFirst();
        return recipeHolder.map(rRecipeHolder -> rRecipeHolder.id().toString()).orElse("");
    }

    public List<Component> getWarnComponent() {
        return Collections.emptyList();
    }

    public static void awardExperience(BlockEntity blockEntity, EntityMaid maid) {
        if (blockEntity instanceof IRecipeExperinceAward iRecipeExperinceAward) {
            iRecipeExperinceAward.tlmk$awardExperience(maid);
        }
    }
}
