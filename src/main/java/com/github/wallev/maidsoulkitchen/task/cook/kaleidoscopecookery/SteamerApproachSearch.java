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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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
        NativeSearchResult nativeResult = NativeBfs.search(level, maid, isWantedWalkPosition);
        if (nativeResult.supported()) {
            return nativeResult.position();
        }

        return findWithLegacyEvaluator(level, maid, isWantedWalkPosition);
    }

    /**
     * TLM 1.5.3 initializes additional evaluator caches inside
     * MaidPathFindingBFS. Prefer that implementation when it is available,
     * while retaining the 1.1.13-compatible evaluator traversal as a fallback.
     */
    private static Optional<BlockPos> findWithLegacyEvaluator(
            ServerLevel level,
            EntityMaid maid,
            Predicate<BlockPos> isWantedWalkPosition
    ) {
        BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition();
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

    private record NativeSearchResult(boolean supported, Optional<BlockPos> position) {
        private static NativeSearchResult unsupported() {
            return new NativeSearchResult(false, Optional.empty());
        }
    }

    /** Reflection keeps the declared minimum TLM version at 1.1.13. */
    private static final class NativeBfs {
        private static final String CLASS_NAME =
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS";
        private static final Constructor<?> CONSTRUCTOR;
        private static final Method FIND;
        private static final Method FINISH;

        static {
            Constructor<?> constructor = null;
            Method find = null;
            Method finish = null;
            try {
                Class<?> type = Class.forName(CLASS_NAME, false, SteamerApproachSearch.class.getClassLoader());
                constructor = type.getConstructor(
                        NodeEvaluator.class, ServerLevel.class, EntityMaid.class);
                find = type.getMethod("find", Predicate.class);
                finish = type.getMethod("finish");
            } catch (ReflectiveOperationException ignored) {
                // TLM 1.1.13 has no MaidPathFindingBFS; use the legacy traversal.
            }
            CONSTRUCTOR = constructor;
            FIND = find;
            FINISH = finish;
        }

        private NativeBfs() {
        }

        private static NativeSearchResult search(
                ServerLevel level,
                EntityMaid maid,
                Predicate<BlockPos> wanted
        ) {
            if (CONSTRUCTOR == null || FIND == null || FINISH == null) {
                return NativeSearchResult.unsupported();
            }

            Object pathFinding = null;
            try {
                pathFinding = CONSTRUCTOR.newInstance(
                        maid.getNavigation().getNodeEvaluator(), level, maid);
                Object result = FIND.invoke(pathFinding, wanted);
                if (result instanceof Optional<?> optional) {
                    return new NativeSearchResult(true,
                            optional.filter(BlockPos.class::isInstance)
                                    .map(BlockPos.class::cast)
                                    .map(BlockPos::immutable));
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // A future TLM may change this API; the legacy traversal remains safe.
            } finally {
                if (pathFinding != null) {
                    try {
                        FINISH.invoke(pathFinding);
                    } catch (ReflectiveOperationException ignored) {
                        // The search result remains valid even if cleanup changed upstream.
                    }
                }
            }
            return NativeSearchResult.unsupported();
        }
    }
}
