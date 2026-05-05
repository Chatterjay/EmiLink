package org.chatterjay.emiextend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.util.ModLogger;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT)
public class BDShortcutHandler {

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        // Prevent Space key from triggering focused BD buttons
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
        if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)) return;

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

        if (slot.index == BDProxy.getResultSlotIndex(menu)) {
            // Space + Click on craft result → mass craft (client side)
            ModLogger.debug("Space+Click: mass craft on result slot");
            BDProxy.massCraft(player);
            event.setCanceled(true);
            return;
        }

        // Crafting grid slots → ignore Space+Click entirely (let BD handle normally)
        if (BDProxy.isBDCraftGUI(screen)) {
            int resultSlotIdx = BDProxy.getResultSlotIndex(menu);
            if (resultSlotIdx >= 0 && slot.index > resultSlotIdx) return;
        }

        boolean isPlayerSlot = slot.index >= inventoryStart && slot.index < inventoryEnd;

        if (isPlayerSlot) {
            // Deposit to BD network (client side)
            int mode = (slot.getSlotIndex() >= 0 && slot.getSlotIndex() < 9) ? 2 : 1;
            ModLogger.debug("Space+Click: deposit to BD network mode={}", mode);
            BDProxy.depositToNetwork(player, mode);
        } else {
            // Extract from BD network (client side)
            ModLogger.debug("Space+Click: extract from BD network");
            BDProxy.extractAllFromNetwork(player, clickedItem);
        }

        event.setCanceled(true);
    }
}
