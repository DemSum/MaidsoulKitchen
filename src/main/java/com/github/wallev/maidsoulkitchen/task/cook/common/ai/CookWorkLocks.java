package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Short-lived per-device claims that prevent two maids selecting the same appliance. */
public final class CookWorkLocks {
    private static final long CLAIM_TICKS = 600;
    private static final Map<MinecraftServer, Map<Key, Claim>> CLAIMS = new WeakHashMap<>();

    private CookWorkLocks() {
    }

    public static synchronized boolean isAvailable(ServerLevel level, BlockPos pos, EntityMaid maid) {
        Map<Key, Claim> serverClaims = activeClaims(level);
        Claim claim = serverClaims.get(new Key(level.dimension(), pos.immutable()));
        return claim == null || claim.maidId().equals(maid.getUUID());
    }

    public static synchronized boolean tryClaim(ServerLevel level, BlockPos pos, EntityMaid maid) {
        Map<Key, Claim> serverClaims = activeClaims(level);

        Key key = new Key(level.dimension(), pos.immutable());
        Claim claim = serverClaims.get(key);
        if (claim != null && !claim.maidId().equals(maid.getUUID())) {
            return false;
        }
        serverClaims.put(key, new Claim(maid.getUUID(), level.getGameTime() + CLAIM_TICKS));
        return true;
    }

    private static Map<Key, Claim> activeClaims(ServerLevel level) {
        long now = level.getGameTime();
        Map<Key, Claim> serverClaims = CLAIMS.computeIfAbsent(level.getServer(), server -> new HashMap<>());
        serverClaims.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        return serverClaims;
    }

    public static synchronized void release(ServerLevel level, BlockPos pos, EntityMaid maid) {
        Map<Key, Claim> serverClaims = CLAIMS.get(level.getServer());
        if (serverClaims == null) {
            return;
        }
        Key key = new Key(level.dimension(), pos.immutable());
        Claim claim = serverClaims.get(key);
        if (claim != null && claim.maidId().equals(maid.getUUID())) {
            serverClaims.remove(key);
        }
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record Claim(UUID maidId, long expiresAt) {
    }
}
