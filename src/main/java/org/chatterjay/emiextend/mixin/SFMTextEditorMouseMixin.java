package org.chatterjay.emiextend.mixin;

import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class SFMTextEditorMouseMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;
    @Shadow
    private int activeButton;
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(
            method = "onPress",
            at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/gui/screens/Screen;wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"),
            cancellable = true
    )
    private void emilink$onSfmEditorMouseDown(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        try {
            if (emilink$isSfmTextEditor(minecraft.screen)) {
                double mouseX = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
                double mouseY = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
                if (EmiScreenManager.mouseClicked(mouseX, mouseY, button)) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling SFM editor EMI mouse press", e);
        }
    }

    @Inject(
            method = "onPress",
            at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/screens/Screen;wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"),
            cancellable = true
    )
    private void emilink$onSfmEditorMouseUp(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        try {
            if (emilink$isSfmTextEditor(minecraft.screen)) {
                double mouseX = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
                double mouseY = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
                if (EmiScreenManager.mouseReleased(mouseX, mouseY, button)) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling SFM editor EMI mouse release", e);
        }
    }

    @Inject(
            method = "turnPlayer",
            at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/screens/Screen;wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"),
            cancellable = true,
            require = 0
    )
    private void emilink$onSfmEditorMouseDragged(CallbackInfo ci) {
        try {
            if (emilink$isSfmTextEditor(minecraft.screen)) {
                double mouseX = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
                double mouseY = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
                double deltaX = accumulatedDX * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
                double deltaY = accumulatedDY * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
                EmiScreenManager.mouseDragged(mouseX, mouseY, activeButton, deltaX, deltaY);
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling SFM editor EMI mouse drag", e);
        }
    }

    @Inject(
            method = "onScroll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"),
            cancellable = true
    )
    private void emilink$onSfmEditorMouseScrolled(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        try {
            if (emilink$isSfmTextEditor(minecraft.screen)) {
                double amount = (minecraft.options.discreteMouseScroll().get() ? Math.signum(yOffset) : yOffset)
                        * minecraft.options.mouseWheelSensitivity().get();
                double mouseX = xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
                double mouseY = ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
                if (EmiScreenManager.mouseScrolled(mouseX, mouseY, amount)) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling SFM editor EMI mouse scroll", e);
        }
    }

    private static boolean emilink$isSfmTextEditor(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1".equals(name)
                || "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2".equals(name);
    }
}
