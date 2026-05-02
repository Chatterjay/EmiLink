package org.chatterjay.emiextend.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EmiScreenManager.class)
public class EmiScreenManagerMixin {
    @Shadow
    private static int lastMouseX;
    @Shadow
    private static int lastMouseY;

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, remap = false)
    private static void emiextend$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof CraftConfirmScreen ccs) {
            StackWithBounds swb = ccs.getStackUnderMouse(lastMouseX, lastMouseY);
            if (swb != null && swb.stack().what() instanceof AEItemKey itemKey) {
                EmiStack emiStack = EmiStack.of(itemKey.toStack());
                if (EmiScreenManager.stackInteraction(new EmiStackInteraction(emiStack), bind -> bind.matchesKey(keyCode, scanCode))) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
