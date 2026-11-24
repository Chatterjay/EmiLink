package org.chatterjay.emiextend.mixin;

import appeng.core.AEConfig;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes AE2's "expose the network inventory to EMI" switch opt-in.
 *
 * <p>With the switch on, AE2 hands every stored stack of the terminal's network to
 * EMI, so EMI considers the whole network when it computes craftables, missing
 * materials and recipe availability. EMI re-derives that view whenever the player
 * inventory changes, which on a large network means rebuilding the craftable list
 * over thousands of stacks several times per second.</p>
 *
 * <p>EmiLink used to force the switch on whenever AE2 was installed, so players on
 * large networks had no way out. The {@code ae_network.exposeNetworkInventoryToEmi}
 * option now decides, and it is off by default: the mixin then leaves AE2's own
 * {@code provideNetworkInventoryToEmi} option in charge.</p>
 */
@Mixin(AEConfig.class)
public class AEConfigMixin {

    @Inject(method = "isExposeNetworkInventoryToEmi", at = @At("HEAD"), cancellable = true, remap = false)
    private void emilink$maybeExposeNetworkInventory(CallbackInfoReturnable<Boolean> cir) {
        // Reading a config value before the client config is loaded throws.
        if (!EmiLinkConfig.isClientConfigLoaded()) return;
        if (EmiLinkConfig.EXPOSE_NETWORK_INVENTORY_TO_EMI.get()) {
            cir.setReturnValue(true);
        }
    }
}
