package org.chatterjay.emiextend.mixin;

import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class SFMTextEditorKeyboardMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "keyPress",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;wrapScreenError(Ljava/lang/Runnable;Ljava/lang/String;Ljava/lang/String;)V"),
            cancellable = true
    )
    private void emilink$onSfmEditorKey(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        try {
            if (emilink$isSfmTextEditor(minecraft.screen) && (action == 1 || action == 2)) {
                if (EmiScreenManager.keyPressed(key, scanCode, modifiers)) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling SFM editor EMI key press", e);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void emilink$onSfmEditorChar(long windowPointer, int codePoint, int modifiers, CallbackInfo ci) {
        try {
            if (windowPointer == minecraft.getWindow().getWindow()
                    && emilink$isSfmTextEditor(minecraft.screen)
                    && minecraft.getOverlay() == null) {
                boolean consume = false;
                if (Character.charCount(codePoint) == 1) {
                    consume = EmiScreenManager.search.charTyped((char) codePoint, modifiers);
                } else {
                    for (char c : Character.toChars(codePoint)) {
                        consume = EmiScreenManager.search.charTyped(c, modifiers) || consume;
                    }
                }
                if (consume) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            EmiLog.error("Error while handling SFM editor EMI char input", e);
        }
    }

    private static boolean emilink$isSfmTextEditor(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1".equals(name)
                || "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2".equals(name);
    }
}
