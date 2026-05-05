package org.chatterjay.emiextend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.util.ModLogger;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT)
public class BDShortcutHandler {
    public static boolean serverHasMod = false;

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        // Prevent Space key from triggering focused BD buttons
        // User clicks a BD function button → it stays focused → pressing Space re-activates it
        // We consume Space here so it only works as a modifier for our mouse handler (which uses GLFW polling)
        if (event.getKeyCode() != GLFW.GLFW_KEY_SPACE) return;
        Screen screen = event.getScreen();
        if (!BDProxy.isBDNetGUI(screen) && !BDProxy.isBDCraftGUI(screen)) return;
        if (screen.getFocused() instanceof EditBox) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseClickedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!BDProxy.isBDNetGUI(screen) && !BDProxy.isBDCraftGUI(screen)) return;
        if (screen.getFocused() instanceof EditBox) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean isSpace = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
        boolean isShift = Screen.hasShiftDown();
        // Must hold Space or Shift, but not both (Space takes priority)
        if (!isSpace && !isShift) return;

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
        Slot slot = containerScreen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return;

        ItemStack clickedItem = slot.getItem();
        if (clickedItem.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var menu = containerScreen.getMenu();
        if (!BDProxy.isBDBaseMenu(menu)) return;

        int inventoryStart = BDProxy.getInventoryStartIndex(menu);
        int inventoryEnd = BDProxy.getInventoryEndIndex(menu);

        // ---- Space+Click handler ----
        if (isSpace) {
            handleSpaceClick(screen, slot, clickedItem, menu, inventoryStart, inventoryEnd, event);
            return;
        }

        // ---- Shift+Click: override BD's native "fill inventory" on network storage slots ----
        // BD's default: shift+click network slot → extract all matching items filling inventory
        // Override: extract only 1 stack via our packet
        if (slot.index >= inventoryEnd) {
            ModLogger.debug("Shift+Click: single-stack extract from BD network");
            PacketDistributor.sendToServer(new BDActionPacket(clickedItem, 0));
            event.setCanceled(true);
        }
    }

    private static void handleSpaceClick(Screen screen, Slot slot, ItemStack clickedItem,
                                          AbstractContainerMenu menu, int inventoryStart, int inventoryEnd,
                                          ScreenEvent.MouseButtonPressed.Pre event) {
        if (slot.index == BDProxy.getResultSlotIndex(menu)) {
            // Space + Click on craft result → mass craft
            ModLogger.debug("Space+Click: mass craft on result slot");
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 1));
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
            PacketDistributor.sendToServer(new TransferMatchingPacket(clickedItem, mode));
        } else {
            // Fallback: use BD's built-in BatchTransferPacket via reflection
            boolean dirToStorage = mode != 0;
            BDProxy.sendBatchTransfer(clickedItem, dirToStorage);
        }

        event.setCanceled(true);
    }
}
