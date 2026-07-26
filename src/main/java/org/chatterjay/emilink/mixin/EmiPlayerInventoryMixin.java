package org.chatterjay.emilink.mixin;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import net.minecraft.world.entity.player.Player;
import org.chatterjay.emilink.client.BDEmiInventoryAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiPlayerInventory.class, remap = false)
public class EmiPlayerInventoryMixin {
    @Inject(method = "of", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$useBdNetworkInventory(Player player, CallbackInfoReturnable<EmiPlayerInventory> cir) {
        EmiPlayerInventory bdInventory = BDEmiInventoryAdapter.createInventoryForCurrentBdContext();
        if (bdInventory != null) {
            cir.setReturnValue(bdInventory);
        }
    }
}
