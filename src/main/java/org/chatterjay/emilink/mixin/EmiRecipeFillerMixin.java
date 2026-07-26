package org.chatterjay.emilink.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiRecipeFiller.class, remap = false)
public class EmiRecipeFillerMixin {
    @Inject(method = "getFirstValidHandler", at = @At("HEAD"), cancellable = true, require = 0)
    private static <T extends AbstractContainerMenu> void emilink$skipNullScreenRecipeFill(
            EmiRecipe recipe,
            AbstractContainerScreen<T> screen,
            CallbackInfoReturnable<EmiRecipeHandler<T>> cir) {
        if (screen == null) {
            cir.setReturnValue(null);
        }
    }
}
