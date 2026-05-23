package org.chatterjay.emilink.mixin;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftConfirmScreen.class)
public class CraftConfirmScreenMixin {

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void emilink$onDrawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        // Revert the container-relative translation so EMI sidebar renders in screen space
        pose.translate(-offsetX, -offsetY, 0);

        var ctx = EmiDrawContext.wrap(guiGraphics);
        EmiScreenManager.drawBackground(ctx, mouseX, mouseY, 0);
        EmiScreenManager.render(ctx, mouseX, mouseY, 0);
        EmiScreenManager.drawForeground(ctx, mouseX, mouseY, 0);

        pose.popPose();
    }
}
