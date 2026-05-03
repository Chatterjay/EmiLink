package org.chatterjay.emiextend.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.items.tools.powered.WirelessTerminalItem;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        if (mc.screen instanceof CraftConfirmScreen ccs) {
            StackWithBounds swb = ccs.getStackUnderMouse(lastMouseX, lastMouseY);
            if (swb != null && swb.stack().what() instanceof AEItemKey itemKey) {
                EmiStack emiStack = EmiStack.of(itemKey.toStack());
                if (EmiScreenManager.stackInteraction(new EmiStackInteraction(emiStack), bind -> bind.matchesKey(keyCode, scanCode))) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 2 && button != 0) return;

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered == null || hovered.isEmpty()) return;

        var itemStack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
        if (itemStack == null) return;

        if (button == 2) {
            handleMiddleClick(itemStack, cir);
        } else if (button == 0 && Screen.hasShiftDown()) {
            handleShiftClick(itemStack, cir);
        }
    }

    private static void handleMiddleClick(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        var aeKey = AEItemKey.of(itemStack);
        if (aeKey == null) return;

        var player = Minecraft.getInstance().player;
        if (player == null || !hasWirelessTerminal(player)) return;

        if (EAEPProxy.openCraftScreen(aeKey)) {
            ModLogger.debug("Middle-click: opened craft screen for {}", itemStack.getHoverName().getString());
            cir.setReturnValue(true);
        }
    }

    private static void handleShiftClick(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        if (!hasWirelessTerminal(player)) return;

        var aeKey = AEItemKey.of(itemStack);
        if (aeKey == null) return;

        if (EAEPProxy.pullFromNetwork(aeKey)) {
            ModLogger.debug("Shift-click: pulled {} from network", itemStack.getHoverName().getString());
            cir.setReturnValue(true);
        }
    }

    private static boolean hasWirelessTerminal(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).getItem() instanceof WirelessTerminalItem) {
                return true;
            }
        }
        if (player.getOffhandItem().getItem() instanceof WirelessTerminalItem) {
            return true;
        }
        return CuriosProxy.hasWirelessTerminal(player, WirelessTerminalItem.class);
    }
}
