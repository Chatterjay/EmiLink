
package org.chatterjay.emiextend.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Method;

public final class AEQuickCraftDelayHandler {
    private static PendingResultClick pending;

    private AEQuickCraftDelayHandler() {}

    public static void schedule(Object action, int slotIndex, long id, int containerId, Object sourceScreen,
                                Object sourceMenu, Object recipeId) {
        pending = new PendingResultClick(action, slotIndex, id, containerId, sourceScreen, sourceMenu, String.valueOf(recipeId), 3);
        ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click scheduled action={} slot={} id={} container={} recipe={} screen={} menu={}",
                action, slotIndex, id, containerId, recipeId,
                sourceScreen == null ? "null" : sourceScreen.getClass().getName(),
                sourceMenu == null ? "null" : sourceMenu.getClass().getName());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (pending == null) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click cancel reason=no_player_or_screen recipe={}", pending.recipeId());
            pending = null;
            return;
        }

        if (AE2Proxy.isCraftConfirmScreen(mc.screen)) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click cancel reason=craft_confirm_opened recipe={} screen={}",
                    pending.recipeId(), mc.screen.getClass().getName());
            pending = null;
            return;
        }

        if (mc.screen != pending.sourceScreen()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click cancel reason=screen_changed recipe={} old={} new={}",
                    pending.recipeId(),
                    pending.sourceScreen() == null ? "null" : pending.sourceScreen().getClass().getName(),
                    mc.screen.getClass().getName());
            pending = null;
            return;
        }

        if (mc.player.containerMenu != pending.sourceMenu()
                || mc.player.containerMenu.containerId != pending.containerId()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click cancel reason=menu_changed recipe={} oldContainer={} newMenu={} newContainer={}",
                    pending.recipeId(), pending.containerId(),
                    mc.player.containerMenu == null ? "null" : mc.player.containerMenu.getClass().getName(),
                    mc.player.containerMenu == null ? -1 : mc.player.containerMenu.containerId);
            pending = null;
            return;
        }

        int ticksLeft = pending.ticksLeft() - 1;
        if (ticksLeft > 0) {
            pending = pending.withTicksLeft(ticksLeft);
            return;
        }

        ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click send action={} slot={} id={} recipe={}",
                pending.action(), pending.slotIndex(), pending.id(), pending.recipeId());
        if (!sendInventoryAction(pending.action(), pending.slotIndex(), pending.id())) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click cancel reason=packet_unavailable recipe={}", pending.recipeId());
        }
        pending = null;
    }

    private static boolean sendInventoryAction(Object action, int slotIndex, long id) {
        if (!AE2Proxy.isLoaded() || action == null) return false;
        try {
            Class<?> actionClass = Class.forName("appeng.helpers.InventoryAction");
            if (!actionClass.isInstance(action)) return false;
            Class<?> packetClass = Class.forName("appeng.core.network.serverbound.InventoryActionPacket");
            Object packet = packetClass.getConstructor(actionClass, int.class, long.class)
                    .newInstance(action, slotIndex, id);
            Class<?> distributor = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            for (Method method : distributor.getMethods()) {
                if ("sendToServer".equals(method.getName()) && method.getParameterCount() == 1) {
                    method.invoke(null, packet);
                    return true;
                }
            }
        } catch (ReflectiveOperationException error) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT delayed-click packet failed: {}", error.toString());
        }
        return false;
    }

    private record PendingResultClick(Object action, int slotIndex, long id, int containerId,
                                      Object sourceScreen, Object sourceMenu, String recipeId, int ticksLeft) {
        PendingResultClick withTicksLeft(int ticksLeft) {
            return new PendingResultClick(action, slotIndex, id, containerId, sourceScreen, sourceMenu, recipeId, ticksLeft);
        }
    }
}
