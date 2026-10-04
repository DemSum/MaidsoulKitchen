package com.github.wallev.maidsoulkitchen.task.cook.common.inv.chest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Source: 58ec08ec task/cook/common/inv/chest/ChestInvsData.java (MIT).
 * Keeps the upstream scanning/planning boundaries; NeoForge and RecipeInput replace 1.20 APIs.
 * Upstream MaidCookManager support; no independent work queue.
 */
public record ChestInvsData(List<BlockPos> chestPoses, List<BlockEntity> chestBes, List<IItemHandler> chestItemHandlers, int invSlots) {
}
