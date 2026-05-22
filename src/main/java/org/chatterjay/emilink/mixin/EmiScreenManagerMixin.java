package org.chatterjay.emilink.mixin;

import dev.emi.emi.screen.EmiScreenManager;
import org.chatterjay.emilink.client.handler.EmiInteractionHandler;
import org.chatterjay.emilink.util.ModLogger;
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
        ModLogger.info("EmiScreenManagerMixin: keyPressed keyCode={} scanCode={} handled={}",
                keyCode, scanCode, EmiInteractionHandler.onKeyPressed(keyCode, scanCode, modifiers, lastMouseX, lastMouseY));
        if (EmiInteractionHandler.onKeyPressed(keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        ModLogger.info("EmiScreenManagerMixin: mouseReleased button={} at ({},{})", button, mouseX, mouseY);
        if (EmiInteractionHandler.onMouseReleased(mouseX, mouseY, button)) {
            ModLogger.info("EmiScreenManagerMixin: mouseReleased handled by EmiInteractionHandler");
            cir.setReturnValue(true);
        }
    }
}
