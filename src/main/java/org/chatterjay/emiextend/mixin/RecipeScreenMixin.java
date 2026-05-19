package org.chatterjay.emiextend.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.chatterjay.emiextend.client.handler.WrapAsBookHandler;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.emi.emi.screen.RecipeScreen", remap = false)
public class RecipeScreenMixin {

    @Shadow(remap = false)
    public AbstractContainerScreen<?> old;

    @Shadow(remap = false)
    private int x, y, backgroundWidth;

    @Unique
    private static final int BTN_SIZE = 14;

    @Inject(method = "render", at = @At("RETURN"))
    private void emilink$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!(old instanceof PatternEncodingTermScreen)) return;
        if (!EmiLinkConfig.ENABLE_WRAP_BOOK.get()) return;

        boolean active = WrapAsBookHandler.isActive();
        int btnX = x + backgroundWidth;
        int btnY = y + 5;
        int s = BTN_SIZE;

        Font font = Minecraft.getInstance().font;
        String label = active ? "书" : "WB";
        int textX = btnX + (s - font.width(label)) / 2;
        int textY = btnY + (s - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, textX, textY, active ? 0xFFFFAA : 0xAAAAAA, true);

        if (mouseX >= btnX && mouseX < btnX + s && mouseY >= btnY && mouseY < btnY + s) {
            String tooltipKey = active ? "emilink.tooltip.wrap_as_book.on" : "emilink.tooltip.wrap_as_book.off";
            guiGraphics.renderTooltip(font, Component.translatable(tooltipKey), mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void emilink$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!(old instanceof PatternEncodingTermScreen)) return;
        if (!EmiLinkConfig.ENABLE_WRAP_BOOK.get()) return;
        if (button != 0) return;

        int btnX = x + backgroundWidth;
        int btnY = y + 5;
        int s = BTN_SIZE;

        if (mouseX >= btnX && mouseX < btnX + s && mouseY >= btnY && mouseY < btnY + s) {
            WrapAsBookHandler.toggle();
            cir.setReturnValue(true);
        }
    }
}
