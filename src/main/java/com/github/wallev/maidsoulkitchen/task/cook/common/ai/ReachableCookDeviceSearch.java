package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
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
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private ReachableCookDeviceSearch() {
    }

    public record Result(BlockPos walkPos, BlockPos workPos) {
    }

    @FunctionalInterface
    public interface CandidateSource {
        boolean select(BlockPos walkPos, Predicate<BlockPos> offerWorkPos);
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
        return find(
                level, maid, searchCenter, searchRange,
                verticalSearchStart, verticalSearchRange,
                isValidDevice, null
        );
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
        Set<BlockPos> checkedDevices = new HashSet<>();
        return findCustom(
                level,
                maid,
                searchCenter,
                searchRange,
                targetCycle,
                (walkPos, offerWorkPos) -> selectAdjacentDevice(
                        level,
                        maid,
                        walkPos,
                        searchCenter,
                        searchRange,
                        verticalSearchStart,
                        verticalSearchRange,
                        checkedDevices,
                        isValidDevice,
                        offerWorkPos
                )
        );
    }

    public static Optional<Result> findCustom(
            ServerLevel level,
            EntityMaid maid,
            BlockPos searchCenter,
            int searchRange,
            CookTargetCycle targetCycle,
            CandidateSource candidates
    ) {
        if (searchRange <= 0) {
            return Optional.empty();
        }

        Selection selection = new Selection(targetCycle);
        findReachableWalkPosition(
                level,
                maid,
                searchCenter,
                searchRange,
                walkPos -> candidates.select(
                        walkPos,
                        workPos -> selection.offer(new Result(walkPos.immutable(), workPos.immutable())))
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
            Predicate<BlockPos> offerWorkPos
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
                if (offerWorkPos.test(devicePos)) {
                    return true;
                }
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
        MaidPathFindingBFS pathFinding = new MaidPathFindingBFS(
                maid.getNavigation().getNodeEvaluator(),
                level,
                maid,
                searchCenter,
                searchRange,
                7
        );
        try {
            return pathFinding.find(isWantedWalkPosition).map(BlockPos::immutable);
        } finally {
            pathFinding.finish();
        }
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

    private static final class Selection {
        private final CookTargetCycle targetCycle;
        private Result selected;
        private Result fallback;

        private Selection(CookTargetCycle targetCycle) {
            this.targetCycle = targetCycle;
        }

        private boolean offer(Result candidate) {
            if (targetCycle == null || targetCycle.prefers(candidate.workPos().asLong())) {
                selected = candidate;
                return true;
            }
            if (fallback == null) {
                fallback = candidate;
            }
            return false;
        }

        private Result result() {
            return selected != null ? selected : fallback;
        }
    }
}
