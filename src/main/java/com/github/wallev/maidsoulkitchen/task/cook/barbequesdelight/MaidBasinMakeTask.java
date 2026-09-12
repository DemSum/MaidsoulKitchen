package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetState;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.google.common.collect.ImmutableMap;
import com.mao.barbequesdelight.content.block.BasinBlockEntity;
import com.mao.barbequesdelight.content.recipe.SkeweringInput;
import com.mao.barbequesdelight.content.recipe.SkeweringRecipe;
import com.mao.barbequesdelight.init.registrate.BBQDRecipes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

@TaskClassAnalyzer(TaskInfo.BD_BASIN)
public class MaidBasinMakeTask extends Behavior<EntityMaid> {
    private final TaskBdBasin task;
    private final MaidRecipesManager<SkeweringRecipe<?>> maidRecipesManager;
    private int tick;

    private ItemStack container = ItemStack.EMPTY;
    private ItemStack tool = ItemStack.EMPTY;
    private ItemStack side = ItemStack.EMPTY;
    private boolean completed;

    public MaidBasinMakeTask(TaskBdBasin task, MaidRecipesManager<SkeweringRecipe<?>> maidRecipesManager) {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT), 1200);
        this.task = task;
        this.maidRecipesManager = maidRecipesManager;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        CookTargetState.StartState state = CookTargetMemory.evaluateStart(
                worldIn, maid, task::isCookBE, task.getCloseEnoughDist());
        if (state == CookTargetState.StartState.CLEAR_INVALID) {
            CookTargetMemory.clear(maid);
        }
        return state == CookTargetState.StartState.READY;
    }

    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        return !completed && CookTargetMemory.hasValidWorkTarget(worldIn, maid, task::isCookBE);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        super.start(worldIn, maid, pGameTime);
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockEntity blockEntity = worldIn.getBlockEntity(posWrapper.currentBlockPosition());
            if (blockEntity instanceof BasinBlockEntity basinBlockEntity) {
                IItemHandlerModifiable inputInv = maidRecipesManager.getInputInv();

                if (!basinBlockEntity.items.isEmpty()) {
                    for (ItemStack itemStack : basinBlockEntity.items.getAsList()) {
                        ItemStack leftStack = ItemHandlerHelper.insertItemStacked(inputInv, itemStack.copy(), false);
                        itemStack.shrink(itemStack.getCount() - leftStack.getCount());
                    }
                }

                if (!basinBlockEntity.items.isEmpty()) {
                    return;
                }

                if (!maidRecipesManager.getRecipesIngredients().isEmpty()) {
                    Pair<List<Integer>, List<List<ItemStack>>> recipeIngredient = maidRecipesManager.getRecipeIngredient();


                    List<List<ItemStack>> second = recipeIngredient.getSecond();
                    ItemStack containerStack = second.get(0).get(0);
                    ItemStack remainder = basinBlockEntity.items.addItem(containerStack.copy());
                    containerStack.shrink(containerStack.getCount() - remainder.getCount());

                    container = basinBlockEntity.items.getItem(0);
                    tool = second.get(1).get(0);
                    if (second.size() > 2) {
                        side = second.get(2).get(0);
                    }

                }

                this.maidRecipesManager.getCookInv().syncInv();
            }
        });
    }

    @Override
    protected void tick(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        if (tick++ % 5 != 0) return;
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockEntity blockEntity = worldIn.getBlockEntity(posWrapper.currentBlockPosition());
            if (blockEntity instanceof BasinBlockEntity basinBlockEntity) {
                IItemHandlerModifiable outputInv = maidRecipesManager.getOutputInv();

                var cont = new SkeweringInput(tool, container, side);
                var optional = worldIn.getRecipeManager().getRecipeFor(BBQDRecipes.RT_SKR.get(), cont, worldIn);
                if (optional.isEmpty()) {
                    this.completed = true;
                    return;
                }
                SkeweringRecipe<?> recipe = (SkeweringRecipe<?>) optional.get().value();
                ItemStack preview = recipe.getResultItem(worldIn.registryAccess()).copy();
                if (!CookInventoryTransactions.canInsertAll(outputInv, preview)) return;

                ItemStack result = recipe.assemble(cont, worldIn.registryAccess());
                if (!CookInventoryTransactions.insertAll(outputInv, result)) return;
                maid.swing(InteractionHand.MAIN_HAND);
                basinBlockEntity.notifyTile();
                completed = true;

            }
        });
    }


    @Override
    protected void stop(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        super.stop(worldIn, maid, pGameTime);
        CookTargetMemory.clear(maid);
        this.tick = 0;
        this.tool = ItemStack.EMPTY;
        this.container = ItemStack.EMPTY;
        this.side = ItemStack.EMPTY;
        this.completed = false;
    }
}
