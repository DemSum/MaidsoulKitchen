package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/** Short-lived per-device claims that prevent two maids selecting the same appliance. */
public final class CookWorkLocks {
    private static final long CLAIM_TICKS = 600;
    private static final Map<MinecraftServer, CookWorkClaimTable<Key>> CLAIMS = new WeakHashMap<>();

    private CookWorkLocks() {
    }

    public static synchronized boolean tryClaim(ServerLevel level, BlockPos pos, EntityMaid maid) {
        long now = level.getGameTime();
        CookWorkClaimTable<Key> serverClaims = CLAIMS.computeIfAbsent(
                level.getServer(), server -> new CookWorkClaimTable<>());
        return serverClaims.tryClaim(
                new Key(level.dimension(), pos.immutable()), maid.getUUID(), now, CLAIM_TICKS);
    }

    public static synchronized boolean isAvailable(ServerLevel level, BlockPos pos, EntityMaid maid) {
        CookWorkClaimTable<Key> serverClaims = CLAIMS.get(level.getServer());
        return serverClaims == null || serverClaims.isAvailable(
                new Key(level.dimension(), pos.immutable()), maid.getUUID(), level.getGameTime());
    }

    public static synchronized boolean renew(ServerLevel level, BlockPos pos, EntityMaid maid) {
        CookWorkClaimTable<Key> serverClaims = CLAIMS.get(level.getServer());
        return serverClaims != null && serverClaims.renew(
                new Key(level.dimension(), pos.immutable()), maid.getUUID(), level.getGameTime(), CLAIM_TICKS);
    }

    public static synchronized void release(ServerLevel level, BlockPos pos, EntityMaid maid) {
        CookWorkClaimTable<Key> serverClaims = CLAIMS.get(level.getServer());
        if (serverClaims != null) {
            serverClaims.release(new Key(level.dimension(), pos.immutable()), maid.getUUID());
        }
    }

    public static synchronized void releaseAll(ServerLevel level, EntityMaid maid) {
        CookWorkClaimTable<Key> serverClaims = CLAIMS.get(level.getServer());
        if (serverClaims != null) serverClaims.releaseAll(maid.getUUID());
    }

    public static synchronized void clear(MinecraftServer server) {
        CLAIMS.remove(server);
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
