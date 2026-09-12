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
        try (CookSearchDiagnostics.Scan diagnostics = CookSearchDiagnostics.begin(
                maid, maid.getTask().getUid())) {
            return find(level, maid, searchCenter, searchRange, verticalSearchStart,
                    verticalSearchRange, isValidDevice, diagnostics);
        }
    }

    public static Optional<Result> find(
            ServerLevel level,
            EntityMaid maid,
            BlockPos searchCenter,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange,
            Predicate<BlockPos> isValidDevice,
            CookSearchDiagnostics.Scan diagnostics
    ) {
        return find(level, maid, searchCenter, searchRange, verticalSearchStart,
                verticalSearchRange, isValidDevice, diagnostics, null);
    }

    public static Optional<Result> find(
            ServerLevel level,
            EntityMaid maid,
            BlockPos searchCenter,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange,
            Predicate<BlockPos> isValidDevice,
            CookSearchDiagnostics.Scan diagnostics,
            CookTargetCycle targetCycle
    ) {
        if (searchRange <= 0) {
            return Optional.empty();
        }

        Set<BlockPos> checkedDevices = new HashSet<>();
        Selection selection = new Selection(targetCycle);
        findReachableWalkPosition(
                level,
                maid,
                searchCenter,
                searchRange,
                candidateWalkPos -> {
                    diagnostics.visitedWalkNode();
                    return selectAdjacentDevice(
                        level,
                        maid,
                        candidateWalkPos,
                        searchCenter,
                        searchRange,
                        verticalSearchStart,
                        verticalSearchRange,
                        checkedDevices,
                        isValidDevice,
                        diagnostics,
                        selection
                    );
                }
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
            CookSearchDiagnostics.Scan diagnostics,
            Selection selection
    ) {
        if (!maid.isWithinRestriction(candidateWalkPos)) {
            diagnostics.rejectedWalkRestriction();
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
            diagnostics.adjacentInBounds();
            if (!maid.isWithinRestriction(devicePos)) {
                diagnostics.rejectedDeviceRestriction();
                continue;
            }
            if (!isWithinOwnerRange(maid, devicePos)) {
                diagnostics.rejectedOwnerRange();
                continue;
            }
            if (!level.isLoaded(devicePos)) {
                diagnostics.rejectedUnloaded();
                continue;
            }
            if (!checkedDevices.add(devicePos)) {
                diagnostics.rejectedDuplicate();
                continue;
            }
            diagnostics.deviceCandidate();
            if (isValidDevice.test(devicePos)) {
                diagnostics.actionableDevice();
                if (selection.offer(new Result(candidateWalkPos.immutable(), devicePos))) return true;
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
        return CookPathSearch.find(level, maid, searchCenter, searchRange, isWantedWalkPosition);
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

    private static final class Selection {
        private final CookTargetCycle targetCycle;
        private Result preferred;
        private Result fallback;

        private Selection(CookTargetCycle targetCycle) {
            this.targetCycle = targetCycle;
        }

        private boolean offer(Result candidate) {
            if (targetCycle == null || targetCycle.prefers(candidate.workPos().asLong())) {
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
