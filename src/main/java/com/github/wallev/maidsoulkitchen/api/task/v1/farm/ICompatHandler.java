package com.github.wallev.maidsoulkitchen.api.task.v1.farm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

public interface ICompatHandler {
    default boolean process(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        // Only known right-click harvests; an arbitrary age property does not imply edible fruit.
        return cropState.getBlock() instanceof SweetBerryBushBlock
                && cropState.getValue(SweetBerryBushBlock.AGE) == SweetBerryBushBlock.MAX_AGE
                || CaveVines.hasGlowBerries(cropState);
    }
}
