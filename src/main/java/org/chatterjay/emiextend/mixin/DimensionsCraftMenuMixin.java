package org.chatterjay.emiextend.mixin;

import org.chatterjay.emiextend.integration.BDProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu", remap = false)
public class DimensionsCraftMenuMixin {
    @ModifyVariable(method = "cleanCraftSlots", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean emilink$keepQuickCraftReturnsInNetwork(boolean toStorageFirst) {
        if (BDProxy.isQuickCraftReturningToInventory((Object) this)) {
            return true;
        }
        return toStorageFirst;
    }

    @Inject(method = "transferRecipe", at = @At("HEAD"), cancellable = true)
    private void emilink$transferQuickCraftRecipeFromNetworkOnly(List<?> inputKeys, List<?> amount, CallbackInfo ci) {
        if (BDProxy.isQuickCraftReturningToInventory((Object) this)) {
            BDProxy.transferRecipeFromNetworkOnly((Object) this, inputKeys, amount);
            ci.cancel();
        }
    }
}
