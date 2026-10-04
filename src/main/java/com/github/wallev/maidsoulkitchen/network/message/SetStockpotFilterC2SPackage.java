package com.github.wallev.maidsoulkitchen.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.StockpotRecipeFilterContainer;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterRules;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.StockpotAdapter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SetStockpotFilterC2SPackage(int maidId, StockpotTaskData settings) implements CustomPacketPayload {
    private static final int MAX_RECIPE_IDS = 4096;

    public static final Type<SetStockpotFilterC2SPackage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, "set_stockpot_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetStockpotFilterC2SPackage> STREAM_CODEC =
            StreamCodec.ofMember(SetStockpotFilterC2SPackage::encode, SetStockpotFilterC2SPackage::decode);

    private static SetStockpotFilterC2SPackage decode(RegistryFriendlyByteBuf buffer) {
        int maidId = buffer.readVarInt();
        RecipeFilterData.Mode mode = buffer.readEnum(RecipeFilterData.Mode.class);
        List<ResourceLocation> whitelist = readIds(buffer);
        List<ResourceLocation> blacklist = readIds(buffer);
        return new SetStockpotFilterC2SPackage(
                maidId, new StockpotTaskData(new RecipeFilterData(mode, whitelist, blacklist), buffer.readBoolean()));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        RecipeFilterData filter = settings.filter();
        buffer.writeVarInt(maidId);
        buffer.writeEnum(filter.mode());
        writeIds(buffer, filter.whitelist());
        writeIds(buffer, filter.blacklist());
        buffer.writeBoolean(settings.allowFlexRecipes());
    }

    private static List<ResourceLocation> readIds(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_RECIPE_IDS) {
            throw new IllegalArgumentException("Invalid stockpot recipe filter size: " + size);
        }
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ids.add(ResourceLocation.STREAM_CODEC.decode(buffer));
        }
        return ids;
    }

    private static void writeIds(RegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
        if (ids.size() > MAX_RECIPE_IDS) {
            throw new IllegalArgumentException("Stockpot recipe filter is too large: " + ids.size());
        }
        buffer.writeVarInt(ids.size());
        for (ResourceLocation id : ids) {
            ResourceLocation.STREAM_CODEC.encode(buffer, id);
        }
    }

    public static void handle(SetStockpotFilterC2SPackage payload, IPayloadContext context) {
        if (!context.flow().isServerbound()) {
            return;
        }
        context.enqueueWork(() -> applyOnServer(payload, context.player()));
    }

    private static void applyOnServer(SetStockpotFilterC2SPackage payload, Player player) {
        Entity entity = player.level().getEntity(payload.maidId());
        if (!(entity instanceof EntityMaid maid)
                || !maid.isOwnedBy(player)
                || !(player.containerMenu instanceof StockpotRecipeFilterContainer menu)
                || menu.getMaidEntityId() != payload.maidId() || !menu.stillValid(player)) {
            return;
        }

        Set<ResourceLocation> knownRecipes = new HashSet<>();
        StockpotAdapter.getRecipeOptions(maid.level(), true)
                .forEach(option -> knownRecipes.addAll(option.recipeIds()));
        RecipeFilterData sanitized = new RecipeFilterData(
                payload.settings().filter().mode(),
                sanitize(payload.settings().filter().whitelist(), knownRecipes),
                sanitize(payload.settings().filter().blacklist(), knownRecipes)
        );
        maid.setAndSyncData(DataRegister.KC_STOCKPOT,
                new StockpotTaskData(sanitized,
                        com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get()));
        if (com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STOCKPOT.uid.equals(maid.getTask().getUid())) {
            CookTargetMemory.getWorkPos(maid).ifPresent(pos -> CookWorkLocks.release(
                    (net.minecraft.server.level.ServerLevel) maid.level(), pos.currentBlockPosition(), maid));
            CookTargetMemory.clear(maid);
        }
    }

    static List<ResourceLocation> sanitize(
            List<ResourceLocation> ids,
            Set<ResourceLocation> knownRecipes
    ) {
        return RecipeFilterRules.sanitize(ids, knownRecipes, ResourceLocation::compareTo);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
