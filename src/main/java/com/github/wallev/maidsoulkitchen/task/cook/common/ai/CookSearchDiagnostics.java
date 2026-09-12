package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in, one-line-per-scan diagnostics for development reproductions. */
public final class CookSearchDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("maidsoulkitchen.debugCookingChain");
    private static final Scan DISABLED = new Scan(null, null, false);
    private static final Map<UUID, String> LAST_ASSIGNMENT_STATE = new ConcurrentHashMap<>();

    private CookSearchDiagnostics() {
    }

    public static Scan begin(EntityMaid maid, ResourceLocation taskUid) {
        return ENABLED ? new Scan(maid, taskUid, true) : DISABLED;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void registration(ResourceLocation taskUid, boolean enabled) {
        if (ENABLED) {
            MaidsoulKitchen.LOGGER.info("Cook debug registration task={} enabled={}", taskUid, enabled);
        }
    }

    public static void brainCreated(EntityMaid maid, ResourceLocation taskUid) {
        if (ENABLED) {
            MaidsoulKitchen.LOGGER.info("Cook debug brain task={} maid={} created=true",
                    taskUid, maid.getUUID());
        }
    }

    public static void assignmentState(EntityMaid maid, ResourceLocation taskUid,
                                       CookTargetState.StartState state, BlockPos workPos) {
        if (!ENABLED) return;
        String value = taskUid + ":" + state + ":" + workPos;
        if (!value.equals(LAST_ASSIGNMENT_STATE.put(maid.getUUID(), value))) {
            MaidsoulKitchen.LOGGER.info(
                    "Cook debug assignment task={} maid={} state={} work={}",
                    taskUid, maid.getUUID(), state, workPos);
        }
    }

    public static void assignmentCleared(EntityMaid maid) {
        if (ENABLED) LAST_ASSIGNMENT_STATE.remove(maid.getUUID());
    }

    public static int totalItems(IItemHandler inventory) {
        if (inventory == null) return -1;
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            total += inventory.getStackInSlot(slot).getCount();
        }
        return total;
    }

    public static void makeStarted(EntityMaid maid, ResourceLocation taskUid, BlockPos workPos,
                                   int inputItems, int outputItems) {
        if (ENABLED) {
            MaidsoulKitchen.LOGGER.info(
                    "Cook debug make-start task={} maid={} work={} input_items={} output_items={}",
                    taskUid, maid.getUUID(), workPos, inputItems, outputItems);
        }
    }

    public static void makeCompleted(EntityMaid maid, ResourceLocation taskUid, BlockPos workPos,
                                     int inputBefore, int inputAfter,
                                     int outputBefore, int outputAfterProcess, int outputAfterStorage) {
        if (ENABLED) {
            MaidsoulKitchen.LOGGER.info(
                    "Cook debug make-end task={} maid={} work={} input_before={} input_after={} "
                            + "output_before={} output_after_process={} output_after_storage={}",
                    taskUid, maid.getUUID(), workPos, inputBefore, inputAfter,
                    outputBefore, outputAfterProcess, outputAfterStorage);
        }
    }

    public static final class Scan implements AutoCloseable {
        private final EntityMaid maid;
        private final ResourceLocation taskUid;
        private final boolean enabled;
        private int visitedWalkNodes;
        private int walkRestrictionRejected;
        private int adjacentInBounds;
        private int deviceRestrictionRejected;
        private int ownerRangeRejected;
        private int unloadedRejected;
        private int duplicateRejected;
        private int deviceCandidates;
        private int lockedDevices;
        private int missingBlockEntities;
        private int wrongBlockEntities;
        private int cookDevices;
        private int inactiveCookDevices;
        private int actionableDevices;
        private int rejectedClaims;
        private boolean selected;
        private boolean recipeManagerReady;
        private int recipePlans = -1;
        private boolean culinaryHub;
        private int inputItems = -1;
        private int outputItems = -1;
        private BlockPos selectedWalkPos;
        private BlockPos selectedWorkPos;

        private Scan(EntityMaid maid, ResourceLocation taskUid, boolean enabled) {
            this.maid = maid;
            this.taskUid = taskUid;
            this.enabled = enabled;
        }

        public void visitedWalkNode() {
            if (enabled) visitedWalkNodes++;
        }

        public void rejectedWalkRestriction() {
            if (enabled) walkRestrictionRejected++;
        }

        public void adjacentInBounds() {
            if (enabled) adjacentInBounds++;
        }

        public void rejectedDeviceRestriction() {
            if (enabled) deviceRestrictionRejected++;
        }

        public void rejectedOwnerRange() {
            if (enabled) ownerRangeRejected++;
        }

        public void rejectedUnloaded() {
            if (enabled) unloadedRejected++;
        }

        public void rejectedDuplicate() {
            if (enabled) duplicateRejected++;
        }

        public void deviceCandidate() {
            if (enabled) deviceCandidates++;
        }

        public void lockedDevice() {
            if (enabled) lockedDevices++;
        }

        public void missingBlockEntity() {
            if (enabled) missingBlockEntities++;
        }

        public void wrongBlockEntity() {
            if (enabled) wrongBlockEntities++;
        }

        public void cookDevice() {
            if (enabled) cookDevices++;
        }

        public void inactiveCookDevice() {
            if (enabled) inactiveCookDevices++;
        }

        public void actionableDevice() {
            if (enabled) actionableDevices++;
        }

        public void rejectedClaim() {
            if (enabled) rejectedClaims++;
        }

        public void selected(BlockPos walkPos, BlockPos workPos) {
            if (enabled) {
                selected = true;
                selectedWalkPos = walkPos.immutable();
                selectedWorkPos = workPos.immutable();
            }
        }

        public void recipeManager(boolean ready, int plans, boolean hasHub,
                                  int inputItems, int outputItems) {
            if (!enabled) return;
            this.recipeManagerReady = ready;
            this.recipePlans = plans;
            this.culinaryHub = hasHub;
            this.inputItems = inputItems;
            this.outputItems = outputItems;
        }

        @Override
        public void close() {
            if (!enabled) return;
            MaidsoulKitchen.LOGGER.info(
                    "Cook scan task={} maid={} manager_ready={} plans={} hub={} input_items={} output_items={} "
                            + "visited={} walk_restrict_rejected={} adjacent_in_bounds={} "
                            + "device_restrict_rejected={} owner_range_rejected={} unloaded_rejected={} "
                            + "duplicate_rejected={} candidates={} locked={} missing_be={} wrong_be={} "
                            + "cook_devices={} inactive={} actionable={} claim_rejected={} selected={} "
                            + "walk={} work={}",
                    taskUid, maid.getUUID(), recipeManagerReady, recipePlans, culinaryHub,
                    inputItems, outputItems, visitedWalkNodes, walkRestrictionRejected,
                    adjacentInBounds, deviceRestrictionRejected, ownerRangeRejected,
                    unloadedRejected, duplicateRejected, deviceCandidates, lockedDevices,
                    missingBlockEntities, wrongBlockEntities, cookDevices, inactiveCookDevices,
                    actionableDevices, rejectedClaims, selected, selectedWalkPos, selectedWorkPos
            );
        }
    }
}
