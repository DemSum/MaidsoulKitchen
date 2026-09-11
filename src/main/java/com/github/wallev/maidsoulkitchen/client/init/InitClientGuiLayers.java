package com.github.wallev.maidsoulkitchen.client.init;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.client.overlay.CulinaryHubOverlay;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Registers official MSK client UI features adapted to NeoForge 1.21.1. */
@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class InitClientGuiLayers {
    private static final ResourceLocation CULINARY_HUB_TIPS =
            ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, "culinary_hub_tips");

    private InitClientGuiLayers() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, CULINARY_HUB_TIPS, CulinaryHubOverlay::render);
    }
}
