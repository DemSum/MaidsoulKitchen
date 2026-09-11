package com.github.wallev.maidsoulkitchen.client.overlay;

import com.github.wallev.maidsoulkitchen.init.MkItems;
import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * NeoForge 1.21.1 adaptation of the official MSK 1.20.1 Culinary Hub overlay.
 */
public final class CulinaryHubOverlay {
    private CulinaryHubOverlay() {
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) return;
        if (minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        LocalPlayer player = minecraft.player;
        if (player == null || !(minecraft.hitResult instanceof BlockHitResult hitResult)) return;

        ItemStack hub = player.getMainHandItem();
        if (!hub.is(MkItems.CULINARY_HUB.get())) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity == null || ItemCulinaryHub.getBeInv(player.level(), blockEntity) == null) return;

        List<Component> tips = getTips(hub, pos);
        int y = guiGraphics.guiHeight() / 2 + 5;
        for (Component tip : tips) {
            int width = minecraft.font.width(tip);
            guiGraphics.drawString(minecraft.font, tip, (guiGraphics.guiWidth() - width) / 2, y, 0xFFFFFF);
            y += minecraft.font.lineHeight + 1;
        }
    }

    private static List<Component> getTips(ItemStack hub, BlockPos pos) {
        Map<BagType, List<BlockPos>> bindPoses = ItemCulinaryHub.getBindPoses(hub);
        List<Component> boundTypes = new ArrayList<>();
        for (BagType type : BagType.DISPLAY_VALS) {
            if (bindPoses.getOrDefault(type, List.of()).contains(pos)) {
                boundTypes.add(Component.translatable("gui.maidsoulkitchen.culinary_hub.config.bind_mode." + type.translateKey));
            }
        }

        if (!boundTypes.isEmpty()) {
            Component names = Component.empty().append(boundTypes.getFirst());
            for (int i = 1; i < boundTypes.size(); i++) {
                names = Component.empty().append(names).append(", ").append(boundTypes.get(i));
            }
            return List.of(Component.translatable("gui.maidsoulkitchen.culinary_hub.overlay.bound", names)
                    .withStyle(ChatFormatting.GRAY));
        }

        List<Component> tips = new ArrayList<>();
        tips.add(Component.translatable("gui.maidsoulkitchen.culinary_hub.overlay.can_bind")
                .withStyle(ChatFormatting.GRAY));
        String bindMode = ItemCulinaryHub.getBindMode(hub);
        BagType selected = Arrays.stream(BagType.DISPLAY_VALS)
                .filter(type -> type.name.equals(bindMode))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            tips.add(Component.translatable("gui.maidsoulkitchen.culinary_hub.overlay.select_mode")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            int size = ItemCulinaryHub.getBindModePoses(hub, bindMode).size();
            Component selectedName = Component.translatable(
                    "gui.maidsoulkitchen.culinary_hub.config.bind_mode." + selected.translateKey);
            tips.add(Component.translatable("gui.maidsoulkitchen.culinary_hub.overlay.current", selectedName, size,
                            ItemCulinaryHub.BIND_SIZE)
                    .withStyle(ChatFormatting.GRAY));
        }
        return tips;
    }
}
