package org.chatterjay.emiextend.mixin;

import dev.emi.emi.screen.WidgetGroup;
import org.chatterjay.emiextend.client.handler.EmiWrapButton;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = dev.emi.emi.screen.RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin {
    @Shadow(remap = false)
    private int backgroundWidth;

    @Shadow(remap = false)
    private int x;

    @Shadow(remap = false)
    private int y;

    @Shadow(remap = false)
    private List<WidgetGroup> currentPage;

    @Unique
    private EmiWrapButton emilink$wrapWidget;

    @Unique
    private void emilink$addWidgetToPage() {
        if (!EmiLinkConfig.ENABLE_WRAP_BOOK.get()) return;
        if (currentPage == null || currentPage.isEmpty()) return;
        WidgetGroup first = currentPage.getFirst();

        // Remove old widget if present
        if (emilink$wrapWidget != null) {
            first.widgets.remove(emilink$wrapWidget);
        }

        // Widget coordinates are relative to WidgetGroup origin
        int relX = (x + backgroundWidth + 2) - first.x;
        int relY = (y + 10) - first.y;
        emilink$wrapWidget = new EmiWrapButton(relX, relY, 12, 12);
        first.widgets.add(emilink$wrapWidget);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void emilink$initWidget(CallbackInfo ci) {
        var self = (dev.emi.emi.screen.RecipeScreen) (Object) this;
        if (!AE2Proxy.isPatternEncodingTermScreen(self.old)) return;

        emilink$addWidgetToPage();
    }

    @Inject(method = "setPage(III)V", at = @At("RETURN"))
    private void emilink$onSetPage(CallbackInfo ci) {
        var self = (dev.emi.emi.screen.RecipeScreen) (Object) this;
        if (!AE2Proxy.isPatternEncodingTermScreen(self.old)) return;
        emilink$addWidgetToPage();
    }
}
