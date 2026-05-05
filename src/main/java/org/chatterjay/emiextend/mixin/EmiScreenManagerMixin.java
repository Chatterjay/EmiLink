package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.integration.CuriosProxy;
import org.chatterjay.emiextend.integration.EAEPProxy;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {
    @Shadow
    private static int lastMouseX;
    @Shadow
    private static int lastMouseY;

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;

        var mc = Minecraft.getInstance();
        if (AE2Proxy.isCraftConfirmScreen(mc.screen)) {
            ItemStack stack = AE2Proxy.getStackUnderMouse(mc.screen, lastMouseX, lastMouseY);
            if (!stack.isEmpty()) {
                EmiStack emiStack = EmiStack.of(stack);
                if (EmiScreenManager.stackInteraction(new EmiStackInteraction(emiStack), bind -> bind.matchesKey(keyCode, scanCode))) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // Only handle left-click (0) with shift, or middle-click (2)
        if (button != 2 && button != 0) return;

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered == null || hovered.isEmpty()) return;

        var itemStack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
        if (itemStack == null) return;

        // ---- AE2 / EAEP integration ----
        if (button == 2) {
            handleMiddleClick(itemStack, cir);
            if (cir.isCancelled()) return;
        } else if (button == 0 && Screen.hasShiftDown()) {
            // If on a BD screen, try BD first
            var mc = Minecraft.getInstance();
            boolean isBDScreen = mc.screen != null && (BDProxy.isBDNetGUI(mc.screen) || BDProxy.isBDCraftGUI(mc.screen));
            ModLogger.debug("Shift-click: isBDScreen={} screen={}", isBDScreen, mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");

            if (isBDScreen) {
                handleShiftClickBD(itemStack, cir);
                if (cir.isCancelled()) return;
                // Fall through to AE2 if BD didn't handle it
                handleShiftClickAE2(itemStack, cir);
            } else {
                // --- Try AE2/EAEP first ---
                handleShiftClickAE2(itemStack, cir);
                if (cir.isCancelled()) return;

                // --- Then try BD ---
                handleShiftClickBD(itemStack, cir);
            }
        }
    }

    // ---- AE2 / EAEP handlers ----

    private static void handleMiddleClick(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player == null || !hasWirelessTerminal(player)) return;

        if (EAEPProxy.openCraftScreen(itemStack)) {
            ModLogger.debug("Middle-click: opened AE2 craft screen for {}", itemStack.getHoverName().getString());
            cir.setReturnValue(true);
        }
    }

    private static void handleShiftClickAE2(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!hasWirelessTerminal(player)) return;

        if (EAEPProxy.pullFromNetwork(itemStack)) {
            ModLogger.debug("Shift-click: pulled {} from AE2 network", itemStack.getHoverName().getString());
            cir.setReturnValue(true);
        }
    }

    private static boolean hasWirelessTerminal(Player player) {
        if (!AE2Proxy.isLoaded()) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (AE2Proxy.isWirelessTerminal(inventory.items.get(i))) {
                return true;
            }
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) {
            return true;
        }
        Class<?> wtClass = AE2Proxy.getWirelessTerminalClass();
        return wtClass != null && CuriosProxy.hasWirelessTerminal(player, wtClass);
    }

    // ---- BD handler ----

    private static void handleShiftClickBD(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        var mc = Minecraft.getInstance();
        var screen = mc.screen;
        if (screen == null) return;

        // Handle both BD storage GUI and craft terminal
        if (!BDProxy.isBDNetGUI(screen) && !BDProxy.isBDCraftGUI(screen)) return;

        var player = mc.player;
        if (player == null) return;

        BDProxy.pullFromNetwork(itemStack);
        ModLogger.debug("Shift-click: sending BD extract request for {}", itemStack.getHoverName().getString());
        cir.setReturnValue(true);
    }
}
