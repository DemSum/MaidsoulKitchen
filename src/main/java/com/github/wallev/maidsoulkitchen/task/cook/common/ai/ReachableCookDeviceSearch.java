package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
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
                        selection
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
            Selection selection
    ) {
        for (int heightOffset : DEVICE_HEIGHT_OFFSETS) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos devicePos = candidateWalkPos.above(heightOffset).relative(direction).immutable();
                if (!isSideApproach(candidateWalkPos, devicePos)
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

    private static boolean isWithinOwnerRange(EntityMaid maid, BlockPos pos) {
        if (maid.isHomeModeEnable()) {
            return true;
        }
        LivingEntity owner = maid.getOwner();
        return owner != null && pos.closerToCenterThan(owner.position(), 8.0);
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
