package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Finds one reachable standing position and the valid cooking device beside it. */
public final class ReachableCookDeviceSearch {
    private static final int[] DEVICE_HEIGHT_OFFSETS = {0, 1};
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
            Predicate<BlockPos> isValidDevice,
            CookTargetCycle targetCycle
    ) {
        return find(level, maid, searchCenter, searchRange, verticalSearchStart, verticalSearchRange,
                isValidDevice, targetCycle, DEVICE_HEIGHT_OFFSETS);
    }

    /** Existing KC side geometry supplies height offsets to the same single-BFS selection. */
    public static Optional<Result> find(ServerLevel level, EntityMaid maid, BlockPos searchCenter,
            int searchRange, int verticalSearchStart, int verticalSearchRange,
            Predicate<BlockPos> isValidDevice, CookTargetCycle targetCycle, int[] deviceHeightOffsets) {
        if (searchRange <= 0) {
            return Optional.empty();
        }

        Set<BlockPos> checkedDevices = new HashSet<>();
        Selection selection = new Selection(targetCycle);
        CookPathSearch.find(
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
                        isValidDevice,
                        selection,
                        deviceHeightOffsets
                )
        );
        return Optional.ofNullable(selection.result());
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
            Predicate<BlockPos> isValidDevice,
            Selection selection,
            int[] deviceHeightOffsets
    ) {
        for (int heightOffset : deviceHeightOffsets) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos devicePos = candidateWalkPos.above(heightOffset).relative(direction).immutable();
                if (!isDeviceWithinSearchBounds(
                        devicePos,
                        searchCenter,
                        searchRange,
                        verticalSearchStart,
                        verticalSearchRange
                )) {
                    continue;
                }
                if (!maid.isWithinRestriction(devicePos)
                        || !ICookTargetTask.isWithinOwnerRange(maid, devicePos)
                        || !level.isLoaded(devicePos)
                        || !checkedDevices.add(devicePos)) {
                    continue;
                }
                if (isValidDevice.test(devicePos)) {
                    if (selection.offer(new Result(candidateWalkPos.immutable(), devicePos))) return true;
                }
            }
        }
        return false;
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

    static boolean isSideApproach(BlockPos walkPos, BlockPos devicePos) {
        return CookTargetGeometry.isSideApproachOffset(
                devicePos.getX() - walkPos.getX(),
                devicePos.getY() - walkPos.getY(),
                devicePos.getZ() - walkPos.getZ()
        );
    }

    private static final class Selection {
        private final CookTargetCycle targetCycle;
        private Result preferred;
        private Result fallback;

        private Selection(CookTargetCycle targetCycle) {
            this.targetCycle = targetCycle;
        }

        private boolean offer(Result candidate) {
            if (targetCycle.prefers(candidate.workPos().asLong())) {
                preferred = candidate;
                return true;
            }
            if (fallback == null) fallback = candidate;
            return false;
        }

        private Result result() {
            return preferred != null ? preferred : fallback;
        }
    }
}
