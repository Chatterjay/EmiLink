package org.chatterjay.emiextend.mixin;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.chatterjay.emiextend.client.handler.BookmarkPriorityHandler;
import org.chatterjay.emiextend.config.EmiLinkConfig;
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
            if (!doTransfer) return;

            // For custom EMI recipes (e.g. forge rituals) there is no Vanilla RecipeHolder.
            // Still set the recipe tree search key and apply bookmark priority.
            if (holder == null || holder.value() == null) {
                if (emiRecipe != null) {
                    ModLogger.info("Pattern written (custom): category={} id={}",
                            emiRecipe.getCategory().getId(), emiRecipe.getId());
                    ProviderSearchHelper.setFromEmiRecipe(emiRecipe);
                    if (EmiLinkConfig.BOOKMARK_PRIORITY.get()) {
                        BookmarkPriorityHandler.applyBookmarkPriority(
                                menu.getProcessingInputSlots(), emiRecipe.getInputs());
                    }
                }
                return;
            }

            Recipe<?> recipe = holder.value();
            ModLogger.info("Pattern written: recipe={} id={}", recipe.getClass().getName(), holder.id());

            if (recipe.getType() == RecipeType.CRAFTING) {
                ProviderSearchHelper.presetCraftingProviderSearchKey();
                if (EmiLinkConfig.BOOKMARK_PRIORITY.get()) {
                    BookmarkPriorityHandler.applyBookmarkPriority(menu.getCraftingGridSlots(), emiRecipe.getInputs());
                }
            } else {
                String name = ProviderSearchHelper.mapRecipeTypeToSearchKey(recipe);
                if (name != null && !name.isBlank()) {
                    ProviderSearchHelper.setLastProcessingName(name);
                }
                if (EmiLinkConfig.BOOKMARK_PRIORITY.get()) {
                    BookmarkPriorityHandler.applyBookmarkPriority(menu.getProcessingInputSlots(), emiRecipe.getInputs());
                }
            }
        } catch (Throwable t) {
            ModLogger.warn("EmiEncodePatternHandlerMixin error: {}: {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
