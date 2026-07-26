package org.chatterjay.emilink.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.util.ModLogger;

public final class AEQuickCraftDelayHandler {
    private static PendingResultClick pending;

    private AEQuickCraftDelayHandler() {
    }

    public static void schedule(AbstractContainerMenu menu, String actionName, long id, Object sourceScreen, Object recipeId) {
        if (menu == null) return;
        pending = new PendingResultClick(actionName, id, menu.containerId, sourceScreen, menu, String.valueOf(recipeId), 3);
        ModLogger.debug("AE_EMI_CTRL_CRAFT scheduled result click action={} id={} container={} recipe={}",
                actionName, id, menu.containerId, recipeId);
    }

    @Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (pending == null) return;

            var mc = Minecraft.getInstance();
            if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || mc.player == null) {
                ModLogger.debug("AE_EMI_CTRL_CRAFT result-click canceled: no screen/player recipe={}", pending.recipeId());
                pending = null;
                return;
            }
            if (mc.screen != pending.sourceScreen()
                    || mc.player.containerMenu != pending.sourceMenu()
                    || screen.getMenu().containerId != pending.containerId()) {
                ModLogger.debug("AE_EMI_CTRL_CRAFT result-click canceled: screen/menu changed recipe={}", pending.recipeId());
                pending = null;
                return;
            }

            int ticksLeft = pending.ticksLeft() - 1;
            if (ticksLeft > 0) {
                pending = pending.withTicksLeft(ticksLeft);
                return;
            }

            Slot resultSlot = findCraftingResultSlot(screen.getMenu());
            if (resultSlot == null) {
                ModLogger.debug("AE_EMI_CTRL_CRAFT result-click skipped: no CraftingTermSlot");
                pending = null;
                return;
            }

            try {
                Object action = Class.forName("appeng.helpers.InventoryAction")
                        .getField(pending.actionName())
                        .get(null);
                Class<?> actionClass = Class.forName("appeng.helpers.InventoryAction");
                Class<?> packetClass = Class.forName("appeng.core.sync.packets.InventoryActionPacket");
                Object packet = packetClass
                        .getConstructor(actionClass, int.class, long.class)
                        .newInstance(action, resultSlot.index, pending.id());
                sendAe2PacketToServer(packet);
                ModLogger.debug("AE_EMI_CTRL_CRAFT result-click sent action={} slot={} id={} container={} recipe={}",
                        pending.actionName(), resultSlot.index, pending.id(), pending.containerId(), pending.recipeId());
            } catch (Throwable t) {
                ModLogger.warn("AE_EMI_CTRL_CRAFT result-click failed: {}", t.toString());
            } finally {
                pending = null;
            }
        }
    }

    private static Slot findCraftingResultSlot(AbstractContainerMenu menu) {
        try {
            Class<?> resultSlotClass = Class.forName("appeng.menu.slot.CraftingTermSlot");
            for (Slot slot : menu.slots) {
                if (resultSlotClass.isInstance(slot)) {
                    return slot;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private static void sendAe2PacketToServer(Object packet) throws ReflectiveOperationException {
        Class<?> networkHandlerClass = Class.forName("appeng.core.sync.network.NetworkHandler");
        Object handler = networkHandlerClass.getMethod("instance").invoke(null);
        Class<?> basePacketClass = Class.forName("appeng.core.sync.BasePacket");
        networkHandlerClass.getMethod("sendToServer", basePacketClass).invoke(handler, packet);
    }

    private record PendingResultClick(String actionName, long id, int containerId, Object sourceScreen,
                                      AbstractContainerMenu sourceMenu, String recipeId, int ticksLeft) {
        PendingResultClick withTicksLeft(int ticksLeft) {
            return new PendingResultClick(actionName, id, containerId, sourceScreen, sourceMenu, recipeId, ticksLeft);
        }
    }
}
