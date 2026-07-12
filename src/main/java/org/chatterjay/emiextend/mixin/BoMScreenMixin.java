package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.chatterjay.emiextend.client.BDEmiInventoryAdapter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BoMScreen.class, remap = false)
public class BoMScreenMixin {
    @Shadow
    public AbstractContainerScreen<?> old;

    @Shadow
    private EmiPlayerInventory playerInv;

    @Inject(
            method = "recalculateTree",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/emi/emi/screen/BoMScreen;playerInv:Ldev/emi/emi/api/recipe/EmiPlayerInventory;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void emilink$useBdInventoryForBoM(CallbackInfo ci) {
        EmiPlayerInventory bdInventory = BDEmiInventoryAdapter.createBoMInventory(old);
        if (bdInventory != null) {
            playerInv = bdInventory;
        }
    }
}
