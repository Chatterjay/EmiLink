package org.chatterjay.emiextend.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.integration.CuriosProxy;
import org.chatterjay.emiextend.integration.EAEPProxy;
import org.chatterjay.emiextend.network.packet.c2s.AELockedSlotsPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.util.IEProxy;
import org.chatterjay.emiextend.util.IPNProxy;
import org.chatterjay.emiextend.util.ModLogger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class BDShortcutHandler {
    public static boolean serverHasMod = false;

    /** Tracks the source inventory of the last plain left-click pickup: null=unknown, true=container, false=player. */
    private static Boolean pickupFromContainer = null;

    /**
     * Safely send a packet to the server, catching the case where the server doesn't have EmiLink.
     */
    private static void sendToServerSafe(net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        if (!serverHasMod) return;
        try {
            PacketDistributor.sendToServer(packet);
        } catch (Exception e) {
            ModLogger.warn("Server doesn't have EmiLink, dropping packet: {}", packet.type().id());
        }
    }

    /**
     * When an AE or BD terminal screen opens, send locked slots to the server
     * and register these screens with IE's ignore list so its Space+click
     * handlers don't interfere.
     */
    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        boolean isAE = AE2Proxy.isMEStorageScreen(screen);
        boolean isBD = BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen);
        if (!isAE && !isBD) return;

        // Register our screens with IE's ignore list (reflection, no-op if IE absent)
        IEProxy.registerIgnoredScreens();

        if (!isAE) return;

        // Pre-populate locked slots for AE screens so the mixin has them ready
        var lockedSet = IPNProxy.getLockedSlots();
        if (lockedSet.isEmpty() || !serverHasMod) return;
        int[] lockedArr = lockedSet.stream().mapToInt(Integer::intValue).toArray();
        sendToServerSafe(new AELockedSlotsPacket(lockedArr, -1));
    }

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_SPACE) return;
        Screen screen = event.getScreen();
        if (!BDProxy.isBDNetGUI(screen) && !BDProxy.isBDCraftGUI(screen)) return;
        if (screen.getFocused() instanceof EditBox) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseClickedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen.getFocused() instanceof EditBox) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        // Track pickup source on ANY left click (before Space/Shift filter)
        if (screen instanceof AbstractContainerScreen<?> cs) {
            Slot s = cs.getSlotUnderMouse();
            if (s != null && s.hasItem() && cs.getMenu().getCarried().isEmpty()) {
                pickupFromContainer = !(s.container instanceof Inventory);
            }
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean isSpace = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
        boolean isShift = Screen.hasShiftDown();
        boolean isCtrl = Screen.hasControlDown();
        if (!isSpace && !isShift && !isCtrl) return;

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
        Slot slot = containerScreen.getSlotUnderMouse();
        ItemStack carried = containerScreen.getMenu().getCarried();

        if (slot == null) {
            if (isShift && !carried.isEmpty()) {
                batchDropByType(containerScreen, carried);
                event.setCanceled(true);
            } else if (isShift && carried.isEmpty()) {
                tryExtractFromEmi(event);
            }
            return;
        }
        if (!slot.hasItem()) return;

        ItemStack clickedItem = slot.getItem();
        if (clickedItem.isEmpty()) return;

        // ---- AE terminal Space+click: send locked slots, let native MOVE_REGION handle deposit ----
        if (isSpace && AE2Proxy.isMEStorageScreen(screen)) {
            var lockedSet = IPNProxy.getLockedSlots();
            int[] lockedArr = lockedSet.stream().mapToInt(Integer::intValue).toArray();
            sendToServerSafe(new AELockedSlotsPacket(lockedArr, -1));
            return;
        }

        // ---- Regular container Space+click: IE-pattern bulk transfer (non-AE, non-BD) ----
        if (isSpace) {
            boolean isBDScreen = BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen);
            if (!isBDScreen) {
                // If IE is installed, let it handle regular containers (our screens already in its ignore list)
                if (!net.neoforged.fml.ModList.get().isLoaded("inventoryessentials")) {
                    bulkTransferAll(containerScreen, slot);
                }
                event.setCanceled(true);
                return;
            }
        }

        // ---- BD-specific handling ----
        boolean isBDScreen = BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen);
        if (!isBDScreen) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var menu = containerScreen.getMenu();
        if (!BDProxy.isBDBaseMenu(menu)) return;

        int inventoryStart = BDProxy.getInventoryStartIndex(menu);
        int inventoryEnd = BDProxy.getInventoryEndIndex(menu);

        // ---- Ctrl+Click: single craft on BD result slot ----
        if (isCtrl && !isSpace && !isShift) {
            if (slot.index == BDProxy.getResultSlotIndex(menu)) {
                sendToServerSafe(new BDActionPacket(ItemStack.EMPTY, 2));
                event.setCanceled(true);
            }
            return;
        }

        // ---- Space+Click handler ----
        if (isSpace) {
            handleSpaceClick(screen, slot, clickedItem, menu, inventoryStart, inventoryEnd, event);
            return;
        }

        // ---- Shift+Click: override BD's native "fill inventory" on network storage slots ----
        if (slot.index >= inventoryEnd) {
            sendToServerSafe(new BDActionPacket(clickedItem, 0));
            event.setCanceled(true);
        }
    }

    /**
     * Shift+click on EMI sidebar: extract item with priority:
     * BD screen → BD first → AE2 fallback
     * Non-BD → AE2 first (needs wireless terminal) → BD fallback
     */
    private static void tryExtractFromEmi(ScreenEvent.MouseButtonPressed.Pre event) {
        var hovered = EmiApi.getHoveredStack((int) event.getMouseX(), (int) event.getMouseY(), false);
        if (hovered == null || hovered.isEmpty()) return;

        var stack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
        if (stack == null) return;

        Screen screen = event.getScreen();
        boolean isBDScreen = BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen);

        if (isBDScreen) {
            if (BDProxy.isLoaded()) {
                BDProxy.pullFromNetwork(stack);
                event.setCanceled(true);
                return;
            }
            // BD fallback → AE2
            if (tryAE2Extract(stack)) {
                event.setCanceled(true);
            }
        } else {
            if (tryAE2Extract(stack)) {
                event.setCanceled(true);
                return;
            }
            // AE2 fallback → BD
            if (BDProxy.isLoaded()) {
                BDProxy.pullFromNetwork(stack);
                event.setCanceled(true);
            }
        }
    }

    private static boolean tryAE2Extract(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!AE2Proxy.isLoaded()) return false;
        if (!hasWirelessTerminal(player)) return false;
        return EAEPProxy.pullFromNetwork(stack);
    }

    private static boolean hasWirelessTerminal(Player player) {
        if (!AE2Proxy.isLoaded()) return false;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (AE2Proxy.isWirelessTerminal(player.getInventory().items.get(i))) return true;
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) return true;
        Class<?> wtClass = AE2Proxy.getWirelessTerminalClass();
        return wtClass != null && CuriosProxy.hasWirelessTerminal(player, wtClass);
    }

    private static void handleSpaceClick(Screen screen, Slot slot, ItemStack clickedItem,
                                          AbstractContainerMenu menu, int inventoryStart, int inventoryEnd,
                                          ScreenEvent.MouseButtonPressed.Pre event) {
        if (slot.index == BDProxy.getResultSlotIndex(menu)) {
            sendToServerSafe(new BDActionPacket(ItemStack.EMPTY, 1));
            event.setCanceled(true);
            return;
        }

        // Crafting grid slots → ignore Space+Click entirely (let BD handle normally)
        if (BDProxy.isBDCraftGUI(screen)) {
            int resultSlotIdx = BDProxy.getResultSlotIndex(menu);
            if (resultSlotIdx >= 0 && slot.index > resultSlotIdx) return;
        }

        boolean isPlayerSlot = slot.index >= inventoryStart && slot.index < inventoryEnd;

        int mode;
        if (isPlayerSlot) {
            mode = (slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 9) ? 2 : 1;
        } else {
            mode = 0;
        }

        if (serverHasMod) {
            int[] locked = getLockedIndices(mode);
            PacketDistributor.sendToServer(new TransferMatchingPacket(clickedItem, mode, locked));
        } else {
            boolean dirToStorage = mode != 0;
            BDProxy.sendBatchTransfer(clickedItem, dirToStorage);
        }

        event.setCanceled(true);
    }

    private static int[] getLockedIndices(int mode) {
        if (mode == 0) return new int[0];
        int start = (mode == 2) ? 0 : 9;
        int end = (mode == 2) ? 9 : 36;
        return IPNProxy.getLockedSlotsInRange(start, end);
    }

    private static void batchDropByType(AbstractContainerScreen<?> screen, ItemStack carried) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        var menu = screen.getMenu();
        var locked = IPNProxy.getLockedSlots();
        int containerId = menu.containerId;

        // Collect matching slot indices (IE pattern)
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            if (!ItemStack.isSameItemSameComponents(slot.getItem(), carried)) continue;
            // Respect pickup source: container pickup → skip player slots; player pickup → skip container slots
            if (pickupFromContainer != null) {
                if (pickupFromContainer && slot.container instanceof Inventory) continue;
                if (!pickupFromContainer && !(slot.container instanceof Inventory)) continue;
            }
            if (slot.container instanceof Inventory) {
                int idx = slot.getSlotIndex();
                if (idx >= 0 && idx < 36 && locked.contains(idx)) continue;
            }
            slots.add(i);
        }
        if (slots.isEmpty()) return;

        // Clear cursor first (IE pattern), then throw each matching stack
        click(menu, containerId, -999, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        for (int slotIndex : slots) {
            click(menu, containerId, slotIndex, 1, net.minecraft.world.inventory.ClickType.THROW);
        }
    }

    /** IE-style slotClick wrapper: validates slot index and delegates to handleInventoryMouseClick. */
    private static void click(AbstractContainerMenu menu, int containerId, int slotIndex, int button, net.minecraft.world.inventory.ClickType clickType) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        if (menu.isValidSlotIndex(slotIndex) || slotIndex == -999) {
            mc.gameMode.handleInventoryMouseClick(containerId, slotIndex, button, clickType, mc.player);
        }
    }

    /**
     * IE-style bulk transfer for regular containers (non-AE, non-BD).
     * Transfers all items from clicked inventory to the other inventory.
     * Skips IPN-locked player slots.
     */
    private static void bulkTransferAll(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        var menu = screen.getMenu();
        int containerId = menu.containerId;
        var locked = IPNProxy.getLockedSlots();

        for (Slot slot : menu.slots) {
            if (!slot.hasItem()) continue;
            if (!canPlayerAccessSlot(slot)) continue;
            // Only transfer from the same inventory section as the clicked slot
            if (isSameInventory(slot, clickedSlot)) {
                // Skip IPN-locked player slots
                if (slot.container instanceof Inventory) {
                    int idx = slot.getContainerSlot();
                    if (idx >= 0 && idx < 36 && locked.contains(idx)) continue;
                }
                click(menu, containerId, slot.index, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
            }
        }
    }

    /** Check if a slot is a valid player-interaction target (skip armor/offhand). */
    private static boolean canPlayerAccessSlot(Slot slot) {
        if (slot.container instanceof Inventory inv) {
            int idx = slot.getContainerSlot();
            // Only main inventory (9-35) and hotbar (0-8), skip armor (36-39) and offhand (40)
            return idx >= 0 && idx < 36;
        }
        return true;
    }

    /**
     * Check if two slots belong to the same inventory section.
     * Player inventory is split into hotbar (0-8) and main (9-35).
     */
    private static boolean isSameInventory(Slot a, Slot b) {
        if (a.container instanceof Inventory && b.container instanceof Inventory) {
            int ai = a.getContainerSlot();
            int bi = b.getContainerSlot();
            // Both must be valid player inventory slots
            if (ai < 0 || ai >= 36 || bi < 0 || bi >= 36) return false;
            // Separate hotbar from main inventory
            return (ai < 9) == (bi < 9);
        }
        return a.container == b.container;
    }
}
