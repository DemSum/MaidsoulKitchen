package com.github.wallev.maidsoulkitchen.network.message;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;


import static com.github.tartaricacid.touhoulittlemaid.util.ResourceLocationUtil.getResourceLocation;

public record ActionCookDataRecC2SPackage(int entityId, ResourceLocation dataKey, String rec, String mode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActionCookDataRecC2SPackage> TYPE = new CustomPacketPayload.Type<>(getResourceLocation("cook_data_rec_c2s"));
    public static final StreamCodec<ByteBuf, ActionCookDataRecC2SPackage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ActionCookDataRecC2SPackage::entityId,
            ResourceLocation.STREAM_CODEC,
            ActionCookDataRecC2SPackage::dataKey,
            ByteBufCodecs.STRING_UTF8,
            ActionCookDataRecC2SPackage::rec,
            ByteBufCodecs.STRING_UTF8,
            ActionCookDataRecC2SPackage::mode,
            ActionCookDataRecC2SPackage::new
    );

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ActionCookDataRecC2SPackage message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                ServerPlayer sender = (ServerPlayer) context.player();
                if (sender == null || !CookData.isValidMode(message.mode)) return;
                Entity entity = sender.level.getEntity(message.entityId);
                ResourceLocation recipeId = ResourceLocation.tryParse(message.rec);
                if (recipeId == null) return;
                if (entity instanceof EntityMaid maid && maid.isOwnedBy(sender)
                        && maid.getTask() instanceof ICookTask<?, ?> cookTask
                        && cookTask.getCookDataKey().getKey().equals(message.dataKey)
                        && cookTask.getRecipeHolders(maid.level).stream().anyMatch(holder -> holder.id().equals(recipeId))) {
                    TaskDataKey<CookData> value = TaskDataRegister.getValue(message.dataKey);
                    if (value == null || value != cookTask.getCookDataKey()) return;
                    CookData cookData = maid.getOrCreateData(value, new CookData());
                    cookData.addOrRemoveRec(message.rec, message.mode);
                    maid.setAndSyncData(value, cookData);
                }
            });
        }
    }
}
