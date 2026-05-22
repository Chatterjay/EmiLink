package org.chatterjay.emilink.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.chatterjay.emilink.util.ModLogger;
import org.chatterjay.emilink.util.ProviderSearchHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.emi.emi.screen.RecipeScreen", remap = false)
public class RecipeScreenMixin {

    @Shadow(remap = false)
    public AbstractContainerScreen<?> old;

    @Shadow(remap = false)
    public EmiRecipeCategory getFocusedCategory() { return null; }

    @Unique
    private String emilink$lastProviderSearchKey;

    @Inject(method = "render", at = @At("RETURN"))
    private void emilink$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!(old instanceof PatternEncodingTermScreen)) return;

        try {
            EmiRecipeCategory category = getFocusedCategory();
            if (category != null && category.getId() != null) {
                String key = category.getId().getPath();
                if (!key.equals(emilink$lastProviderSearchKey)) {
                    emilink$lastProviderSearchKey = key;
                    ProviderSearchHelper.setLastProcessingName(key);
                    ModLogger.debug("RecipeScreen: set provider search key to '{}'", key);
                }
            }
        } catch (Throwable e) {
            ModLogger.warn("RecipeScreen: failed to get focused category: {}", e.getMessage());
        }
    }
}
