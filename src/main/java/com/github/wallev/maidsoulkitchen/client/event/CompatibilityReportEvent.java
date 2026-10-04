package com.github.wallev.maidsoulkitchen.client.event;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.CompatibilityRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reports disabled compatibility tasks once after the local player joins. */
@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, value = Dist.CLIENT)
public final class CompatibilityReportEvent {
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private CompatibilityReportEvent() {
    }

    @SubscribeEvent
    public static void reportFailures(PlayerEvent.PlayerLoggedInEvent event) {
        List<CompatibilityRegistry.Failure> failures = CompatibilityRegistry.failures();
        if (failures.isEmpty() || !REPORTED.compareAndSet(false, true)) {
            return;
        }

        MutableComponent message = Component.translatable("message.maidsoulkitchen.warning.title")
                .withStyle(ChatFormatting.DARK_RED)
                .append(CommonComponents.NEW_LINE)
                .append(Component.translatable("message.maidsoulkitchen.warning.compat_failed"));
        for (CompatibilityRegistry.Failure failure : failures) {
            message.append(CommonComponents.NEW_LINE)
                    .append(Component.literal(">- " + failure.taskUid() + " (" + failure.modId() + ")")
                            .withStyle(ChatFormatting.RED));
        }
        message.append(CommonComponents.NEW_LINE)
                .append(Component.translatable("message.maidsoulkitchen.warning.feedbacked")
                        .withStyle(ChatFormatting.GRAY));
        event.getEntity().sendSystemMessage(message);
    }
}
