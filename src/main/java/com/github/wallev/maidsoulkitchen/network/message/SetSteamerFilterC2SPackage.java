package com.github.wallev.maidsoulkitchen.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterRules;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerAdapter;
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

public record SetSteamerFilterC2SPackage(int maidId, RecipeFilterData filter) implements CustomPacketPayload {
    private static final int MAX_RECIPE_IDS = 4096;

    public static final Type<SetSteamerFilterC2SPackage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, "set_steamer_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetSteamerFilterC2SPackage> STREAM_CODEC =
            StreamCodec.ofMember(SetSteamerFilterC2SPackage::encode, SetSteamerFilterC2SPackage::decode);

    private static SetSteamerFilterC2SPackage decode(RegistryFriendlyByteBuf buffer) {
        int maidId = buffer.readVarInt();
        RecipeFilterData.Mode mode = buffer.readEnum(RecipeFilterData.Mode.class);
        List<ResourceLocation> whitelist = readIds(buffer);
        List<ResourceLocation> blacklist = readIds(buffer);
        return new SetSteamerFilterC2SPackage(
                maidId, new RecipeFilterData(mode, whitelist, blacklist));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(maidId);
        buffer.writeEnum(filter.mode());
        writeIds(buffer, filter.whitelist());
        writeIds(buffer, filter.blacklist());
    }

    private static List<ResourceLocation> readIds(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_RECIPE_IDS) {
            throw new IllegalArgumentException("Invalid steamer recipe filter size: " + size);
        }
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ids.add(ResourceLocation.STREAM_CODEC.decode(buffer));
        }
        return ids;
    }

    private static void writeIds(RegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
        if (ids.size() > MAX_RECIPE_IDS) {
            throw new IllegalArgumentException("Steamer recipe filter is too large: " + ids.size());
        }
        buffer.writeVarInt(ids.size());
        for (ResourceLocation id : ids) {
            ResourceLocation.STREAM_CODEC.encode(buffer, id);
        }
    }

    public static void handle(SetSteamerFilterC2SPackage payload, IPayloadContext context) {
        if (!context.flow().isServerbound()) {
            return;
        }
        context.enqueueWork(() -> applyOnServer(payload, context.player()));
    }

    private static void applyOnServer(SetSteamerFilterC2SPackage payload, Player player) {
        Entity entity = player.level().getEntity(payload.maidId());
        if (!(entity instanceof EntityMaid maid)
                || !maid.isOwnedBy(player)
                || !TaskInfo.KC_STEAMER.uid.equals(maid.getTask().getUid())) {
            return;
        }

        Set<ResourceLocation> knownRecipes = new HashSet<>();
        SteamerAdapter.getRecipeOptions(maid.level())
                .forEach(option -> knownRecipes.add(option.id()));
        RecipeFilterData sanitized = new RecipeFilterData(
                payload.filter().mode(),
                sanitize(payload.filter().whitelist(), knownRecipes),
                sanitize(payload.filter().blacklist(), knownRecipes)
        );
        maid.setAndSyncData(DataRegister.KC_STEAMER, sanitized);
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
