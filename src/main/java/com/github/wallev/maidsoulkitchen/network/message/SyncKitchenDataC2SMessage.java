package com.github.wallev.maidsoulkitchen.network.message;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData;
import com.github.wallev.maidsoulkitchen.task.cook.common.task.TaskCook;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Source: 58ec08ec SyncKitchenDataC2SMessage and CookConfigGuiV1.sendToServer (MIT).
 * NeoForge payload replaces Forge networking. Send only the selected UID: the upstream whole-data
 * packet let clients replace server permissions and unrelated filters. No work state is transmitted. */
public record SyncKitchenDataC2SMessage(int maidId, ResourceLocation cookName, boolean recipeSettings) implements CustomPacketPayload {
    public static final Type<SyncKitchenDataC2SMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("maidsoulkitchen", "select_cooking_task"));
    public static final StreamCodec<ByteBuf, SyncKitchenDataC2SMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncKitchenDataC2SMessage::maidId, ResourceLocation.STREAM_CODEC,
            SyncKitchenDataC2SMessage::cookName, ByteBufCodecs.BOOL, SyncKitchenDataC2SMessage::recipeSettings, SyncKitchenDataC2SMessage::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SyncKitchenDataC2SMessage message, IPayloadContext context) {
        if (!context.flow().isServerbound()) return;
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sender)
                    || !(player.level().getEntity(message.maidId) instanceof EntityMaid maid) || !maid.isOwnedBy(player)
                    || !(maid.getTask() instanceof TaskCook task)
                    || !(player.containerMenu instanceof com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer menu)
                    || menu.getMaid() != maid || !menu.stillValid(player)) return;
            if (!KitchenData.get(maid).getCookName().equals(message.cookName) && !TaskCook.select(maid, message.cookName)) return;
            var selected = task.getTask(maid);
            var provider = message.recipeSettings && selected.isPresent() ? selected.get().getTaskConfigGuiProvider(maid)
                    : new net.minecraft.world.SimpleMenuProvider((id, inventory, menuPlayer) ->
                    new com.github.wallev.maidsoulkitchen.inventory.container.maid.CookConfigContainer(id, inventory, maid.getId(), true), task.getName());
            sender.openMenu(provider, buffer -> { buffer.writeInt(maid.getId()); buffer.writeBoolean(!message.recipeSettings); });
        });
    }
}
