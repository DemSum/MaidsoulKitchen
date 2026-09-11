package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One bounded traversal of the maid navigation graph. It is deliberately
 * independent of TLM's newer MaidPathFindingBFS API so 1.1.13 remains the
 * compile baseline.
 */
final class SteamerApproachSearch {
    private static final int VERTICAL_NAVIGATION_RANGE = 7;

    private SteamerApproachSearch() {
    }

    static Optional<BlockPos> find(
            ServerLevel level,
            EntityMaid maid,
            Predicate<BlockPos> isWantedWalkPosition
    ) {
        BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition().below();
        int horizontalRange = Math.max(1, (int) maid.getRestrictRadius());
        PathNavigationRegion region = new PathNavigationRegion(
                level,
                center.offset(-horizontalRange, -VERTICAL_NAVIGATION_RANGE, -horizontalRange),
                center.offset(horizontalRange, VERTICAL_NAVIGATION_RANGE, horizontalRange)
        );
        NodeEvaluator nodeEvaluator = maid.getNavigation().getNodeEvaluator();
        Queue<Node> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Node[] neighbors = new Node[32];

        nodeEvaluator.prepare(region, maid);
        try {
            Node start = nodeEvaluator.getStart();
            if (start == null) {
                return Optional.empty();
            }
            open.add(start);
            visited.add(start.asBlockPos());

            while (!open.isEmpty()) {
                Node current = open.remove();
                BlockPos currentPos = current.asBlockPos();
                if (isWantedWalkPosition.test(currentPos)) {
                    return Optional.of(currentPos.immutable());
                }

                int neighborCount = nodeEvaluator.getNeighbors(neighbors, current);
                for (int index = 0; index < neighborCount; index++) {
                    Node neighbor = neighbors[index];
                    BlockPos neighborPos = neighbor.asBlockPos();
                    if (insideBounds(neighborPos, center, horizontalRange)
                            && visited.add(neighborPos)) {
                        open.add(neighbor);
                    }
                }
            }
            return Optional.empty();
        } finally {
            nodeEvaluator.done();
        }
    }

    static boolean insideBounds(BlockPos pos, BlockPos center, int horizontalRange) {
        return SteamerSearchGeometry.insideBounds(
                pos.getX() - center.getX(),
                pos.getY() - center.getY(),
                pos.getZ() - center.getZ(),
                horizontalRange,
                VERTICAL_NAVIGATION_RANGE
        );
    }
}
