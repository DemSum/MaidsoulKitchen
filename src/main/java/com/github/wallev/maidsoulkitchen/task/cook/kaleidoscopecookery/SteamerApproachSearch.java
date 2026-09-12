package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookPathSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * One bounded traversal of the maid navigation graph using TLM 1.5.3's
 * pathfinding API.
 */
final class SteamerApproachSearch {
    private SteamerApproachSearch() {
    }

    static Optional<BlockPos> find(
            ServerLevel level,
            EntityMaid maid,
            Predicate<BlockPos> isWantedWalkPosition
    ) {
        BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition();
        return CookPathSearch.find(level, maid, center, maid.searchRadius(), isWantedWalkPosition);
    }
}
