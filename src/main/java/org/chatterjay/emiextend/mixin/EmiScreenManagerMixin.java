package org.chatterjay.emiextend.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.GridInventoryEntry;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 2) return;

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered == null || hovered.isEmpty()) return;

        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof appeng.client.gui.me.common.MEStorageScreen<?> meScreen)) return;

        var itemStack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
        if (itemStack == null) return;

        var aeKey = AEItemKey.of(itemStack);
        if (aeKey == null) return;

        var menu = meScreen.getMenu();
        var repo = menu.getClientRepo();
        if (repo == null) return;

        for (GridInventoryEntry entry : repo.getAllEntries()) {
            if (aeKey.equals(entry.getWhat()) && entry.isCraftable()) {
                ModLogger.debug("Middle-click: AUTO_CRAFT for {} (serial={})",
                        itemStack.getHoverName().getString(), entry.getSerial());
                menu.handleInteraction(entry.getSerial(), InventoryAction.AUTO_CRAFT);
                cir.setReturnValue(true);
                return;
            }
        }

        ModLogger.debug("Middle-click: {} not found in terminal", itemStack.getHoverName().getString());
    }
}
