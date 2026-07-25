package org.chatterjay.emiextend.mixin;

import dev.emi.emi.screen.widget.EmiSearchWidget;
import org.chatterjay.emiextend.client.search.SearchHistoryOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiSearchWidget.class, remap = false)
public class EmiSearchWidgetMixin {
    @Unique
    private boolean emilink$wasFocused;

    @Inject(method = "setFocused", at = @At("HEAD"), require = 0)
    private void emilink$rememberSearchOnBlur(boolean focused, CallbackInfo ci) {
        EmiSearchWidget self = (EmiSearchWidget) (Object) this;
        if (emilink$wasFocused && !focused) {
            SearchHistoryOverlay.remember(self.getValue());
        }
        emilink$wasFocused = focused;
    }
}
