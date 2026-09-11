package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binary-compatible access to the maid inventory available to work tasks.
 *
 * <p>Touhou Little Maid changed the declared return type of
 * {@code EntityMaid#getAvailableBackpackInv()} between 1.1.13 and 1.5.3. A
 * normal Java invocation embeds that return type in the bytecode descriptor,
 * so a Maidsoul Kitchen jar compiled against either version cannot invoke the
 * other version directly. Looking the public method up once by name avoids
 * linking against either wrapper implementation.</p>
 */
public final class MaidInventoryCompat {
    private static final String AVAILABLE_INVENTORY_METHOD = "getAvailableBackpackInv";
    private static final AtomicBoolean FALLBACK_WARNING_LOGGED = new AtomicBoolean();
    private static final Method AVAILABLE_INVENTORY = findAvailableInventoryMethod();

    private MaidInventoryCompat() {
    }

    public static IItemHandlerModifiable availableInventory(EntityMaid maid) {
        if (AVAILABLE_INVENTORY != null) {
            try {
                Object inventory = AVAILABLE_INVENTORY.invoke(maid);
                if (inventory instanceof IItemHandlerModifiable modifiable) {
                    return modifiable;
                }
                warnFallback("returned an unsupported inventory type", null);
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
                warnFallback("could not be invoked", exception);
            }
        }
        return maid.getMaidInv();
    }

    private static Method findAvailableInventoryMethod() {
        try {
            return EntityMaid.class.getMethod(AVAILABLE_INVENTORY_METHOD);
        } catch (NoSuchMethodException | SecurityException exception) {
            warnFallback("was not found", exception);
            return null;
        }
    }

    private static void warnFallback(String reason, Throwable exception) {
        if (!FALLBACK_WARNING_LOGGED.compareAndSet(false, true)) {
            return;
        }
        String message = "TLM {} {}. Falling back to the maid inventory.";
        if (exception == null) {
            MaidsoulKitchen.LOGGER.warn(message, AVAILABLE_INVENTORY_METHOD, reason);
        } else {
            MaidsoulKitchen.LOGGER.warn(message, AVAILABLE_INVENTORY_METHOD, reason, exception);
        }
    }
}
