package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetState;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.google.common.collect.ImmutableMap;
import com.mao.barbequesdelight.content.block.GrillBlockEntity;
import com.mao.barbequesdelight.content.recipe.GrillingRecipe;
import com.mao.barbequesdelight.init.registrate.BBQDItems;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.BD_GRILL)
public class MaidGrillMakeTask extends Behavior<EntityMaid> {
    private final TaskBdGrill task;
    private final MaidRecipesManager<GrillingRecipe<?>> maidRecipesManager;
    private final List<ItemStack> grillStacks = new ArrayList<>();

    public MaidGrillMakeTask(TaskBdGrill task, MaidRecipesManager<GrillingRecipe<?>> maidRecipesManager) {
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
        return CookTargetMemory.hasValidWorkTarget(worldIn, maid, task::isCookBE);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        super.start(worldIn, maid, pGameTime);
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockEntity blockEntity = worldIn.getBlockEntity(posWrapper.currentBlockPosition());
            if (blockEntity instanceof GrillBlockEntity grillBlockEntity) {
                if (!maidRecipesManager.getRecipesIngredients().isEmpty()) {
                    Pair<List<Integer>, List<List<ItemStack>>> recipeIngredient = maidRecipesManager.getRecipeIngredient();
                    grillStacks.addAll(recipeIngredient.getSecond().get(0));
                }

                this.maidRecipesManager.getCookInv().syncInv();
            }
        });
    }

    @Override
    protected void tick(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockEntity blockEntity = worldIn.getBlockEntity(posWrapper.currentBlockPosition());
            if (blockEntity instanceof GrillBlockEntity grillBlockEntity) {
                IItemHandlerModifiable outputInv = maidRecipesManager.getOutputInv();

                boolean nothing = true;
                GrillBlockEntity.ItemEntry[] itemEntries = grillBlockEntity.entries;
                for (GrillBlockEntity.ItemEntry itemEntry : itemEntries) {
                    ItemStack stack = itemEntry.stack;
                    if (stack.is(BBQDItems.BURNT_FOOD.asItem())) {
                        ItemStack leftStack = ItemHandlerHelper.insertItemStacked(outputInv, stack.copy(), false);
                        stack.shrink(stack.getCount() - leftStack.getCount());
                        grillBlockEntity.inventoryChanged();

                        nothing = false;
                    } else if (!stack.isEmpty()) {
                        // 要翻转了
                        if (itemEntry.canFlip()) {
                            itemEntry.flip(grillBlockEntity);
                            maid.swing(InteractionHand.MAIN_HAND);

                        }

                        // 熟了，可以取出来了
                        if (itemEntry.flipped && itemEntry.time >= itemEntry.duration) {
                            ItemStack leftStack = ItemHandlerHelper.insertItemStacked(outputInv, stack.copy(), false);
                            stack.shrink(stack.getCount() - leftStack.getCount());
                            grillBlockEntity.inventoryChanged();
                        }

                        nothing = false;
                    } else {
                        if (!grillStacks.isEmpty()) {
                            ItemStack grillStack = grillStacks.get(0);

                            if (!grillStack.isEmpty() && itemEntry.addItem(grillBlockEntity, grillStack.copyWithCount(1))) {
                                maid.swing(InteractionHand.MAIN_HAND);
                                grillBlockEntity.inventoryChanged();
                                grillStack.shrink(1);
                                nothing = false;
                            }

                        }

                    }
                }

                if (nothing) {
                    this.stop(worldIn, maid, pGameTime);
                    this.maidRecipesManager.getCookInv().syncInv();
                    return;
                }

            }
        });
    }

    @Override
    protected void stop(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        super.stop(worldIn, maid, pGameTime);
        CookTargetMemory.clear(maid);
        grillStacks.clear();
    }
}
