package com.github.wallev.maidsoulkitchen.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Optional;

/**
 * Owns the cooking-task memory contract.
 *
 * <p>This is a NeoForge 1.21.1 adaptation of the official MSK 1.20.1
 * {@code MemoryUtil}. It intentionally contains no MaidSoul Brewery logic.</p>
 */
public final class MemoryUtil {
    private MemoryUtil() {
    }

    public static void rememberWorkPos(EntityMaid maid, BlockPos walkPos, BlockPos workPos,
                                       float speed, int closeEnoughDistance) {
        Brain<EntityMaid> brain = maid.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(walkPos, speed, closeEnoughDistance));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(workPos));
        brain.setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(workPos));
        brain.setMemory(MkMemories.WORK_POS.get(), new BlockPosTracker(workPos));
        brain.setMemory(MkMemories.DESTROY_POS.get(), new BlockPosTracker(workPos));
    }

    public static void rememberWalkPos(EntityMaid maid, BlockPos walkPos, BlockPos workPos,
                                       float speed, int closeEnoughDistance) {
        Brain<EntityMaid> brain = maid.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(walkPos, speed, closeEnoughDistance));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(workPos));
        brain.setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(workPos));
    }

    public static Optional<PositionTracker> getWorkPos(EntityMaid maid) {
        return maid.getBrain().getMemory(MkMemories.WORK_POS.get());
    }

    public static void eraseWorkPos(EntityMaid maid) {
        Brain<EntityMaid> brain = maid.getBrain();
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(InitEntities.TARGET_POS.get());
        brain.eraseMemory(MkMemories.WORK_POS.get());
        brain.eraseMemory(MkMemories.DESTROY_POS.get());
    }
}
