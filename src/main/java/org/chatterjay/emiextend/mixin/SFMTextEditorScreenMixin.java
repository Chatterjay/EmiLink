package org.chatterjay.emiextend.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.EmiPort;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class SFMTextEditorScreenMixin {
    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("RETURN"))
    private void emilink$addSfmEditorEmiWidgets(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (minecraft.screen == screen && emilink$isSfmTextEditor(screen)) {
            EmiScreenManager.addWidgets(screen);
        }
    }

    @Inject(method = "resize", at = @At("RETURN"))
    private void emilink$resizeSfmEditorEmiWidgets(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (minecraft.screen == screen && emilink$isSfmTextEditor(screen)) {
            EmiScreenManager.addWidgets(screen);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void emilink$renderEmiOnSfmEditor(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!emilink$isSfmTextEditor(screen)) return;

        emilink$resetRenderStateForEmi(guiGraphics);
        var context = EmiDrawContext.wrap(guiGraphics);
        context.push();
        try {
            EmiPort.setPositionTexShader();
            EmiScreenManager.drawBackground(context, mouseX, mouseY, partialTick);
            EmiScreenManager.render(context, mouseX, mouseY, partialTick);
            EmiScreenManager.drawForeground(context, mouseX, mouseY, partialTick);
        } finally {
            context.pop();
            emilink$resetRenderStateForEmi(guiGraphics);
        }
    }

    private static void emilink$resetRenderStateForEmi(GuiGraphics guiGraphics) {
        guiGraphics.flush();
        guiGraphics.disableScissor();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        EmiPort.setPositionTexShader();
    }

    private static boolean emilink$isSfmTextEditor(Screen screen) {
        String name = screen.getClass().getName();
        return "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1".equals(name)
                || "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2".equals(name);
    }
}
