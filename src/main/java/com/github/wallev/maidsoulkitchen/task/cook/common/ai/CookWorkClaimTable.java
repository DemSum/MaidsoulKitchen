package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure ownership/expiry state used by the server-scoped cooking device lock wrapper. */
final class CookWorkClaimTable<K> {
    private final Map<K, Claim> claims = new HashMap<>();

    boolean tryClaim(K key, UUID maidId, long now, long duration) {
        purgeExpired(now);
        Claim existing = claims.get(key);
        if (existing != null && !existing.maidId().equals(maidId)) return false;
        claims.put(key, new Claim(maidId, now + duration));
        return true;
    }

    boolean isAvailable(K key, UUID maidId, long now) {
        Claim existing = claims.get(key);
        if (existing == null) return true;
        if (existing.expiresAt() <= now) {
            claims.remove(key);
            return true;
        }
        return existing.maidId().equals(maidId);
    }

    boolean renew(K key, UUID maidId, long now, long duration) {
        Claim existing = claims.get(key);
        if (existing == null || existing.expiresAt() <= now || !existing.maidId().equals(maidId)) {
            if (existing != null && existing.expiresAt() <= now) claims.remove(key);
            return false;
        }
        claims.put(key, new Claim(maidId, now + duration));
        return true;
    }

    void release(K key, UUID maidId) {
        Claim existing = claims.get(key);
        if (existing != null && existing.maidId().equals(maidId)) claims.remove(key);
    }

    void releaseAll(UUID maidId) {
        claims.entrySet().removeIf(entry -> entry.getValue().maidId().equals(maidId));
    }

    int size() {
        return claims.size();
    }

    private void purgeExpired(long now) {
        claims.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private record Claim(UUID maidId, long expiresAt) {
    }
}
