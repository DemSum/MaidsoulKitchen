package com.github.wallev.maidsoulkitchen.client.gui.entity.maid.cook;

import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.TouhouImageButton;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.TouhouStateSwitchButton;
import com.github.wallev.maidsoulkitchen.client.gui.entity.maid.MaidTaskConfigGui;
import com.github.wallev.maidsoulkitchen.client.gui.widget.button.TaskInfoButton;
import com.github.wallev.maidsoulkitchen.client.gui.widget.button.TypeButton;
import com.github.wallev.maidsoulkitchen.client.gui.widget.button.Zone;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.SteamerRecipeFilterContainer;
import com.github.wallev.maidsoulkitchen.network.NetworkHandler;
import com.github.wallev.maidsoulkitchen.network.message.SetSteamerFilterC2SPackage;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.RecipeOption;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Recipe whitelist/blacklist UI, natively ported from Public commit de47e15. */
public final class SteamerRecipeFilterGui extends MaidTaskConfigGui<SteamerRecipeFilterContainer> {
    private static final ResourceLocation COOK_GUIDE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("maidsoulkitchen", "textures/gui/cook_guide.png");
    private static final int GRID_COLUMNS = 7;
    private static final int GRID_ROWS = 4;
    private static final int RECIPES_PER_PAGE = GRID_COLUMNS * GRID_ROWS;
    private static final int RECIPE_BUTTON_SIZE = 20;
    private static final int RECIPE_BUTTON_SPACING = 2;
    private static final Zone TASK_DISPLAY = new Zone(6, 20, 70, 20);
    private static final Zone TYPE_DISPLAY = new Zone(-4, 22, 18, 18);
    private static final Zone SEARCH_BUTTON_DISPLAY = new Zone(-25, 22, 18, 18);
    private static final Zone SEARCH_TEXT_DISPLAY = new Zone(-25, 22, 41, 18);
    private static final Zone RESULT_DISPLAY = new Zone(6, 44, 152, 86);
    private static final Zone SCROLL_DISPLAY = new Zone(161, 44, 9, 86);

    private final List<RecipeButton> recipeButtons = new ArrayList<>();
    private List<RecipeOption> recipes = List.of();
    private RecipeFilterData filterData = RecipeFilterData.DEFAULT;
    private Button previousPageButton;
    private Button nextPageButton;
    private EditBox searchBox;
    private int page;

    public SteamerRecipeFilterGui(
            SteamerRecipeFilterContainer menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, Component.translatable("gui.maidsoulkitchen.cook_setting_screen.title"));
    }

    @Override
    protected void initAdditionData() {
        super.initAdditionData();
        recipes = menu.getRecipeOptions();
        if (searchBox != null && searchBox.isVisible() && !searchBox.getValue().isBlank()) {
            String search = searchBox.getValue().toLowerCase(Locale.ROOT);
            recipes = recipes.stream()
                    .filter(recipe -> recipe.result().getHoverName().getString()
                            .toLowerCase(Locale.ROOT).contains(search))
                    .toList();
        }
        filterData = menu.getFilterData();
        recipeButtons.clear();
        page = 0;
    }

    @Override
    protected void initAdditionWidgets() {
        super.initAdditionWidgets();
        addTaskInfoButton();
        addSearchTextBox();
        addSearchButton();
        addModeButton();
        addScrollButtons();
        rebuildRecipeButtons();
    }

    @Override
    protected void renderAddition(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderSearchText(graphics, mouseX, mouseY, partialTick);
        renderSearchButton(graphics);
        drawModeSeparator(graphics);
        drawScrollBar(graphics);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        for (RecipeButton button : recipeButtons) {
            if (button.isHovered()) {
                button.renderRecipeTooltip(graphics, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        boolean inside = mouseX >= visualZone.startX()
                && mouseY >= visualZone.startY()
                && mouseX < visualZone.startX() + visualZone.width()
                && mouseY < visualZone.startY() + visualZone.height();
        if (inside && deltaY != 0.0) {
            changePage(deltaY > 0.0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(searchBox);
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox == null || !searchBox.isFocused()) {
            return super.charTyped(codePoint, modifiers);
        }
        String before = searchBox.getValue();
        if (!searchBox.charTyped(codePoint, modifiers)) {
            return false;
        }
        refreshAfterSearchChange(before);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox == null || !searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        String before = searchBox.getValue();
        if (!searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        refreshAfterSearchChange(before);
        return true;
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (searchBox == null) {
            super.insertText(text, overwrite);
            return;
        }
        String before = searchBox.getValue();
        if (overwrite) {
            searchBox.setValue(text);
        } else {
            searchBox.insertText(text);
        }
        refreshAfterSearchChange(before);
    }

    private void refreshAfterSearchChange(String before) {
        if (!Objects.equals(before, searchBox.getValue())) {
            page = 0;
            init();
        }
    }

    private void addTaskInfoButton() {
        int x = visualZone.startX() + TASK_DISPLAY.startX();
        int y = visualZone.startY() + TASK_DISPLAY.startY();
        addRenderableWidget(new TaskInfoButton(x, y, TASK_DISPLAY.width(), TASK_DISPLAY.height(), task));
    }

    private void addModeButton() {
        int x = width - leftPos - (-TYPE_DISPLAY.startX()) - TYPE_DISPLAY.width() - 1;
        int y = visualZone.startY() + TYPE_DISPLAY.startY();
        boolean whitelist = filterData.mode() == RecipeFilterData.Mode.WHITELIST;
        addRenderableWidget(new TypeButton(x, y, TYPE_DISPLAY.width(), TYPE_DISPLAY.height(), whitelist) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                toggleMode();
                toggleState();
            }
        });
    }

    private void addSearchTextBox() {
        int x = width - leftPos - (-SEARCH_TEXT_DISPLAY.startX()) - SEARCH_TEXT_DISPLAY.width() - 1;
        int y = visualZone.startY() + SEARCH_TEXT_DISPLAY.startY();
        String cachedText = searchBox == null ? "" : searchBox.getValue();
        boolean visible = searchBox != null && searchBox.isVisible();
        boolean focused = searchBox != null && searchBox.isFocused();

        searchBox = new EditBox(font, x, y, SEARCH_TEXT_DISPLAY.width(), SEARCH_TEXT_DISPLAY.height(), Component.empty()) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (isVisible()) {
                    graphics.blit(COOK_GUIDE_TEXTURE, x - SEARCH_BUTTON_DISPLAY.width(), y,
                            40, 232, 59, 18);
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                }
            }

            @Override
            public int getY() {
                return super.getY() + 5;
            }

            @Override
            public int getX() {
                return super.getX() + 3;
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                return visible && mouseX >= x && mouseX < x + width
                        && mouseY >= y && mouseY < y + height;
            }
        };
        searchBox.setVisible(visible);
        searchBox.setFocused(focused);
        searchBox.setValue(cachedText);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xF3EFE0);
        addWidget(searchBox);
    }

    private void addSearchButton() {
        int x = width - leftPos - (-SEARCH_BUTTON_DISPLAY.startX()) - SEARCH_BUTTON_DISPLAY.width() - 1;
        int y = visualZone.startY() + SEARCH_BUTTON_DISPLAY.startY();
        if (searchBox.isVisible()) {
            x -= SEARCH_TEXT_DISPLAY.width();
        }
        int initialX = x;
        addRenderableWidget(new StateSwitchingButton(
                initialX, y, SEARCH_BUTTON_DISPLAY.width(), SEARCH_BUTTON_DISPLAY.height(), searchBox.isVisible()) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            }

            @Override
            public void onClick(double mouseX, double mouseY) {
                isStateTriggered = !isStateTriggered;
                if (isStateTriggered) {
                    setX(initialX - SEARCH_TEXT_DISPLAY.width());
                    searchBox.setVisible(true);
                    searchBox.setFocused(true);
                    searchBox.moveCursorToEnd(false);
                } else {
                    setX(initialX);
                    searchBox.setVisible(false);
                    searchBox.setFocused(false);
                    searchBox.setValue("");
                }
                page = 0;
                init();
            }
        });
    }

    private void addScrollButtons() {
        int x = visualZone.startX() + SCROLL_DISPLAY.startX();
        int y = visualZone.startY() + SCROLL_DISPLAY.startY();
        previousPageButton = addRenderableWidget(new TouhouImageButton(
                x, y, 9, 7, 199, 74, 14, COOK_GUIDE_TEXTURE, button -> changePage(-1)
        ));
        nextPageButton = addRenderableWidget(new TouhouImageButton(
                x, y + 79, 9, 7, 208, 74, 14, COOK_GUIDE_TEXTURE, button -> changePage(1)
        ));
    }

    private void toggleMode() {
        filterData = filterData.withMode(filterData.mode().next());
        syncFilter();
        rebuildRecipeButtons();
    }

    private void toggleRecipe(RecipeOption recipe) {
        filterData = filterData.toggle(recipe.id());
        syncFilter();
        rebuildRecipeButtons();
    }

    private void syncFilter() {
        int maidId = menu.getMaidEntityId();
        if (maidId >= 0) {
            NetworkHandler.sendToServer(new SetSteamerFilterC2SPackage(maidId, filterData));
        }
    }

    private void changePage(int change) {
        int changed = Mth.clamp(page + change, 0, maxPage());
        if (changed != page) {
            page = changed;
            rebuildRecipeButtons();
        }
    }

    private int maxPage() {
        return Math.max(0, (recipes.size() - 1) / RECIPES_PER_PAGE);
    }

    private void rebuildRecipeButtons() {
        for (RecipeButton button : recipeButtons) {
            removeWidget(button);
        }
        recipeButtons.clear();

        page = Math.min(page, maxPage());
        int start = page * RECIPES_PER_PAGE;
        int end = Math.min(start + RECIPES_PER_PAGE, recipes.size());
        int gridLeft = visualZone.startX() + RESULT_DISPLAY.startX();
        int gridTop = visualZone.startY() + RESULT_DISPLAY.startY();
        for (int index = start; index < end; index++) {
            RecipeOption recipe = recipes.get(index);
            int localIndex = index - start;
            int x = gridLeft + (localIndex % GRID_COLUMNS) * (RECIPE_BUTTON_SIZE + RECIPE_BUTTON_SPACING);
            int y = gridTop + (localIndex / GRID_COLUMNS) * (RECIPE_BUTTON_SIZE + RECIPE_BUTTON_SPACING);
            RecipeButton button = new RecipeButton(x, y, recipe, filterData, () -> toggleRecipe(recipe));
            recipeButtons.add(addRenderableWidget(button));
        }

        if (previousPageButton != null) {
            previousPageButton.active = page > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = page < maxPage();
        }
    }

    private void drawScrollBar(GuiGraphics graphics) {
        int x = visualZone.startX() + SCROLL_DISPLAY.startX();
        int y = visualZone.startY() + SCROLL_DISPLAY.startY();
        graphics.blit(COOK_GUIDE_TEXTURE, x, y + 8, 189, 64, 9, 70);
        if (maxPage() == 0) {
            graphics.blit(COOK_GUIDE_TEXTURE, x + 1, y + 9, 206, 64, 7, 9);
            return;
        }
        int indicatorOffset = Math.round(59.0F * page / maxPage());
        graphics.blit(COOK_GUIDE_TEXTURE, x + 1, y + 9 + indicatorOffset, 199, 64, 7, 9);
    }

    private void renderSearchText(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (searchBox == null) {
            return;
        }
        int x = width - leftPos - (-SEARCH_TEXT_DISPLAY.startX()) - SEARCH_TEXT_DISPLAY.width() - 1;
        int y = visualZone.startY() + SEARCH_TEXT_DISPLAY.startY();
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        if (searchBox.isVisible() && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            graphics.drawString(font,
                    Component.translatable("gui.maidsoulkitchen.search").withStyle(ChatFormatting.ITALIC),
                    x + 3, y + 5, 0xF5F5F5);
        }
    }

    private void renderSearchButton(GuiGraphics graphics) {
        if (searchBox == null) {
            return;
        }
        int x = width - leftPos - (-SEARCH_BUTTON_DISPLAY.startX()) - SEARCH_BUTTON_DISPLAY.width() - 1;
        int y = visualZone.startY() + SEARCH_BUTTON_DISPLAY.startY();
        if (searchBox.isVisible()) {
            x -= SEARCH_TEXT_DISPLAY.width();
        } else {
            graphics.blit(COOK_GUIDE_TEXTURE, x, y, 0, 232, 18, 18);
        }
        graphics.blit(COOK_GUIDE_TEXTURE, x + 1, y + 1, 0, 181, 16, 16);
    }

    private void drawModeSeparator(GuiGraphics graphics) {
        int x = width - leftPos - (-TYPE_DISPLAY.startX()) - TYPE_DISPLAY.width() - 2;
        int y = visualZone.startY() + TYPE_DISPLAY.startY();
        graphics.fill(x - 1, y, x, y + TYPE_DISPLAY.width(), 0xFF000000);
    }

    private static final class RecipeButton extends TouhouStateSwitchButton {
        private final RecipeOption recipe;
        private final RecipeFilterData.Mode mode;
        private final boolean allowed;
        private final Runnable pressAction;

        private RecipeButton(
                int x,
                int y,
                RecipeOption recipe,
                RecipeFilterData filterData,
                Runnable pressAction
        ) {
            super(x, y, RECIPE_BUTTON_SIZE, RECIPE_BUTTON_SIZE, filterData.contains(recipe.id()));
            initTextureValues(179, 25, 22, 0, COOK_GUIDE_TEXTURE);
            setMessage(recipe.result().getHoverName());
            this.recipe = recipe;
            this.mode = filterData.mode();
            this.allowed = filterData.allows(recipe.id());
            this.pressAction = pressAction;
        }

        private void renderRecipeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
            Minecraft minecraft = Minecraft.getInstance();
            ItemStack result = recipe.result();
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, result));
            tooltip.add(CommonComponents.EMPTY);
            tooltip.add(Component.translatable("gui.maidsoulkitchen.btn.cook_guide.warn.now_type")
                    .append(Component.translatable("gui.maidsoulkitchen.btn.cook_guide.type."
                            + mode.name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("gui.maidsoulkitchen.btn.cook_guide.can_cook")
                    .append(Component.translatable("gui.maidsoulkitchen.btn.cook_guide.can_cook." + allowed))
                    .withStyle(allowed ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
            if (minecraft.options.advancedItemTooltips) {
                tooltip.add(Component.literal("RecipeId: " + recipe.id()).withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.renderComponentTooltip(minecraft.font, tooltip, mouseX, mouseY, result);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pressAction.run();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            ItemStack result = recipe.result();
            graphics.renderItem(result, getX() + 2, getY() + 2);
            int shadow = mode == RecipeFilterData.Mode.WHITELIST ? 0x50F9F9F9 : 0x50000010;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, shadow);
        }
    }
}
