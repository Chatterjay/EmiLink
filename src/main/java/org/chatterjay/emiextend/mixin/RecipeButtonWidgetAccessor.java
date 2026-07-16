package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.widget.RecipeButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RecipeButtonWidget.class, remap = false)
public interface RecipeButtonWidgetAccessor {
    @Accessor("recipe")
    EmiRecipe emilink$getRecipe();
}
