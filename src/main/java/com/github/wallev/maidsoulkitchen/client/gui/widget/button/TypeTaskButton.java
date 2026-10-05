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
 * TLM 1.5.3 exposes descriptions and favour predicates with an EntityMaid argument; reuse its
 * native task-tooltip format in the device chooser. Only favour gates selection, never work state. */
public class TypeTaskButton extends NormalTooltipButton {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, "textures/gui/cook_guide.png");

    private final ICookTask<?, ?> cookTask;
    private final ItemStack icon;
    private final EntityMaid maid;

    public TypeTaskButton(int pX, int pY, int pWidth, int pHeight, ICookTask<?, ?> cookTask, EntityMaid maid, OnPress pOnPress) {
        super(pX, pY, pWidth, pHeight, cookTask.getName(), getDesc(cookTask, maid), pOnPress);
        this.cookTask = cookTask;
        this.icon = cookTask.getIcon();
        this.maid = maid;
        this.active = cookTask.hasEnoughFavor(maid);
    }

    @Override
    public void onPress() {
        this.active = cookTask.hasEnoughFavor(maid);
        if (this.active) super.onPress();
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        // Read the synchronized maid directly; a favour change must not leave a stale tooltip.
        graphics.renderComponentTooltip(mc.font, getDesc(cookTask, maid), mouseX, mouseY);
    }

    @Override
    protected void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.active = cookTask.hasEnoughFavor(maid);
        Minecraft mc = Minecraft.getInstance();
        pGuiGraphics.blit(TEXTURE, this.getX(), this.getY(), 179, 2, this.width, this.height);
        if (!this.active) pGuiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x88000000);
        pGuiGraphics.renderItem(icon, this.getX() + 2, this.getY() + 2);
        List<FormattedCharSequence> splitTexts = mc.font.split(this.getMessage(), 42);
        if (!splitTexts.isEmpty()) {
            pGuiGraphics.drawString(mc.font, splitTexts.get(0), this.getX() + 22, this.getY() + 5, this.active ? 0xffffff : 0xa0a0a0, false);
        }
    }

    protected void renderScrollingTaskString(GuiGraphics pGuiGraphics, Font pFont, int x, int y, int pWidth, int pColor) {
        renderScrollingString(pGuiGraphics, pFont, this.getMessage(), x, y, x + pWidth, y + pFont.lineHeight, pColor);
    }

    private static List<Component> getDesc(ICookTask<?, ?> task, EntityMaid maid) {
        List<Component> components = new ArrayList<>();
        components.add(task.getIcon().getHoverName());
        components.addAll(ParseI18n.keysToTrans(task.getDescription(maid), ChatFormatting.GRAY));

        var favourConditions = task.getEnableConditionDesc(maid).stream()
                .filter(condition -> condition.getFirst().equals("has_enough_favor")).toList();
        if (!favourConditions.isEmpty()) {
            components.add(CommonComponents.SPACE);
            components.add(Component.translatable("task.touhou_little_maid.desc.enable_condition").withStyle(ChatFormatting.GOLD));
            for (var condition : favourConditions) {
                boolean met = condition.getSecond().test(maid);
                components.add(Component.literal(met ? "✓ " : "✗ ").append(Component.translatable(
                        "task." + task.getUid().getNamespace() + "." + task.getUid().getPath()
                                + ".enable_condition." + condition.getFirst()))
                        .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }

        String typeString = task.getRecipeType().toString();
        components.add(CommonComponents.SPACE);
        components.add(Component.translatable("gui.maidsoulkitchen.widget.cook_guide.task.recipe_type", typeString).withStyle(ChatFormatting.DARK_GRAY));

        return components;
    }
}
