package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.runtime.EmiSidebars;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(value = EmiApi.class, remap = false)
public class EmiApiMixin {
    @Inject(method = "setPages", at = @At("HEAD"), cancellable = true)
    private static void emilink$openRecipesFromSfmEditor(
            Map<EmiRecipeCategory, List<EmiRecipe>> recipes,
            EmiIngredient stack,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (!emilink$isSfmTextEditor(screen)) return;

        recipes = recipes.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (recipes.isEmpty()) {
            ci.cancel();
            return;
        }

        EmiSidebars.lookup(stack);
        EmiHistory.push(screen);
        minecraft.setScreen(new RecipeScreen(null, recipes));
        ci.cancel();
    }

    private static boolean emilink$isSfmTextEditor(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1".equals(name)
                || "ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2".equals(name);
    }
}
