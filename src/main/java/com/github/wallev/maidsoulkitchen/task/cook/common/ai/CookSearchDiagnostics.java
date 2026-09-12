package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import net.minecraft.resources.ResourceLocation;

/** Opt-in, one-line-per-scan diagnostics for development reproductions. */
public final class CookSearchDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("maidsoulkitchen.debugCookingChain");
    private static final Scan DISABLED = new Scan(null, null, false);

    private CookSearchDiagnostics() {
    }

    public static Scan begin(EntityMaid maid, ResourceLocation taskUid) {
        return ENABLED ? new Scan(maid, taskUid, true) : DISABLED;
    }

    public static final class Scan implements AutoCloseable {
        private final EntityMaid maid;
        private final ResourceLocation taskUid;
        private final boolean enabled;
        private int visitedWalkNodes;
        private int deviceCandidates;
        private int actionableDevices;
        private int rejectedClaims;
        private boolean selected;

        private Scan(EntityMaid maid, ResourceLocation taskUid, boolean enabled) {
            this.maid = maid;
            this.taskUid = taskUid;
            this.enabled = enabled;
        }

        public void visitedWalkNode() {
            if (enabled) visitedWalkNodes++;
        }

        public void deviceCandidate() {
            if (enabled) deviceCandidates++;
        }

        public void actionableDevice() {
            if (enabled) actionableDevices++;
        }

        public void rejectedClaim() {
            if (enabled) rejectedClaims++;
        }

        public void selected() {
            if (enabled) selected = true;
        }

        @Override
        public void close() {
            if (!enabled) return;
            MaidsoulKitchen.LOGGER.info(
                    "Cook scan task={} maid={} visited={} candidates={} actionable={} lock_rejected={} selected={}",
                    taskUid, maid.getUUID(), visitedWalkNodes, deviceCandidates,
                    actionableDevices, rejectedClaims, selected
            );
        }
    }
}
