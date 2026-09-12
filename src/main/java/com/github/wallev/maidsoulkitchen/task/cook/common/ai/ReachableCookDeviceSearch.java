package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/** Finds one reachable standing position and the valid cooking device beside it. */
public final class ReachableCookDeviceSearch {
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private ReachableCookDeviceSearch() {
    }

    public record Result(BlockPos walkPos, BlockPos workPos) {
    }

    public static Optional<Result> find(
            ServerLevel level,
            EntityMaid maid,
            BlockPos searchCenter,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange,
            Predicate<BlockPos> isValidDevice
    ) {
        if (searchRange <= 0) {
            return Optional.empty();
        }

        Set<BlockPos> checkedDevices = new HashSet<>();
        BlockPos[] selectedDevice = new BlockPos[1];
        Optional<BlockPos> walkPos = findReachableWalkPosition(
                level,
                maid,
                searchCenter,
                searchRange,
                candidateWalkPos -> selectAdjacentDevice(
                        level,
                        maid,
                        candidateWalkPos,
                        searchCenter,
                        searchRange,
                        verticalSearchStart,
                        verticalSearchRange,
                        checkedDevices,
                        selectedDevice,
                        isValidDevice
                )
        );

        if (walkPos.isEmpty() || selectedDevice[0] == null) {
            return Optional.empty();
        }
        return Optional.of(new Result(walkPos.get().immutable(), selectedDevice[0]));
    }

    private static boolean selectAdjacentDevice(
            ServerLevel level,
            EntityMaid maid,
            BlockPos candidateWalkPos,
            BlockPos searchCenter,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange,
            Set<BlockPos> checkedDevices,
            BlockPos[] selectedDevice,
            Predicate<BlockPos> isValidDevice
    ) {
        if (!maid.isWithinRestriction(candidateWalkPos)) {
            return false;
        }
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos devicePos = candidateWalkPos.relative(direction).immutable();
            if (!isHorizontalNeighbor(candidateWalkPos, devicePos)
                    || !isDeviceWithinSearchBounds(
                    devicePos,
                    searchCenter,
                    searchRange,
                    verticalSearchStart,
                    verticalSearchRange
            )) {
                continue;
            }
            if (!maid.isWithinRestriction(devicePos)
                    || !isWithinOwnerRange(maid, devicePos)
                    || !level.isLoaded(devicePos)
                    || !checkedDevices.add(devicePos)) {
                continue;
            }
            if (isValidDevice.test(devicePos)) {
                selectedDevice[0] = devicePos;
                return true;
            }
        }
        return false;
    }

    /**
     * Runs one breadth-first traversal over the maid navigation graph. Candidate
     * devices are evaluated from visited standing nodes, so this never creates a
     * separate path for every device.
     */
    private static Optional<BlockPos> findReachableWalkPosition(
            ServerLevel level,
            EntityMaid maid,
            BlockPos searchCenter,
            int searchRange,
            Predicate<BlockPos> isWantedWalkPosition
    ) {
        int verticalNavigationRange = 7;
        PathNavigationRegion region = new PathNavigationRegion(
                level,
                searchCenter.offset(-searchRange, -verticalNavigationRange, -searchRange),
                searchCenter.offset(searchRange, verticalNavigationRange, searchRange)
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
                    return Optional.of(currentPos);
                }

                int neighborCount = nodeEvaluator.getNeighbors(neighbors, current);
                for (int index = 0; index < neighborCount; index++) {
                    Node neighbor = neighbors[index];
                    BlockPos neighborPos = neighbor.asBlockPos();
                    if (isInsideNavigationBounds(
                            neighborPos,
                            searchCenter,
                            searchRange,
                            verticalNavigationRange
                    ) && visited.add(neighborPos)) {
                        open.add(neighbor);
                    }
                }
            }
            return Optional.empty();
        } finally {
            nodeEvaluator.done();
        }
    }

    static boolean isInsideNavigationBounds(
            BlockPos pos,
            BlockPos center,
            int horizontalRange,
            int verticalRange
    ) {
        int x = pos.getX() - center.getX();
        int y = pos.getY() - center.getY();
        int z = pos.getZ() - center.getZ();
        return CookTargetGeometry.isInsideNavigationBounds(
                x, y, z, horizontalRange, verticalRange);
    }

    static boolean isDeviceWithinSearchBounds(
            BlockPos devicePos,
            BlockPos searchCenter,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange
    ) {
        int relativeY = devicePos.getY() - searchCenter.getY() - 1;
        return CookTargetGeometry.isDeviceOffsetWithinSearchBounds(
                devicePos.getX() - searchCenter.getX(),
                relativeY,
                devicePos.getZ() - searchCenter.getZ(),
                searchRange,
                verticalSearchStart,
                verticalSearchRange
        );
    }

    static boolean isHorizontalNeighbor(BlockPos first, BlockPos second) {
        return CookTargetGeometry.isHorizontalNeighbor(
                first.getX() - second.getX(),
                first.getY() - second.getY(),
                first.getZ() - second.getZ()
        );
    }

    private static boolean isWithinOwnerRange(EntityMaid maid, BlockPos pos) {
        if (maid.isHomeModeEnable()) {
            return true;
        }
        LivingEntity owner = maid.getOwner();
        return owner != null && pos.closerToCenterThan(owner.position(), 8.0);
    }
}
