package org.chatterjay.emiextend.mixin;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.chatterjay.emiextend.util.ModLogger;
import org.chatterjay.emiextend.util.ProviderSearchHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public class EmiEncodePatternHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("RETURN"), require = 0)
    private void emilink$afterTransfer(PatternEncodingTermMenu menu, RecipeHolder<?> holder, EmiRecipe emiRecipe, boolean doTransfer, CallbackInfoReturnable<?> cir) {
        try {
            if (!doTransfer || holder == null || holder.value() == null) return;

            Recipe<?> recipe = holder.value();
            ModLogger.info("Pattern written: recipe={} id={}", recipe.getClass().getName(), holder.id());

            if (EncodingHelper.isSupportedCraftingRecipe(recipe)) {
                ProviderSearchHelper.presetCraftingProviderSearchKey();
                ModLogger.debug("RecipeType pre-fill: set crafting preset");
            } else {
                String name = ProviderSearchHelper.mapRecipeTypeToSearchKey(recipe);
                if (name != null && !name.isBlank()) {
                    ProviderSearchHelper.setLastProcessingName(name);
                    ModLogger.debug("RecipeType pre-fill: set '{}' for {}", name, holder.id());
                } else {
                    ModLogger.debug("RecipeType pre-fill: no mapping for {}", holder.id());
                }
            }
        } catch (Throwable t) {
            ModLogger.warn("EmiEncodePatternHandlerMixin error: {}", t.getMessage());
        }
    }
}
