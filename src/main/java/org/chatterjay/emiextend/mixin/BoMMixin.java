package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import org.chatterjay.emiextend.client.bookmark.BomTreePageHelper;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BoM.class, remap = false)
public abstract class BoMMixin {
    @Inject(method = "setGoal", at = @At("RETURN"))
    private static void emilink$storeGoalOnActiveBookmarkPage(EmiRecipe recipe, CallbackInfo ci) {
        ModLogger.debug("BOM_TREE setGoal recipe={} tree={} craftingMode={} activePage={}",
                recipe == null || recipe.getId() == null ? "null" : recipe.getId(),
                BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                        ? "null"
                        : BoM.tree.goal.recipe.getId(),
                BoM.craftingMode,
                BomTreePageHelper.getActiveFavoritePage());
        BomTreePageHelper.storeNewActiveTree();
    }
}
