package org.chatterjay.emilink.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.integration.CuriosProxy;
import org.chatterjay.emilink.integration.EAEPProxy;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.network.packet.c2s.BDActionPacket;
import org.chatterjay.emilink.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emilink.util.IPNProxy;
import org.chatterjay.emilink.util.ModLogger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BDShortcutHandler {

    public static boolean serverHasMod = false;

    private static Boolean pickupFromContainer = null;
    private static long lastShiftClickTime = 0;

    private static boolean hasServerMod() {
        return org.chatterjay.emilink.network.packet.s2c.ServerHasModPacket.serverHasMod || serverHasMod;
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
        int button = event.getButton();
        boolean isLeft = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean isRight = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (!isLeft && !isRight) return;

        // Track pickup source on ANY left click (before Space/Shift filter)
        if (isLeft && screen instanceof AbstractContainerScreen<?> cs) {
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
            if (isLeft && isShift && !carried.isEmpty()) {
                batchDropByType(containerScreen, carried);
                event.setCanceled(true);
            } else if (isLeft && isShift && carried.isEmpty()) {
                tryExtractFromEmi(event);
            }
            return;
        }
        if (!slot.hasItem()) return;

        ItemStack clickedItem = slot.getItem();
        if (clickedItem.isEmpty()) return;

        // ---- AE terminal Space+click: let native MOVE_REGION handle deposit ----
        if (isSpace && AE2Proxy.isMEStorageScreen(screen)) {
            return;
        }

        // ---- Regular container Space+click: bulk transfer (non-BD) ----
        if (isSpace) {
            boolean isBDScreen = BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen);
            if (!isBDScreen) {
                bulkTransferAll(containerScreen, slot);
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
                if (hasServerMod()) {
                    NetworkHandler.sendToServer(new BDActionPacket(ItemStack.EMPTY, 2));
                }
                event.setCanceled(true);
            }
            return;
        }

        // ---- Space+Click handler ----
        if (isSpace) {
            handleSpaceClick(screen, slot, clickedItem, menu, inventoryStart, inventoryEnd, event);
            return;
        }

        // ---- Shift+Click (left or right): extract one stack from BD network storage ----
        // Also intercepts BD's native Shift+right-click 3x = extract-all behavior
        if (slot.index >= inventoryEnd) {
            long now = System.currentTimeMillis();
            if (now - lastShiftClickTime < 300) {
                ModLogger.info("BDShortcutHandler: shift+click throttled (too fast)");
                event.setCanceled(true);
                return;
            }
            lastShiftClickTime = now;
            if (hasServerMod()) {
                ItemStack sendStack = clickedItem.copy();
                sendStack.setCount(1);
                ModLogger.info("BDShortcutHandler: shift+click sending TransferMatchingPacket mode=3 slot={} item={}",
                        slot.index, sendStack.getHoverName().getString());
                NetworkHandler.sendToServer(new TransferMatchingPacket(sendStack, 3, new int[0]));
            } else {
                ModLogger.info("BDShortcutHandler: shift+click client-only extract item={}",
                        clickedItem.getHoverName().getString());
                BDProxy.clientExtract(clickedItem);
            }
            event.setCanceled(true);
        }
    }

    /**
     * Shift+click on EMI sidebar: AE2 extraction on mouse press.
     * BD EMI extraction is handled by EmiInteractionHandler.onMouseReleased.
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

        // AE2 extraction: handled here on mouse press
        if (tryAE2Extract(stack)) {
            event.setCanceled(true);
        }
        // BD EMI extraction is handled by EmiInteractionHandler.onMouseReleased (mouse release)
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
        // Space+click on result slot → mass craft (requires EmiLink server)
        if (slot.index == BDProxy.getResultSlotIndex(menu)) {
            if (hasServerMod()) {
                NetworkHandler.sendToServer(new BDActionPacket(ItemStack.EMPTY, 1));
            }
            event.setCanceled(true);
            return;
        }

        // Crafting grid slots → ignore Space+Click entirely (let BD handle normally)
        if (BDProxy.isBDCraftGUI(screen)) {
            int resultSlotIdx = BDProxy.getResultSlotIndex(menu);
            if (resultSlotIdx >= 0 && slot.index > resultSlotIdx) return;
        }

        boolean isPlayerSlot = slot.index >= inventoryStart && slot.index < inventoryEnd;

        if (hasServerMod()) {
            int mode;
            if (isPlayerSlot) {
                mode = (slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 9) ? 2 : 1;
            } else {
                mode = 0;
            }
            ItemStack sendStack = mode == 0 ? clickedItem.copy() : clickedItem;
            if (mode == 0) sendStack.setCount(1);
            int[] locked = getLockedIndices(mode);
            NetworkHandler.sendToServer(new TransferMatchingPacket(sendStack, mode, locked));
            ModLogger.debug("handleSpaceClick: mode={}, hasServerMod={}, locked={}", mode, hasServerMod(), locked.length);
        } else {
            // Client-only: use BD's native BatchTransferPacket
            if (isPlayerSlot) {
                BDProxy.clientDeposit(clickedItem);
            } else {
                BDProxy.clientExtract(clickedItem);
            }
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

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            if (!ItemStack.isSameItemSameTags(slot.getItem(), carried)) continue;
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

        click(menu, containerId, -999, 0, ClickType.PICKUP);
        for (int slotIndex : slots) {
            click(menu, containerId, slotIndex, 1, ClickType.THROW);
        }
    }

    private static void click(AbstractContainerMenu menu, int containerId, int slotIndex, int button, ClickType clickType) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        if (menu.isValidSlotIndex(slotIndex) || slotIndex == -999) {
            mc.gameMode.handleInventoryMouseClick(containerId, slotIndex, button, clickType, mc.player);
        }
    }

    private static void bulkTransferAll(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        var menu = screen.getMenu();
        int containerId = menu.containerId;
        var locked = IPNProxy.getLockedSlots();

        for (Slot slot : menu.slots) {
            if (!slot.hasItem()) continue;
            if (!canPlayerAccessSlot(slot)) continue;
            if (isSameInventory(slot, clickedSlot)) {
                if (slot.container instanceof Inventory) {
                    int idx = slot.getContainerSlot();
                    if (idx >= 0 && idx < 36 && locked.contains(idx)) continue;
                }
                click(menu, containerId, slot.index, 0, ClickType.QUICK_MOVE);
            }
        }
    }

    private static boolean canPlayerAccessSlot(Slot slot) {
        if (slot.container instanceof Inventory inv) {
            int idx = slot.getContainerSlot();
            return idx >= 0 && idx < 36;
        }
        return true;
    }

    private static boolean isSameInventory(Slot a, Slot b) {
        if (a.container instanceof Inventory && b.container instanceof Inventory) {
            int ai = a.getContainerSlot();
            int bi = b.getContainerSlot();
            if (ai < 0 || ai >= 36 || bi < 0 || bi >= 36) return false;
            return (ai < 9) == (bi < 9);
        }
        return a.container == b.container;
    }
}
