
package org.chatterjay.emiextend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.handler.EmiInteractionHandler;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.mixin.AbstractContainerScreenAccessor;
import org.chatterjay.emiextend.network.EmiLinkNetwork;
import org.chatterjay.emiextend.network.packet.c2s.AELockedSlotsPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.util.IEProxy;
import org.chatterjay.emiextend.util.IPNProxy;
import org.chatterjay.emiextend.util.ModLogger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public class BDShortcutHandler {
    public static boolean serverHasMod = false;

    /** Tracks the source inventory of the last plain left-click pickup: null=unknown, true=container, false=player. */
    private static Boolean pickupFromContainer = null;

    /**
     * Safely send a packet to the server, catching the case where the server doesn't have EmiLink.
     */
    private static <T> void sendToServerSafe(T packet) {
        if (!serverHasMod) return;
        try {
            EmiLinkNetwork.sendToServer(packet);
        } catch (Exception e) {
            ModLogger.warn("Server doesn't have EmiLink, dropping packet: {}", packet.getClass().getSimpleName());
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
            } else if (carried.isEmpty() && EmiInteractionHandler.matchesExtractModifier()) {
                if (EmiInteractionHandler.tryExtractFromHovered()) {
                    event.setCanceled(true);
                }
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
                if (EmiLinkConfig.ENABLE_BULK_TRANSFER.get()) {
                    // If IE is installed, let it handle regular containers (our screens already in its ignore list)
                    if (!net.minecraftforge.fml.ModList.get().isLoaded("inventoryessentials")) {
                        bulkTransferAll(containerScreen, slot);
                    }
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



    /** Keyboard entry point: drop all stacks matching the hovered slot item. */
    public static boolean tryBatchDropMatchingFromCurrentScreen() {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) return false;
        if (mc.screen.getFocused() instanceof EditBox) return false;

        Slot slot = ((AbstractContainerScreenAccessor) containerScreen).emilink$getHoveredSlot();
        if (slot == null || !slot.hasItem()) return false;

        return batchDropByType(containerScreen, slot);
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
            EmiLinkNetwork.sendToServer(new TransferMatchingPacket(clickedItem, mode, locked));
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

    private static boolean batchDropByType(AbstractContainerScreen<?> screen, ItemStack carried) {
        return batchDropByType(screen, carried, pickupFromContainer);
    }

    /** InventoryEssentials-style hovered-slot bulk drop: same item, same inventory area. */
    private static boolean batchDropByType(AbstractContainerScreen<?> screen, Slot hoverSlot) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        if (!hoverSlot.hasItem()) return false;

        var menu = screen.getMenu();
        var targetStack = hoverSlot.getItem().copy();
        var locked = IPNProxy.getLockedSlots();
        int containerId = menu.containerId;

        List<Integer> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!slot.hasItem()) continue;
            if (!canPlayerAccessSlot(slot)) continue;
            if (!isSameInventory(slot, hoverSlot)) continue;
            if (!ItemStack.isSameItemSameTags(slot.getItem(), targetStack)) continue;
            if (slot.container instanceof Inventory) {
                int idx = slot.getContainerSlot();
                if (idx >= 0 && idx < 36 && locked.contains(idx)) continue;
            }
            slots.add(slot.index);
        }
        if (slots.isEmpty()) return false;

        for (int slotIndex : slots) {
            click(menu, containerId, slotIndex, 1, net.minecraft.world.inventory.ClickType.THROW);
        }
        return true;
    }

    private static boolean batchDropByType(AbstractContainerScreen<?> screen, ItemStack carried, Boolean sourceFromContainer) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        var menu = screen.getMenu();
        var locked = IPNProxy.getLockedSlots();
        int containerId = menu.containerId;

        // Collect matching slot indices (IE pattern)
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            if (!ItemStack.isSameItemSameTags(slot.getItem(), carried)) continue;
            // Respect pickup source: container pickup → skip player slots; player pickup → skip container slots
            if (sourceFromContainer != null) {
                if (sourceFromContainer && slot.container instanceof Inventory) continue;
                if (!sourceFromContainer && !(slot.container instanceof Inventory)) continue;
            }
            if (slot.container instanceof Inventory) {
                int idx = slot.getSlotIndex();
                if (idx >= 0 && idx < 36 && locked.contains(idx)) continue;
            }
            slots.add(i);
        }
        if (slots.isEmpty()) return false;

        // Clear cursor first (IE pattern), then throw each matching stack
        click(menu, containerId, -999, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        for (int slotIndex : slots) {
            click(menu, containerId, slotIndex, 1, net.minecraft.world.inventory.ClickType.THROW);
        }
        return true;
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
        boolean containerToPlayerWithLocks = !(clickedSlot.container instanceof Inventory) && !locked.isEmpty();

        List<Integer> sourceSlots = new ArrayList<>();
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
                sourceSlots.add(slot.index);
            }
        }

        for (int slotIndex : sourceSlots) {
            Slot slot = menu.getSlot(slotIndex);
            if (!slot.hasItem()) continue;

            if (containerToPlayerWithLocks) {
                safeTransferContainerSlotToUnlockedPlayerSlots(menu, containerId, slot, locked);
            } else {
                click(menu, containerId, slot.index, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
            }
        }
    }

    /**
     * Transfer a container stack into player inventory without ever targeting IPN-locked slots.
     * Vanilla QUICK_MOVE cannot express forbidden target slots, so locked target slots may otherwise
     * consume transfer capacity and leave exactly that many source stacks behind.
     */
    private static void safeTransferContainerSlotToUnlockedPlayerSlots(AbstractContainerMenu menu, int containerId,
                                                                       Slot sourceSlot, Set<Integer> locked) {
        if (sourceSlot.container instanceof Inventory || !sourceSlot.hasItem()) return;
        if (!menu.getCarried().isEmpty()) return;

        click(menu, containerId, sourceSlot.index, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;

        // Fill existing unlocked stacks first.
        for (Slot targetSlot : menu.slots) {
            carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (!isUnlockedPlayerInventorySlot(targetSlot, locked)) continue;
            if (!targetSlot.hasItem()) continue;
            ItemStack targetStack = targetSlot.getItem();
            if (!ItemStack.isSameItemSameTags(targetStack, carried)) continue;
            if (targetStack.getCount() >= getSlotStackLimit(targetSlot, targetStack)) continue;
            click(menu, containerId, targetSlot.index, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        }

        // Then place into unlocked empty slots.
        for (Slot targetSlot : menu.slots) {
            carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (!isUnlockedPlayerInventorySlot(targetSlot, locked)) continue;
            if (targetSlot.hasItem()) continue;
            if (!targetSlot.mayPlace(carried)) continue;
            click(menu, containerId, targetSlot.index, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        }

        carried = menu.getCarried();
        if (!carried.isEmpty()) {
            // Could not fully transfer; put the remainder back where it came from.
            click(menu, containerId, sourceSlot.index, 0, net.minecraft.world.inventory.ClickType.PICKUP);
        }
    }

    private static boolean isUnlockedPlayerInventorySlot(Slot slot, Set<Integer> locked) {
        if (!(slot.container instanceof Inventory)) return false;
        if (!canPlayerAccessSlot(slot)) return false;
        int idx = slot.getContainerSlot();
        return idx >= 0 && idx < 36 && !locked.contains(idx);
    }

    private static int getSlotStackLimit(Slot slot, ItemStack stack) {
        return Math.min(slot.getMaxStackSize(), slot.getMaxStackSize(stack));
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
