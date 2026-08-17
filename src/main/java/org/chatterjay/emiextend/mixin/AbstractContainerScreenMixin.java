package org.chatterjay.emiextend.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.client.bookmark.BomFavoriteQuickCraftButton;
import org.chatterjay.emiextend.client.search.SearchHistoryOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void emilink$beginSearchHistoryHoverSuppression(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                            float partialTick, CallbackInfo ci) {
        SearchHistoryOverlay.setSuppressUnderlyingHover(SearchHistoryOverlay.isMouseOver(mouseX, mouseY));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void emilink$endSearchHistoryHoverSuppression(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                          float partialTick, CallbackInfo ci) {
        SearchHistoryOverlay.setSuppressUnderlyingHover(false);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void emilink$discardMatchingBeforeVanillaDrop(int keyCode, int scanCode, int modifiers,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (InputEvents.tryHandleDiscardMatchingKey(keyCode, scanCode)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void emilink$clickFavoriteBomQuickCraftPanel(double mouseX, double mouseY, int button,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (BomFavoriteQuickCraftButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderSlotHighlight", at = @At("HEAD"), cancellable = true)
    private static void emilink$hideSlotHighlightBehindSearchHistory(GuiGraphics guiGraphics, int x, int y, int blitOffset,
                                                                     CallbackInfo ci) {
        if (SearchHistoryOverlay.shouldSuppressUnderlyingHover()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void emilink$hideSlotTooltipBehindSearchHistory(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                            CallbackInfo ci) {
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
