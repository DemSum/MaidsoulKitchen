package com.github.wallev.maidsoulkitchen.util;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.vhelper.client.chat.VComponent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Cooking feedback restored from the 1.20.1 implementation. */
public final class BubbleUtil {
    private static final int MAX_OVERVIEW_ENTRIES = 8;
    private static final String COLLECT_INGREDIENTS = "chat_bubble.maidsoulkitchen.cook.collect_ingredients";
    private static final String NO_INGREDIENT = "chat_bubble.maidsoulkitchen.cook.no_ingredient_cook";

    private BubbleUtil() {
    }

    /** Source: protected local WIP BubbleUtil.Feedback (6060fad0). Upstream lacked replaceable
     * failure messages. Preserve this TLM 1.5.3 presentation boundary verbatim: it stores only
     * bubble text/time, never recipes, inventory or execution status. */
    public static final class Feedback {
        private static final int REPEAT_TICKS = 200;
        private long bubbleId = -1;
        private long nextRepeat;
        private Component currentText;

        public long show(EntityMaid maid, String key) {
            return show(maid, VComponent.translatable(key));
        }

        public long noIngredient(EntityMaid maid) {
            return show(maid, NO_INGREDIENT);
        }

        public long show(EntityMaid maid, Component message) {
            var manager = maid.getChatBubbleManager();
            var active = manager.getChatBubble(bubbleId);
            long now = maid.level().getGameTime();
            if (active instanceof TextChatBubbleData text) {
                if (!message.equals(currentText)) {
                    text.setText(message);
                    manager.forceUpdateChatBubble();
                    currentText = message.copy();
                    nextRepeat = now + REPEAT_TICKS;
                }
                return bubbleId;
            }
            if (message.equals(currentText) && now < nextRepeat) return bubbleId;
            bubbleId = manager.addChatBubble(TextChatBubbleData.type2(message));
            currentText = message.copy();
            nextRepeat = now + REPEAT_TICKS;
            return bubbleId;
        }

        public void clear(EntityMaid maid) {
            if (maid.getChatBubbleManager().getChatBubble(bubbleId) != null) {
                maid.getChatBubbleManager().removeChatBubble(bubbleId);
            }
            bubbleId = -1;
            currentText = null;
            nextRepeat = 0;
        }
    }

    public static long collectIngredients(EntityMaid maid, long previousBubbleId) {
        return maid.getChatBubbleManager().addTextChatBubbleIfTimeout(COLLECT_INGREDIENTS, previousBubbleId);
    }

    public static long noIngredient(EntityMaid maid, long previousBubbleId) {
        return maid.getChatBubbleManager().addTextChatBubbleIfTimeout(NO_INGREDIENT, previousBubbleId);
    }

    public static long availableFoods(EntityMaid maid, List<ItemStack> plannedResults, long previousBubbleId) {
        if (plannedResults.isEmpty()
                || previousBubbleId >= 0 && maid.getChatBubbleManager().getChatBubble(previousBubbleId) != null) {
            return previousBubbleId;
        }

        List<ItemStack> combined = combineResults(plannedResults);
        MutableComponent foods = VComponent.empty();
        int shown = Math.min(combined.size(), MAX_OVERVIEW_ENTRIES);
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                foods.append(VComponent.translatable("chat_bubble.maidsoulkitchen.cook.food_separator"));
            }
            ItemStack result = combined.get(index);
            foods.append(VComponent.literal(result.getHoverName().getString() + " " + result.getCount()))
                    .append(VComponent.translatable("chat_bubble.maidsoulkitchen.cook.food_amount"));
        }
        if (combined.size() > shown) {
            foods.append(VComponent.translatable("chat_bubble.maidsoulkitchen.cook.food_separator"))
                    .append(VComponent.translatable(
                            "chat_bubble.maidsoulkitchen.cook.and_more", combined.size() - shown));
        }

        MutableComponent text = VComponent.translatable(
                "chat_bubble.maidsoulkitchen.cook.can_cook_these_food").append(foods);
        return maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type2(text));
    }

    private static List<ItemStack> combineResults(List<ItemStack> plannedResults) {
        List<ItemStack> combined = new ArrayList<>();
        for (ItemStack planned : plannedResults) {
            if (planned.isEmpty()) {
                continue;
            }
            ItemStack existing = combined.stream()
                    .filter(result -> ItemStack.isSameItemSameComponents(result, planned))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                combined.add(planned.copy());
            } else {
                existing.grow(planned.getCount());
            }
        }
        return combined;
    }

    public static void makeFood(EntityMaid maid, ItemStack food) {
        if (food.isEmpty()) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        String ownerName = owner == null
                ? VComponent.translatable("chat_bubble.maidsoulkitchen.cook.master").getString()
                : owner.getDisplayName().getString();
        String foodName = food.getHoverName().getString();
        int count = food.getCount();
        MutableComponent text = switch (maid.getRandom().nextInt(5)) {
            case 0 -> VComponent.translatable(
                    "chat_bubble.maidsoulkitchen.cook.make_food.0", ownerName, count, foodName);
            case 1 -> VComponent.translatable(
                    "chat_bubble.maidsoulkitchen.cook.make_food.1", count, foodName);
            case 2 -> VComponent.translatable(
                    "chat_bubble.maidsoulkitchen.cook.make_food.2", count, foodName);
            case 3 -> VComponent.translatable(
                    "chat_bubble.maidsoulkitchen.cook.make_food.3", foodName, count);
            default -> VComponent.translatable(
                    "chat_bubble.maidsoulkitchen.cook.make_food.4", count, foodName);
        };
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type2(text));
    }
}
