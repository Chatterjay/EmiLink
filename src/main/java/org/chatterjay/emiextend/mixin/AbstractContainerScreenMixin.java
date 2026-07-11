package org.chatterjay.emiextend.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.chatterjay.emiextend.client.InputEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void emilink$discardMatchingBeforeVanillaDrop(int keyCode, int scanCode, int modifiers,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (InputEvents.tryHandleDiscardMatchingKey(keyCode)) {
            cir.setReturnValue(true);
        }
    }
}
