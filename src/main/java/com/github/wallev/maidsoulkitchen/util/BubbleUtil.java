package com.github.wallev.maidsoulkitchen.util;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.vhelper.client.chat.VComponent;
import net.minecraft.network.chat.MutableComponent;
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
