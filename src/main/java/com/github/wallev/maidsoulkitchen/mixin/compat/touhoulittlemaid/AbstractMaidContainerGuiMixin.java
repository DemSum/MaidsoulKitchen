package com.github.wallev.maidsoulkitchen.mixin.compat.touhoulittlemaid;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.github.wallev.maidsoulkitchen.client.event.RenderSlotHighEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractMaidContainerGui.class)
public abstract class AbstractMaidContainerGuiMixin<T extends AbstractMaidContainer> extends AbstractContainerScreen<T> {

    @Shadow
    @Final
    protected EntityMaid maid;

    public AbstractMaidContainerGuiMixin(T pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    @Inject(at = @At("TAIL"), method = "renderLabels", remap = true)
    private void tlmk$renderHubSlotHighlight(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        RenderSlotHighEvent.renderSlotHighlight(
                (AbstractMaidContainerGui<?>) (Object) this,
                graphics,
                this.leftPos,
                this.topPos
        );
    }
}
