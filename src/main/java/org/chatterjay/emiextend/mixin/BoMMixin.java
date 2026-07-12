package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import org.chatterjay.emiextend.client.BomTreePageHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BoM.class, remap = false)
public abstract class BoMMixin {
    @Inject(method = "setGoal", at = @At("RETURN"))
    private static void emilink$storeGoalOnActiveBookmarkPage(EmiRecipe recipe, CallbackInfo ci) {
        BomTreePageHelper.storeNewActiveTree();
    }
}
