package org.chatterjay.emiextend.mixin;

import appeng.core.AEConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEConfig.class)
public class AEConfigMixin {

    @Inject(method = "isExposeNetworkInventoryToEmi", at = @At("HEAD"), cancellable = true, remap = false)
    private void emilink$forceExposeNetworkInventory(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
