package org.chatterjay.emiextend.mixin;

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
    private void emiextend$onDrawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(-offsetX, -offsetY, 0);

        var ctx = EmiDrawContext.wrap(guiGraphics);
        EmiScreenManager.drawBackground(ctx, mouseX, mouseY, 0);

        pose.popPose();
    }
}
