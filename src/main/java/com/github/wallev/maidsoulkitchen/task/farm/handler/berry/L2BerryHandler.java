package com.github.wallev.maidsoulkitchen.task.farm.handler.berry;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import dev.xkmc.l2harvester.api.HarvestableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Source: 58ec08ec L2BerryHandler (MIT); native state and drops come from the current HarvestResult. */
@TaskClassAnalyzer(TaskInfo.BERRY_L2_HARVESTER)
public final class L2BerryHandler extends BerryHandler {
    @Override protected ActionState processCanHarvest(EntityMaid maid, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof HarvestableBlock crop)) return ActionState.DEFAULT;
        return crop.getHarvestResult(maid.level(), state, pos) != null ? ActionState.ALLOW : ActionState.DENY;
    }
    @Override protected boolean processHarvest(EntityMaid maid, BlockPos pos, BlockState state) {
        if (!maid.canDestroyBlock(pos) || !(state.getBlock() instanceof HarvestableBlock crop)) return false;
        var result = crop.getHarvestResult(maid.level(), state, pos);
        if (result == null) return false;
        result.updateState(maid.level(), pos);
        result.drops().forEach(drop -> CookInventoryTransactions.returnOrDrop(maid.getAvailableInv(true), drop, maid));
        return true;
    }
    @Override public boolean canLoad() { return TaskInfo.BERRY_L2_HARVESTER.canLoad(); }
    @Override public boolean isFarmBlock(Block block) { return block instanceof HarvestableBlock; }
    @Override public ItemStack getIcon() { return Items.SWEET_BERRIES.getDefaultInstance(); }
    @Override public ResourceLocation getUid() { return TaskInfo.BERRY_L2_HARVESTER.getUid(); }
}
