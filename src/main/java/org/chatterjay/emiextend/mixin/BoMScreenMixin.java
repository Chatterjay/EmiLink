package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.chatterjay.emiextend.client.BDEmiInventoryAdapter;
import org.chatterjay.emiextend.client.BomTreePageHelper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void emilink$syncPageTreeAfterKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        BomTreePageHelper.syncActiveModeFromBoM();
        BomTreePageHelper.refreshActiveSynthetic();
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void emilink$syncPageTreeAfterClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        BomTreePageHelper.syncActiveModeFromBoM();
        BomTreePageHelper.refreshActiveSynthetic();
    }

    @Inject(method = "mouseScrolled", at = @At("RETURN"))
    private void emilink$syncPageTreeAfterScroll(double mouseX, double mouseY, double horizontal, double amount, CallbackInfoReturnable<Boolean> cir) {
        BomTreePageHelper.syncActiveModeFromBoM();
        BomTreePageHelper.refreshActiveSynthetic();
    }
}
