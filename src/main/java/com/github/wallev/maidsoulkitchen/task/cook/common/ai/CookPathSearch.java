package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Predicate;

/** Thin lifecycle wrapper around TLM 1.5.3's supported BFS implementation. */
public final class CookPathSearch {
    private static final int DEFAULT_VERTICAL_RANGE = 7;

    private CookPathSearch() {
    }

    public static Optional<BlockPos> find(
            ServerLevel level,
            EntityMaid maid,
            BlockPos center,
            float horizontalRange,
            Predicate<BlockPos> wantedWalkPosition
    ) {
        MaidPathFindingBFS pathFinding = new MaidPathFindingBFS(
                maid.getNavigation().getNodeEvaluator(),
                level,
                maid,
                center,
                horizontalRange,
                DEFAULT_VERTICAL_RANGE
        );
        try {
            return pathFinding.find(wantedWalkPosition).map(BlockPos::immutable);
        } finally {
            pathFinding.finish();
        }
    }
}
