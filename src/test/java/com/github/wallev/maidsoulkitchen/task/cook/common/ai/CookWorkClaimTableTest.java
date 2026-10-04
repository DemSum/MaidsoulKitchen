package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookWorkClaimTableTest {
    @Test
    void enforcesOwnershipUntilExpiryAndAllowsRenewal() {
        CookWorkClaimTable<String> claims = new CookWorkClaimTable<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(claims.tryClaim("barrel", first, 100, 20));
        assertFalse(claims.tryClaim("barrel", second, 101, 20));
        assertTrue(claims.renew("barrel", first, 110, 20));
        assertFalse(claims.isAvailable("barrel", second, 129));
        assertTrue(claims.isAvailable("barrel", second, 130));
    }

    @Test
    void taskCleanupOnlyReleasesTheOwningMaidClaims() {
        CookWorkClaimTable<String> claims = new CookWorkClaimTable<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        claims.tryClaim("first", first, 0, 100);
        claims.tryClaim("second", second, 0, 100);

        claims.releaseAll(first);

        assertEquals(1, claims.size());
        assertTrue(claims.isAvailable("first", second, 1));
        assertFalse(claims.isAvailable("second", first, 1));
    }
}
