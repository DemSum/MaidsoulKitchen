package com.github.wallev.maidsoulkitchen.client.gui.widget.button;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Source: 58ec08ec TypeTaskButton (MIT), including full device descriptions.
 * TLM 1.5.3 exposes descriptions with an EntityMaid argument. Permission and its tooltip
 * belong to TLM's native task picker for TaskCook, not the internal device chooser. */
public class TypeTaskButton extends NormalTooltipButton {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, "textures/gui/cook_guide.png");

    private final ItemStack icon;

    public TypeTaskButton(int pX, int pY, int pWidth, int pHeight, ICookTask<?, ?> cookTask, EntityMaid maid, OnPress pOnPress) {
        super(pX, pY, pWidth, pHeight, cookTask.getName(), getDesc(cookTask, maid), pOnPress);
        this.icon = cookTask.getIcon();
    }

    @Override
    protected void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        Minecraft mc = Minecraft.getInstance();
        pGuiGraphics.blit(TEXTURE, this.getX(), this.getY(), 179, 2, this.width, this.height);
        pGuiGraphics.renderItem(icon, this.getX() + 2, this.getY() + 2);
        List<FormattedCharSequence> splitTexts = mc.font.split(this.getMessage(), 42);
        if (!splitTexts.isEmpty()) {
            pGuiGraphics.drawString(mc.font, splitTexts.get(0), this.getX() + 22, this.getY() + 5, 0xffffff, false);
        }
    }

    protected void renderScrollingTaskString(GuiGraphics pGuiGraphics, Font pFont, int x, int y, int pWidth, int pColor) {
        renderScrollingString(pGuiGraphics, pFont, this.getMessage(), x, y, x + pWidth, y + pFont.lineHeight, pColor);
    }

    private static List<Component> getDesc(ICookTask<?, ?> task, EntityMaid maid) {
        List<Component> components = new ArrayList<>();
        components.add(task.getIcon().getHoverName());
        components.addAll(ParseI18n.keysToTrans(task.getDescription(maid), ChatFormatting.GRAY));

        String typeString = task.getRecipeType().toString();
        components.add(CommonComponents.SPACE);
        components.add(Component.translatable("gui.maidsoulkitchen.widget.cook_guide.task.recipe_type", typeString).withStyle(ChatFormatting.DARK_GRAY));

        return components;
    }
}
