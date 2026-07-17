
package org.chatterjay.emiextend.client;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.util.ModLogger;

public final class AEQuickCraftDelayHandler {
    private static PendingResultClick pending;

    private AEQuickCraftDelayHandler() {}

    public static void schedule(InventoryAction action, int slotIndex, long id, int containerId, Object sourceScreen,
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
        PacketDistributor.sendToServer(new InventoryActionPacket(pending.action(), pending.slotIndex(), pending.id()));
        pending = null;
    }

    private record PendingResultClick(InventoryAction action, int slotIndex, long id, int containerId,
                                      Object sourceScreen, Object sourceMenu, String recipeId, int ticksLeft) {
        PendingResultClick withTicksLeft(int ticksLeft) {
            return new PendingResultClick(action, slotIndex, id, containerId, sourceScreen, sourceMenu, recipeId, ticksLeft);
        }
    }
}
