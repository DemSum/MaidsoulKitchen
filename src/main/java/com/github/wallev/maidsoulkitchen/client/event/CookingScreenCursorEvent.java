package com.github.wallev.maidsoulkitchen.client.event;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.client.gui.entity.maid.MaidTaskConfigGui;
import com.github.wallev.maidsoulkitchen.task.cook.common.task.TaskCook;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Source: 58ec08ec CookConfigGuiV1 device/settings navigation. NeoForge menus now
 * reopen through the server, with a container-close packet before the new screen.
 * Minecraft's intervening mouse grab/release centers the cursor. Preserve only the
 * client cursor across this handoff; menu, task and cooking state stay unchanged. */
@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, value = Dist.CLIENT)
public final class CookingScreenCursorEvent {
    private static CursorPosition pending;

    private CookingScreenCursorEvent() { }

    @SubscribeEvent
    public static void rememberCursor(ScreenEvent.Closing event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getScreen() instanceof AbstractMaidContainerGui<?> screen
                && screen.getMaid() != null && screen.getMaid().getTask() instanceof TaskCook
                && !minecraft.mouseHandler.isMouseGrabbed()) {
            pending = new CursorPosition(screen.getMaid().getId(), minecraft.getWindow().getWindow(),
                    minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos(),
                    System.nanoTime() + 5_000_000_000L);
        } else pending = null;
    }

    @SubscribeEvent
    public static void restoreCursor(ScreenEvent.Init.Post event) {
        CursorPosition position = pending;
        pending = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (position != null && System.nanoTime() < position.expiresAt()
                && event.getScreen() instanceof MaidTaskConfigGui<?> screen
                && screen.getMaid() != null && screen.getMaid().getId() == position.maidId()
                && screen.getMaid().getTask() instanceof TaskCook
                && minecraft.isWindowActive() && !minecraft.mouseHandler.isMouseGrabbed()
                && minecraft.getWindow().getWindow() == position.window()) {
            GLFW.glfwSetCursorPos(position.window(), position.x(), position.y());
        }
    }

    private record CursorPosition(int maidId, long window, double x, double y, long expiresAt) { }
}
