package com.github.wallev.maidsoulkitchen.client.gui.widget.button;

import com.github.tartaricacid.touhoulittlemaid.api.client.gui.ITooltipButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public class NormalTooltipButton extends Button implements ITooltipButton {
    private List<Component> tooltips;

    public NormalTooltipButton(int pX, int pY, int pWidth, int pHeight, Component pMessage, List<Component> tooltips, OnPress pOnPress) {
        super(pX, pY, pWidth, pHeight, pMessage, pOnPress, Supplier::get);
        this.tooltips = tooltips;
    }

    @Override
    public boolean isTooltipHovered() {
        return this.isHovered();
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        graphics.renderComponentTooltip(mc.font, this.tooltips, mouseX, mouseY);
    }

    public List<Component> getTooltips() {
        return tooltips;
    }

    public void setTooltips(List<Component> tooltips) {
        this.tooltips = tooltips;
    }

    /** Custom upstream texture renderers bypass Button's native hover background. */
    protected final void renderHoverHighlight(GuiGraphics graphics) {
        if (active && isHoveredOrFocused())
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x30FFFFFF);
    }
}
