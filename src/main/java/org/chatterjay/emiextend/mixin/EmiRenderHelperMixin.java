package org.chatterjay.emiextend.mixin;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.network.chat.Component;
import org.chatterjay.emiextend.client.NetworkBadgeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the EMI stack amount over for every EMI stack type while the sidebar is
 * drawn, not only for items.
 * <p>
 * EMI draws stack amounts through this one helper, and every stack type calls it:
 * items, fluids, chemicals and any other AE key that a mod shows as an EMI
 * stack. Suppressing the amount inside {@code ItemEmiStack} alone therefore left
 * AE key stacks with both their own amount and EmiLink's network count on the
 * same icon. Cancelling here covers them all, so the network amount is the only
 * number shown and AE keys are rendered exactly like items.
 */
@Mixin(value = EmiRenderHelper.class, remap = false)
public class EmiRenderHelperMixin {

    @Inject(method = "renderAmount", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void emilink$ownSidebarAmount(EmiDrawContext context, int x, int y, Component amount,
                                                 CallbackInfo ci) {
        if (NetworkBadgeRenderState.isSidebarRendering() && NetworkBadgeRenderState.ownsSidebarAmount()) {
            ci.cancel();
        }
    }
}
