package org.chatterjay.emilink.client;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.client.handler.AENetworkCache;
import org.chatterjay.emilink.client.search.SearchHistoryOverlay;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InputEvents {
    private InputEvents() {}

    private static Boolean ae2Available;
    private static Class<?> fakeSlotClass;
    private static Class<?> inventoryActionClass;
    private static Class<?> inventoryActionPacketClass;
    private static Object ae2NetworkHandler;
    private static Method ae2SendToServerMethod;
    private static Object inventoryActionSetFilter;

    private static boolean initAE2Reflection() {
        if (ae2Available != null) return ae2Available;
        try {
            fakeSlotClass = Class.forName("appeng.menu.slot.FakeSlot");
            ModLogger.debug("InputEvents: FakeSlot class loaded: {}", fakeSlotClass.getName());
            inventoryActionClass = Class.forName("appeng.helpers.InventoryAction");
            inventoryActionPacketClass = Class.forName("appeng.core.sync.packets.InventoryActionPacket");
            ModLogger.debug("InputEvents: InventoryActionPacket class loaded: {}", inventoryActionPacketClass.getName());

            var nhClass = Class.forName("appeng.core.sync.network.NetworkHandler");
            var instanceMethod = nhClass.getMethod("instance");
            ae2NetworkHandler = instanceMethod.invoke(null);
            var basePacketClass = Class.forName("appeng.core.sync.BasePacket");
            ae2SendToServerMethod = ae2NetworkHandler.getClass().getMethod("sendToServer", basePacketClass);
            ModLogger.debug("InputEvents: NetworkHandler loaded, sendToServer method: {}", ae2SendToServerMethod.getName());

            boolean found = false;
            for (var field : inventoryActionClass.getEnumConstants()) {
                if (field.toString().equals("SET_FILTER")) {
                    inventoryActionSetFilter = field;
                    found = true;
                    break;
                }
            }
            if (!found) {
                ModLogger.warn("InputEvents: SET_FILTER not found in InventoryAction");
                ae2Available = false;
                return false;
            }

            // Verify FakeSlot.index field exists
            try {
                var idxField = fakeSlotClass.getField("index");
                ModLogger.debug("InputEvents: FakeSlot.index field: {}", idxField);
            } catch (NoSuchFieldException e) {
                ModLogger.warn("InputEvents: FakeSlot.index field not found, trying getSlotIndex method");
            }

            ae2Available = true;
            ModLogger.debug("InputEvents: AE2 reflection initialized successfully");
        } catch (Throwable e) {
            ModLogger.warn("InputEvents: AE2 reflection failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            ae2Available = false;
        }
        return ae2Available;
    }

    private static void sendInventoryAction(int slotIndex, ItemStack stack) {
        if (!initAE2Reflection()) return;
        try {
            Object packet = inventoryActionPacketClass.getConstructor(inventoryActionClass, int.class, ItemStack.class)
                    .newInstance(inventoryActionSetFilter, slotIndex, stack);
            ae2SendToServerMethod.invoke(ae2NetworkHandler, packet);
        } catch (Exception e) {
            ModLogger.debug("InputEvents: sendInventoryAction error: {}", e.getMessage());
        }
    }

    private static int getFakeSlotIndex(Object fakeSlot) {
        if (!initAE2Reflection()) return -1;
        try {
            return (int) fakeSlotClass.getField("index").getInt(fakeSlot);
        } catch (Exception e) {
            // Try method
            try {
                return (int) fakeSlotClass.getMethod("getSlotIndex").invoke(fakeSlot);
            } catch (Exception e2) {
                return -1;
            }
        }
    }

    private static boolean fillSearchHandled = false;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCharTypedPre(ScreenEvent.CharacterTyped.Pre event) {
        if (fillSearchHandled) {
            fillSearchHandled = false;
            if (event.getCodePoint() == 'f' || event.getCodePoint() == 'F') {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        ModLogger.debug("InputEvents: KeyPressed.Pre keyCode={} scanCode={} screen={}",
                keyCode, scanCode, event.getScreen().getClass().getSimpleName());

        boolean fillMatch = ModKeybindings.FILL_SEARCH_KEY.matches(keyCode, scanCode);
        boolean quickMatch = ModKeybindings.QUICK_FILL_SLOT_KEY.matches(keyCode, scanCode);
        ModLogger.debug("InputEvents: FILL_SEARCH_KEY.match={} QUICK_FILL_SLOT_KEY.match={}", fillMatch, quickMatch);

        if (fillMatch) {
            onFillSearchKey(event);
            return;
        }

        if (quickMatch) {
            ModLogger.debug("InputEvents: QUICK_FILL_SLOT_KEY matched, calling handleQuickFillSlot");
            if (handleQuickFillSlot()) {
                ModLogger.debug("InputEvents: handleQuickFillSlot succeeded, canceling event");
                event.setCanceled(true);
            } else {
                ModLogger.debug("InputEvents: handleQuickFillSlot returned false");
            }
            return;
        }

    }

    /**
     * Quick fill to first empty FakeSlot.
     */
    public static boolean handleQuickFillSlot() {
        return quickFillSlot();
    }

    private static void onFillSearchKey(ScreenEvent.KeyPressed.Pre event) {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return;

        var first = emiStacks.get(0);

        boolean alt = Screen.hasAltDown();
        var text = alt ? "@" + first.getId().getNamespace() : first.getName().getString();
        if (text.isEmpty()) return;

        try {
            SearchHistoryOverlay.applySearch(text, first.getItemStack());
            fillSearchHandled = true;
            event.setCanceled(true);
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: apply search failed: {}", e.getMessage());
        }
    }

    private static boolean quickFillSlot() {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) {
            ModLogger.debug("QuickFillSlot: no hovered stack");
            return false;
        }

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) {
            ModLogger.debug("QuickFillSlot: ingredient empty");
            return false;
        }

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) {
            ModLogger.debug("QuickFillSlot: no emi stacks");
            return false;
        }

        var emiStack = emiStacks.get(0);

        var itemStack = emiStack.getItemStack();
        if (itemStack.isEmpty()) {
            ModLogger.debug("QuickFillSlot: itemStack empty");
            return false;
        }

        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) {
            ModLogger.debug("QuickFillSlot: screen not AbstractContainerScreen: {}", mc.screen);
            return false;
        }

        if (!initAE2Reflection()) {
            ModLogger.debug("QuickFillSlot: AE2 reflection not available");
            return false;
        }

        ModLogger.debug("QuickFillSlot: scanning {} slots for empty FakeSlot", containerScreen.getMenu().slots.size());
        for (var slot : containerScreen.getMenu().slots) {
            if (fakeSlotClass.isInstance(slot) && slot.getItem().isEmpty()) {
                int idx = getFakeSlotIndex(slot);
                sendInventoryAction(idx, itemStack.copy());
                ModLogger.debug("QuickFillSlot: set slot {} with {}", idx, emiStack.getId());
                return true;
            }
        }
        ModLogger.debug("QuickFillSlot: no empty FakeSlot found");
        return false;
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        AENetworkCache.save();
        ModLogger.debug("Disconnected: AE network cache saved to disk");
    }
}
