package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import net.minecraft.core.BlockPos;

/** Stable work-area range used by all remote Culinary Hub inventory access. */
public final class CulinaryHubRange {
    private CulinaryHubRange() {
    }

    public static boolean contains(EntityMaid maid, BlockPos storagePos) {
        BlockPos workCenter = maid.hasRestriction()
                ? maid.getRestrictCenter()
                : maid.blockPosition().below();
        double range = maid.getRestrictRadius() * ItemCulinaryHub.WORK_RANGE;
        return contains(
                workCenter.getX(), workCenter.getY(), workCenter.getZ(),
                storagePos.getX(), storagePos.getY(), storagePos.getZ(),
                range
        );
    }

    static boolean contains(
            int centerX,
            int centerY,
            int centerZ,
            int storageX,
            int storageY,
            int storageZ,
            double range
    ) {
        if (range <= 0.0) {
            return false;
        }
        long deltaX = centerX - storageX;
        long deltaY = centerY - storageY;
        long deltaZ = centerZ - storageZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ < range * range;
    }
}
