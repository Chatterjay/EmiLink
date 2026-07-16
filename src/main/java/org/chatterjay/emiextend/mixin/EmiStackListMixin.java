package org.chatterjay.emiextend.mixin;

import dev.emi.emi.registry.EmiStackList;
import org.chatterjay.emiextend.client.bookmark.MobSeparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After EMI reloads the stack list, capture mob creative tab items.
 */
@Mixin(value = EmiStackList.class, remap = false)
public class EmiStackListMixin {

    @Inject(method = "reload", at = @At("RETURN"))
    private static void emilink$captureMobGroups(CallbackInfo ci) {
        MobSeparator.init();
    }
}
