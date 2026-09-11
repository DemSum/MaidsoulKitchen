package com.github.wallev.maidsoulkitchen.init;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


import java.util.Optional;

public final class MkMemories {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULE_TYPES = DeferredRegister.create(BuiltInRegistries.MEMORY_MODULE_TYPE, MaidsoulKitchen.MOD_ID);
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<PositionTracker>> DESTROY_POS = MEMORY_MODULE_TYPES.register("destroy_pos", () -> new MemoryModuleType<>(Optional.empty()));
    /**
     * The cooking block position. This deliberately differs from WALK_TARGET,
     * which may point at the floor beside the cooking block.
     *
     * <p>Ported from the official MSK 1.20.1 development line.</p>
     */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<PositionTracker>> WORK_POS = MEMORY_MODULE_TYPES.register("work_pos", () -> new MemoryModuleType<>(Optional.empty()));

}
